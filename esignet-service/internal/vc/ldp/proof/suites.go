/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package proof

import (
	"context"
	"crypto"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"fmt"
	"slices"
	"sort"

	"github.com/mr-tron/base58"

	"github.com/mosip/esignet/internal/vc/ldp/sig"
)

// Suite identifiers: the proof type (as in OpenID4VP ldp_vc metadata).
const (
	Ed25519Signature2018        = "Ed25519Signature2018"
	Ed25519Signature2020        = "Ed25519Signature2020"
	EcdsaSecp256k1Signature2019 = "EcdsaSecp256k1Signature2019"
	EcdsaSecp256r1Signature2019 = "EcdsaSecp256r1Signature2019"
	RsaSignature2018            = "RsaSignature2018"
	JSONWebSignature2020        = "JsonWebSignature2020"
)

// Suite contexts for legacy proof options.
const (
	securityV2Context         = "https://w3id.org/security/v2"
	ed25519Suite2020Context   = "https://w3id.org/security/suites/ed25519-2020/v1"
	jws2020Context            = "https://w3id.org/security/suites/jws-2020/v1"
	secp256k1Suite2019Context = "https://w3id.org/security/suites/secp256k1-2019/v1"
)

// legacyProofContexts are the contexts ld-signatures-java hashes proof
// options under, instead of the document's @context. Only pinned contexts
// are tried (Canonicalizer.Pinned).
var legacyProofContexts = map[string][]string{
	Ed25519Signature2018:        {securityV2Context},
	RsaSignature2018:            {securityV2Context},
	EcdsaSecp256r1Signature2019: {securityV2Context},
	EcdsaSecp256k1Signature2019: {securityV2Context, secp256k1Suite2019Context},
	Ed25519Signature2020:        {ed25519Suite2020Context},
	JSONWebSignature2020:        {jws2020Context},
}

var (
	// ErrUnsupportedSuite reports an unknown or disallowed proof suite.
	ErrUnsupportedSuite = errors.New("unsupported proof suite")
	// ErrUnsupportedAlg reports a JWS algorithm the suite doesn't allow.
	ErrUnsupportedAlg = errors.New("unsupported proof algorithm")
	// ErrMalformed reports a structurally invalid proof.
	ErrMalformed = errors.New("malformed proof")
)

type suiteSpec struct {
	jwsAlgs       []string // algorithms accepted in a detached JWS; nil => no jws
	proofValueAlg string   // algorithm for proofValue; "" => no proofValue
}

// suites is the suite registry. The key type is checked against the
// algorithm before any signature is verified (sig.CheckKey).
var suites = map[string]suiteSpec{
	Ed25519Signature2018:        {jwsAlgs: []string{sig.EdDSA}},
	Ed25519Signature2020:        {proofValueAlg: sig.EdDSA},
	EcdsaSecp256k1Signature2019: {jwsAlgs: []string{sig.ES256K}, proofValueAlg: sig.ES256K},
	EcdsaSecp256r1Signature2019: {jwsAlgs: []string{sig.ES256}, proofValueAlg: sig.ES256},
	RsaSignature2018:            {jwsAlgs: []string{sig.RS256, sig.PS256}, proofValueAlg: sig.RS256},
	JSONWebSignature2020:        {jwsAlgs: []string{sig.EdDSA, sig.ES256, sig.ES256K}},
}

// DefaultVPSuites returns the suites accepted for presentation proofs by
// default: all but RSA.
func DefaultVPSuites() []string { return sortedExcept(RsaSignature2018) }

// DefaultVCSuites returns the suites accepted for credential proofs by
// default: all but JsonWebSignature2020.
func DefaultVCSuites() []string { return sortedExcept(JSONWebSignature2020) }

// Supported reports whether id is a suite in the registry.
func Supported(id string) bool {
	_, ok := suites[id]
	return ok
}

func sortedExcept(excluded string) []string {
	out := make([]string, 0, len(suites))
	for id := range suites {
		if id != excluded {
			out = append(out, id)
		}
	}
	sort.Strings(out)
	return out
}

// SuiteID returns the registry id of a proof: its type.
func SuiteID(p map[string]any) (string, error) {
	typ, _ := p["type"].(string)
	if typ == "" {
		return "", fmt.Errorf("%w: proof.type is missing", ErrMalformed)
	}
	return typ, nil
}

// Proof is a proof that passed the suite registry and the caller's allow
// list, with its signature decoded. Parse does no cryptography.
type Proof struct {
	Suite              string
	Alg                string
	VerificationMethod string
	raw                map[string]any
	jws                *detachedJWS
	signature          []byte
}

