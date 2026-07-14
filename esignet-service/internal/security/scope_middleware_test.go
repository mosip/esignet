/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package security

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/golang-jwt/jwt/v5"

	"github.com/mosip/esignet/internal/config"
)

func newTestJWKSServer(t *testing.T, key *rsa.PrivateKey, kid string) *httptest.Server {
	t.Helper()
	n := base64.RawURLEncoding.EncodeToString(key.N.Bytes())
	e := base64.RawURLEncoding.EncodeToString([]byte{1, 0, 1}) // 65537

	doc := fmt.Sprintf(`{"keys":[{"kty":"RSA","kid":%q,"use":"sig","alg":"RS256","n":%q,"e":%q}]}`, kid, n, e)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(doc))
	}))
	t.Cleanup(srv.Close)
	return srv
}

func signTestToken(t *testing.T, key *rsa.PrivateKey, kid string, claims jwt.MapClaims) string {
	t.Helper()
	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	token.Header["kid"] = kid
	signed, err := token.SignedString(key)
	if err != nil {
		t.Fatalf("SignedString: %v", err)
	}
	return signed
}

func newTestMiddleware(t *testing.T, key *rsa.PrivateKey, kid, issuer string) func(http.Handler) http.Handler {
	t.Helper()
	srv := newTestJWKSServer(t, key, kid)
	cache := NewJWKSCache(srv.URL, time.Minute)
	return ScopeMiddleware(cache, config.SecurityConfig{IssuerURL: issuer})
}

func newProtectedHandler() (http.Handler, *bool) {
	called := new(bool)
	return http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		*called = true
		w.WriteHeader(http.StatusOK)
	}), called
}

func (ts *ScopeMiddlewareTestSuite) TestScopeMiddleware_Success() {
	t := ts.T()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("GenerateKey: %v", err)
	}
	mw := newTestMiddleware(t, key, "kid-1", "https://issuer.example.com")

	claims := jwt.MapClaims{
		"iss":   "https://issuer.example.com",
		"exp":   time.Now().Add(time.Hour).Unix(),
		"scope": "test other",
	}
	tokenStr := signTestToken(t, key, "kid-1", claims)

	handler, called := newProtectedHandler()
	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("Authorization", "Bearer "+tokenStr)
	mw(handler).ServeHTTP(w, r)

	if w.Code != http.StatusOK {
		t.Errorf("status = %d, want 200; body=%s", w.Code, w.Body.String())
	}
	if !*called {
		t.Error("expected downstream handler to be called")
	}
}

func (ts *ScopeMiddlewareTestSuite) TestScopeMiddleware_MissingAuthHeader() {
	t := ts.T()
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	mw := newTestMiddleware(t, key, "kid-1", "https://issuer.example.com")

	handler, called := newProtectedHandler()
	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/", nil)
	mw(handler).ServeHTTP(w, r)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want 401", w.Code)
	}
	if *called {
		t.Error("expected downstream handler not to be called")
	}
}

func (ts *ScopeMiddlewareTestSuite) TestScopeMiddleware_MalformedAuthHeader() {
	t := ts.T()
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	mw := newTestMiddleware(t, key, "kid-1", "https://issuer.example.com")

	handler, called := newProtectedHandler()
	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("Authorization", "Basic abc123")
	mw(handler).ServeHTTP(w, r)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want 401", w.Code)
	}
	if *called {
		t.Error("expected downstream handler not to be called")
	}
}

func (ts *ScopeMiddlewareTestSuite) TestScopeMiddleware_InvalidSignature() {
	t := ts.T()
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	otherKey, _ := rsa.GenerateKey(rand.Reader, 2048)
	mw := newTestMiddleware(t, key, "kid-1", "https://issuer.example.com")

	claims := jwt.MapClaims{
		"iss":   "https://issuer.example.com",
		"exp":   time.Now().Add(time.Hour).Unix(),
		"scope": "test",
	}
	// Signed with a different key than the one published in the JWKS.
	tokenStr := signTestToken(t, otherKey, "kid-1", claims)

	handler, called := newProtectedHandler()
	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("Authorization", "Bearer "+tokenStr)
	mw(handler).ServeHTTP(w, r)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want 401", w.Code)
	}
	if *called {
		t.Error("expected downstream handler not to be called")
	}
}

func (ts *ScopeMiddlewareTestSuite) TestScopeMiddleware_ExpiredToken() {
	t := ts.T()
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	mw := newTestMiddleware(t, key, "kid-1", "https://issuer.example.com")

	claims := jwt.MapClaims{
		"iss":   "https://issuer.example.com",
		"exp":   time.Now().Add(-time.Hour).Unix(),
		"scope": "test",
	}
	tokenStr := signTestToken(t, key, "kid-1", claims)

	handler, called := newProtectedHandler()
	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("Authorization", "Bearer "+tokenStr)
	mw(handler).ServeHTTP(w, r)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want 401", w.Code)
	}
	if *called {
		t.Error("expected downstream handler not to be called")
	}
}

