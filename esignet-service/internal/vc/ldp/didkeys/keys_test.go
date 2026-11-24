/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package didkeys

import (
	"bytes"
	"context"
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"io"
	"maps"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/btcsuite/btcd/btcec/v2"
	jose "github.com/go-jose/go-jose/v4"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/vc/ldp/fetch"
)

// Keys from the sample fixtures in ../testdata.
const (
	ed25519DIDKey = "did:key:z6MkpiJgQdNWUzyojaFuCzQ1MWvSSaxUfL1tvbcRfqWFoJRK"
	p256DIDKey    = "did:key:zDnaerxS9pZpUuTfMDfbSgK8Sst98qTPrQ7nZ29K8xPj7HNo6"
	x25519DIDKey  = "did:key:z6LSpTHR8VNsBx7Bx5a97BvL9K6U373q9B5h1m2S1e4e"
	mockDIDWeb    = "did:web:mosip.github.io:inji-config:qa-inji1:mock"
	mockDIDWebURL = "https://mosip.github.io/inji-config/qa-inji1/mock/did.json"
	idaKeyURL     = "https://api.collab.mosip.net/.well-known/ida-public-key.json"
	idaController = "https://api.collab.mosip.net/.well-known/ida-controller.json"
)

// routes serves each URL its body, or a redirect for a body made by redirect.
type routes map[string][]byte

const redirectPrefix = "redirect:"

func redirect(to string) []byte { return []byte(redirectPrefix + to) }

func (r routes) RoundTrip(req *http.Request) (*http.Response, error) {
	body, ok := r[req.URL.String()]
	if !ok {
		return &http.Response{StatusCode: http.StatusNotFound, Body: http.NoBody, Request: req}, nil
	}
	if to, ok := bytes.CutPrefix(body, []byte(redirectPrefix)); ok {
		return &http.Response{StatusCode: http.StatusFound, Header: http.Header{"Location": {string(to)}}, Body: http.NoBody, Request: req}, nil
	}
	return &http.Response{StatusCode: http.StatusOK, Body: io.NopCloser(bytes.NewReader(body)), Request: req}, nil
}

type KeysTestSuite struct {
	suite.Suite
	ctx    context.Context
	routes routes
	r      Resolver
}

func TestKeysTestSuite(t *testing.T) {
	suite.Run(t, new(KeysTestSuite))
}

func (ts *KeysTestSuite) SetupTest() {
	ts.ctx = context.Background()
	ts.routes = routes{
		mockDIDWebURL: ts.testdata("didMockKey.json"),
		idaKeyURL:     ts.testdata("idaPublicKey.json"),
		idaController: ts.testdata("idaController.json"),
	}
	ts.r = ts.newResolver()
}

func (ts *KeysTestSuite) newResolver() Resolver {
	return NewResolver(fetch.New(fetch.Options{Client: &http.Client{Transport: ts.routes}, MaxBytes: 1 << 20}), time.Minute, 8)
}

func (ts *KeysTestSuite) testdata(name string) []byte {
	b, err := os.ReadFile(filepath.Join("..", "testdata", "did", name))
	ts.Require().NoError(err)
	return b
}

func (ts *KeysTestSuite) TestDIDKey() {
	k, err := KeyFromDID(ed25519DIDKey + "#z6MkpiJgQdNWUzyojaFuCzQ1MWvSSaxUfL1tvbcRfqWFoJRK")
	ts.Require().NoError(err)
	ts.IsType(ed25519.PublicKey{}, k)

	k, err = KeyFromDID(p256DIDKey)
	ts.Require().NoError(err)
	ec, ok := k.(*ecdsa.PublicKey)
	ts.Require().True(ok)
	ts.Equal(elliptic.P256(), ec.Curve)

	_, err = KeyFromDID(x25519DIDKey)
	ts.ErrorIs(err, ErrUnsupportedKey)
	_, err = KeyFromDID("did:key:6MkpiJgQdNWUzyojaFuCzQ1MWvSSaxUfL1tvbcRfqWFoJRK")
	ts.ErrorIs(err, ErrResolution)
	_, err = KeyFromDID("did:key:z6MkpiJgQdNWUzyojaFuCzQ1MWvSSaxUfL1tvbcRfqWFoJR@")
	ts.ErrorIs(err, ErrResolution)
	_, err = KeyFromDID("did:web:example.com")
	ts.ErrorIs(err, ErrUnsupportedMethod)
}

