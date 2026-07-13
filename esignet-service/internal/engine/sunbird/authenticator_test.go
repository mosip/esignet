/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package sunbird

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

func testConfig(searchURL, entityURL string) Config {
	return Config{
		SearchURL:     searchURL,
		EntityURL:     entityURL,
		IDField:       "policyNumber",
		EntityIDField: "osid",
		FieldDetails:  defaultSunbirdFieldDetails,
		ClaimsMapping: defaultSunbirdClaimsMapping,
		TimeoutSecs:   defaultSunbirdTimeoutSecs,
	}
}

func newTestProvider(t *testing.T, cfg Config) *sunbirdAuthnProvider {
	t.Helper()
	fields, err := parseSunbirdFieldDetails(cfg.FieldDetails)
	if err != nil {
		t.Fatalf("parseSunbirdFieldDetails: %v", err)
	}
	var kbiFieldIDs []string
	for _, f := range fields {
		if f.ID != cfg.IDField {
			kbiFieldIDs = append(kbiFieldIDs, f.ID)
		}
	}
	return &sunbirdAuthnProvider{cfg: cfg, client: http.DefaultClient, kbiFieldIDs: kbiFieldIDs}
}

func (ts *AuthenticatorTestSuite) TestNewSunbirdAuthnProvider() {
	t := ts.T()
	t.Run("missing search url", func(t *testing.T) {
		t.Setenv(envSunbirdSearchURL, "")
		if _, err := NewSunbirdAuthnProvider(); err == nil {
			t.Fatal("expected error when SUNBIRD_SEARCH_URL is unset")
		}
	})

	t.Run("no kbi fields other than id field", func(t *testing.T) {
		t.Setenv(envSunbirdSearchURL, "http://example.com/search")
		t.Setenv(envSunbirdFieldDetails, `[{"id":"policyNumber","type":"text","format":""}]`)
		if _, err := NewSunbirdAuthnProvider(); err == nil {
			t.Fatal("expected error when no KBI field is configured")
		}
	})

	t.Run("invalid field details json", func(t *testing.T) {
		t.Setenv(envSunbirdSearchURL, "http://example.com/search")
		t.Setenv(envSunbirdFieldDetails, `not-json`)
		if _, err := NewSunbirdAuthnProvider(); err == nil {
			t.Fatal("expected error for invalid field details JSON")
		}
	})

	t.Run("success", func(t *testing.T) {
		t.Setenv(envSunbirdSearchURL, "http://example.com/search")
		t.Setenv(envSunbirdFieldDetails, defaultSunbirdFieldDetails)
		t.Setenv(envSunbirdTimeout, "0")
		p, err := NewSunbirdAuthnProvider()
		if err != nil {
			t.Fatalf("NewSunbirdAuthnProvider: %v", err)
		}
		if p == nil {
			t.Fatal("expected non-nil provider")
		}
	})
}

func (ts *AuthenticatorTestSuite) TestSendOTP() {
	t := ts.T()
	p := newTestProvider(t, testConfig("http://example.com", "http://example.com"))
	result, svcErr := p.SendOTP(context.Background(), nil, nil)
	if result != nil {
		t.Errorf("expected nil result, got %+v", result)
	}
	if svcErr == nil {
		t.Fatal("expected NotImplemented service error")
	}
}

