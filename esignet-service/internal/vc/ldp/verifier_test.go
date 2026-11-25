/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ldp

import (
	"bytes"
	"compress/gzip"
	"encoding/base64"
	"maps"
	"strings"
	"sync"
	"time"

	"github.com/mosip/esignet/internal/vc/ldp/didkeys"
	"github.com/mosip/esignet/internal/vc/ldp/jsonld"
	"github.com/mosip/esignet/internal/vc/ldp/proof"
	"github.com/mosip/esignet/internal/vc/ldp/status"
)

const (
	testChallenge     = "n-0S6_WzA2Mj"
	testDomain        = "x509_san_dns:verifier.example.org"
	contextExamplesV2 = "https://www.w3.org/ns/credentials/examples/v2"
	// contextEd25519 defines the Ed25519Signature2020 proof terms, which the
	// VCDM 2.0 context does not.
	contextEd25519    = "https://w3id.org/security/suites/ed25519-2020/v1"
	testStatusListURL = "https://issuer.example.org/status/1"
)

var testNow = time.Date(2026, 6, 1, 0, 0, 0, 0, time.UTC)

// credential returns a VCDM 2.0 credential from issuer about holder, signed
// with Ed25519Signature2020. mutate runs before signing.
func (ts *LDPVerifierTestSuite) credential(issuer, holder signer, mutate func(vc map[string]any)) map[string]any {
	vc := map[string]any{
		"@context":   []any{contextV2, contextExamplesV2, contextEd25519},
		"id":         "urn:uuid:58172aac-d8ba-11ed-83dd-0b3aef56cc33",
		"type":       []any{typeCredential, "AlumniCredential"},
		"issuer":     issuer.did,
		"validFrom":  "2026-01-01T00:00:00Z",
		"validUntil": "2027-01-01T00:00:00Z",
		"credentialSubject": map[string]any{
			"id":       holder.did,
			"alumniOf": "The School of Examples",
		},
	}
	if mutate != nil {
		mutate(vc)
	}
	return ts.sign(vc, issuer, didkeys.AssertionMethod, nil)
}

// presentation returns a VP from holder carrying vcs, bound to the test
// challenge and domain unless proofExtra overrides them.
func (ts *LDPVerifierTestSuite) presentation(holder signer, vcs any, proofExtra map[string]any) map[string]any {
	vp := map[string]any{
		"@context":           []any{contextV2, contextEd25519},
		"type":               []any{typePresentation},
		"holder":             holder.did,
		fieldVerifiableCreds: vcs,
	}
	extra := map[string]any{"challenge": testChallenge, "domain": testDomain}
	maps.Copy(extra, proofExtra)
	return ts.sign(vp, holder, didkeys.Authentication, extra)
}

// trusting returns a verifier trusting only issuers, with mutate applied to
// its config.
func (ts *LDPVerifierTestSuite) trusting(issuers []signer, mutate func(*Config)) *Verifier {
	var ids []string
	for _, s := range issuers {
		ids = append(ids, s.did)
	}
	cfg := Config{IssuerTrusted: TrustIssuers(ids...)}
	if mutate != nil {
		mutate(&cfg)
	}
	return ts.newVerifier(cfg)
}

// verify runs VerifyPresentation with the test challenge and domain.
func (ts *LDPVerifierTestSuite) verify(v *Verifier, vp map[string]any) (*PresentationResult, error) {
	return v.VerifyPresentation(ts.ctx, ts.marshal(vp), testChallenge, testDomain)
}

func (ts *LDPVerifierTestSuite) TestVerifyPresentationSucceeds() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	vp := ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil)

	res, err := ts.verify(ts.trusting([]signer{issuer}, nil), vp)
	ts.Require().NoError(err)
	ts.Equal(FormatLDPVC, res.Format)
	ts.Equal(map[string]any{"id": holder.did, "alumniOf": "The School of Examples"}, res.Claims)
	holderKey, err := didkeys.KeyFromDID(holder.did)
	ts.Require().NoError(err)
	thumbprint, err := didkeys.Thumbprint(holderKey)
	ts.Require().NoError(err)
	ts.Equal(thumbprint, res.HolderBindingThumbprint)
	ts.Require().NotNil(res.Created)
	ts.Equal(time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC), *res.Created)
}

func (ts *LDPVerifierTestSuite) TestVerifyPresentationSingleCredentialObject() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	vp := ts.presentation(holder, ts.credential(issuer, holder, nil), nil)
	res, err := ts.verify(ts.trusting([]signer{issuer}, nil), vp)
	ts.Require().NoError(err)
	ts.Equal("The School of Examples", res.Claims["alumniOf"])
}

