/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package didkeys resolves proof verificationMethod URLs (did:key, did:jwk,
// did:web, HTTPS key documents) to public keys, their controllers and the
// verification relationships the controller authorizes them for.
package didkeys

import (
	"context"
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/rsa"
	"errors"
	"fmt"
	"slices"
	"strings"
	"time"

	"github.com/btcsuite/btcd/btcec/v2"

	"github.com/mosip/esignet/internal/vc/ldp/fetch"
)

// Verification relationships, named as the proof purposes they authorize.
const (
	AssertionMethod = "assertionMethod"
	Authentication  = "authentication"
)

// relationships are the verification relationships Method reports.
var relationships = []string{AssertionMethod, Authentication}

var (
	// ErrUnsupportedMethod reports a verificationMethod scheme or DID method
	// this package does not resolve.
	ErrUnsupportedMethod = errors.New("unsupported verification method")
	// ErrUnsupportedKey reports a key encoding or type this package does not
	// handle.
	ErrUnsupportedKey = errors.New("unsupported key")
	// ErrResolution reports a key that could not be resolved: malformed
	// identifiers, unreachable or invalid documents, missing entries.
	ErrResolution = errors.New("public key resolution failed")
)

// Method is a resolved verification method.
type Method struct {
	// ID is the verificationMethod URL as given.
	ID string
	// Controller is the DID for DID URLs, or the controller of an HTTPS key
	// document. Issuer and holder control checks compare it.
	Controller string
	Key        crypto.PublicKey
	// Relationships lists the verification relationships (AssertionMethod,
	// Authentication) the controller document authorizes the method for.
	Relationships []string
}

// Authorizes reports whether the controller authorizes the method for a
// proof purpose.
func (m *Method) Authorizes(purpose string) bool {
	return slices.Contains(m.Relationships, purpose)
}

// Resolver resolves verificationMethod URLs.
type Resolver interface {
	Resolve(ctx context.Context, verificationMethod string) (*Method, error)
}

type router struct {
	web   *didWeb
	https *httpsKeys
}

// NewResolver returns a Resolver that routes by prefix. Network documents
// (did:web, HTTPS key and controller documents) are fetched with f and
// cached for ttl. Their redirects must stay on the origin of the document
// URL: that origin's TLS certificate is what vouches for the document, so a
// redirect elsewhere (an open redirect, say) must not stand in for it.
func NewResolver(f *fetch.Fetcher, ttl time.Duration, cacheSize int) Resolver {
	f = f.WithRedirectCheck(fetch.SameOrigin)
	web := &didWeb{fetch: f, cache: fetch.NewCache[map[string]any](ttl, cacheSize)}
	return &router{
		web:   web,
		https: &httpsKeys{fetch: f, cache: fetch.NewCache[map[string]any](ttl, cacheSize), web: web},
	}
}

func (r *router) Resolve(ctx context.Context, vm string) (*Method, error) {
	switch {
	case strings.HasPrefix(vm, "did:"):
		d, err := ParseDIDURL(vm)
		if err != nil {
			return nil, err
		}
		if d.Method == "web" {
			return r.web.resolve(ctx, d)
		}
		// A did:key or did:jwk document holds only this key, under every
		// relationship its key use allows.
		key, rels, err := keyFromParsedDID(d)
		if err != nil {
			return nil, err
		}
		return &Method{ID: d.URL, Controller: d.DID, Key: key, Relationships: rels}, nil
	case strings.HasPrefix(vm, "https://"), strings.HasPrefix(vm, "http://"):
		return r.https.resolve(ctx, vm)
	default:
		return nil, fmt.Errorf("%w: %s", ErrUnsupportedMethod, vm)
	}
}

// KeyFromDID returns the key embedded in a did:key or did:jwk DID or DID URL
// without network access. Other DID methods return ErrUnsupportedMethod.
func KeyFromDID(did string) (crypto.PublicKey, error) {
	d, err := ParseDIDURL(did)
	if err != nil {
		return nil, err
	}
	key, _, err := keyFromParsedDID(d)
	return key, err
}

// keyFromParsedDID returns the key of a did:key or did:jwk DID and the
// relationships its DID document lists it under.
func keyFromParsedDID(d *DIDURL) (crypto.PublicKey, []string, error) {
	switch d.Method {
	case "key":
		key, err := didKeyPublicKey(d.ID)
		return key, relationships, err
	case "jwk":
		return didJWK(d.ID)
	default:
		return nil, nil, fmt.Errorf("%w: did:%s", ErrUnsupportedMethod, d.Method)
	}
}

// Equal reports whether a and b are the same public key.
func Equal(a, b crypto.PublicKey) bool {
	switch k := a.(type) {
	case ed25519.PublicKey:
		return k.Equal(b)
	case *ecdsa.PublicKey:
		return k.Equal(b)
	case *rsa.PublicKey:
		return k.Equal(b)
	case *btcec.PublicKey:
		o, ok := b.(*btcec.PublicKey)
		return ok && k.IsEqual(o)
	}
	return false
}

// refersTo reports whether a relationship or verificationMethod entry (a
// reference string or an embedded method object) names one of ids. A
// "#fragment" reference is relative to base, the document's id.
func refersTo(entry any, base string, ids ...string) bool {
	var ref string
	switch e := entry.(type) {
	case string:
		ref = e
	case map[string]any:
		ref, _ = e["id"].(string)
	}
	if strings.HasPrefix(ref, "#") {
		ref = base + ref
	}
	return ref != "" && slices.Contains(ids, ref)
}

// authorizes reports whether a relationship entry refers to one of ids with
// key. An embedded method states its own key, so it authorizes only that key,
// not whatever a fetched key document under the same id holds.
func authorizes(entry any, base string, key crypto.PublicKey, ids ...string) bool {
	if !refersTo(entry, base, ids...) {
		return false
	}
	vm, embedded := entry.(map[string]any)
	if !embedded {
		return true
	}
	k, err := keyFromVM(vm)
	return err == nil && Equal(k, key)
}

// authorizedRelationships lists the relationships of a controller document
// (id base) that authorize key under one of ids.
func authorizedRelationships(doc map[string]any, base string, key crypto.PublicKey, ids ...string) []string {
	var out []string
	for _, rel := range relationships {
		entries, _ := doc[rel].([]any)
		if slices.ContainsFunc(entries, func(e any) bool { return authorizes(e, base, key, ids...) }) {
			out = append(out, rel)
		}
	}
	return out
}

const keyDocAccept = "application/ld+json, application/json"

// httpsKeys resolves a verificationMethod that is itself an HTTPS URL of a
// key document ({id, type, controller, publicKey*}), as MOSIP IDA publishes.
//
// The key document's controller claim is not trusted on its own: anyone can
// host a key document naming any controller. The controller document (an
// HTTPS document or a did:web DID document) must reference the key in a
// verification relationship, and only those relationships are reported.
type httpsKeys struct {
	fetch *fetch.Fetcher
	cache *fetch.Cache[map[string]any]
	web   *didWeb
}

func (h *httpsKeys) resolve(ctx context.Context, vm string) (*Method, error) {
	docURL, _, _ := strings.Cut(vm, "#")
	doc, err := h.document(ctx, docURL)
	if err != nil {
		return nil, err
	}
	ids := []string{vm}
	if id, _ := doc["id"].(string); id != "" {
		if id != vm && id != docURL {
			return nil, fmt.Errorf("%w: key document id %q does not match %s", ErrResolution, id, vm)
		}
		if id != vm {
			ids = append(ids, id)
		}
	}
	key, err := keyFromVM(doc)
	if err != nil {
		return nil, err
	}
	controller, _ := doc["controller"].(string)
	if controller == "" {
		return nil, fmt.Errorf("%w: key document %s has no controller", ErrResolution, vm)
	}
	controllerDoc, err := h.controllerDocument(ctx, controller)
	if err != nil {
		return nil, err
	}
	return &Method{
		ID:            vm,
		Controller:    controller,
		Key:           key,
		Relationships: authorizedRelationships(controllerDoc, controller, key, ids...),
	}, nil
}

// controllerDocument fetches the document of a key's controller, whose id
// must be the controller.
func (h *httpsKeys) controllerDocument(ctx context.Context, controller string) (map[string]any, error) {
	switch {
	case strings.HasPrefix(controller, "did:web:"):
		return h.web.document(ctx, controller)
	case strings.HasPrefix(controller, "did:"):
		return nil, fmt.Errorf("%w: controller %s", ErrUnsupportedMethod, controller)
	}
	doc, err := h.document(ctx, controller)
	if err != nil {
		return nil, err
	}
	if id, _ := doc["id"].(string); id != controller {
		return nil, fmt.Errorf("%w: controller document id %q does not match %s", ErrResolution, id, controller)
	}
	return doc, nil
}

func (h *httpsKeys) document(ctx context.Context, docURL string) (map[string]any, error) {
	if doc, ok := h.cache.Get(docURL); ok {
		return doc, nil
	}
	doc, err := h.fetch.GetJSON(ctx, docURL, keyDocAccept)
	if err != nil {
		return nil, fmt.Errorf("%w: %s: %w", ErrResolution, docURL, err)
	}
	h.cache.Put(docURL, doc)
	return doc, nil
}