func (ts *AuthenticatorTestSuite) TestAuthenticateUser() {
	t := ts.T()
	t.Run("missing individual id", func(t *testing.T) {
		p := newTestProvider(t, testConfig("http://example.com", "http://example.com"))
		var authUser providers.AuthUser
		_, claims, svcErr := p.AuthenticateUser(context.Background(),
			map[string]interface{}{}, map[string]interface{}{}, nil, nil, authUser)
		if svcErr == nil {
			t.Fatal("expected invalid individual id error")
		}
		if claims != nil {
			t.Errorf("expected nil claims, got %+v", claims)
		}
	})

	t.Run("empty individual id", func(t *testing.T) {
		p := newTestProvider(t, testConfig("http://example.com", "http://example.com"))
		var authUser providers.AuthUser
		_, _, svcErr := p.AuthenticateUser(context.Background(),
			map[string]interface{}{sunbirdIndividualIDKey: ""}, map[string]interface{}{}, nil, nil, authUser)
		if svcErr == nil {
			t.Fatal("expected invalid individual id error")
		}
	})

	t.Run("missing kbi field", func(t *testing.T) {
		p := newTestProvider(t, testConfig("http://example.com", "http://example.com"))
		var authUser providers.AuthUser
		_, _, svcErr := p.AuthenticateUser(context.Background(),
			map[string]interface{}{sunbirdIndividualIDKey: "pol-1"},
			map[string]interface{}{"fullName": "Jane"}, // missing "dob"
			nil, nil, authUser)
		if svcErr == nil {
			t.Fatal("expected invalid request error when a KBI field is missing")
		}
	})

	t.Run("registry match success", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			var req sunbirdSearchRequest
			if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
				t.Fatalf("decode request: %v", err)
			}
			if req.Filters["policyNumber"].Eq != "pol-1" {
				t.Errorf("policyNumber filter = %q, want pol-1", req.Filters["policyNumber"].Eq)
			}
			_ = json.NewEncoder(w).Encode([]map[string]interface{}{
				{"osid": "entity-123"},
			})
		}))
		defer srv.Close()

		p := newTestProvider(t, testConfig(srv.URL, srv.URL))
		var authUser providers.AuthUser
		resultUser, _, svcErr := p.AuthenticateUser(context.Background(),
			map[string]interface{}{sunbirdIndividualIDKey: "pol-1"},
			map[string]interface{}{"fullName": "Jane", "dob": "01/01/1990"},
			nil, nil, authUser)
		if svcErr != nil {
			t.Fatalf("unexpected service error: %v", svcErr)
		}
		if resultUser.EntityReferenceToken() != "entity-123" {
			t.Errorf("entity reference token = %v, want entity-123", resultUser.EntityReferenceToken())
		}
		if resultUser.AttributeToken() != "entity-123" {
			t.Errorf("attribute token = %v, want entity-123", resultUser.AttributeToken())
		}
	})

	t.Run("registry no match maps to invalid request", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			_ = json.NewEncoder(w).Encode([]map[string]interface{}{})
		}))
		defer srv.Close()

		p := newTestProvider(t, testConfig(srv.URL, srv.URL))
		var authUser providers.AuthUser
		_, _, svcErr := p.AuthenticateUser(context.Background(),
			map[string]interface{}{sunbirdIndividualIDKey: "pol-1"},
			map[string]interface{}{"fullName": "Jane", "dob": "01/01/1990"},
			nil, nil, authUser)
		if svcErr == nil {
			t.Fatal("expected invalid request error on zero registry matches")
		}
	})

	t.Run("registry transport error maps to authentication failed", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer srv.Close()

		p := newTestProvider(t, testConfig(srv.URL, srv.URL))
		var authUser providers.AuthUser
		_, _, svcErr := p.AuthenticateUser(context.Background(),
			map[string]interface{}{sunbirdIndividualIDKey: "pol-1"},
			map[string]interface{}{"fullName": "Jane", "dob": "01/01/1990"},
			nil, nil, authUser)
		if svcErr == nil {
			t.Fatal("expected authentication failed error on transport/server error")
		}
	})
}

func (ts *AuthenticatorTestSuite) TestGetUserAttributes() {
	t := ts.T()
	t.Run("success", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path != "/entity-123" {
				t.Errorf("path = %q, want /entity-123", r.URL.Path)
			}
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"fullName": "Jane Doe",
				"email":    "jane@example.com",
			})
		}))
		defer srv.Close()

		cfg := testConfig(srv.URL, srv.URL)
		cfg.ClaimsMapping = `{"name":"fullName","email":"email"}`
		p := newTestProvider(t, cfg)

		var authUser providers.AuthUser
		authUser.SetEntityReferenceToken("entity-123")

		_, attrs, svcErr := p.GetUserAttributes(context.Background(), nil, nil, authUser)
		if svcErr != nil {
			t.Fatalf("unexpected service error: %v", svcErr)
		}
		if attrs.Attributes["name"].Value != "Jane Doe" {
			t.Errorf("name claim = %v, want Jane Doe", attrs.Attributes["name"].Value)
		}
		if attrs.Attributes["email"].Value != "jane@example.com" {
			t.Errorf("email claim = %v, want jane@example.com", attrs.Attributes["email"].Value)
		}
	})

	t.Run("fetch failure", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusNotFound)
		}))
		defer srv.Close()

		p := newTestProvider(t, testConfig(srv.URL, srv.URL))
		var authUser providers.AuthUser
		authUser.SetEntityReferenceToken("entity-123")

		_, _, svcErr := p.GetUserAttributes(context.Background(), nil, nil, authUser)
		if svcErr == nil {
			t.Fatal("expected invalid request error on entity fetch failure")
		}
	})
}