func (ts *LDPVerifierTestSuite) TestVerifyPresentationMultipleCredentials() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	vcs := []any{ts.credential(issuer, holder, nil), ts.credential(issuer, holder, nil), ts.credential(issuer, holder, nil)}
	res, err := ts.verify(ts.trusting([]signer{issuer}, func(c *Config) { c.CredentialConcurrency = 2 }), ts.presentation(holder, vcs, nil))
	ts.Require().NoError(err)
	ts.Equal(map[string]any{"id": holder.did, "alumniOf": "The School of Examples"}, res.Claims)
}

// Claims of every credential are merged; a later value overrides an earlier one.
func (ts *LDPVerifierTestSuite) TestVerifyPresentationMergesClaims() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	withClaim := func(name, value string) map[string]any {
		return ts.credential(issuer, holder, func(vc map[string]any) {
			vc[fieldCredentialSubject].(map[string]any)[name] = value
		})
	}
	v := ts.trusting([]signer{issuer}, nil)

	vcs := []any{withClaim("degree", "BSc"), withClaim("memberOf", "Alumni Association")}
	res, err := ts.verify(v, ts.presentation(holder, vcs, nil))
	ts.Require().NoError(err)
	ts.Equal(map[string]any{
		"id":       holder.did,
		"alumniOf": "The School of Examples",
		"degree":   "BSc",
		"memberOf": "Alumni Association",
	}, res.Claims)

	vcs = []any{withClaim("degree", "BSc"), withClaim("degree", "MSc")}
	res, err = ts.verify(v, ts.presentation(holder, vcs, nil))
	ts.Require().NoError(err)
	ts.Equal("MSc", res.Claims["degree"])
}

// Credentials are verified in parallel; the error reported is the first
// failing credential in document order.
func (ts *LDPVerifierTestSuite) TestVerifyPresentationReportsFirstFailingCredential() {
	issuer, holder, other := newSigner(ts.T()), newSigner(ts.T()), newSigner(ts.T())
	expired := func(vc map[string]any) { vc[fieldValidUntil] = "2026-05-01T00:00:00Z" }
	vcs := []any{
		ts.credential(issuer, holder, nil),
		ts.credential(other, holder, nil),
		ts.credential(issuer, holder, expired),
	}
	for range 5 {
		_, err := ts.verify(ts.trusting([]signer{issuer}, nil), ts.presentation(holder, vcs, nil))
		ts.requireKind(err, ErrUntrustedIssuer, 1)
	}
}

func (ts *LDPVerifierTestSuite) TestVerifyPresentationRequiresChallengeAndDomain() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	raw := ts.marshal(ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil))
	v := ts.trusting([]signer{issuer}, nil)
	_, err := v.VerifyPresentation(ts.ctx, raw, "", testDomain)
	ts.requireKind(err, ErrInvalidRequest, presentationIndex)
	_, err = v.VerifyPresentation(ts.ctx, raw, testChallenge, "")
	ts.requireKind(err, ErrInvalidRequest, presentationIndex)
}

func (ts *LDPVerifierTestSuite) TestNewValidatesConfig() {
	_, err := New(Config{})
	ts.ErrorContains(err, "IssuerTrusted")
	_, err = New(Config{IssuerTrusted: AnyIssuer, VPSuites: []string{"NoSuchSuite2099"}})
	ts.ErrorContains(err, "NoSuchSuite2099")
}