func (ts *ScopeMiddlewareTestSuite) TestScopeMiddleware_WrongIssuer() {
	t := ts.T()
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	mw := newTestMiddleware(t, key, "kid-1", "https://issuer.example.com")

	claims := jwt.MapClaims{
		"iss":   "https://wrong-issuer.example.com",
		"exp":   time.Now().Add(time.Hour).Unix(),
		"scope": "test",
	}
	tokenStr := signTestToken(t, key, "kid-1", claims)

	handler, called := newProtectedHandler()
	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("Authorization", "Bearer "+tokenStr)
	mw(handler).ServeHTTP(w, r)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want 401", w.Code)
	}
	if *called {
		t.Error("expected downstream handler not to be called")
	}
}

func (ts *ScopeMiddlewareTestSuite) TestScopeMiddleware_MissingScope() {
	t := ts.T()
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	mw := newTestMiddleware(t, key, "kid-1", "https://issuer.example.com")

	claims := jwt.MapClaims{
		"iss":   "https://issuer.example.com",
		"exp":   time.Now().Add(time.Hour).Unix(),
		"scope": "other",
	}
	tokenStr := signTestToken(t, key, "kid-1", claims)

	handler, called := newProtectedHandler()
	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("Authorization", "Bearer "+tokenStr)
	mw(handler).ServeHTTP(w, r)

	if w.Code != http.StatusForbidden {
		t.Errorf("status = %d, want 403", w.Code)
	}
	if *called {
		t.Error("expected downstream handler not to be called")
	}
}

func (ts *ScopeMiddlewareTestSuite) TestBearerToken() {
	t := ts.T()
	t.Run("missing header", func(t *testing.T) {
		r := httptest.NewRequest(http.MethodGet, "/", nil)
		if _, err := bearerToken(r); err == nil {
			t.Error("expected error for missing Authorization header")
		}
	})

	t.Run("wrong scheme", func(t *testing.T) {
		r := httptest.NewRequest(http.MethodGet, "/", nil)
		r.Header.Set("Authorization", "Basic abc123")
		if _, err := bearerToken(r); err == nil {
			t.Error("expected error for non-Bearer scheme")
		}
	})

	t.Run("empty token", func(t *testing.T) {
		r := httptest.NewRequest(http.MethodGet, "/", nil)
		r.Header.Set("Authorization", "Bearer   ")
		if _, err := bearerToken(r); err == nil {
			t.Error("expected error for empty token")
		}
	})

	t.Run("valid", func(t *testing.T) {
		r := httptest.NewRequest(http.MethodGet, "/", nil)
		r.Header.Set("Authorization", "Bearer abc.def.ghi")
		got, err := bearerToken(r)
		if err != nil {
			t.Fatalf("bearerToken: %v", err)
		}
		if got != "abc.def.ghi" {
			t.Errorf("bearerToken() = %q, want abc.def.ghi", got)
		}
	})

	t.Run("case insensitive scheme", func(t *testing.T) {
		r := httptest.NewRequest(http.MethodGet, "/", nil)
		r.Header.Set("Authorization", "bearer abc.def.ghi")
		got, err := bearerToken(r)
		if err != nil {
			t.Fatalf("bearerToken: %v", err)
		}
		if got != "abc.def.ghi" {
			t.Errorf("bearerToken() = %q, want abc.def.ghi", got)
		}
	})
}

func (ts *ScopeMiddlewareTestSuite) TestClaimHasScope() {
	t := ts.T()
	t.Run("missing scope claim", func(t *testing.T) {
		if claimHasScope(jwt.MapClaims{}, "test") {
			t.Error("expected false when scope claim is absent")
		}
	})

	t.Run("scope claim wrong type", func(t *testing.T) {
		if claimHasScope(jwt.MapClaims{"scope": 123}, "test") {
			t.Error("expected false when scope claim is not a string")
		}
	})

	t.Run("scope present", func(t *testing.T) {
		if !claimHasScope(jwt.MapClaims{"scope": "read test write"}, "test") {
			t.Error("expected true when scope is present among space-separated values")
		}
	})

	t.Run("scope absent", func(t *testing.T) {
		if claimHasScope(jwt.MapClaims{"scope": "read write"}, "test") {
			t.Error("expected false when scope is not present")
		}
	})
}

func (ts *ScopeMiddlewareTestSuite) TestParseAndValidate_MissingKid() {
	t := ts.T()
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	srv := newTestJWKSServer(t, key, "kid-1")
	cache := NewJWKSCache(srv.URL, time.Minute)
	parser := jwt.NewParser(jwt.WithExpirationRequired())

	token := jwt.NewWithClaims(jwt.SigningMethodRS256, jwt.MapClaims{
		"exp": time.Now().Add(time.Hour).Unix(),
	})
	// Deliberately omit the "kid" header.
	signed, err := token.SignedString(key)
	if err != nil {
		t.Fatalf("SignedString: %v", err)
	}

	if _, err := parseAndValidate(parser, signed, cache); err == nil {
		t.Fatal("expected error when token header has no kid")
	}
}

func (ts *ScopeMiddlewareTestSuite) TestParseAndValidate_MalformedToken() {
	t := ts.T()
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	srv := newTestJWKSServer(t, key, "kid-1")
	cache := NewJWKSCache(srv.URL, time.Minute)
	parser := jwt.NewParser()

	if _, err := parseAndValidate(parser, "not-a-jwt", cache); err == nil {
		t.Fatal("expected error for malformed token")
	}
}

type ScopeMiddlewareTestSuite struct {
	suite.Suite
}

func TestScopeMiddlewareTestSuite(t *testing.T) {
	suite.Run(t, new(ScopeMiddlewareTestSuite))
}