func (ts *AuthenticatorTestSuite) TestGetEntityReference() {
	t := ts.T()
	p := newTestProvider(t, testConfig("http://example.com", "http://example.com"))

	t.Run("missing entity reference token", func(t *testing.T) {
		var authUser providers.AuthUser
		_, ref, svcErr := p.GetEntityReference(context.Background(), authUser)
		if svcErr == nil {
			t.Fatal("expected authentication failed error")
		}
		if ref != nil {
			t.Errorf("expected nil entity reference, got %+v", ref)
		}
	})

	t.Run("success", func(t *testing.T) {
		var authUser providers.AuthUser
		authUser.SetEntityReferenceToken("entity-123")
		_, ref, svcErr := p.GetEntityReference(context.Background(), authUser)
		if svcErr != nil {
			t.Fatalf("unexpected service error: %v", svcErr)
		}
		if ref.EntityID != "entity-123" {
			t.Errorf("entityID = %q, want entity-123", ref.EntityID)
		}
	})
}

func (ts *AuthenticatorTestSuite) TestGetUserAvailableAttributes() {
	t := ts.T()
	p := newTestProvider(t, testConfig("http://example.com", "http://example.com"))
	var authUser providers.AuthUser
	attrs, svcErr := p.GetUserAvailableAttributes(context.Background(), authUser)
	if svcErr != nil {
		t.Fatalf("unexpected service error: %v", svcErr)
	}
	if attrs == nil {
		t.Fatal("expected non-nil attributes response")
	}
}

func (ts *AuthenticatorTestSuite) TestParseSunbirdFieldDetails() {
	t := ts.T()
	if _, err := parseSunbirdFieldDetails(""); err == nil {
		t.Error("expected error for empty field details")
	}
	if _, err := parseSunbirdFieldDetails("not-json"); err == nil {
		t.Error("expected error for invalid JSON")
	}
	fields, err := parseSunbirdFieldDetails(defaultSunbirdFieldDetails)
	if err != nil {
		t.Fatalf("parseSunbirdFieldDetails: %v", err)
	}
	if len(fields) != 3 {
		t.Errorf("len(fields) = %d, want 3", len(fields))
	}
}

func (ts *AuthenticatorTestSuite) TestBuildSunbirdMappedClaims() {
	t := ts.T()
	entityData := map[string]interface{}{
		"fullName": "Jane Doe",
		"mobile":   "1234567890",
	}

	t.Run("empty mapping yields no claims", func(t *testing.T) {
		claims := buildSunbirdMappedClaims(entityData, "")
		if len(claims) != 0 {
			t.Errorf("expected no claims, got %+v", claims)
		}
	})

	t.Run("invalid mapping fails closed", func(t *testing.T) {
		claims := buildSunbirdMappedClaims(entityData, "not-json")
		if len(claims) != 0 {
			t.Errorf("expected no claims on invalid mapping, got %+v", claims)
		}
	})

	t.Run("maps configured fields only", func(t *testing.T) {
		claims := buildSunbirdMappedClaims(entityData, `{"name":"fullName","phone_number":"mobile","gender":"missingField"}`)
		if claims["name"] != "Jane Doe" {
			t.Errorf("name = %v, want Jane Doe", claims["name"])
		}
		if claims["phone_number"] != "1234567890" {
			t.Errorf("phone_number = %v, want 1234567890", claims["phone_number"])
		}
		if _, ok := claims["gender"]; ok {
			t.Errorf("gender should not be present when source field is missing")
		}
	})
}

type AuthenticatorTestSuite struct {
	suite.Suite
}

func TestAuthenticatorTestSuite(t *testing.T) {
	suite.Run(t, new(AuthenticatorTestSuite))
}