func (ts *LDPVerifierTestSuite) TestVerifyPresentationBinding() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	vc := ts.credential(issuer, holder, nil)
	cases := []struct {
		name              string
		proofExtra        map[string]any
		challenge, domain string // "" keeps the test values
		maxAge            time.Duration
		kind              Kind // 0 = pass
		field             string
	}{
		{name: "challenge mismatch", challenge: "replayed", kind: ErrChallenge},
		{name: "domain mismatch", domain: "https://attacker.example", kind: ErrDomain},
		{name: "no challenge", proofExtra: map[string]any{"challenge": nil}, kind: ErrChallenge},
		{name: "no domain", proofExtra: map[string]any{"domain": nil}, kind: ErrDomain},
		{name: "domain array", proofExtra: map[string]any{"domain": []any{"https://other.example", testDomain}}},
		{name: "domain array with non-string", proofExtra: map[string]any{"domain": []any{testDomain, map[string]any{}}}, kind: ErrDomain},
		{name: "assertion purpose", proofExtra: map[string]any{"proofPurpose": didkeys.AssertionMethod}, kind: ErrProofPurpose},
		{name: "expired proof", proofExtra: map[string]any{"expires": "2026-05-01T00:00:00Z"}, kind: ErrProofExpired},
		{name: "stale proof", maxAge: time.Hour, kind: ErrProofExpired},
		{name: "fresh proof", proofExtra: map[string]any{"created": "2026-05-31T23:30:00Z"}, maxAge: time.Hour},
		{name: "future proof", proofExtra: map[string]any{"created": "2026-06-02T00:00:00Z"}, maxAge: time.Hour, kind: ErrInvalidField, field: "proof.created"},
	}
	for _, c := range cases {
		ts.Run(c.name, func() {
			vp := ts.presentation(holder, []any{vc}, c.proofExtra)
			challenge, domain := testChallenge, testDomain
			if c.challenge != "" {
				challenge = c.challenge
			}
			if c.domain != "" {
				domain = c.domain
			}
			v := ts.trusting([]signer{issuer}, func(cfg *Config) { cfg.MaxVPAge = c.maxAge })
			_, err := v.VerifyPresentation(ts.ctx, ts.marshal(vp), challenge, domain)
			if c.kind == 0 {
				ts.Require().NoError(err)
				return
			}
			ts.requireField(err, c.kind, c.field, presentationIndex)
		})
	}
}

func (ts *LDPVerifierTestSuite) TestVerifyPresentationCredentialValidity() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	for name, c := range map[string]struct {
		mutate func(map[string]any)
		kind   Kind
		field  string
	}{
		"expired":     {func(vc map[string]any) { vc[fieldValidUntil] = "2026-05-01T00:00:00Z" }, ErrExpired, ""},
		"not yet":     {func(vc map[string]any) { vc[fieldValidFrom] = "2026-07-01T00:00:00Z" }, ErrNotYetValid, fieldValidFrom},
		"bad date":    {func(vc map[string]any) { vc[fieldValidUntil] = "next year" }, ErrInvalidField, fieldValidUntil},
		"no subject":  {func(vc map[string]any) { vc[fieldCredentialSubject] = []any{} }, ErrInvalidField, fieldCredentialSubject},
		"bad issuer":  {func(vc map[string]any) { vc["issuer"] = "https:///no-host" }, ErrInvalidField, "issuer"},
		"wrong type":  {func(vc map[string]any) { vc["type"] = "AlumniCredential" }, ErrInvalidField, "type"},
		"bad context": {func(vc map[string]any) { vc[fieldContext] = []any{contextExamplesV2} }, ErrInvalidField, fieldContext},
	} {
		ts.Run(name, func() {
			vp := ts.presentation(holder, []any{ts.credential(issuer, holder, c.mutate)}, nil)
			_, err := ts.verify(ts.trusting([]signer{issuer}, nil), vp)
			ts.requireField(err, c.kind, c.field, 0)
		})
	}
}

func (ts *LDPVerifierTestSuite) TestVerifyPresentationCredentialProofPurpose() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	vc := ts.sign(ts.credential(issuer, holder, nil), issuer, didkeys.Authentication, nil)
	_, err := ts.verify(ts.trusting([]signer{issuer}, nil), ts.presentation(holder, []any{vc}, nil))
	ts.requireKind(err, ErrProofPurpose, 0)
}

// A credential proof's own expires applies, separately from the
// credential's validity window.
func (ts *LDPVerifierTestSuite) TestVerifyPresentationCredentialProofExpiry() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	for name, c := range map[string]struct {
		expires any
		kind    Kind // 0 = pass
		field   string
	}{
		"no expires":  {nil, 0, ""},
		"not expired": {"2026-07-01T00:00:00Z", 0, ""},
		"expired":     {"2026-05-01T00:00:00Z", ErrProofExpired, ""},
		"bad date":    {"next year", ErrInvalidField, "proof.expires"},
	} {
		ts.Run(name, func() {
			vc := ts.sign(ts.credential(issuer, holder, nil), issuer, didkeys.AssertionMethod, map[string]any{"expires": c.expires})
			_, err := ts.verify(ts.trusting([]signer{issuer}, nil), ts.presentation(holder, []any{vc}, nil))
			if c.kind == 0 {
				ts.Require().NoError(err)
				return
			}
			ts.requireField(err, c.kind, c.field, 0)
		})
	}
}

