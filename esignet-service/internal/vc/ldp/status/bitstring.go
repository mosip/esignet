/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package status checks W3C BitstringStatusList entries. It fails closed: a
// list that can't be fetched, verified or decoded is an error, never "not
// revoked".
package status

import (
	"bytes"
	"compress/gzip"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/mosip/esignet/internal/vc/ldp/fetch"
	"github.com/mosip/esignet/internal/vc/ldp/jsonval"
)

const (
	entryType         = "BitstringStatusListEntry"
	listType          = "BitstringStatusList"
	maxDecompressed   = 32 << 20
	maxStatusSize     = 16
	statusListAccept  = "application/vc+ld+json, application/ld+json, application/json"
	validStatusValue  = 0
	defaultStatusSize = 1
)

// ErrStatus reports a status entry that could not be checked.
var ErrStatus = errors.New("credential status check failed")

// Result is the status of one purpose.
type Result struct {
	Valid bool // the status value is 0
	Value int  // the raw status value (statusSize bits)
}

// ListVerifier verifies a fetched status list credential (signature,
// structure, validity window at now) and returns its issuer.
type ListVerifier func(ctx context.Context, listVC map[string]any, now time.Time) (issuer string, err error)

// Checker fetches, verifies and reads status lists.
type Checker struct {
	fetch *fetch.Fetcher
	cache *fetch.Cache[*list]
}

// list is a verified, decoded status list credential.
type list struct {
	issuer     string
	purposes   []string // lower-cased credentialSubject.statusPurpose
	statusSize int      // from credentialSubject, if set
	bits       []byte
}

// NewChecker returns a Checker. Verified lists are cached for ttl, or until
// their validUntil or expirationDate if sooner.
func NewChecker(f *fetch.Fetcher, ttl time.Duration, cacheSize int) *Checker {
	return &Checker{fetch: f, cache: fetch.NewCache[*list](ttl, cacheSize)}
}

// Request is one status check.
type Request struct {
	Purposes []string
	// Verify verifies a status list credential. The verified list is cached
	// per URL, so it must not depend on per-call policy beyond the clock.
	Verify ListVerifier
	// Issuer is the credential's issuer. A status list from anyone else
	// cannot speak for the credential.
	Issuer string
	Now    time.Time
}

// Check reads every credentialStatus entry of vc whose statusPurpose is in
// r.Purposes. A purpose with several entries is invalid if any of them is. A
// credential without credentialStatus has no status to check and returns an
// empty map.
func (c *Checker) Check(ctx context.Context, vc map[string]any, r Request) (map[string]Result, error) {
	raw, present := vc["credentialStatus"]
	if !present {
		return map[string]Result{}, nil
	}
	var entries []map[string]any
	for _, e := range jsonval.List(raw) {
		if m, ok := e.(map[string]any); ok {
			entries = append(entries, m)
		}
	}
	if len(entries) == 0 {
		return nil, fmt.Errorf("%w: no valid credentialStatus entries", ErrStatus)
	}
	wanted := map[string]bool{}
	for _, p := range r.Purposes {
		wanted[strings.ToLower(p)] = true
	}
	results := map[string]Result{}
	for _, entry := range entries {
		purpose := strings.ToLower(jsonval.String(entry["statusPurpose"]))
		if !wanted[purpose] {
			continue
		}
		res, err := c.checkEntry(ctx, entry, purpose, r)
		if err != nil {
			return nil, fmt.Errorf("%s: %w", purpose, err)
		}
		if prev, seen := results[purpose]; seen && !prev.Valid {
			continue
		}
		results[purpose] = res
	}
	return results, nil
}

func (c *Checker) checkEntry(ctx context.Context, entry map[string]any, purpose string, r Request) (Result, error) {
	if t := jsonval.String(entry["type"]); t != entryType {
		return Result{}, fmt.Errorf("%w: credentialStatus.type %q, want %s", ErrStatus, t, entryType)
	}
	listURL := jsonval.String(entry["statusListCredential"])
	if listURL == "" {
		return Result{}, fmt.Errorf("%w: statusListCredential is missing", ErrStatus)
	}
	index, err := nonNegativeInt(entry["statusListIndex"])
	if err != nil {
		return Result{}, fmt.Errorf("%w: statusListIndex: %w", ErrStatus, err)
	}
	l, err := c.list(ctx, listURL, r)
	if err != nil {
		return Result{}, err
	}
	if l.issuer != r.Issuer {
		return Result{}, fmt.Errorf("%w: status list issuer %s is not the credential issuer %s", ErrStatus, l.issuer, r.Issuer)
	}
	if !slices.Contains(l.purposes, purpose) {
		return Result{}, fmt.Errorf("%w: status list purpose %q, want %q", ErrStatus, l.purposes, purpose)
	}
	size, err := statusSize(entry, l)
	if err != nil {
		return Result{}, err
	}
	if size > 1 {
		if err := checkStatusMessages(entry["statusMessage"], size); err != nil {
			return Result{}, err
		}
	}
	if index >= len(l.bits)*8/size {
		return Result{}, fmt.Errorf("%w: statusListIndex %d out of range", ErrStatus, index)
	}
	value, err := readBits(l.bits, index*size, size)
	if err != nil {
		return Result{}, err
	}
	return Result{Valid: value == validStatusValue, Value: value}, nil
}

// statusSize takes the entry's statusSize (W3C), falling back to the list's
// credentialSubject, then 1.
func statusSize(entry map[string]any, l *list) (int, error) {
	size := l.statusSize
	if v, ok := entry["statusSize"]; ok {
		n, err := nonNegativeInt(v)
		if err != nil {
			return 0, fmt.Errorf("%w: statusSize: %w", ErrStatus, err)
		}
		size = n
	}
	if size == 0 {
		size = defaultStatusSize
	}
	if size > maxStatusSize {
		return 0, fmt.Errorf("%w: statusSize %d is too large", ErrStatus, size)
	}
	return size, nil
}

