package host

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/config"
)

const (
	testSunbirdFieldDetails = `[{"id":"policyNumber","type":"text","format":""},` +
		`{"id":"fullName","type":"text","format":""},` +
		`{"id":"dob","type":"date","format":"dd/mm/yyyy"}]`
	testSunbirdClaimsMapping = `{"name":"fullName","email":"email","birthdate":"dob"}`
)

func newTestSunbirdProvider(t *testing.T, searchURL, entityURL string) *sunbirdAuthnProvider {
	t.Helper()
	provider, err := NewSunbirdAuthnProvider(config.SunbirdAuthn{
		SearchURL:     searchURL,
		EntityURL:     entityURL,
		IDField:       "policyNumber",
		EntityIDField: "osid",
		FieldDetails:  testSunbirdFieldDetails,
		ClaimsMapping: testSunbirdClaimsMapping,
		TimeoutSecs:   5,
	})
	require.NoError(t, err)
	return provider.(*sunbirdAuthnProvider)
}

func TestNewSunbirdAuthnProvider_requiresSearchURL(t *testing.T) {
	_, err := NewSunbirdAuthnProvider(config.SunbirdAuthn{})
	require.Error(t, err)
	require.Contains(t, err.Error(), "SUNBIRD_SEARCH_URL")
}

func TestSunbirdAuthenticate_success(t *testing.T) {
	var gotReq sunbirdSearchRequest
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		require.Equal(t, http.MethodPost, r.Method)
		require.NoError(t, json.NewDecoder(r.Body).Decode(&gotReq))
		_, _ = w.Write([]byte(`[{"osid":"entity-123"}]`))
	}))
	defer srv.Close()

	p := newTestSunbirdProvider(t, srv.URL, "")
	result, err := p.Authenticate(context.Background(),
		map[string]interface{}{"individualId": "POL-1"},
		map[string]interface{}{"fullName": "John Doe", "dob": "1990-01-01"},
		nil)

	require.NoError(t, err)
	require.True(t, result.Authenticated)
	require.Equal(t, "entity-123", result.UserID)
	require.Equal(t, "entity-123", result.AuthToken)

	// individualId maps to the configured IDField in the search filter.
	require.Equal(t, "POL-1", gotReq.Filters["policyNumber"].Eq)
	require.Equal(t, "John Doe", gotReq.Filters["fullName"].Eq)
	require.Equal(t, "1990-01-01", gotReq.Filters["dob"].Eq)
}

func TestSunbirdAuthenticate_zeroMatchFails(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`[]`))
	}))
	defer srv.Close()

	p := newTestSunbirdProvider(t, srv.URL, "")
	_, err := p.Authenticate(context.Background(),
		map[string]interface{}{"individualId": "POL-1"},
		map[string]interface{}{"fullName": "John Doe", "dob": "1990-01-01"},
		nil)
	require.ErrorIs(t, err, errSunbirdKBIAuthFailed)
}

func TestSunbirdAuthenticate_multiMatchFails(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`[{"osid":"a"},{"osid":"b"}]`))
	}))
	defer srv.Close()

	p := newTestSunbirdProvider(t, srv.URL, "")
	_, err := p.Authenticate(context.Background(),
		map[string]interface{}{"individualId": "POL-1"},
		map[string]interface{}{"fullName": "John Doe", "dob": "1990-01-01"},
		nil)
	require.ErrorIs(t, err, errSunbirdKBIAuthFailed)
}

func TestSunbirdAuthenticate_missingCredentialFails(t *testing.T) {
	p := newTestSunbirdProvider(t, "https://reg.example/search", "")
	_, err := p.Authenticate(context.Background(),
		map[string]interface{}{"individualId": "POL-1"},
		map[string]interface{}{"fullName": "John Doe"}, // dob missing
		nil)
	require.Error(t, err)
	require.Contains(t, err.Error(), "dob")
	require.False(t, errors.Is(err, errSunbirdKBIAuthFailed))
}

func TestSunbirdAuthenticate_missingIndividualIDFails(t *testing.T) {
	p := newTestSunbirdProvider(t, "https://reg.example/search", "")
	_, err := p.Authenticate(context.Background(),
		map[string]interface{}{},
		map[string]interface{}{"fullName": "John Doe", "dob": "1990-01-01"},
		nil)
	require.Error(t, err)
	require.Contains(t, err.Error(), "individualId")
}

func TestSunbirdGetAttributes_mapsClaims(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		require.Equal(t, http.MethodGet, r.Method)
		require.True(t, strings.HasSuffix(r.URL.Path, "/entity-123"))
		_, _ = w.Write([]byte(`{"fullName":"John Doe","email":"john@example.com","dob":"1990-01-01","extra":"ignored"}`))
	}))
	defer srv.Close()

	p := newTestSunbirdProvider(t, "https://reg.example/search", srv.URL)
	result, err := p.GetAttributes(context.Background(), "entity-123", nil, nil)
	require.NoError(t, err)

	var claims map[string]interface{}
	require.NoError(t, json.Unmarshal(result.Attributes, &claims))
	require.Equal(t, "John Doe", claims["name"])
	require.Equal(t, "john@example.com", claims["email"])
	require.Equal(t, "1990-01-01", claims["birthdate"])
	require.NotContains(t, claims, "extra") // unmapped fields are dropped
}

func TestSunbirdGetAttributes_emptyEntityURLReturnsEmpty(t *testing.T) {
	p := newTestSunbirdProvider(t, "https://reg.example/search", "")
	result, err := p.GetAttributes(context.Background(), "entity-123", nil, nil)
	require.NoError(t, err)
	require.JSONEq(t, "{}", string(result.Attributes))
}

func TestBuildSunbirdMappedClaims_rawPassthroughWhenNoMapping(t *testing.T) {
	entity := map[string]interface{}{"fullName": "John", "email": "j@e.com"}
	out := buildSunbirdMappedClaims(entity, "")
	require.Equal(t, entity, out)
}
