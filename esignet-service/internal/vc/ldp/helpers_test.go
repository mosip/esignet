/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ldp

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"sync"
	"testing"
	"time"

	"github.com/mr-tron/base58"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/vc/ldp/didkeys"
	"github.com/mosip/esignet/internal/vc/ldp/jsonval"
	"github.com/mosip/esignet/internal/vc/ldp/proof"
)

// Status list URL used by the mosip(Un)RevokedVC fixtures.
const mosipStatusListURL = "https://injicertify-mock.qa-inji1.mosip.net/v1/certify/credentials/status-list/56622ad1-c304-4d7a-baf0-08836d63c2bf"

// fixtureRoutes serves the DID, key and controller documents the sample
// fixtures reference, so the real did:web and HTTPS resolvers run without
// network access.
var fixtureRoutes = map[string]string{
	"https://api.collab.mosip.net/.well-known/ida-public-key.json":                                  "did/idaPublicKey.json",
	"https://api.collab.mosip.net/.well-known/ida-controller.json":                                  "did/idaController.json",
	"https://vharsh.github.io/DID/mock-public-key.json":                                             "did/httpsMockKey.json",
	"https://vharsh.github.io/DID/mock-controller.json":                                             "did/httpsMockController.json",
	"https://vharsh.github.io/DID/mock-public-key2.json":                                            "did/httpsMockKey2.json",
	"https://vharsh.github.io/DID/mock-controller2.json":                                            "did/httpsMockController2.json",
	"https://868c-2405-201-1029-3025-a153-97e5-f47f-3b16.ngrok-free.app/.well-known/did.json":       "did/didEcr1Key.json",
	"https://vharsh.github.io/DID/echarsh/did.json":                                                 "did/didEck1Key.json",
	"https://vharsh.github.io/DID/local/did.json":                                                   "did/didEdPublicKey.json",
	"https://api.released.mosip.net/identity-service/02b073b8-aacd-472e-b63f-265bb7ccdd9f/did.json": "did/didMosipKey.json",
	"https://mosip.github.io/inji-config/qa-inji1/mock/did.json":                                    "did/didMockKey.json",
}

// fakeWeb is an http.RoundTripper serving canned bodies by URL.
type fakeWeb struct {
	mu     sync.Mutex
	bodies map[string][]byte
	hits   map[string]int
}

func newFakeWeb() *fakeWeb {
	return &fakeWeb{bodies: map[string][]byte{}, hits: map[string]int{}}
}

func (f *fakeWeb) serve(url string, body []byte) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.bodies[url] = body
}

func (f *fakeWeb) hitCount(url string) int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.hits[url]
}

func (f *fakeWeb) RoundTrip(req *http.Request) (*http.Response, error) {
	f.mu.Lock()
	body, ok := f.bodies[req.URL.String()]
	f.hits[req.URL.String()]++
	f.mu.Unlock()
	if !ok {
		return &http.Response{StatusCode: http.StatusNotFound, Body: http.NoBody, Request: req}, nil
	}
	return &http.Response{
		StatusCode: http.StatusOK,
		Header:     http.Header{"Content-Type": {"application/json"}},
		Body:       io.NopCloser(bytes.NewReader(body)),
		Request:    req,
	}, nil
}

// LDPVerifierTestSuite covers the ldp package: sample fixtures and the
// server-side policy.
type LDPVerifierTestSuite struct {
	suite.Suite
	ctx context.Context
	web *fakeWeb
	v   *Verifier
}

func TestLDPVerifierTestSuite(t *testing.T) {
	suite.Run(t, new(LDPVerifierTestSuite))
}

func (ts *LDPVerifierTestSuite) SetupTest() {
	ts.ctx = context.Background()
	ts.web = newFakeWeb()
	for url, file := range fixtureRoutes {
		ts.web.serve(url, ts.fixture(file))
	}
	ts.v = ts.newVerifier(Config{})
}

// newVerifier builds a Verifier on the fake web with the test contexts. It
// trusts every issuer and reads testNow unless cfg says otherwise.
func (ts *LDPVerifierTestSuite) newVerifier(cfg Config) *Verifier {
	cfg.ContextsDir = filepath.Join("testdata", "contexts")
	cfg.HTTPClient = &http.Client{Transport: ts.web}
	if cfg.IssuerTrusted == nil {
		cfg.IssuerTrusted = AnyIssuer
	}
	if cfg.Now == nil {
		cfg.Now = func() time.Time { return testNow }
	}
	v, err := New(cfg)
	ts.Require().NoError(err)
	return v
}

// verifierAt is a verifier trusting every issuer with its clock at now.
func (ts *LDPVerifierTestSuite) verifierAt(now func() time.Time) *Verifier {
	return ts.newVerifier(Config{Now: now})
}

func (ts *LDPVerifierTestSuite) fixture(name string) []byte {
	b, err := os.ReadFile(filepath.Join("testdata", filepath.FromSlash(name)))
	ts.Require().NoError(err)
	return b
}

func (ts *LDPVerifierTestSuite) fixtureObject(name string) map[string]any {
	doc, err := decodeObject(ts.fixture(name), 0)
	ts.Require().NoError(err)
	return doc
}

// clockFor pins the clock just after a credential became valid, so fixtures
// whose validity has since passed still verify.
func (ts *LDPVerifierTestSuite) clockFor(vc map[string]any) func() time.Time {
	for _, f := range []string{fieldIssuanceDate, fieldValidFrom} {
		if t, ok := jsonval.Time(vc[f]); ok {
			return func() time.Time { return t.Add(5 * time.Second) }
		}
	}
	ts.FailNow("credential has no issuanceDate/validFrom")
	return nil
}