func (ts *KeysTestSuite) TestDIDJWK() {
	pub, _, err := ed25519.GenerateKey(nil)
	ts.Require().NoError(err)
	jwk, err := json.Marshal(map[string]string{"kty": "OKP", "crv": "Ed25519", "x": base64.RawURLEncoding.EncodeToString(pub)})
	ts.Require().NoError(err)
	unpadded := "did:jwk:" + base64.RawURLEncoding.EncodeToString(jwk)
	padded := "did:jwk:" + base64.URLEncoding.EncodeToString(jwk)

	for _, did := range []string{unpadded, unpadded + "#0", padded, padded + "#0"} {
		k, err := KeyFromDID(did)
		ts.Require().NoError(err, did)
		ts.True(Equal(pub, k), did)
	}

	private, err := json.Marshal(map[string]string{"kty": "OKP", "crv": "Ed25519", "x": "AA", "d": "AA"})
	ts.Require().NoError(err)
	_, err = KeyFromDID("did:jwk:" + base64.RawURLEncoding.EncodeToString(private))
	ts.ErrorIs(err, ErrResolution)
	_, err = KeyFromDID("did:jwk:invalid-base64")
	ts.ErrorIs(err, ErrResolution)
}

// A did:jwk encryption key is listed under keyAgreement only, so it
// authorizes no proof purpose.
func (ts *KeysTestSuite) TestDIDJWKKeyUse() {
	pub, _, err := ed25519.GenerateKey(nil)
	ts.Require().NoError(err)
	for use, want := range map[string][]string{"": relationships, "sig": relationships, "enc": nil} {
		jwk := ts.jwk(pub)
		if use != "" {
			jwk["use"] = use
		}
		m, err := ts.r.Resolve(ts.ctx, "did:jwk:"+base64.RawURLEncoding.EncodeToString(ts.marshal(jwk))+"#0")
		ts.Require().NoError(err, use)
		ts.Equal(want, m.Relationships, use)
	}
}

func (ts *KeysTestSuite) TestParseDIDURL() {
	d, err := ParseDIDURL("did:web:example.com%3A8443:issuers:1#key-1")
	ts.Require().NoError(err)
	ts.Equal("did:web:example.com%3A8443:issuers:1", d.DID)
	ts.Equal("web", d.Method)
	ts.Equal("key-1", d.Fragment)
	_, err = ParseDIDURL("did:Web:example.com")
	ts.ErrorIs(err, ErrResolution)
}

func (ts *KeysTestSuite) TestDIDWebURL() {
	for id, want := range map[string]string{
		"example.com":                     "https://example.com/.well-known/did.json",
		"example.com%3A8443":              "https://example.com:8443/.well-known/did.json",
		"mosip.github.io:inji-config:qa":  "https://mosip.github.io/inji-config/qa/did.json",
		"example.com:user%20one:profiles": "https://example.com/user%20one/profiles/did.json",
	} {
		got, err := didWebURL(id)
		ts.Require().NoError(err, id)
		ts.Equal(want, got, id)
	}
	for _, id := range []string{"example.com:..", "example.com::x", "exa%2Fmple.com", "example.com:a%2Fb", "user%40example.com"} {
		_, err := didWebURL(id)
		ts.ErrorIs(err, ErrResolution, id)
	}
}

func (ts *KeysTestSuite) TestDIDKeyMethod() {
	m, err := ts.r.Resolve(ts.ctx, ed25519DIDKey+"#z6MkpiJgQdNWUzyojaFuCzQ1MWvSSaxUfL1tvbcRfqWFoJRK")
	ts.Require().NoError(err)
	ts.Equal(ed25519DIDKey, m.Controller)
	ts.True(m.Authorizes(AssertionMethod))
	ts.True(m.Authorizes(Authentication))
}

