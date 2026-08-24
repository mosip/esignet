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
	"time"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
)

func newTestProvider(searchURL, entityURL string) *sunbirdAuthnProvider {
	return &sunbirdAuthnProvider{
		cfg: Config{
			SearchURL:     searchURL,
			EntityURL:     entityURL,
			IDField:       "policyNumber",
			EntityIDField: "osid",
			ClaimsMapping: defaultSunbirdClaimsMapping,
		},
		client:      &http.Client{Timeout: 5 * time.Second},
		kbiFieldIDs: []string{"fullName", "dob"},
	}
}

func (ts *AuthenticatorTestSuite) TestNewSunbirdAuthnProvider() {
	t := ts.T()
	clearSunbirdEnv(t)
	httpClient := &http.Client{Timeout: 30 * time.Second}

	t.Run("missing search url", func(t *testing.T) {
		_, err := NewSunbirdAuthnProvider(httpClient)
		require.Error(t, err)
	})

	t.Run("invalid field details", func(t *testing.T) {
		t.Setenv(envSunbirdSearchURL, "http://example.com/search")
		t.Setenv(envSunbirdFieldDetails, "not-json")
		_, err := NewSunbirdAuthnProvider(httpClient)
		require.Error(t, err)
	})

	t.Run("no kbi fields other than id field", func(t *testing.T) {
		t.Setenv(envSunbirdSearchURL, "http://example.com/search")
		t.Setenv(envSunbirdFieldDetails, `[{"id":"policyNumber","type":"text","format":""}]`)
		_, err := NewSunbirdAuthnProvider(httpClient)
		require.Error(t, err)
	})

	t.Run("success", func(t *testing.T) {
		t.Setenv(envSunbirdSearchURL, "http://example.com/search")
		t.Setenv(envSunbirdFieldDetails, defaultSunbirdFieldDetails)
		provider, err := NewSunbirdAuthnProvider(httpClient)
		require.NoError(t, err)
		require.NotNil(t, provider)
	})
}