// laterClock is clockFor of whichever document became valid last.
func (ts *LDPVerifierTestSuite) laterClock(docs ...map[string]any) func() time.Time {
	var latest time.Time
	for _, d := range docs {
		if t := ts.clockFor(d)(); t.After(latest) {
			latest = t
		}
	}
	return func() time.Time { return latest }
}

// requireKind asserts a *VerificationError of kind at index.
func (ts *LDPVerifierTestSuite) requireKind(err error, kind Kind, index int) {
	_ = ts.verificationError(err, kind, index)
}

// requireField asserts a *VerificationError of kind about field at index.
func (ts *LDPVerifierTestSuite) requireField(err error, kind Kind, field string, index int) {
	ve := ts.verificationError(err, kind, index)
	ts.Require().Equal(field, ve.Field, ve.Error())
}

// verificationError is requireKind returning the error for further checks.
func (ts *LDPVerifierTestSuite) verificationError(err error, kind Kind, index int) *VerificationError {
	ts.Require().Error(err)
	var ve *VerificationError
	ts.Require().True(errors.As(err, &ve), "not a VerificationError: %v", err)
	ts.Require().Equal(kind, ve.Kind, ve.Error())
	ts.Require().Equal(index, ve.Index, ve.Error())
	ts.Require().ErrorIs(err, kind)
	return ve
}

// verifyBareCredential runs the per-VC checks VerifyPresentation applies to
// each embedded credential, for fixtures that are a bare VC.
func (v *Verifier) verifyBareCredential(ctx context.Context, raw []byte) (*credentialResult, error) {
	vc, err := v.decode(raw)
	if err != nil {
		return nil, err
	}
	return v.verifyCredential(ctx, vc, v.cfg.Now(), 0)
}

// verifyProofOnly checks a document's proof signature with every suite
// allowed and no issuer, holder or key-authorization policy.
func (ts *LDPVerifierTestSuite) verifyProofOnly(doc map[string]any) error {
	pr, err := singleProof(doc)
	if err != nil {
		return err
	}
	parsed, err := proof.Parse(pr, slices.Concat(proof.DefaultVCSuites(), []string{proof.JSONWebSignature2020}))
	if err != nil {
		return proofError(err)
	}
	m, err := ts.v.keys.Resolve(ts.ctx, parsed.VerificationMethod)
	if err != nil {
		return wrapError(ErrKeyResolution, "resolve", err)
	}
	canonical, err := ts.v.loader.Canonicalize(ts.ctx, withoutProof(doc))
	if err != nil {
		return jsonldError(err)
	}
	if err := parsed.Verify(ts.ctx, ts.v.loader, doc[fieldContext], canonical, m.Key); err != nil {
		return proofError(err)
	}
	return nil
}

// authorizeAll serves the DID document in file at url with every
// verification method listed under assertionMethod and authentication.
func (ts *LDPVerifierTestSuite) authorizeAll(url, file string) {
	doc := ts.fixtureObject(file)
	var ids []any
	for _, e := range jsonval.List(doc["verificationMethod"]) {
		ids = append(ids, jsonval.ID(e))
	}
	doc[didkeys.AssertionMethod], doc[didkeys.Authentication] = ids, ids
	ts.web.serve(url, ts.marshal(doc))
}

// signer is an Ed25519 identity for Go-generated fixtures.
type signer struct {
	did, vm string
	priv    ed25519.PrivateKey
}

// newSigner returns a did:key signer.
func newSigner(t *testing.T) signer {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	id := "z" + base58.Encode(append([]byte{0xed, 0x01}, pub...))
	return signer{did: "did:key:" + id, vm: "did:key:" + id + "#" + id, priv: priv}
}

// newWebSigner returns a did:web signer for host whose DID document, served
// on the fake web, lists its key under the given relationships.
func (ts *LDPVerifierTestSuite) newWebSigner(host string, relationships ...string) signer {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	ts.Require().NoError(err)
	s := signer{did: "did:web:" + host, vm: "did:web:" + host + "#key-1", priv: priv}
	doc := map[string]any{
		"id": s.did,
		"verificationMethod": []any{map[string]any{
			"id": s.vm, "type": "JsonWebKey2020", "controller": s.did,
			"publicKeyJwk": map[string]any{"kty": "OKP", "crv": "Ed25519", "x": base64.RawURLEncoding.EncodeToString(pub)},
		}},
	}
	for _, rel := range relationships {
		doc[rel] = []any{"#key-1"}
	}
	ts.web.serve("https://"+host+"/.well-known/did.json", ts.marshal(doc))
	return s
}

// sign attaches an Ed25519Signature2020 proof to doc.
func (ts *LDPVerifierTestSuite) sign(doc map[string]any, s signer, purpose string, extra map[string]any) map[string]any {
	pr := map[string]any{
		"type":               proof.Ed25519Signature2020,
		"created":            "2026-01-01T00:00:00Z",
		"verificationMethod": s.vm,
		"proofPurpose":       purpose,
	}
	for k, v := range extra {
		if v == nil {
			delete(pr, k)
			continue
		}
		pr[k] = v
	}
	delete(doc, "proof")
	hash, err := proof.HashData(ts.ctx, ts.v.loader, doc, pr)
	ts.Require().NoError(err)
	pr["proofValue"] = "z" + base58.Encode(ed25519.Sign(s.priv, hash))
	doc["proof"] = pr
	return doc
}

func (ts *LDPVerifierTestSuite) marshal(doc map[string]any) []byte {
	b, err := json.Marshal(doc)
	ts.Require().NoError(err)
	return b
}