func (ts *KeysTestSuite) TestDIDWebResolver() {
	for _, fragment := range []string{"kMVyZTvx8G0h1YZnUW4OJtr2LRVZUuGl8pQuJl3ymXI", "ojsIq-D4X3UvTBdMQwRudb8cR-BOhoXtdM6cj74hKGM", "QizhlYu5pcVa8l_fmElYzeLukKPShmkd0vIfF1Y7Oco"} {
		m, err := ts.r.Resolve(ts.ctx, mockDIDWeb+"#"+fragment)
		ts.Require().NoError(err, fragment)
		ts.Equal(mockDIDWeb, m.Controller)
		ts.NotNil(m.Key)
		// The document lists the DID itself, not the method, under its
		// relationships, so it authorizes nothing.
		ts.Empty(m.Relationships, fragment)
	}
	_, err := ts.r.Resolve(ts.ctx, mockDIDWeb+"#missing")
	ts.ErrorIs(err, ErrResolution)

	// Cached: the document is not fetched again.
	delete(ts.routes, mockDIDWebURL)
	_, err = ts.r.Resolve(ts.ctx, mockDIDWeb+"#kMVyZTvx8G0h1YZnUW4OJtr2LRVZUuGl8pQuJl3ymXI")
	ts.NoError(err)

	_, err = ts.r.Resolve(ts.ctx, "did:web:unknown.example.org#key-1")
	ts.ErrorIs(err, ErrResolution)
}

func (ts *KeysTestSuite) TestDIDWebDocumentChecks() {
	pub, _, err := ed25519.GenerateKey(nil)
	ts.Require().NoError(err)
	vm := map[string]any{"id": "#key-1", "type": "JsonWebKey2020", "publicKeyJwk": ts.jwk(pub)}
	doc := func(id string, rels map[string]any) []byte {
		d := map[string]any{"id": id, "verificationMethod": []any{vm}}
		maps.Copy(d, rels)
		b, err := json.Marshal(d)
		ts.Require().NoError(err)
		return b
	}
	ts.routes["https://issuer.example.org/.well-known/did.json"] = doc("did:web:issuer.example.org",
		map[string]any{"assertionMethod": []any{"#key-1"}, "authentication": []any{"did:web:issuer.example.org#other"}})
	ts.routes["https://other.example.org/.well-known/did.json"] = doc("did:web:issuer.example.org", nil)

	m, err := ts.r.Resolve(ts.ctx, "did:web:issuer.example.org#key-1")
	ts.Require().NoError(err, "relative verification method id")
	ts.True(Equal(pub, m.Key))
	ts.Equal("did:web:issuer.example.org", m.Controller)
	ts.Equal([]string{AssertionMethod}, m.Relationships, "only relationships that reference the method")

	_, err = ts.r.Resolve(ts.ctx, "did:web:other.example.org#key-1")
	ts.ErrorIs(err, ErrResolution, "document id must match the DID")
}

// A did:web document may redirect within its origin only: an open redirect on
// the DID's host must not let another host answer for the DID.
func (ts *KeysTestSuite) TestDIDWebRedirects() {
	pub, _, err := ed25519.GenerateKey(nil)
	ts.Require().NoError(err)
	doc := func(did string) []byte {
		return ts.marshal(map[string]any{
			"id":                 did,
			"verificationMethod": []any{map[string]any{"id": "#key-1", "type": "JsonWebKey2020", "publicKeyJwk": ts.jwk(pub)}},
		})
	}
	ts.routes["https://moved.example.org/.well-known/did.json"] = redirect("https://moved.example.org/did/current.json")
	ts.routes["https://moved.example.org/did/current.json"] = doc("did:web:moved.example.org")
	ts.routes["https://open.example.org/.well-known/did.json"] = redirect("https://attacker.example.net/did.json")
	ts.routes["https://attacker.example.net/did.json"] = doc("did:web:open.example.org")

	_, err = ts.r.Resolve(ts.ctx, "did:web:moved.example.org#key-1")
	ts.NoError(err, "same-origin redirect")
	_, err = ts.r.Resolve(ts.ctx, "did:web:open.example.org#key-1")
	ts.ErrorIs(err, ErrResolution)
	ts.ErrorIs(err, fetch.ErrForbiddenURL, "cross-origin redirect")
}

// A method embedded in a relationship is found and authorized for it.
func (ts *KeysTestSuite) TestDIDWebEmbeddedMethod() {
	pub, _, err := ed25519.GenerateKey(nil)
	ts.Require().NoError(err)
	ts.routes["https://embedded.example.org/.well-known/did.json"] = ts.marshal(map[string]any{
		"id": "did:web:embedded.example.org",
		"authentication": []any{map[string]any{
			"id": "did:web:embedded.example.org#auth", "type": "JsonWebKey2020", "publicKeyJwk": ts.jwk(pub),
		}},
	})
	m, err := ts.r.Resolve(ts.ctx, "did:web:embedded.example.org#auth")
	ts.Require().NoError(err)
	ts.Equal([]string{Authentication}, m.Relationships)
}

