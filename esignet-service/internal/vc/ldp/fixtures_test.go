/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ldp

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"

	"github.com/mr-tron/base58"

	"github.com/mosip/esignet/internal/vc/ldp/didkeys"
	"github.com/mosip/esignet/internal/vc/ldp/jsonval"
	"github.com/mosip/esignet/internal/vc/ldp/proof"
	"github.com/mosip/esignet/internal/vc/ldp/sig"
	"github.com/mosip/esignet/internal/vc/ldp/status"
)

// Tests against the sample credentials and presentations in
// testdata/{ldp_vc,vp,w3c,did} (see testdata/README.md).

// DID documents of sample issuers that list the DID itself, not the
// verification method, under assertionMethod, so they authorize no key.
const (
	mockIssuerDIDURL  = "https://mosip.github.io/inji-config/qa-inji1/mock/did.json"
	ngrokIssuerDIDURL = "https://868c-2405-201-1029-3025-a153-97e5-f47f-3b16.ngrok-free.app/.well-known/did.json"
)

// Sample credentials with valid signatures: json-gold canonicalization must
// reproduce the hash data their issuers signed.
var signedSampleCredentials = []string{
	"ldp_vc/PS256SignedMosipVC.json",
	"ldp_vc/RS256SignedMosipVC.json",
	"ldp_vc/ES256SignedVC.json",
	"ldp_vc/ES256KSignedMockVC.json",
	"ldp_vc/Ed25519Signature2018SignedVC-did.json",
	"ldp_vc/Ed25519Signature2018SignedVC-https.json",
	"ldp_vc/Ed25519Signature2020SignedSunbirdVC.json",
	"ldp_vc/mosipStatusVC.json",
	"ldp_vc/mosipUnrevokedStatusList.json",
	"ldp_vc/mosipRevokedStatusList.json",
}

func (ts *LDPVerifierTestSuite) TestSampleCredentialSignatures() {
	for _, name := range signedSampleCredentials {
		ts.Run(name, func() {
			ts.Require().NoError(ts.verifyProofOnly(ts.fixtureObject(name)))
		})
	}
}

// The full path adds VCDM validation, safe mode, key authorization and
// issuer/key control.
func (ts *LDPVerifierTestSuite) TestSampleCredentialsVerify() {
	for name, want := range map[string]struct {
		kind  Kind // 0 = pass
		field string
	}{
		"ldp_vc/ES256KSignedMockVC.json":                  {},
		"ldp_vc/Ed25519Signature2018SignedVC-did.json":    {},
		"ldp_vc/Ed25519Signature2020SignedSunbirdVC.json": {},
		// The issuer DID documents authorize no key (see authorizeAll below).
		"ldp_vc/ES256SignedVC.json":            {kind: ErrKeyNotAuthorized},
		"ldp_vc/mosipStatusVC.json":            {kind: ErrKeyNotAuthorized},
		"ldp_vc/mosipUnrevokedStatusList.json": {kind: ErrKeyNotAuthorized},
		// type "MOSIPVerifiableCredential" is not defined by its context, so
		// the signature does not cover it.
		"ldp_vc/PS256SignedMosipVC.json": {kind: ErrUndefinedTerm},
		// A VCDM 1.1 credential without issuanceDate.
		"ldp_vc/RS256SignedMosipVC.json": {kind: ErrMissingField, field: fieldIssuanceDate},
		// Uses terms (renderMethod and others) the pinned VCDM 2.0 context
		// does not define.
		"ldp_vc/Ed25519Signature2018SignedVC-https.json": {kind: ErrUndefinedTerm},
	} {
		ts.Run(name, func() {
			vc := ts.fixtureObject(name)
			res, err := ts.verifierAt(ts.clockFor(vc)).verifyBareCredential(ts.ctx, ts.fixture(name))
			if want.kind != 0 {
				ts.requireField(err, want.kind, want.field, 0)
				return
			}
			ts.Require().NoError(err)
			ts.Equal(jsonval.ID(vc["issuer"]), res.Issuer)
			ts.Contains(res.Types, typeCredential)
			ts.NotNil(res.Subject)
		})
	}
}