// ClockSkew tolerates a credential that has only just expired, and does not
// reject one about to expire.
func (ts *LDPVerifierTestSuite) TestCredentialExpiryClockSkew() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	for name, c := range map[string]struct {
		until   time.Duration
		expired bool
	}{
		"expires in 2s":     {2 * time.Second, false},
		"expired 2s ago":    {-2 * time.Second, false},
		"expired 4s ago":    {-4 * time.Second, true},
		"expires right now": {-DefaultClockSkew, true},
	} {
		ts.Run(name, func() {
			vc := ts.credential(issuer, holder, func(vc map[string]any) {
				vc[fieldValidUntil] = testNow.Add(c.until).Format(time.RFC3339)
			})
			_, err := ts.verify(ts.trusting([]signer{issuer}, nil), ts.presentation(holder, []any{vc}, nil))
			if c.expired {
				ts.requireKind(err, ErrExpired, 0)
				return
			}
			ts.NoError(err)
		})
	}
}

// VCDM 2.0 name and description take a string, a language value object or
// an array of them.
func (ts *LDPVerifierTestSuite) TestCredentialLanguageValues() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	for name, c := range map[string]struct {
		value any
		valid bool
	}{
		"string":       {"Degree", true},
		"object":       {map[string]any{"@value": "Degree", "@language": "en"}, true},
		"array":        {[]any{map[string]any{"@value": "Degree", "@language": "en"}}, true},
		"no language":  {map[string]any{"@value": "Degree"}, false},
		"boolean":      {true, false},
		"array of str": {[]any{"Degree"}, false},
	} {
		ts.Run(name, func() {
			vc := ts.credential(issuer, holder, func(vc map[string]any) { vc["name"] = c.value })
			_, err := ts.verify(ts.trusting([]signer{issuer}, nil), ts.presentation(holder, []any{vc}, nil))
			if c.valid {
				ts.NoError(err)
				return
			}
			ts.requireField(err, ErrInvalidField, "name", 0)
		})
	}
}

func (ts *LDPVerifierTestSuite) TestHolderBinding() {
	issuer, holder, other := newSigner(ts.T()), newSigner(ts.T()), newSigner(ts.T())
	cases := []struct {
		name      string
		subjectID any // nil removes the id
		pass      bool
	}{
		{"subject without id", nil, false},
		{"unsupported subject method", "did:example:abcdefgh", false},
		{"non-DID subject", "urn:uuid:7d1a4c90-0000-4000-8000-000000000000", false},
		{"someone else's credential", other.did, false},
		{"holder as did:key URL", holder.vm, true},
	}
	for _, c := range cases {
		ts.Run(c.name, func() {
			vc := ts.credential(issuer, holder, func(vc map[string]any) {
				subject := vc[fieldCredentialSubject].(map[string]any)
				if c.subjectID == nil {
					delete(subject, "id")
				} else {
					subject["id"] = c.subjectID
				}
			})
			_, err := ts.verify(ts.trusting([]signer{issuer}, nil), ts.presentation(holder, []any{vc}, nil))
			if c.pass {
				ts.Require().NoError(err)
				return
			}
			ts.requireKind(err, ErrHolderBinding, 0)
		})
	}
}

// A VP signed by one key but naming another holder fails proof of possession.
func (ts *LDPVerifierTestSuite) TestHolderMustControlProofKey() {
	issuer, holder, other := newSigner(ts.T()), newSigner(ts.T()), newSigner(ts.T())
	v := ts.trusting([]signer{issuer}, nil)
	vp := ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil)
	vp["holder"] = other.did
	vp = ts.sign(vp, holder, didkeys.Authentication, map[string]any{"challenge": testChallenge, "domain": testDomain})
	_, err := ts.verify(v, vp)
	ts.requireKind(err, ErrHolderBinding, presentationIndex)

	delete(vp, "holder")
	vp = ts.sign(vp, holder, didkeys.Authentication, map[string]any{"challenge": testChallenge, "domain": testDomain})
	_, err = ts.verify(v, vp)
	ve := ts.verificationError(err, ErrHolderBinding, presentationIndex)
	ts.Equal(msgHolderMissing, ve.Message)
}