func (ts *KeysTestSuite) TestHTTPSKeyDocument() {
	m, err := ts.r.Resolve(ts.ctx, idaKeyURL)
	ts.Require().NoError(err)
	ts.IsType(&rsa.PublicKey{}, m.Key)
	ts.Equal(idaController, m.Controller)
	ts.Equal([]string{AssertionMethod}, m.Relationships)

	_, err = ts.r.Resolve(ts.ctx, "ftp://example.org/key")
	ts.ErrorIs(err, ErrUnsupportedMethod)
}

// HTTPS key and controller documents may redirect within their origin only.
func (ts *KeysTestSuite) TestHTTPSKeyRedirects() {
	const (
		controller = "https://keys.example.org/controller.json"
		movedKey   = "https://keys.example.org/moved-key.json"
		crossKey   = "https://keys.example.org/cross-key.json"
		ctrlKey    = "https://ctrl.example.org/key.json"
		movedCtrl  = "https://ctrl.example.org/controller.json"
		crossKey2  = "https://ctrl-cross.example.org/key.json"
		crossCtrl  = "https://ctrl-cross.example.org/controller.json"
	)
	pub, _, err := ed25519.GenerateKey(nil)
	ts.Require().NoError(err)
	keyDoc := func(id, controller string) []byte {
		return ts.marshal(map[string]any{"id": id, "type": "JsonWebKey2020", "controller": controller, "publicKeyJwk": ts.jwk(pub)})
	}
	controllerDoc := func(id string, keys ...any) []byte {
		return ts.marshal(map[string]any{"id": id, "assertionMethod": keys})
	}
	ts.routes[controller] = controllerDoc(controller, movedKey, crossKey)
	ts.routes[movedKey] = redirect("https://keys.example.org/v2/key.json")
	ts.routes["https://keys.example.org/v2/key.json"] = keyDoc(movedKey, controller)
	ts.routes[crossKey] = redirect("https://attacker.example.net/key.json")
	ts.routes["https://attacker.example.net/key.json"] = keyDoc(crossKey, controller)

	m, err := ts.r.Resolve(ts.ctx, movedKey)
	ts.Require().NoError(err, "same-origin key document redirect")
	ts.Equal([]string{AssertionMethod}, m.Relationships)
	_, err = ts.r.Resolve(ts.ctx, crossKey)
	ts.ErrorIs(err, fetch.ErrForbiddenURL, "cross-origin key document redirect")

	ts.routes[ctrlKey] = keyDoc(ctrlKey, movedCtrl)
	ts.routes[movedCtrl] = redirect("https://ctrl.example.org/v2/controller.json")
	ts.routes["https://ctrl.example.org/v2/controller.json"] = controllerDoc(movedCtrl, ctrlKey)
	ts.routes[crossKey2] = keyDoc(crossKey2, crossCtrl)
	ts.routes[crossCtrl] = redirect("https://attacker.example.net/controller.json")
	ts.routes["https://attacker.example.net/controller.json"] = controllerDoc(crossCtrl, crossKey2)

	m, err = ts.r.Resolve(ts.ctx, ctrlKey)
	ts.Require().NoError(err, "same-origin controller document redirect")
	ts.Equal([]string{AssertionMethod}, m.Relationships)
	_, err = ts.r.Resolve(ts.ctx, crossKey2)
	ts.ErrorIs(err, fetch.ErrForbiddenURL, "cross-origin controller document redirect")
}