// checkStatusMessages requires 2^size statusMessage entries (array per the
// W3C spec, or an object keyed by status value).
func checkStatusMessages(v any, size int) error {
	n := -1
	switch m := v.(type) {
	case []any:
		n = len(m)
	case map[string]any:
		n = len(m)
	}
	if want := 1 << size; n != want {
		return fmt.Errorf("%w: statusMessage must have %d entries for statusSize=%d", ErrStatus, want, size)
	}
	return nil
}

func (c *Checker) list(ctx context.Context, listURL string, r Request) (*list, error) {
	if l, ok := c.cache.Get(listURL); ok {
		return l, nil
	}
	doc, err := c.fetch.GetJSON(ctx, listURL, statusListAccept)
	if err != nil {
		return nil, fmt.Errorf("%w: fetch status list: %w", ErrStatus, err)
	}
	issuer, err := r.Verify(ctx, doc, r.Now)
	if err != nil {
		return nil, fmt.Errorf("%w: status list credential: %w", ErrStatus, err)
	}
	subject, ok := doc["credentialSubject"].(map[string]any)
	if !ok {
		return nil, fmt.Errorf("%w: status list credentialSubject is not an object", ErrStatus)
	}
	notAfter, err := checkSubjectWindow(subject, r.Now)
	if err != nil {
		return nil, err
	}
	if t := jsonval.String(subject["type"]); t != listType {
		return nil, fmt.Errorf("%w: status list type %q, want %s", ErrStatus, t, listType)
	}
	l := &list{issuer: issuer}
	for _, p := range jsonval.Strings(subject["statusPurpose"]) {
		l.purposes = append(l.purposes, strings.ToLower(p))
	}
	if v, ok := subject["statusSize"]; ok {
		if l.statusSize, err = nonNegativeInt(v); err != nil {
			return nil, fmt.Errorf("%w: status list statusSize: %w", ErrStatus, err)
		}
	}
	if l.bits, err = decodeList(jsonval.String(subject["encodedList"])); err != nil {
		return nil, err
	}
	// validUntil (VCDM 2.0) or expirationDate (VCDM 1.1): a cached list must
	// not outlive the credential.
	for _, field := range []string{"validUntil", "expirationDate"} {
		if t, ok := jsonval.Time(doc[field]); ok && (notAfter.IsZero() || t.Before(notAfter)) {
			notAfter = t
		}
	}
	c.cache.PutUntil(listURL, l, notAfter)
	return l, nil
}

// checkSubjectWindow applies the credentialSubject validFrom/validUntil,
// returning validUntil (zero if absent).
func checkSubjectWindow(subject map[string]any, now time.Time) (time.Time, error) {
	var until time.Time
	if v, ok := subject["validFrom"]; ok {
		from, valid := jsonval.Time(v)
		if !valid {
			return until, fmt.Errorf("%w: status list validFrom %v", ErrStatus, v)
		}
		if now.Before(from) {
			return until, fmt.Errorf("%w: status list is not yet valid", ErrStatus)
		}
	}
	if v, ok := subject["validUntil"]; ok {
		var valid bool
		if until, valid = jsonval.Time(v); !valid {
			return until, fmt.Errorf("%w: status list validUntil %v", ErrStatus, v)
		}
		if now.After(until) {
			return until, fmt.Errorf("%w: status list has expired", ErrStatus)
		}
	}
	return until, nil
}

// decodeList decodes encodedList: multibase base64url ('u' optional), then
// GZIP capped at 32 MiB.
func decodeList(encoded string) ([]byte, error) {
	if encoded == "" {
		return nil, fmt.Errorf("%w: encodedList is missing", ErrStatus)
	}
	encoded = strings.TrimRight(strings.TrimPrefix(encoded, "u"), "=")
	compressed, err := base64.RawURLEncoding.DecodeString(encoded)
	if err != nil {
		return nil, fmt.Errorf("%w: encodedList is not base64url: %w", ErrStatus, err)
	}
	zr, err := gzip.NewReader(bytes.NewReader(compressed))
	if err != nil {
		return nil, fmt.Errorf("%w: encodedList is not gzip: %w", ErrStatus, err)
	}
	defer func() { _ = zr.Close() }()
	bits, err := io.ReadAll(io.LimitReader(zr, maxDecompressed+1))
	if err != nil {
		return nil, fmt.Errorf("%w: decompress encodedList: %w", ErrStatus, err)
	}
	if len(bits) > maxDecompressed {
		return nil, fmt.Errorf("%w: status list exceeds %d bytes", ErrStatus, maxDecompressed)
	}
	return bits, nil
}

// readBits reads size bits starting at bit position pos, most significant
// bit first within each byte.
func readBits(bits []byte, pos, size int) (int, error) {
	if pos < 0 || pos+size > len(bits)*8 {
		return 0, fmt.Errorf("%w: bit position %d out of range", ErrStatus, pos)
	}
	value := 0
	for i := range size {
		p := pos + i
		bit := int(bits[p/8]>>(7-p%8)) & 1
		value = value<<1 | bit
	}
	return value, nil
}

func nonNegativeInt(v any) (int, error) {
	var s string
	switch t := v.(type) {
	case string:
		s = t
	case json.Number:
		s = t.String()
	default:
		return 0, fmt.Errorf("not an integer: %v", v)
	}
	n, err := strconv.Atoi(s)
	if err != nil || n < 0 {
		return 0, fmt.Errorf("not a non-negative integer: %q", s)
	}
	return n, nil
}