// A did:web holder is bound through its DID document, which must list the
// presentation key under authentication.
func (ts *LDPVerifierTestSuite) TestDIDWebHolder() {
	issuer := newSigner(ts.T())
	holder := ts.newWebSigner("holder.example.org", didkeys.Authentication)
	vp := ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil)
	res, err := ts.verify(ts.trusting([]signer{issuer}, nil), vp)
	ts.Require().NoError(err)
	ts.Equal(holder.did, res.Claims["id"])

	assertionOnly := ts.newWebSigner("assertion-only.example.org", didkeys.AssertionMethod)
	vp = ts.presentation(assertionOnly, []any{ts.credential(issuer, assertionOnly, nil)}, nil)
	_, err = ts.verify(ts.trusting([]signer{issuer}, nil), vp)
	ts.requireKind(err, ErrKeyNotAuthorized, presentationIndex)
}

// A credential key must be listed under assertionMethod in the issuer's DID
// document.
func (ts *LDPVerifierTestSuite) TestCredentialKeyMustBeAuthorized() {
	holder := newSigner(ts.T())
	for name, c := range map[string]struct {
		relationships []string
		kind          Kind // 0 = pass
	}{
		"assertionMethod":     {[]string{didkeys.AssertionMethod}, 0},
		"authentication only": {[]string{didkeys.Authentication}, ErrKeyNotAuthorized},
		"no relationship":     {nil, ErrKeyNotAuthorized},
	} {
		ts.Run(name, func() {
			issuer := ts.newWebSigner(strings.ToLower(strings.ReplaceAll(name, " ", "-"))+".example.org", c.relationships...)
			_, err := ts.verify(ts.trusting([]signer{issuer}, nil), ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil))
			if c.kind == 0 {
				ts.Require().NoError(err)
				return
			}
			ts.requireKind(err, c.kind, 0)
		})
	}
}

// An HTTPS key document can name any controller. A credential signed with
// an attacker's key, claiming a trusted issuer as the key's controller, is
// rejected because the issuer's controller document does not list the key.
func (ts *LDPVerifierTestSuite) TestHTTPSKeyControllerClaimIsNotEnough() {
	attacker, holder := newSigner(ts.T()), newSigner(ts.T())
	const trusted = "https://api.collab.mosip.net/.well-known/ida-controller.json"
	const keyURL = "https://attacker.example/key.json"
	ts.web.serve(keyURL, ts.marshal(map[string]any{
		"id": keyURL, "type": "Ed25519VerificationKey2020", "controller": trusted,
		"publicKeyMultibase": strings.TrimPrefix(attacker.did, "did:key:"),
	}))
	vc := ts.credential(attacker, holder, func(vc map[string]any) { vc["issuer"] = trusted })
	vc = ts.sign(vc, attacker, didkeys.AssertionMethod, map[string]any{"verificationMethod": keyURL})
	v := ts.newVerifier(Config{IssuerTrusted: TrustIssuers(trusted)})
	_, err := v.verifyBareCredential(ts.ctx, ts.marshal(vc))
	ts.requireKind(err, ErrKeyNotAuthorized, 0)
}

func (ts *LDPVerifierTestSuite) TestIssuerTrust() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	vp := ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil)

	_, err := ts.verify(ts.trusting([]signer{newSigner(ts.T())}, nil), vp)
	ts.requireKind(err, ErrUntrustedIssuer, 0)

	_, err = ts.verify(ts.trusting([]signer{issuer}, nil), vp)
	ts.Require().NoError(err)

	byPrefix := func(id string) bool { return strings.HasPrefix(id, "did:key:") }
	_, err = ts.verify(ts.newVerifier(Config{IssuerTrusted: byPrefix}), vp)
	ts.Require().NoError(err)

	_, err = ts.verify(ts.newVerifier(Config{IssuerTrusted: AnyIssuer}), vp)
	ts.Require().NoError(err)
}

// Issuer trust is decided before the issuer's key is resolved, so an
// untrusted issuer causes no fetch.
func (ts *LDPVerifierTestSuite) TestUntrustedIssuerIsNotFetched() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	vc := ts.credential(issuer, holder, func(vc map[string]any) { vc["issuer"] = "did:web:untrusted.example.org" })
	vc = ts.sign(vc, issuer, didkeys.AssertionMethod, map[string]any{"verificationMethod": "did:web:untrusted.example.org#key-1"})
	_, err := ts.trusting([]signer{holder}, nil).verifyBareCredential(ts.ctx, ts.marshal(vc))
	ts.requireKind(err, ErrUntrustedIssuer, 0)
	ts.Zero(ts.web.hitCount("https://untrusted.example.org/.well-known/did.json"))
}