func (ts *AuthenticatorTestSuite) TestAuthenticate() {
	t := ts.T()

	t.Run("missing individual id", func(t *testing.T) {
		p := newTestProvider("http://unused", "http://unused")
		result, svcErr := p.Authenticate(context.Background(), map[string]interface{}{}, map[string]interface{}{}, nil)
		require.Nil(t, result)
		require.Same(t, shared.InvalidIndividualIDError, svcErr)
	})

	t.Run("empty individual id", func(t *testing.T) {
		p := newTestProvider("http://unused", "http://unused")
		identifiers := map[string]interface{}{sunbirdIndividualIDKey: ""}
		result, svcErr := p.Authenticate(context.Background(), identifiers, map[string]interface{}{}, nil)
		require.Nil(t, result)
		require.Same(t, shared.InvalidIndividualIDError, svcErr)
	})

	t.Run("missing credential field", func(t *testing.T) {
		p := newTestProvider("http://unused", "http://unused")
		identifiers := map[string]interface{}{sunbirdIndividualIDKey: "POL123"}
		credentials := map[string]interface{}{"fullName": "Jane Doe"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, nil)
		require.Nil(t, result)
		require.Same(t, shared.InvalidRequestError, svcErr)
	})

	t.Run("empty credential value", func(t *testing.T) {
		p := newTestProvider("http://unused", "http://unused")
		identifiers := map[string]interface{}{sunbirdIndividualIDKey: "POL123"}
		credentials := map[string]interface{}{"fullName": "Jane Doe", "dob": ""}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, nil)
		require.Nil(t, result)
		require.Same(t, shared.InvalidRequestError, svcErr)
	})

	t.Run("successful match", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			require.Equal(t, http.MethodPost, r.Method)
			var body sunbirdSearchRequest
			require.NoError(t, json.NewDecoder(r.Body).Decode(&body))
			require.Equal(t, "POL123", body.Filters["policyNumber"].Eq)
			require.Equal(t, "Jane Doe", body.Filters["fullName"].Eq)
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode([]map[string]interface{}{
				{"osid": "entity-1"},
			})
		}))
		defer server.Close()

		p := newTestProvider(server.URL, "http://unused")
		identifiers := map[string]interface{}{sunbirdIndividualIDKey: "POL123"}
		credentials := map[string]interface{}{"fullName": "Jane Doe", "dob": "01/01/1990"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, &providers.AuthnMetadata{})
		require.Nil(t, svcErr)
		require.NotNil(t, result)
		require.Equal(t, "entity-1", result.EntityReferenceToken)
		require.Equal(t, "entity-1", result.AttributeToken)
	})

	t.Run("no match returns invalid request", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode([]map[string]interface{}{})
		}))
		defer server.Close()

		p := newTestProvider(server.URL, "http://unused")
		identifiers := map[string]interface{}{sunbirdIndividualIDKey: "POL123"}
		credentials := map[string]interface{}{"fullName": "Jane Doe", "dob": "01/01/1990"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, nil)
		require.Nil(t, result)
		require.Same(t, shared.InvalidRequestError, svcErr)
	})

	t.Run("multiple matches returns invalid request", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode([]map[string]interface{}{
				{"osid": "entity-1"}, {"osid": "entity-2"},
			})
		}))
		defer server.Close()

		p := newTestProvider(server.URL, "http://unused")
		identifiers := map[string]interface{}{sunbirdIndividualIDKey: "POL123"}
		credentials := map[string]interface{}{"fullName": "Jane Doe", "dob": "01/01/1990"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, nil)
		require.Nil(t, result)
		require.Same(t, shared.InvalidRequestError, svcErr)
	})

	t.Run("registry error returns authentication failed", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer server.Close()

		p := newTestProvider(server.URL, "http://unused")
		identifiers := map[string]interface{}{sunbirdIndividualIDKey: "POL123"}
		credentials := map[string]interface{}{"fullName": "Jane Doe", "dob": "01/01/1990"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, nil)
		require.Nil(t, result)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})

	t.Run("unreachable registry returns authentication failed", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(_ http.ResponseWriter, _ *http.Request) {}))
		server.Close()

		p := newTestProvider(server.URL, "http://unused")
		identifiers := map[string]interface{}{sunbirdIndividualIDKey: "POL123"}
		credentials := map[string]interface{}{"fullName": "Jane Doe", "dob": "01/01/1990"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, nil)
		require.Nil(t, result)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})

	t.Run("entity id field missing in response", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode([]map[string]interface{}{{"other": "value"}})
		}))
		defer server.Close()

		p := newTestProvider(server.URL, "http://unused")
		identifiers := map[string]interface{}{sunbirdIndividualIDKey: "POL123"}
		credentials := map[string]interface{}{"fullName": "Jane Doe", "dob": "01/01/1990"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, nil)
		require.Nil(t, result)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})

	t.Run("entity id field not a string", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode([]map[string]interface{}{{"osid": 123}})
		}))
		defer server.Close()

		p := newTestProvider(server.URL, "http://unused")
		identifiers := map[string]interface{}{sunbirdIndividualIDKey: "POL123"}
		credentials := map[string]interface{}{"fullName": "Jane Doe", "dob": "01/01/1990"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, nil)
		require.Nil(t, result)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})

	t.Run("malformed json response", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte("not-json"))
		}))
		defer server.Close()

		p := newTestProvider(server.URL, "http://unused")
		identifiers := map[string]interface{}{sunbirdIndividualIDKey: "POL123"}
		credentials := map[string]interface{}{"fullName": "Jane Doe", "dob": "01/01/1990"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, nil)
		require.Nil(t, result)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})
}

