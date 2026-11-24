/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package proof verifies Linked Data proofs: suite
// registry, signing input ("hash data") construction and detached JWS.
package proof

import (
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"maps"
	"strings"
)

// Canonicalizer produces the URDNA2015 N-Quads of a JSON-LD document.
type Canonicalizer interface {
	// Canonicalize drops terms the document's context does not define, as
	// JSON-LD processing does.
	Canonicalize(ctx context.Context, doc map[string]any) (string, error)
	// CanonicalizeSafe fails on such terms instead (JSON-LD safe mode).
	CanonicalizeSafe(ctx context.Context, doc map[string]any) (string, error)
	// Pinned reports whether a context URL is available without a fetch.
	Pinned(contextURL string) bool
}

// signatureMembers are removed from the proof to form the proof configuration.
var signatureMembers = []string{"proofValue", "jws", "signatureValue"}

// HashData returns SHA-256(c14n(proofConfig)) || SHA-256(c14n(unsecuredDoc)),
// the 64-byte input every supported suite signs, with proofConfig (the proof
// minus its signature) under the document's @context and unsecuredDoc the
// document minus "proof". Verify also tries the pinned suite contexts.
func HashData(ctx context.Context, c Canonicalizer, doc, proof map[string]any) ([]byte, error) {
	unsecured := without(doc, "proof")
	cfg := without(proof, signatureMembers...)
	cfg["@context"] = doc["@context"]

	canonicalProof, err := c.Canonicalize(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("canonicalize proof configuration: %w", err)
	}
	canonicalDoc, err := c.Canonicalize(ctx, unsecured)
	if err != nil {
		return nil, fmt.Errorf("canonicalize document: %w", err)
	}
	h1 := sha256.Sum256([]byte(canonicalProof))
	h2 := sha256.Sum256([]byte(canonicalDoc))
	return append(h1[:], h2[:]...), nil
}

func without(m map[string]any, keys ...string) map[string]any {
	out := maps.Clone(m)
	for _, k := range keys {
		delete(out, k)
	}
	return out
}

// detachedJWS is a parsed RFC 7797 JWS with an unencoded, detached payload.
type detachedJWS struct {
	alg             string
	protectedHeader string // base64url, as sent: it is part of the signing input
	signature       []byte
}

// parseDetachedJWS parses header..signature with b64=false. The payload
// segment must be empty: the signed payload is always the proof hash data.
func parseDetachedJWS(jws string) (*detachedJWS, error) {
	parts := strings.Split(jws, ".")
	if len(parts) != 3 {
		return nil, fmt.Errorf("%w: jws must have three segments", ErrMalformed)
	}
	if parts[1] != "" {
		return nil, fmt.Errorf("%w: jws payload must be detached (empty)", ErrMalformed)
	}
	headerJSON, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil {
		return nil, fmt.Errorf("%w: jws header: %w", ErrMalformed, err)
	}
	var hdr struct {
		Alg  string   `json:"alg"`
		B64  *bool    `json:"b64"`
		Crit []string `json:"crit"`
	}
	if err := json.Unmarshal(headerJSON, &hdr); err != nil {
		return nil, fmt.Errorf("%w: jws header: %w", ErrMalformed, err)
	}
	if hdr.B64 == nil || *hdr.B64 {
		return nil, fmt.Errorf("%w: jws must set b64=false", ErrMalformed)
	}
	if len(hdr.Crit) != 1 || hdr.Crit[0] != "b64" {
		return nil, fmt.Errorf("%w: jws crit must be exactly [\"b64\"]", ErrMalformed)
	}
	signature, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return nil, fmt.Errorf("%w: jws signature: %w", ErrMalformed, err)
	}
	return &detachedJWS{alg: hdr.Alg, protectedHeader: parts[0], signature: signature}, nil
}

// signingInput is ASCII(protectedHeader) || '.' || payload (RFC 7797 §3).
func (j *detachedJWS) signingInput(payload []byte) []byte {
	in := make([]byte, 0, len(j.protectedHeader)+1+len(payload))
	in = append(in, j.protectedHeader...)
	in = append(in, '.')
	return append(in, payload...)
}