// A did:key issuer whose credential is signed with a different key.
func (ts *LDPVerifierTestSuite) TestIssuerMustControlProofKey() {
	issuer, holder, forger := newSigner(ts.T()), newSigner(ts.T()), newSigner(ts.T())
	vc := ts.sign(ts.credential(issuer, holder, nil), forger, didkeys.AssertionMethod, nil)
	_, err := ts.verify(ts.trusting([]signer{issuer}, nil), ts.presentation(holder, []any{vc}, nil))
	ts.requireKind(err, ErrIssuerKeyMismatch, 0)
}

func (ts *LDPVerifierTestSuite) TestTamperedPresentation() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	vp := ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil)
	vc := vp[fieldVerifiableCreds].([]any)[0].(map[string]any)
	vc[fieldCredentialSubject].(map[string]any)["alumniOf"] = "Tampered University"
	_, err := ts.verify(ts.trusting([]signer{issuer}, nil), vp)
	ts.requireKind(err, ErrSignature, presentationIndex)
}

// An undefined term is dropped by canonicalization, so the signature
// still verifies, but safe mode rejects the credential.
func (ts *LDPVerifierTestSuite) TestUndefinedTermRejected() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	vc := map[string]any{
		"@context":     []any{contextV1, contextEd25519},
		"type":         []any{typeCredential},
		"issuer":       issuer.did,
		"issuanceDate": "2026-01-01T00:00:00Z",
		"credentialSubject": map[string]any{
			"id": holder.did,
		},
	}
	vc = ts.sign(vc, issuer, didkeys.AssertionMethod, nil)
	v := ts.trusting([]signer{issuer}, nil)

	res, err := v.verifyBareCredential(ts.ctx, ts.marshal(vc))
	ts.Require().NoError(err, "legacy Ed25519Signature2020 path")
	ts.Equal(issuer.did, res.Issuer)

	vc[fieldCredentialSubject].(map[string]any)["role"] = "admin"
	ts.Require().NoError(ts.verifyProofOnly(vc), "the signature does not cover the dropped term")
	_, err = v.verifyBareCredential(ts.ctx, ts.marshal(vc))
	ts.requireKind(err, ErrUndefinedTerm, 0)

	delete(vc[fieldCredentialSubject].(map[string]any), "role")
	vc["type"] = []any{typeCredential, "UndefinedCredentialType"}
	_, err = v.verifyBareCredential(ts.ctx, ts.marshal(vc))
	ts.requireKind(err, ErrUndefinedTerm, 0)

	// An option the proof's context does not define is caught too.
	vc["type"] = []any{typeCredential}
	vc = ts.sign(vc, issuer, didkeys.AssertionMethod, map[string]any{"undefinedOption": "x"})
	_, err = v.verifyBareCredential(ts.ctx, ts.marshal(vc))
	ts.requireKind(err, ErrUndefinedTerm, 0)
}

func (ts *LDPVerifierTestSuite) TestContexts() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	const remote = "https://contexts.example.org/alumni/v1"
	ts.web.serve(remote, []byte(`{"@context": {"@vocab": "https://contexts.example.org/alumni#"}}`))

	allowing := ts.trusting([]signer{issuer}, func(c *Config) { c.RemoteContextAllowlist = []string{"https://contexts.example.org/"} })
	strict := ts.trusting([]signer{issuer}, nil)
	signing := ts.v
	ts.v = allowing // sign with a loader that can fetch the remote context
	vc := ts.credential(issuer, holder, func(vc map[string]any) {
		vc[fieldContext] = []any{contextV2, remote, contextEd25519}
	})
	ts.v = signing
	raw := ts.marshal(vc)

	_, err := strict.verifyBareCredential(ts.ctx, raw)
	ts.requireKind(err, ErrContext, 0)
	ts.ErrorIs(err, jsonld.ErrContextNotAllowed)

	for range 2 {
		_, err = allowing.verifyBareCredential(ts.ctx, raw)
		ts.Require().NoError(err)
	}
	ts.Equal(1, ts.web.hitCount(remote), "remote context is cached")
}