// With their DID documents listing the keys under assertionMethod, the
// samples rejected above for key authorization verify.
func (ts *LDPVerifierTestSuite) TestSampleCredentialsWithAuthorizedKeys() {
	ts.authorizeAll(mockIssuerDIDURL, "did/didMockKey.json")
	ts.authorizeAll(ngrokIssuerDIDURL, "did/didEcr1Key.json")
	for _, name := range []string{
		"ldp_vc/ES256SignedVC.json",
		"ldp_vc/mosipStatusVC.json",
		"ldp_vc/mosipUnrevokedStatusList.json",
	} {
		ts.Run(name, func() {
			vc := ts.fixtureObject(name)
			_, err := ts.verifierAt(ts.clockFor(vc)).verifyBareCredential(ts.ctx, ts.fixture(name))
			ts.Require().NoError(err)
		})
	}
}

// Legacy proof options are hashed under the suite context, not the
// document's @context. Without the security v2 context pinned, the
// RsaSignature2018 / Ed25519Signature2018 samples that depend on it fail.
func (ts *LDPVerifierTestSuite) TestLegacyProofsNeedSuiteContext() {
	dir := ts.T().TempDir()
	index := map[string]string{}
	ts.Require().NoError(json.Unmarshal(ts.fixture("contexts/index.json"), &index))
	for u, file := range index {
		if u == "https://w3id.org/security/v2" {
			delete(index, u)
			continue
		}
		ts.Require().NoError(os.WriteFile(filepath.Join(dir, file), ts.fixture("contexts/"+file), 0o600))
	}
	raw, err := json.Marshal(index)
	ts.Require().NoError(err)
	ts.Require().NoError(os.WriteFile(filepath.Join(dir, "index.json"), raw, 0o600))
	v, err := New(Config{IssuerTrusted: AnyIssuer, ContextsDir: dir, HTTPClient: ts.v.cfg.HTTPClient})
	ts.Require().NoError(err)
	vc := ts.fixtureObject("ldp_vc/RS256SignedMosipVC.json")
	parsed, err := proof.Parse(vc[fieldProof].(map[string]any), proof.DefaultVCSuites())
	ts.Require().NoError(err)
	m, err := v.keys.Resolve(ts.ctx, parsed.VerificationMethod)
	ts.Require().NoError(err)
	canonical, err := v.loader.Canonicalize(ts.ctx, withoutProof(vc))
	ts.Require().NoError(err)
	ts.ErrorIs(parsed.Verify(ts.ctx, v.loader, vc[fieldContext], canonical, m.Key), sig.ErrInvalidSignature)
}

func (ts *LDPVerifierTestSuite) TestTamperedCredentialFails() {
	ts.requireKind(ts.verifyProofOnly(ts.fixtureObject("ldp_vc/tamperedVC.json")), ErrSignature, presentationIndex)
}

func (ts *LDPVerifierTestSuite) TestCredentialMissingContext() {
	_, err := ts.v.verifyBareCredential(ts.ctx, ts.fixture("ldp_vc/invalidVC.json"))
	ts.requireField(err, ErrMissingField, fieldContext, 0)
}

func (ts *LDPVerifierTestSuite) TestUnsupportedDIDMethod() {
	vc := ts.fixtureObject("ldp_vc/InvalidDidUrlSunbirdVC.json")
	_, err := ts.verifierAt(ts.clockFor(vc)).verifyBareCredential(ts.ctx, ts.fixture("ldp_vc/InvalidDidUrlSunbirdVC.json"))
	ts.requireKind(err, ErrKeyResolution, 0)
}