func (ts *AuthenticatorTestSuite) TestGetAttributes() {
	t := ts.T()

	t.Run("nil consented attributes", func(t *testing.T) {
		p := newTestProvider("http://unused", "http://unused")
		attrs, svcErr := p.GetAttributes(context.Background(), "entity-1", nil, nil)
		require.Nil(t, attrs)
		require.Same(t, shared.InvalidRequestError, svcErr)
	})

	t.Run("entity fetch error", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusNotFound)
		}))
		defer server.Close()

		p := newTestProvider("http://unused", server.URL)
		attrs, svcErr := p.GetAttributes(context.Background(), "entity-1", &providers.RequestedAttributes{}, nil)
		require.Nil(t, attrs)
		require.Same(t, shared.InvalidRequestError, svcErr)
	})

	t.Run("success maps claims", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			require.Equal(t, http.MethodGet, r.Method)
			require.Equal(t, "/entity-1", r.URL.Path)
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"fullName": "Jane Doe",
				"email":    "jane@example.com",
				"mobile":   "9999999999",
				"gender":   "female",
				"dob":      "01/01/1990",
				"unused":   "should not be mapped",
			})
		}))
		defer server.Close()

		p := newTestProvider("http://unused", server.URL)
		attrs, svcErr := p.GetAttributes(context.Background(), "entity-1", &providers.RequestedAttributes{}, &providers.GetAttributesMetadata{})
		require.Nil(t, svcErr)
		require.NotNil(t, attrs)
		require.Equal(t, "Jane Doe", attrs.Attributes["name"].Value)
		require.Equal(t, "jane@example.com", attrs.Attributes["email"].Value)
		require.Equal(t, "9999999999", attrs.Attributes["phone_number"].Value)
		require.Equal(t, "female", attrs.Attributes["gender"].Value)
		require.Equal(t, "01/01/1990", attrs.Attributes["birthdate"].Value)
		_, ok := attrs.Attributes["unused"]
		require.False(t, ok)
	})

	t.Run("empty claims mapping yields no claims", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"fullName": "Jane Doe"})
		}))
		defer server.Close()

		p := newTestProvider("http://unused", server.URL)
		p.cfg.ClaimsMapping = ""
		attrs, svcErr := p.GetAttributes(context.Background(), "entity-1", &providers.RequestedAttributes{}, nil)
		require.Nil(t, svcErr)
		require.Empty(t, attrs.Attributes)
	})

	t.Run("malformed claims mapping yields no claims", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"fullName": "Jane Doe"})
		}))
		defer server.Close()

		p := newTestProvider("http://unused", server.URL)
		p.cfg.ClaimsMapping = "not-json"
		attrs, svcErr := p.GetAttributes(context.Background(), "entity-1", &providers.RequestedAttributes{}, nil)
		require.Nil(t, svcErr)
		require.Empty(t, attrs.Attributes)
	})
}

func (ts *AuthenticatorTestSuite) TestGetEntityReference() {
	t := ts.T()
	p := newTestProvider("http://unused", "http://unused")

	t.Run("valid token", func(t *testing.T) {
		ref, svcErr := p.GetEntityReference(context.Background(), "entity-1")
		require.Nil(t, svcErr)
		require.Equal(t, "entity-1", ref.EntityID)
	})

	t.Run("empty token", func(t *testing.T) {
		ref, svcErr := p.GetEntityReference(context.Background(), "")
		require.Nil(t, ref)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})

	t.Run("non string token", func(t *testing.T) {
		ref, svcErr := p.GetEntityReference(context.Background(), 42)
		require.Nil(t, ref)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})
}

func (ts *AuthenticatorTestSuite) TestNoOpMethods() {
	t := ts.T()
	p := newTestProvider("http://unused", "http://unused")

	result, svcErr := p.InitiateAuthentication(context.Background(), "individual-1", nil, nil)
	require.Nil(t, result)
	require.Nil(t, svcErr)

	result, svcErr = p.InitiateEnrollment(context.Background(), "individual-1", nil, nil)
	require.Nil(t, result)
	require.Nil(t, svcErr)

	authnResult, svcErr := p.Enroll(context.Background(), nil, nil, nil)
	require.Nil(t, authnResult)
	require.Nil(t, svcErr)

	otpResult, svcErr := p.SendOTP(context.Background(), nil, nil)
	require.Nil(t, otpResult)
	require.Same(t, shared.NotImplementedError, svcErr)

	certs, svcErr := p.GetSigningCertificates(context.Background())
	require.Nil(t, certs)
	require.Nil(t, svcErr)
}