// A key document's controller claim alone proves nothing: the controller
// document must reference the key.
func (ts *KeysTestSuite) TestHTTPSKeyNeedsControllerDocument() {
	pub, _, err := ed25519.GenerateKey(nil)
	ts.Require().NoError(err)
	const keyURL = "https://attacker.example/key.json"
	keyDoc := func(controller string) []byte {
		d := map[string]any{"id": keyURL, "type": "JsonWebKey2020", "publicKeyJwk": ts.jwk(pub)}
		if controller != "" {
			d["controller"] = controller
		}
		return ts.marshal(d)
	}

	ts.routes[keyURL] = keyDoc(idaController)
	m, err := ts.r.Resolve(ts.ctx, keyURL)
	ts.Require().NoError(err)
	ts.Equal(idaController, m.Controller)
	ts.Empty(m.Relationships, "the IDA controller document does not list this key")

	ts.routes["https://wrong-id.example/controller.json"] = ts.marshal(map[string]any{
		"id": "https://other.example/controller.json", "assertionMethod": []any{keyURL},
	})
	for name, controller := range map[string]string{
		"controller document must be retrievable":       "https://unreachable.example/controller.json",
		"controller document id must be the controller": "https://wrong-id.example/controller.json",
		"key document must name a controller":           "",
	} {
		ts.routes[keyURL] = keyDoc(controller)
		_, err = ts.newResolver().Resolve(ts.ctx, keyURL)
		ts.ErrorIs(err, ErrResolution, name)
	}
}

// A did:web controller authorizes an HTTPS key through its DID document.
func (ts *KeysTestSuite) TestHTTPSKeyWithDIDWebController() {
	pub, _, err := ed25519.GenerateKey(nil)
	ts.Require().NoError(err)
	const keyURL = "https://keys.example.org/k1.json"
	ts.routes[keyURL] = ts.marshal(map[string]any{
		"id": keyURL, "type": "JsonWebKey2020", "controller": "did:web:keys.example.org", "publicKeyJwk": ts.jwk(pub),
	})
	ts.routes["https://keys.example.org/.well-known/did.json"] = ts.marshal(map[string]any{
		"id": "did:web:keys.example.org", "assertionMethod": []any{keyURL},
	})
	m, err := ts.r.Resolve(ts.ctx, keyURL)
	ts.Require().NoError(err)
	ts.Equal("did:web:keys.example.org", m.Controller)
	ts.True(m.Authorizes(AssertionMethod))
	ts.False(m.Authorizes(Authentication))
}

// A method embedded in the controller document authorizes its own key only,
// not a different key served at its id.
func (ts *KeysTestSuite) TestHTTPSKeyEmbeddedInControllerMustMatch() {
	served, _, err := ed25519.GenerateKey(nil)
	ts.Require().NoError(err)
	embedded, _, err := ed25519.GenerateKey(nil)
	ts.Require().NoError(err)
	const keyURL = "https://keys.example.org/k2.json"
	const controller = "https://keys.example.org/controller.json"
	ts.routes[keyURL] = ts.marshal(map[string]any{
		"id": keyURL, "type": "JsonWebKey2020", "controller": controller, "publicKeyJwk": ts.jwk(served),
	})
	controllerDoc := func(pub ed25519.PublicKey) []byte {
		return ts.marshal(map[string]any{
			"id": controller,
			"assertionMethod": []any{map[string]any{
				"id": keyURL, "type": "JsonWebKey2020", "controller": controller, "publicKeyJwk": ts.jwk(pub),
			}},
		})
	}

	ts.routes[controller] = controllerDoc(embedded)
	m, err := ts.newResolver().Resolve(ts.ctx, keyURL)
	ts.Require().NoError(err)
	ts.Empty(m.Relationships)

	ts.routes[controller] = controllerDoc(served)
	m, err = ts.newResolver().Resolve(ts.ctx, keyURL)
	ts.Require().NoError(err)
	ts.Equal([]string{AssertionMethod}, m.Relationships)
}

func (ts *KeysTestSuite) jwk(pub ed25519.PublicKey) map[string]any {
	return map[string]any{"kty": "OKP", "crv": "Ed25519", "x": base64.RawURLEncoding.EncodeToString(pub)}
}

func (ts *KeysTestSuite) marshal(v any) []byte {
	b, err := json.Marshal(v)
	ts.Require().NoError(err)
	return b
}