// The W3C Data Integrity test vectors (digitalbazaar) cross-check json-gold
// canonicalization and its workarounds against another implementation,
// including the VCDM 2.0 @graph container of a VP. The verifier does not
// accept Data Integrity proofs, so the test recomputes their hash data and
// checks the signature directly.
func (ts *LDPVerifierTestSuite) TestW3CVectorsCanonicalization() {
	for name, alg := range map[string]string{
		"w3c/eddsa-rdfc-2022-signed.json":            sig.EdDSA,
		"w3c/eddsa-rdfc-2022-presentation.json":      sig.EdDSA,
		"w3c/ecdsa-rdfc-2019-p256-signed.json":       sig.ES256,
		"w3c/ecdsa-rdfc-2019-p256-presentation.json": sig.ES256,
	} {
		ts.Run(name, func() {
			doc := ts.fixtureObject(name)
			pr := doc[fieldProof].(map[string]any)
			pub, err := didkeys.KeyFromDID(pr["verificationMethod"].(string))
			ts.Require().NoError(err)
			signature, err := base58.Decode(strings.TrimPrefix(pr["proofValue"].(string), "z"))
			ts.Require().NoError(err)

			hash, err := proof.HashData(ts.ctx, ts.v.loader, doc, pr)
			ts.Require().NoError(err)
			ts.NoError(sig.Verify(alg, pub, hash, signature))

			doc["tampered"] = true
			doc[fieldContext] = append(jsonval.List(doc[fieldContext]), map[string]any{"tampered": "https://example.org/tampered"})
			hash, err = proof.HashData(ts.ctx, ts.v.loader, doc, pr)
			ts.Require().NoError(err)
			ts.ErrorIs(sig.Verify(alg, pub, hash, signature), sig.ErrInvalidSignature)
		})
	}

	// The verifier itself rejects them.
	_, err := ts.v.verifyBareCredential(ts.ctx, ts.fixture("w3c/eddsa-rdfc-2022-signed.json"))
	ts.requireField(err, ErrUnsupportedProof, "proof.type", 0)
}

func (ts *LDPVerifierTestSuite) TestSamplePresentationSignatures() {
	for name, want := range map[string]Kind{
		"vp/JsonWebSignature2020SignedVP-didJws.json": 0,
		"vp/ES256AlgorithmVP.json":                    0,
		"vp/ES256KAlgorithmVP.json":                   0,
		"vp/Ed25519Signature2020SignedVP-didKey.json": ErrSignature,
		// Carries "hello" in the detached JWS payload segment.
		"vp/Ed25519Signature2018SignedVP-didKey.json": ErrInvalidField,
	} {
		ts.Run(name, func() {
			err := ts.verifyProofOnly(ts.fixtureObject(name))
			if want == 0 {
				ts.Require().NoError(err)
				return
			}
			ts.requireKind(err, want, presentationIndex)
		})
	}
}

// ES256AlgorithmVP / ES256KAlgorithmVP carry no proofPurpose, so the
// presentation fails. Their credential verifies on its own, and the
// did:jwk holder binds to the differently encoded did:jwk subject.
func (ts *LDPVerifierTestSuite) TestJWSPresentations() {
	for _, name := range []string{"vp/ES256AlgorithmVP.json", "vp/ES256KAlgorithmVP.json"} {
		ts.Run(name, func() {
			vp := ts.fixtureObject(name)
			vc := jsonval.List(vp[fieldVerifiableCreds])[0].(map[string]any)
			pr := vp[fieldProof].(map[string]any)
			v := ts.verifierAt(ts.clockFor(vc))

			_, err := v.VerifyPresentation(ts.ctx, ts.fixture(name), jsonval.String(pr["challenge"]), jsonval.String(pr["domain"]))
			ts.requireKind(err, ErrProofPurpose, presentationIndex)

			res, err := v.verifyBareCredential(ts.ctx, ts.marshal(vc))
			ts.Require().NoError(err)
			ts.Equal("did:web:api.released.mosip.net:identity-service:02b073b8-aacd-472e-b63f-265bb7ccdd9f", res.Issuer)
			ts.NoError(ts.bindFixture(name))
		})
	}
}

// The enclosed credential's issuer is not served, so the full path passes
// the VP proof and holder binding, then stops at the credential.
func (ts *LDPVerifierTestSuite) TestSamplePresentationBoundToChallengeAndDomain() {
	const name = "vp/JsonWebSignature2020SignedVP-didJws.json"
	vp := ts.fixtureObject(name)
	pr := vp[fieldProof].(map[string]any)
	v := ts.verifierAt(ts.clockFor(jsonval.List(vp[fieldVerifiableCreds])[0].(map[string]any)))
	_, err := v.VerifyPresentation(ts.ctx, ts.fixture(name), jsonval.String(pr["challenge"]), jsonval.String(pr["domain"]))
	ts.requireKind(err, ErrKeyResolution, 0)
}

