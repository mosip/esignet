/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package didkeys

import (
	"context"
	"crypto"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/url"
	"regexp"
	"strings"

	"github.com/mr-tron/base58"

	"github.com/mosip/esignet/internal/vc/ldp/fetch"
)

// didURLPattern is the DID URL syntax:
// did:<method>:<method-specific-id>[;params][/path][?query][#fragment].
var didURLPattern = regexp.MustCompile(
	`^did:([a-z0-9]+):((?:(?:[a-zA-Z0-9._-]|%[0-9a-fA-F]{2})*:)*(?:[a-zA-Z0-9._-]|%[0-9a-fA-F]{2})+)` +
		`((?:;[a-zA-Z0-9_.:%-]+=[a-zA-Z0-9_.:%-]*)*)(/[^#?]*)?(\?[^#]*)?(#.*)?$`)

// DIDURL is a parsed DID URL.
type DIDURL struct {
	URL      string // the input, with did:jwk padding removed
	DID      string // did:<method>:<id>
	Method   string
	ID       string // method-specific id
	Fragment string // without '#'
}

// ParseDIDURL parses a DID or DID URL.
func ParseDIDURL(s string) (*DIDURL, error) {
	s = stripDIDJWKPadding(s)
	m := didURLPattern.FindStringSubmatch(s)
	if m == nil {
		return nil, fmt.Errorf("%w: malformed DID URL %q", ErrResolution, s)
	}
	return &DIDURL{
		URL:      s,
		DID:      "did:" + m[1] + ":" + m[2],
		Method:   m[1],
		ID:       m[2],
		Fragment: strings.TrimPrefix(m[6], "#"),
	}, nil
}

// stripDIDJWKPadding removes base64url '=' padding, which DID syntax doesn't
// allow but some wallets emit in did:jwk.
func stripDIDJWKPadding(s string) string {
	if !strings.HasPrefix(s, "did:jwk:") {
		return s
	}
	did, rest, hasFragment := strings.Cut(s, "#")
	did = strings.TrimRight(did, "=")
	if hasFragment {
		return did + "#" + rest
	}
	return did
}

// didKeyPublicKey decodes a did:key method-specific id: base58btc multibase
// over a multicodec-prefixed key.
func didKeyPublicKey(id string) (crypto.PublicKey, error) {
	if !strings.HasPrefix(id, "z") {
		return nil, fmt.Errorf("%w: did:key must use base58btc multibase", ErrResolution)
	}
	raw, err := base58.Decode(id[1:])
	if err != nil {
		return nil, fmt.Errorf("%w: did:key: %w", ErrResolution, err)
	}
	return decodeMulticodecKey(raw)
}

// didJWK decodes a did:jwk method-specific id into its key and the
// relationships its DID document lists the key under. A JWK with "use":"enc"
// is listed under keyAgreement only, so it gets none of the relationships
// Method reports.
func didJWK(id string) (crypto.PublicKey, []string, error) {
	raw, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(id, "="))
	if err != nil {
		return nil, nil, fmt.Errorf("%w: did:jwk: %w", ErrResolution, err)
	}
	var jwk map[string]any
	if err := json.Unmarshal(raw, &jwk); err != nil {
		return nil, nil, fmt.Errorf("%w: did:jwk: %w", ErrResolution, err)
	}
	key, err := parseJWK(jwk)
	if err != nil {
		return nil, nil, err
	}
	if use, _ := jwk["use"].(string); use == "enc" {
		return key, nil, nil
	}
	return key, relationships, nil
}

const didDocAccept = "application/did+json, application/json"

type didWeb struct {
	fetch *fetch.Fetcher
	cache *fetch.Cache[map[string]any]
}

func (w *didWeb) resolve(ctx context.Context, d *DIDURL) (*Method, error) {
	doc, err := w.document(ctx, d.DID)
	if err != nil {
		return nil, err
	}
	vm := findVerificationMethod(doc, d.DID, d.URL)
	if vm == nil {
		return nil, fmt.Errorf("%w: %s is not in the DID document", ErrResolution, d.URL)
	}
	key, err := keyFromVM(vm)
	if err != nil {
		return nil, err
	}
	return &Method{
		ID:            d.URL,
		Controller:    d.DID,
		Key:           key,
		Relationships: authorizedRelationships(doc, d.DID, key, d.URL),
	}, nil
}

func (w *didWeb) document(ctx context.Context, did string) (map[string]any, error) {
	if doc, ok := w.cache.Get(did); ok {
		return doc, nil
	}
	docURL, err := didWebURL(strings.TrimPrefix(did, "did:web:"))
	if err != nil {
		return nil, err
	}
	doc, err := w.fetch.GetJSON(ctx, docURL, didDocAccept)
	if err != nil {
		return nil, fmt.Errorf("%w: %s: %w", ErrResolution, did, err)
	}
	if id, _ := doc["id"].(string); id != did {
		return nil, fmt.Errorf("%w: DID document id %q does not match %s", ErrResolution, id, did)
	}
	w.cache.Put(did, doc)
	return doc, nil
}

// didWebURL maps a did:web method-specific id to its document URL:
// host -> https://host/.well-known/did.json, host:a:b -> https://host/a/b/did.json.
// A percent-encoded colon in the host carries a port.
func didWebURL(id string) (string, error) {
	parts := strings.Split(id, ":")
	host, err := url.PathUnescape(parts[0])
	if err != nil || host == "" || strings.ContainsAny(host, "/?#@\\") {
		return "", fmt.Errorf("%w: invalid did:web host %q", ErrResolution, parts[0])
	}
	if len(parts) == 1 {
		return "https://" + host + "/.well-known/did.json", nil
	}
	segments := make([]string, 0, len(parts)-1)
	for _, p := range parts[1:] {
		seg, err := url.PathUnescape(p)
		if err != nil || seg == "" || seg == "." || seg == ".." || strings.ContainsAny(seg, "/?#\\") {
			return "", fmt.Errorf("%w: invalid did:web path segment %q", ErrResolution, p)
		}
		segments = append(segments, url.PathEscape(seg))
	}
	return "https://" + host + "/" + strings.Join(segments, "/") + "/did.json", nil
}

// findVerificationMethod returns the method object whose id is vmURL,
// absolute or as a "#fragment" relative to the DID. It searches
// verificationMethod and the methods embedded in relationships.
func findVerificationMethod(doc map[string]any, did, vmURL string) map[string]any {
	for _, rel := range append([]string{"verificationMethod"}, relationships...) {
		entries, _ := doc[rel].([]any)
		for _, e := range entries {
			if vm, ok := e.(map[string]any); ok && refersTo(vm, did, vmURL) {
				return vm
			}
		}
	}
	return nil
}