// Parse checks p against the registry and allowed (suite ids), and decodes
// its jws or proofValue.
func Parse(p map[string]any, allowed []string) (*Proof, error) {
	id, err := SuiteID(p)
	if err != nil {
		return nil, err
	}
	s, ok := suites[id]
	if !ok {
		return nil, fmt.Errorf("%w: %q", ErrUnsupportedSuite, id)
	}
	if !slices.Contains(allowed, id) {
		return nil, fmt.Errorf("%w: %q is not allowed here", ErrUnsupportedSuite, id)
	}
	vm, _ := p["verificationMethod"].(string)
	if vm == "" {
		return nil, fmt.Errorf("%w: verificationMethod is missing", ErrMalformed)
	}
	out := &Proof{Suite: id, VerificationMethod: vm, raw: p}

	jws, hasJWS := p["jws"].(string)
	proofValue, hasProofValue := p["proofValue"].(string)
	switch {
	case hasJWS && hasProofValue:
		return nil, fmt.Errorf("%w: both jws and proofValue are present", ErrMalformed)
	case hasJWS:
		if s.jwsAlgs == nil {
			return nil, fmt.Errorf("%w: %s does not use jws", ErrMalformed, id)
		}
		if out.jws, err = parseDetachedJWS(jws); err != nil {
			return nil, err
		}
		if !slices.Contains(s.jwsAlgs, out.jws.alg) {
			return nil, fmt.Errorf("%w: %s with %q", ErrUnsupportedAlg, id, out.jws.alg)
		}
		out.Alg, out.signature = out.jws.alg, out.jws.signature
	case hasProofValue:
		if s.proofValueAlg == "" {
			return nil, fmt.Errorf("%w: %s does not use proofValue", ErrMalformed, id)
		}
		if out.signature, err = decodeProofValue(proofValue); err != nil {
			return nil, err
		}
		out.Alg = s.proofValueAlg
	default:
		return nil, fmt.Errorf("%w: proof has neither jws nor proofValue", ErrMalformed)
	}
	return out, nil
}

// decodeProofValue decodes a multibase proofValue: base58btc ('z') or
// base64url ('u').
func decodeProofValue(v string) ([]byte, error) {
	if v == "" {
		return nil, fmt.Errorf("%w: empty proofValue", ErrMalformed)
	}
	var b []byte
	var err error
	switch {
	case v[0] == 'z':
		b, err = base58.Decode(v[1:])
	case v[0] == 'u':
		b, err = base64.RawURLEncoding.DecodeString(v[1:])
	default:
		return nil, fmt.Errorf("%w: unsupported proofValue multibase prefix %q", ErrMalformed, v[0])
	}
	if err != nil {
		return nil, fmt.Errorf("%w: proofValue: %w", ErrMalformed, err)
	}
	return b, nil
}

// Verify checks the signature with pub over a document whose @context is
// docContext and whose canonical N-Quads, without its proof, are
// canonicalDoc. The caller canonicalizes the document, choosing whether in
// safe mode. The key is matched to the algorithm first.
//
// The proof options (the proof minus its signature) are canonicalized in
// safe mode, under the document's @context first (what jsonld-signatures
// does), then under each pinned suite context (what ld-signatures-java, used by MOSIP and Inji
// issuers, does). The proof verifies under the first context that defines
// every option and yields a valid signature.
func (p *Proof) Verify(ctx context.Context, c Canonicalizer, docContext any, canonicalDoc string, pub crypto.PublicKey) error {
	if err := sig.CheckKey(p.Alg, pub); err != nil {
		return err
	}
	docHash := sha256.Sum256([]byte(canonicalDoc))

	candidates := []any{docContext}
	for _, u := range legacyProofContexts[p.Suite] {
		if c.Pinned(u) {
			candidates = append(candidates, u)
		}
	}
	var canonErr error
	signed := false // some candidate canonicalized, so a failure is the signature's
	for _, proofContext := range candidates {
		options := without(p.raw, signatureMembers...)
		options["@context"] = proofContext
		canonicalOptions, err := c.CanonicalizeSafe(ctx, options)
		if err != nil {
			if canonErr == nil {
				canonErr = fmt.Errorf("canonicalize proof configuration: %w", err)
			}
			continue
		}
		signed = true
		optionsHash := sha256.Sum256([]byte(canonicalOptions))
		msg := slices.Concat(optionsHash[:], docHash[:])
		if p.jws != nil {
			msg = p.jws.signingInput(msg)
		}
		if sig.Verify(p.Alg, pub, msg, p.signature) == nil {
			return nil
		}
	}
	if signed {
		return sig.ErrInvalidSignature
	}
	return canonErr
}
