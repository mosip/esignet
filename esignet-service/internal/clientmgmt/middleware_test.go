package clientmgmt_test

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"math/big"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/clientmgmt"
)

const (
	testIssuer = "https://test-issuer.example.com"
	testScope  = "client_mgmt"
	testKID    = "test-key-1"
)

// jwksFixture holds a generated RSA key pair and a test JWKS HTTP server.
type jwksFixture struct {
	key    *rsa.PrivateKey
	server *httptest.Server
	cache  *clientmgmt.JWKSCache
}

func newJWKSFixture(t *testing.T) *jwksFixture {
	t.Helper()

	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		n := base64.RawURLEncoding.EncodeToString(key.PublicKey.N.Bytes())
		e := base64.RawURLEncoding.EncodeToString(big.NewInt(int64(key.PublicKey.E)).Bytes())
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"keys": []map[string]interface{}{
				{"kty": "RSA", "kid": testKID, "use": "sig", "n": n, "e": e},
			},
		})
	}))
	t.Cleanup(server.Close)

	cache := clientmgmt.NewJWKSCache(server.URL, time.Minute)

	return &jwksFixture{key: key, server: server, cache: cache}
}

// signToken creates a signed JWT with the given claims using the fixture's key.
func (f *jwksFixture) signToken(t *testing.T, claims jwt.MapClaims) string {
	t.Helper()
	tok := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	tok.Header["kid"] = testKID
	signed, err := tok.SignedString(f.key)
	require.NoError(t, err)
	return signed
}

func validClaims() jwt.MapClaims {
	return jwt.MapClaims{
		"iss":   testIssuer,
		"exp":   time.Now().Add(time.Hour).Unix(),
		"scope": "openid " + testScope,
	}
}

// okHandler is a trivial next-handler that records it was reached.
func okHandler(reached *bool) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		*reached = true
		w.WriteHeader(http.StatusOK)
	})
}

// ── tests ─────────────────────────────────────────────────────────────────────

func TestScopeMiddleware_NoAuthorizationHeader(t *testing.T) {
	f := newJWKSFixture(t)
	mw := clientmgmt.ScopeMiddleware(f.cache, testIssuer, testScope)

	reached := false
	h := mw(okHandler(&reached))

	req := httptest.NewRequest(http.MethodGet, "/client-mgmt/oidc-client/c1", nil)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
	assert.False(t, reached)
}

func TestScopeMiddleware_MalformedAuthorizationHeader(t *testing.T) {
	f := newJWKSFixture(t)
	mw := clientmgmt.ScopeMiddleware(f.cache, testIssuer, testScope)

	reached := false
	h := mw(okHandler(&reached))

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Authorization", "Basic dXNlcjpwYXNz") // not Bearer
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
	assert.False(t, reached)
}

func TestScopeMiddleware_ExpiredToken(t *testing.T) {
	f := newJWKSFixture(t)
	mw := clientmgmt.ScopeMiddleware(f.cache, testIssuer, testScope)

	claims := jwt.MapClaims{
		"iss":   testIssuer,
		"exp":   time.Now().Add(-time.Minute).Unix(), // already expired
		"scope": testScope,
	}
	token := f.signToken(t, claims)

	reached := false
	h := mw(okHandler(&reached))

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
	assert.False(t, reached)
}

func TestScopeMiddleware_WrongIssuer(t *testing.T) {
	f := newJWKSFixture(t)
	mw := clientmgmt.ScopeMiddleware(f.cache, testIssuer, testScope)

	claims := jwt.MapClaims{
		"iss":   "https://wrong-issuer.example.com",
		"exp":   time.Now().Add(time.Hour).Unix(),
		"scope": testScope,
	}
	token := f.signToken(t, claims)

	reached := false
	h := mw(okHandler(&reached))

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
	assert.False(t, reached)
}

func TestScopeMiddleware_MissingScope(t *testing.T) {
	f := newJWKSFixture(t)
	mw := clientmgmt.ScopeMiddleware(f.cache, testIssuer, testScope)

	claims := jwt.MapClaims{
		"iss":   testIssuer,
		"exp":   time.Now().Add(time.Hour).Unix(),
		"scope": "openid profile", // client_mgmt is absent
	}
	token := f.signToken(t, claims)

	reached := false
	h := mw(okHandler(&reached))

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusForbidden, rec.Code)
	assert.False(t, reached)
}

func TestScopeMiddleware_ValidToken_PassesThrough(t *testing.T) {
	f := newJWKSFixture(t)
	mw := clientmgmt.ScopeMiddleware(f.cache, testIssuer, testScope)

	token := f.signToken(t, validClaims())

	reached := false
	h := mw(okHandler(&reached))

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	assert.True(t, reached)
}

func TestScopeMiddleware_ScopeIsExactMatch(t *testing.T) {
	f := newJWKSFixture(t)
	// Require "client_mgmt" but token only carries "client_mgmt_admin"
	mw := clientmgmt.ScopeMiddleware(f.cache, testIssuer, "client_mgmt")

	claims := jwt.MapClaims{
		"iss":   testIssuer,
		"exp":   time.Now().Add(time.Hour).Unix(),
		"scope": "client_mgmt_admin",
	}
	token := f.signToken(t, claims)

	reached := false
	h := mw(okHandler(&reached))

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusForbidden, rec.Code)
	assert.False(t, reached, "partial-match scope must not pass")
}
