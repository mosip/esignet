/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package security

import (
	"encoding/base64"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

const testRSAJWKS = `{"keys":[{"kty":"RSA","kid":"rsa-1","use":"sig","alg":"RS256","n":"AQAB","e":"AQAB"}]}`

func (ts *JwksTestSuite) TestJWKSCache_GetKey_RSA() {
	t := ts.T()
	var calls int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		atomic.AddInt32(&calls, 1)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(testRSAJWKS))
	}))
	defer srv.Close()

	cache := NewJWKSCache(srv.URL, time.Minute)
	key, err := cache.GetKey("rsa-1")
	if err != nil {
		t.Fatalf("GetKey: %v", err)
	}
	if key == nil {
		t.Fatal("expected non-nil public key")
	}

	// Second call within TTL should be served from cache, not re-fetch.
	if _, err := cache.GetKey("rsa-1"); err != nil {
		t.Fatalf("GetKey (cached): %v", err)
	}
	if got := atomic.LoadInt32(&calls); got != 1 {
		t.Errorf("fetch calls = %d, want 1 (should be served from cache)", got)
	}
}

func (ts *JwksTestSuite) TestJWKSCache_GetKey_UnknownKidForcesRefresh() {
	t := ts.T()
	var calls int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		atomic.AddInt32(&calls, 1)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(testRSAJWKS))
	}))
	defer srv.Close()

	cache := NewJWKSCache(srv.URL, time.Minute)
	if _, err := cache.GetKey("rsa-1"); err != nil {
		t.Fatalf("GetKey: %v", err)
	}
	if _, err := cache.GetKey("missing-kid"); err == nil {
		t.Fatal("expected error for unknown kid")
	}
	if got := atomic.LoadInt32(&calls); got != 2 {
		t.Errorf("fetch calls = %d, want 2 (unknown kid should force one refresh)", got)
	}
}

func (ts *JwksTestSuite) TestJWKSCache_GetKey_ExpiredTTLRefetches() {
	t := ts.T()
	var calls int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		atomic.AddInt32(&calls, 1)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(testRSAJWKS))
	}))
	defer srv.Close()

	cache := NewJWKSCache(srv.URL, time.Millisecond)
	if _, err := cache.GetKey("rsa-1"); err != nil {
		t.Fatalf("GetKey: %v", err)
	}
	time.Sleep(5 * time.Millisecond)
	if _, err := cache.GetKey("rsa-1"); err != nil {
		t.Fatalf("GetKey after TTL expiry: %v", err)
	}
	if got := atomic.LoadInt32(&calls); got != 2 {
		t.Errorf("fetch calls = %d, want 2 (TTL expiry should trigger refetch)", got)
	}
}

func (ts *JwksTestSuite) TestJWKSCache_GetKey_HTTPError() {
	t := ts.T()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	cache := NewJWKSCache(srv.URL, time.Minute)
	if _, err := cache.GetKey("rsa-1"); err == nil {
		t.Fatal("expected error on non-200 response")
	}
}

func (ts *JwksTestSuite) TestJWKSCache_GetKey_TransportError() {
	t := ts.T()
	cache := NewJWKSCache("http://127.0.0.1:0", time.Minute)
	if _, err := cache.GetKey("rsa-1"); err == nil {
		t.Fatal("expected error on transport failure")
	}
}

func (ts *JwksTestSuite) TestJWKSCache_GetKey_InvalidJSON() {
	t := ts.T()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("not-json"))
	}))
	defer srv.Close()

	cache := NewJWKSCache(srv.URL, time.Minute)
	if _, err := cache.GetKey("rsa-1"); err == nil {
		t.Fatal("expected error on invalid JSON")
	}
}

func (ts *JwksTestSuite) TestJWKSCache_SkipsEncryptionKeysAndUnparsableKeys() {
	t := ts.T()
	doc := `{"keys":[
		{"kty":"RSA","kid":"enc-1","use":"enc","n":"AQAB","e":"AQAB"},
		{"kty":"RSA","kid":"bad-1","use":"sig","n":"not base64!","e":"AQAB"},
		{"kty":"oct","kid":"unsupported-1","use":"sig"}
	]}`
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(doc))
	}))
	defer srv.Close()

	cache := NewJWKSCache(srv.URL, time.Minute)
	for _, kid := range []string{"enc-1", "bad-1", "unsupported-1"} {
		if _, err := cache.GetKey(kid); err == nil {
			t.Errorf("expected %q to be excluded from the cache", kid)
		}
	}
}

func (ts *JwksTestSuite) TestParseJWK_EC() {
	t := ts.T()
	x := base64.RawURLEncoding.EncodeToString([]byte{1, 2, 3})
	y := base64.RawURLEncoding.EncodeToString([]byte{4, 5, 6})

	t.Run("P-256", func(t *testing.T) {
		pub, err := parseJWK(jwkKey{Kty: "EC", Crv: "P-256", X: x, Y: y})
		if err != nil {
			t.Fatalf("parseJWK: %v", err)
		}
		if pub == nil {
			t.Fatal("expected non-nil public key")
		}
	})

	t.Run("unsupported curve", func(t *testing.T) {
		if _, err := parseJWK(jwkKey{Kty: "EC", Crv: "P-999", X: x, Y: y}); err == nil {
			t.Fatal("expected error for unsupported curve")
		}
	})

	t.Run("invalid x", func(t *testing.T) {
		if _, err := parseJWK(jwkKey{Kty: "EC", Crv: "P-256", X: "not base64!", Y: y}); err == nil {
			t.Fatal("expected error for invalid x")
		}
	})

	t.Run("invalid y", func(t *testing.T) {
		if _, err := parseJWK(jwkKey{Kty: "EC", Crv: "P-256", X: x, Y: "not base64!"}); err == nil {
			t.Fatal("expected error for invalid y")
		}
	})
}

func (ts *JwksTestSuite) TestParseJWK_UnsupportedKty() {
	t := ts.T()
	if _, err := parseJWK(jwkKey{Kty: "oct"}); err == nil {
		t.Fatal("expected error for unsupported key type")
	}
}

func (ts *JwksTestSuite) TestParseRSAKey_InvalidN() {
	t := ts.T()
	if _, err := parseRSAKey(jwkKey{N: "not base64!", E: "AQAB"}); err == nil {
		t.Fatal("expected error for invalid n")
	}
}

func (ts *JwksTestSuite) TestParseRSAKey_InvalidE() {
	t := ts.T()
	if _, err := parseRSAKey(jwkKey{N: "AQAB", E: "not base64!"}); err == nil {
		t.Fatal("expected error for invalid e")
	}
}

type JwksTestSuite struct {
	suite.Suite
}

func TestJwksTestSuite(t *testing.T) {
	suite.Run(t, new(JwksTestSuite))
}