func (ts *KeysTestSuite) TestKeyFromVM() {
	for _, c := range []struct {
		file, id string
		want     any
	}{
		{"didEck1Key.json", "did:web:vharsh.github.io:DID:echarsh#key-0", &btcec.PublicKey{}},
		{"didEcr1Key.json", "did:web:868c-2405-201-1029-3025-a153-97e5-f47f-3b16.ngrok-free.app#-Htu28ZSg5Ov-KEcOIo_XRwu777juTuGCZn-IMx6MIU", &ecdsa.PublicKey{}},
		{"didEdPublicKey.json", "did:web:vharsh.github.io:DID:local#key-0", ed25519.PublicKey{}},
		{"httpsMockKey2.json", "", ed25519.PublicKey{}},
	} {
		var doc map[string]any
		ts.Require().NoError(json.Unmarshal(ts.testdata(c.file), &doc))
		vm := doc
		if c.id != "" {
			did, _, _ := strings.Cut(c.id, "#")
			vm = findVerificationMethod(doc, did, c.id)
			ts.Require().NotNil(vm, c.file)
		}
		k, err := keyFromVM(vm)
		ts.Require().NoError(err, c.file)
		ts.IsType(c.want, k, c.file)
	}

	pub, _, err := ed25519.GenerateKey(nil)
	ts.Require().NoError(err)
	hexVM := map[string]any{"type": vmEd25519Key2020, "publicKeyHex": "00"}
	_, err = keyFromVM(hexVM)
	ts.ErrorIs(err, ErrResolution)

	mismatch := map[string]any{"type": vmSecp256k1Key2019, "publicKeyJwk": map[string]any{
		"kty": "OKP", "crv": "Ed25519", "x": base64.RawURLEncoding.EncodeToString(pub),
	}}
	_, err = keyFromVM(mismatch)
	ts.ErrorIs(err, ErrResolution, "key contradicts the method type")

	unknownType := map[string]any{"type": "BbsBlsSignature2020", "publicKeyJwk": mismatch["publicKeyJwk"]}
	_, err = keyFromVM(unknownType)
	ts.ErrorIs(err, ErrUnsupportedKey)

	_, err = keyFromVM(map[string]any{"type": vmMultikey})
	ts.ErrorIs(err, ErrUnsupportedKey)
}

func (ts *KeysTestSuite) TestEqual() {
	a, err := KeyFromDID(ed25519DIDKey)
	ts.Require().NoError(err)
	b, err := KeyFromDID(p256DIDKey)
	ts.Require().NoError(err)
	ts.True(Equal(a, a))
	ts.False(Equal(a, b))
	ts.False(Equal(b, a))
	ts.False(Equal(nil, a))
}

func (ts *KeysTestSuite) TestThumbprint() {
	// RFC 8037 appendix A.3.
	x, err := base64.RawURLEncoding.DecodeString("11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo")
	ts.Require().NoError(err)
	got, err := Thumbprint(ed25519.PublicKey(x))
	ts.Require().NoError(err)
	ts.Equal("kPrK_qmxVWaYVA9wwBF6Iuo3vVzz7TxHCTwXBygrS4k", got)

	p256, err := KeyFromDID(p256DIDKey)
	ts.Require().NoError(err)
	rsaKey, err := rsa.GenerateKey(rand.Reader, 2048)
	ts.Require().NoError(err)
	for _, pub := range []crypto.PublicKey{p256, &rsaKey.PublicKey} {
		want, err := (&jose.JSONWebKey{Key: pub}).Thumbprint(crypto.SHA256)
		ts.Require().NoError(err)
		got, err := Thumbprint(pub)
		ts.Require().NoError(err)
		ts.Equal(base64.RawURLEncoding.EncodeToString(want), got, "%T", pub)
	}

	// go-jose has no secp256k1; hash the JWK members directly.
	k, err := btcec.NewPrivateKey()
	ts.Require().NoError(err)
	point := k.PubKey().SerializeUncompressed()
	jwk := map[string]any{
		"kty": "EC", "crv": "secp256k1",
		"x": base64.RawURLEncoding.EncodeToString(point[1:33]),
		"y": base64.RawURLEncoding.EncodeToString(point[33:]),
	}
	parsed, err := parseJWK(jwk)
	ts.Require().NoError(err)
	ts.True(Equal(k.PubKey(), parsed))
	sum := sha256.Sum256(ts.marshal(jwk)) // json.Marshal sorts the members
	got, err = Thumbprint(k.PubKey())
	ts.Require().NoError(err)
	ts.Equal(base64.RawURLEncoding.EncodeToString(sum[:]), got)

	_, err = Thumbprint("not a key")
	ts.ErrorIs(err, ErrUnsupportedKey)
}