// Holder binding on sample presentations whose signatures are placeholders:
// the VP proof key is taken from its did:key/did:jwk verificationMethod.
func (ts *LDPVerifierTestSuite) TestSampleHolderBinding() {
	for name, pass := range map[string]bool{
		"vp/validJWKHolderBindingVP.json":   true,
		"vp/crossMethodBindingVP.json":      true,
		"vp/P256HolderBindingVP.json":       true,
		"vp/webHolderBindingVP.json":        false,
		"vp/VPWithMissingHolder.json":       false,
		"vp/InvalidHolderBindingVP.json":    false,
		"vp/InvalidJWKHolderBindingVP.json": false,
		"vp/VPWithMalformedJwk.json":        false,
		"vp/VPWithEmptySubject.json":        false,
		"vp/VPWithSubjectMissingId.json":    false,
		"vp/UnsupportedKeyTypeVP.json":      false,
	} {
		ts.Run(name, func() {
			err := ts.bindFixture(name)
			if pass {
				ts.Require().NoError(err)
				return
			}
			ts.requireKind(err, ErrHolderBinding, presentationIndex)
		})
	}
}

// The holder's proof method binds, so the subject check itself rejects them.
func (ts *LDPVerifierTestSuite) TestSubjectIDMissingMessage() {
	for _, name := range []string{"vp/VPWithEmptySubject.json", "vp/VPWithSubjectMissingId.json"} {
		ve := ts.verificationError(ts.bindFixture(name), ErrHolderBinding, presentationIndex)
		ts.Equal(msgSubjectIDMissing, ve.Message, name)
	}
}

func (ts *LDPVerifierTestSuite) TestMalformedJWKMessage() {
	ve := ts.verificationError(ts.bindFixture("vp/VPWithMalformedJwk.json"), ErrHolderBinding, presentationIndex)
	ts.Equal(msgFailedToDecode, ve.Message)
}

func (ts *LDPVerifierTestSuite) TestEmptyCredentialList() {
	for _, name := range []string{"vp/EmptyVerifiableCredentialVP.json", "vp/vpWithMissingVerifiableCredential.json"} {
		vp := ts.fixtureObject(name)
		if vp[fieldProof] == nil {
			vp[fieldProof] = map[string]any{}
		}
		_, err := ts.v.parsePresentation(vp)
		ts.requireField(err, ErrMissingField, fieldVerifiableCreds, presentationIndex)
	}
}

// Status lists end to end, with the issuer's DID document authorizing its
// key (see TestSampleCredentialsVerify).
func (ts *LDPVerifierTestSuite) TestSampleStatusList() {
	ts.authorizeAll(mockIssuerDIDURL, "did/didMockKey.json")
	for _, c := range []struct {
		vc, list string
		kind     Kind // 0 = pass
	}{
		{"ldp_vc/mosipStatusVC.json", "ldp_vc/mosipUnrevokedStatusList.json", 0},
		{"ldp_vc/mosipStatusVC.json", "ldp_vc/mosipRevokedStatusList.json", ErrStatusSet},
	} {
		ts.Run(c.list, func() {
			ts.web.serve(mosipStatusListURL, ts.fixture(c.list))
			v := ts.newVerifier(Config{
				StatusPurposes: []string{"revocation"},
				Now:            ts.laterClock(ts.fixtureObject(c.vc), ts.fixtureObject(c.list)),
			})
			res, err := v.verifyBareCredential(ts.ctx, ts.fixture(c.vc))
			if c.kind != 0 {
				ts.requireKind(err, c.kind, 0)
				return
			}
			ts.Require().NoError(err)
			ts.Equal(map[string]status.Result{"revocation": {Valid: true}}, res.Status)
		})
	}
}

// bindFixture runs holder binding on a sample presentation whose signature
// is a placeholder. A presentation without a proof is taken as signed by
// its holder.
func (ts *LDPVerifierTestSuite) bindFixture(name string) error {
	vp := ts.fixtureObject(name)
	holderID := jsonval.ID(vp["holder"])
	vm := holderID
	if pr, ok := vp[fieldProof].(map[string]any); ok {
		vm = jsonval.String(pr["verificationMethod"])
	}
	m, err := ts.v.keys.Resolve(ts.ctx, vm)
	if err != nil {
		m = &didkeys.Method{}
	}
	h, err := bindHolder(holderID, m)
	if err != nil {
		return err
	}
	vcs := jsonval.List(vp[fieldVerifiableCreds])
	if len(vcs) == 0 {
		return holderError("no credentials", nil)
	}
	for _, e := range vcs {
		vc, _ := e.(map[string]any)
		subjects, _ := jsonval.Objects(vc[fieldCredentialSubject])
		if err := h.bindSubjects(subjects); err != nil {
			return err
		}
	}
	return nil
}