func (ts *LDPVerifierTestSuite) TestProofPolicy() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	vp := ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil)

	_, err := ts.verify(ts.trusting([]signer{issuer}, func(c *Config) { c.VPSuites = []string{proof.EcdsaSecp256r1Signature2019} }), vp)
	ts.requireField(err, ErrUnsupportedProof, "proof.type", presentationIndex)

	_, err = ts.verify(ts.trusting([]signer{issuer}, func(c *Config) { c.VCSuites = []string{proof.EcdsaSecp256r1Signature2019} }), vp)
	ts.requireField(err, ErrUnsupportedProof, "proof.type", 0)

	v := ts.trusting([]signer{issuer}, nil)
	// An Ed25519 key behind an EcdsaSecp256r1Signature2019 proof fails before
	// hashing.
	vp = ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil)
	vp[fieldProof].(map[string]any)["type"] = proof.EcdsaSecp256r1Signature2019
	_, err = ts.verify(v, vp)
	ts.requireKind(err, ErrSignature, presentationIndex)

	vp = ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil)
	vp[fieldProof] = []any{vp[fieldProof], vp[fieldProof]}
	_, err = ts.verify(v, vp)
	ts.requireField(err, ErrInvalidField, fieldProof, presentationIndex)

	vp = ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil)
	vp[fieldProof].(map[string]any)["proofValue"] = "m" + base64.StdEncoding.EncodeToString(make([]byte, 64))
	_, err = ts.verify(v, vp)
	ts.requireField(err, ErrInvalidField, fieldProof, presentationIndex)
}

func (ts *LDPVerifierTestSuite) TestPresentationShape() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	enveloped := map[string]any{
		"@context": []any{contextV2},
		"type":     "EnvelopedVerifiableCredential",
		"id":       "data:application/vc+sd-jwt,eyJhbGciOiJFUzI1NiJ9...",
	}
	v := ts.trusting([]signer{issuer}, nil)
	for name, c := range map[string]struct {
		vcs   any
		mut   func(map[string]any)
		kind  Kind
		field string
		index int
	}{
		"enveloped":     {[]any{enveloped}, nil, ErrInvalidField, fieldVerifiableCreds, 0},
		"string vc":     {[]any{"eyJhbGciOiJFUzI1NiJ9"}, nil, ErrInvalidField, fieldVerifiableCreds, 0},
		"no vcs":        {[]any{}, nil, ErrMissingField, fieldVerifiableCreds, presentationIndex},
		"not a VP":      {nil, func(vp map[string]any) { vp["type"] = []any{typeCredential} }, ErrInvalidField, "type", presentationIndex},
		"wrong context": {nil, func(vp map[string]any) { vp[fieldContext] = contextExamplesV2 }, ErrInvalidField, fieldContext, presentationIndex},
		"no proof":      {nil, func(vp map[string]any) { delete(vp, fieldProof) }, ErrMissingField, fieldProof, presentationIndex},
	} {
		ts.Run(name, func() {
			vcs := c.vcs
			if vcs == nil {
				vcs = []any{ts.credential(issuer, holder, nil)}
			}
			vp := ts.presentation(holder, vcs, nil)
			if c.mut != nil {
				c.mut(vp)
			}
			_, err := ts.verify(v, vp)
			ts.requireField(err, c.kind, c.field, c.index)
		})
	}
	_, err := v.VerifyPresentation(ts.ctx, []byte(`["not", "an", "object"]`), testChallenge, testDomain)
	ts.requireKind(err, ErrMalformed, presentationIndex)
}

func (ts *LDPVerifierTestSuite) TestLimits() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	vc := ts.credential(issuer, holder, nil)
	vp := ts.presentation(holder, []any{vc, vc}, nil)
	for name, c := range map[string]struct {
		mutate func(*Config)
		kind   Kind
	}{
		"size":        {func(c *Config) { c.MaxPresentationBytes = 100 }, ErrLimitExceeded},
		"depth":       {func(c *Config) { c.MaxJSONDepth = 3 }, ErrMalformed},
		"credentials": {func(c *Config) { c.MaxCredentials = 1 }, ErrLimitExceeded},
		"blank nodes": {func(c *Config) { c.MaxBlankNodes = 1 }, ErrLimitExceeded},
	} {
		ts.Run(name, func() {
			_, err := ts.verify(ts.trusting([]signer{issuer}, c.mutate), vp)
			ts.requireKind(err, c.kind, presentationIndex)
		})
	}
}