func (ts *AuthenticatorTestSuite) TestValidateKBIRequestBuildError() {
	t := ts.T()
	p := newTestProvider("http://example.com/%zz", "http://unused")
	_, err := p.validateKBI(context.Background(), "POL123", map[string]string{"fullName": "Jane"})
	require.Error(t, err)
}

func (ts *AuthenticatorTestSuite) TestFetchEntityDataRequestBuildError() {
	t := ts.T()
	p := newTestProvider("http://unused", "http://example.com")
	_, err := p.fetchEntityData(context.Background(), "%zz")
	require.Error(t, err)
}

func (ts *AuthenticatorTestSuite) TestFetchEntityDataStatusError() {
	t := ts.T()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	p := newTestProvider("http://unused", server.URL)
	_, err := p.fetchEntityData(context.Background(), "entity-1")
	require.Error(t, err)
}

func (ts *AuthenticatorTestSuite) TestFetchEntityDataDecodeError() {
	t := ts.T()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("not-json"))
	}))
	defer server.Close()

	p := newTestProvider("http://unused", server.URL)
	_, err := p.fetchEntityData(context.Background(), "entity-1")
	require.Error(t, err)
}

func (ts *AuthenticatorTestSuite) TestParseSunbirdFieldDetails() {
	t := ts.T()

	t.Run("empty string", func(t *testing.T) {
		_, err := parseSunbirdFieldDetails("")
		require.Error(t, err)
	})

	t.Run("invalid json", func(t *testing.T) {
		_, err := parseSunbirdFieldDetails("not-json")
		require.Error(t, err)
	})

	t.Run("valid", func(t *testing.T) {
		fields, err := parseSunbirdFieldDetails(defaultSunbirdFieldDetails)
		require.NoError(t, err)
		require.Len(t, fields, 3)
		require.Equal(t, "policyNumber", fields[0].ID)
	})
}

func (ts *AuthenticatorTestSuite) TestParseSunbirdClaimsMapping() {
	t := ts.T()

	t.Run("invalid json", func(t *testing.T) {
		_, err := parseSunbirdClaimsMapping("not-json")
		require.Error(t, err)
	})

	t.Run("valid", func(t *testing.T) {
		mapping, err := parseSunbirdClaimsMapping(`{"name":"fullName"}`)
		require.NoError(t, err)
		require.Equal(t, "fullName", mapping["name"])
	})
}

func (ts *AuthenticatorTestSuite) TestBuildSunbirdMappedClaims() {
	t := ts.T()

	t.Run("empty mapping json", func(t *testing.T) {
		claims := buildSunbirdMappedClaims(context.Background(), map[string]interface{}{"fullName": "Jane"}, "")
		require.Empty(t, claims)
	})

	t.Run("malformed mapping json", func(t *testing.T) {
		claims := buildSunbirdMappedClaims(context.Background(), map[string]interface{}{"fullName": "Jane"}, "not-json")
		require.Empty(t, claims)
	})

	t.Run("missing registry field is skipped", func(t *testing.T) {
		claims := buildSunbirdMappedClaims(context.Background(), map[string]interface{}{}, `{"name":"fullName"}`)
		require.Empty(t, claims)
	})

	t.Run("mapped field present", func(t *testing.T) {
		claims := buildSunbirdMappedClaims(context.Background(), map[string]interface{}{"fullName": "Jane"}, `{"name":"fullName"}`)
		require.Equal(t, "Jane", claims["name"])
	})
}

type AuthenticatorTestSuite struct {
	suite.Suite
}

func TestAuthenticatorTestSuite(t *testing.T) {
	suite.Run(t, new(AuthenticatorTestSuite))
}