// statusList returns a BitstringStatusList credential signed by s with the
// given bits set.
func (ts *LDPVerifierTestSuite) statusList(s signer, set ...int) map[string]any {
	bits := make([]byte, 16<<10) // 131072 entries, the herd privacy minimum
	for _, i := range set {
		bits[i/8] |= 0x80 >> (i % 8)
	}
	var buf bytes.Buffer
	zw := gzip.NewWriter(&buf)
	_, err := zw.Write(bits)
	ts.Require().NoError(err)
	ts.Require().NoError(zw.Close())
	list := map[string]any{
		"@context":  []any{contextV2, contextEd25519},
		"id":        testStatusListURL,
		"type":      []any{typeCredential, "BitstringStatusListCredential"},
		"issuer":    s.did,
		"validFrom": "2026-01-01T00:00:00Z",
		"credentialSubject": map[string]any{
			"id":            testStatusListURL + "#list",
			"type":          "BitstringStatusList",
			"statusPurpose": "revocation",
			"encodedList":   "u" + base64.RawURLEncoding.EncodeToString(buf.Bytes()),
		},
	}
	return ts.sign(list, s, didkeys.AssertionMethod, nil)
}

func (ts *LDPVerifierTestSuite) TestCredentialStatus() {
	issuer, holder, other := newSigner(ts.T()), newSigner(ts.T()), newSigner(ts.T())
	vc := ts.credential(issuer, holder, func(vc map[string]any) {
		vc["credentialStatus"] = map[string]any{
			"id":                   testStatusListURL + "#5",
			"type":                 "BitstringStatusListEntry",
			"statusPurpose":        "revocation",
			"statusListIndex":      "5",
			"statusListCredential": testStatusListURL,
		}
	})
	vp := ts.presentation(holder, []any{vc}, nil)
	cases := []struct {
		name string
		list map[string]any // nil: not served
		kind Kind           // 0 = pass
	}{
		{"valid", ts.statusList(issuer, 4, 6), 0},
		{"revoked", ts.statusList(issuer, 5), ErrStatusSet},
		{"list from another issuer", ts.statusList(other), ErrStatusUnavailable},
		{"unreachable list", nil, ErrStatusUnavailable},
	}
	for _, c := range cases {
		ts.Run(c.name, func() {
			ts.web = newFakeWeb()
			if c.list != nil {
				ts.web.serve(testStatusListURL, ts.marshal(c.list))
			}
			withPurposes := func(purposes ...string) *Verifier {
				return ts.trusting([]signer{issuer}, func(cfg *Config) { cfg.StatusPurposes = purposes })
			}
			v := withPurposes("revocation")
			_, err := ts.verify(v, vp)
			if c.kind != 0 {
				ve := ts.verificationError(err, c.kind, 0)
				if c.kind == ErrStatusSet {
					ts.Equal("revocation", ve.StatusPurpose)
				}
				return
			}
			ts.Require().NoError(err)
			cred, err := v.verifyBareCredential(ts.ctx, ts.marshal(vc))
			ts.Require().NoError(err)
			ts.Equal(map[string]status.Result{"revocation": {Valid: true}}, cred.Status)
			ts.Equal(1, ts.web.hitCount(testStatusListURL), "status list is cached")

			cred, err = withPurposes("suspension").verifyBareCredential(ts.ctx, ts.marshal(vc))
			ts.Require().NoError(err)
			ts.Empty(cred.Status, "no entry for the purpose")
		})
	}
}

func (ts *LDPVerifierTestSuite) TestConcurrentVerification() {
	issuer, holder := newSigner(ts.T()), newSigner(ts.T())
	raw := ts.marshal(ts.presentation(holder, []any{ts.credential(issuer, holder, nil)}, nil))
	v := ts.trusting([]signer{issuer}, nil)
	var wg sync.WaitGroup
	errs := make(chan error, 8)
	for range 8 {
		wg.Go(func() {
			_, err := v.VerifyPresentation(ts.ctx, raw, testChallenge, testDomain)
			errs <- err
		})
	}
	wg.Wait()
	close(errs)
	for err := range errs {
		ts.NoError(err)
	}
}

func (ts *LDPVerifierTestSuite) TestErrorFormatting() {
	err := wrapError(ErrMalformed, "boom", proof.ErrMalformed)
	ts.Equal("presentation: malformed document: boom: malformed proof", err.Error())
	ts.ErrorIs(err, proof.ErrMalformed)
	ts.ErrorIs(err, ErrMalformed)
	ts.NotErrorIs(err, ErrSignature)
	ts.Equal("credential 2: missing field issuanceDate", withIndex(missingField(fieldIssuanceDate), 2).Error())
	ts.Equal("ldp.Kind(99)", Kind(99).Error())

	status := &VerificationError{Kind: ErrStatusSet, Index: 0, StatusPurpose: "revocation"}
	ts.Equal("credential 0: credential status set revocation", status.Error())
}
