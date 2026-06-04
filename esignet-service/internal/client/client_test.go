package client

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	applog "github.com/mosip/esignet/internal/log"
)

// =============================================================================
// Test helpers
// =============================================================================

// init bumps LOG_LEVEL to error so the upstream applog singleton stays quiet
// for the test binary. The singleton initialises lazily on first GetLogger
// call, so setting the env in init() (which runs before any test) is enough.
func init() {
	if os.Getenv("LOG_LEVEL") == "" {
		_ = os.Setenv("LOG_LEVEL", "error")
	}
}

// testLogger returns the package singleton at error level. Tests don't depend
// on log output; this just keeps the signature compatible with production code.
func testLogger() *applog.Logger { return applog.GetLogger() }

// validRSAJWK is a syntactically valid RSA JWK used as the publicKey for
// happy-path tests. The key material is irrelevant — nothing is signed.
const validRSAJWK = `{"kty":"RSA","n":"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw","e":"AQAB"}`

// fakeClientRepo records calls and returns canned errors. The zero value
// behaves like an empty database.
type fakeClientRepo struct {
	existsByID    map[string]bool
	existsErr     error
	insertedRows  []*ClientDetailRow
	insertErr     error
	insertErrOnce error
}

func (r *fakeClientRepo) ExistsByID(_ context.Context, id string) (bool, error) {
	if r.existsErr != nil {
		return false, r.existsErr
	}
	return r.existsByID[id], nil
}

func (r *fakeClientRepo) Insert(_ context.Context, row *ClientDetailRow) error {
	if r.insertErrOnce != nil {
		err := r.insertErrOnce
		r.insertErrOnce = nil
		return err
	}
	if r.insertErr != nil {
		return r.insertErr
	}
	r.insertedRows = append(r.insertedRows, row)
	return nil
}

// testValidatorConfig returns the canonical operator-configured allowed-value
// sets that every test exercises. Mirrors the values the deployed binary
// would receive from env config defaults.
func testValidatorConfig() ClientValidatorConfig {
	return ClientValidatorConfig{
		SupportedGrantTypes:        []string{"authorization_code"},
		SupportedClientAuthMethods: []string{"private_key_jwt"},
		SupportedUserClaims:        []string{"name", "given_name", "middle_name", "preferred_username", "nickname", "gender", "birthdate", "email", "phone_number", "picture", "address"},
		SupportedACRValues:         []string{"mosip:idp:acr:static-code", "mosip:idp:acr:generated-code", "mosip:idp:acr:linked-wallet", "mosip:idp:acr:biometrics", "mosip:idp:acr:knowledge", "mosip:idp:acr:id-token", "mosip:idp:acr:password"},
		SupportedIDRegex:           `^\S+$`,
	}
}

// newTestValidator builds a Validator from the embedded create + additionalConfig
// schemas using the canonical test config.
func newTestValidator(t *testing.T) *Validator {
	t.Helper()
	val, err := NewValidator(testValidatorConfig())
	if err != nil {
		t.Fatalf("NewValidator: %v", err)
	}
	return val
}

// schemasFixturePath returns the absolute path to the production additionalConfig
// schema file on disk; used by file:// override-URL tests so prod and test
// share the same schema source.
func schemasFixturePath(t *testing.T) string {
	t.Helper()
	path, err := filepath.Abs("schemas/additional_config.schema.json")
	if err != nil {
		t.Fatalf("resolve schemas path: %v", err)
	}
	if _, err := os.Stat(path); err != nil {
		t.Fatalf("schema missing: %v", err)
	}
	return path
}

// callCreate dispatches a request through the handler and returns the parsed
// envelope plus the response recorder. The recorder's body is preserved
// (we decode from a copy) so tests can also assert raw wire shape.
func callCreate(t *testing.T, body string, repo ClientRepo) (createResponseEnvelope, *httptest.ResponseRecorder) {
	t.Helper()
	val := newTestValidator(t)
	svc := NewService(repo, testLogger())
	h := ClientMgmtCreate(svc, val, testLogger())

	req := httptest.NewRequest(http.MethodPost, "/v1/esignet/client-mgmt/client", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	h(rec, req)

	var env createResponseEnvelope
	if rec.Body.Len() > 0 {
		if err := json.Unmarshal(rec.Body.Bytes(), &env); err != nil {
			t.Fatalf("decode response: %v\nbody=%s", err, rec.Body.String())
		}
	}
	return env, rec
}

// validBody returns a JSON envelope that satisfies every constraint. Tests
// then mutate one field at a time to drive the validation paths.
func validBody(t *testing.T, mutators ...func(m map[string]any)) string {
	t.Helper()
	req := map[string]any{
		"clientId":          "test-client-001",
		"clientName":        "Test Client",
		"clientNameLangMap": map[string]string{"eng": "Test Client"},
		"relyingPartyId":    "test-rp-001",
		"logoUri":           "https://example.com/logo.png",
		"redirectUris":      []string{"https://example.com/cb"},
		"authContextRefs":   []string{"mosip:idp:acr:biometrics"},
		"publicKey":         json.RawMessage(validRSAJWK),
		"userClaims":        []string{"name", "email"},
		"grantTypes":        []string{"authorization_code"},
		"clientAuthMethods": []string{"private_key_jwt"},
	}
	for _, m := range mutators {
		m(req)
	}
	env := map[string]any{
		"requestTime": "2026-05-22T00:00:00Z",
		"request":     req,
	}
	buf, err := json.Marshal(env)
	if err != nil {
		t.Fatalf("marshal valid body: %v", err)
	}
	return string(buf)
}

// =============================================================================
// Happy path
// =============================================================================

func TestCreate_HappyPath(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t)

	env, rec := callCreate(t, body, repo)

	if rec.Code != http.StatusOK {
		t.Fatalf("status: got %d, want %d", rec.Code, http.StatusOK)
	}
	if got := rec.Header().Get("Content-Type"); got != "application/json" {
		t.Errorf("Content-Type: got %q, want application/json", got)
	}
	if len(env.Errors) != 0 {
		t.Errorf("expected no errors, got %v", env.Errors)
	}
	if env.Response == nil {
		t.Fatal("expected response, got nil")
	}
	if env.Response.ClientID != "test-client-001" {
		t.Errorf("response clientId: got %q, want test-client-001", env.Response.ClientID)
	}
	if env.Response.Status != clientStatusActive {
		t.Errorf("response status: got %q, want ACTIVE", env.Response.Status)
	}
	if len(repo.insertedRows) != 1 {
		t.Fatalf("expected 1 inserted row, got %d", len(repo.insertedRows))
	}
	row := repo.insertedRows[0]
	if row.Status != clientStatusActive {
		t.Errorf("row.Status: got %s, want ACTIVE", row.Status)
	}
	if row.RpID != "test-rp-001" {
		t.Errorf("row.RpID: got %s", row.RpID)
	}
	// List fields are JSON-serialized arrays.
	if row.RedirectURIs != `["https://example.com/cb"]` {
		t.Errorf("row.RedirectURIs: %s", row.RedirectURIs)
	}
	if row.GrantTypes != `["authorization_code"]` {
		t.Errorf("row.GrantTypes: %s", row.GrantTypes)
	}
	// Name field is the language map when langMap is provided.
	if !strings.Contains(row.Name, `"eng":"Test Client"`) {
		t.Errorf("row.Name should be langmap JSON, got %s", row.Name)
	}
}

// When ClientValidatorConfig.SupportedIDRegex is empty, the validator must
// fall back to DefaultIDRegex. Guards against `envconfig` quirk where an
// empty-string env var bypasses the struct-tag default.
func TestNewValidator_EmptyRegex_FallsBackToDefault(t *testing.T) {
	cfg := testValidatorConfig()
	cfg.SupportedIDRegex = "" // empty — must fall back to DefaultIDRegex

	val, err := NewValidator(cfg)
	if err != nil {
		t.Fatalf("NewValidator: %v", err)
	}

	// Build a request with a whitespace-containing clientId. The default
	// regex `^\S+$` must reject it.
	bodyWithWhitespace := validBody(t, func(m map[string]any) {
		m["clientId"] = "a b"
	})
	var doc any
	if err := json.Unmarshal([]byte(bodyWithWhitespace), &doc); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	codes := val.ValidateCreate(doc)
	if !containsCodeString(codes, errInvalidClientID) {
		t.Errorf("expected %s for whitespace clientId, got %v", errInvalidClientID, codes)
	}

	// Non-whitespace id with various punctuation must pass the default.
	bodyClean := validBody(t, func(m map[string]any) {
		m["clientId"] = "client.id_with-special@chars"
	})
	if err := json.Unmarshal([]byte(bodyClean), &doc); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if codes := val.ValidateCreate(doc); len(codes) != 0 {
		t.Errorf("default regex should accept any non-whitespace id, got %v", codes)
	}
}

// containsCodeString is the string-slice variant of containsCode.
func containsCodeString(codes []string, want string) bool {
	for _, c := range codes {
		if c == want {
			return true
		}
	}
	return false
}

// id_format default accepts any non-blank, non-whitespace value — including
// punctuation like dots, slashes, and `@`. Guards against accidental over-
// strict validation in this Go port.
func TestCreate_ClientIDWithSpecialChars_Accepted(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["clientId"] = "client.with-various_chars/and@stuff:ok"
		m["relyingPartyId"] = "rp.with.dots"
	})
	env, _ := callCreate(t, body, repo)
	if len(env.Errors) != 0 {
		t.Errorf("clientId/relyingPartyId with non-whitespace special chars should be accepted, got %v", env.Errors)
	}
}

func TestCreate_EmptyLangMap_Accepted(t *testing.T) {
	// clientNameLangMap is optional and may be empty — the row builder
	// falls back to the plain clientName when no map entries are supplied.
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["clientNameLangMap"] = map[string]string{}
	})

	env, _ := callCreate(t, body, repo)
	if len(env.Errors) != 0 {
		t.Errorf("expected no errors, got %v", env.Errors)
	}
}

// =============================================================================
// Validation error paths — one test per documented eSignet error code
// =============================================================================

func TestCreate_ValidationErrors(t *testing.T) {
	cases := []struct {
		name     string
		mutate   func(m map[string]any)
		wantCode string
	}{
		{"clientId missing", func(m map[string]any) { delete(m, "clientId") }, errInvalidClientID},
		{"clientId too long", func(m map[string]any) { m["clientId"] = strings.Repeat("a", 101) }, errInvalidClientID},
		{"clientId bad chars", func(m map[string]any) { m["clientId"] = "has spaces" }, errInvalidClientID},
		{"clientName missing", func(m map[string]any) { delete(m, "clientName") }, errInvalidClientName},
		{"clientName too long", func(m map[string]any) { m["clientName"] = strings.Repeat("x", 257) }, errInvalidClientName},
		{"logoUri missing", func(m map[string]any) { delete(m, "logoUri") }, errInvalidURI},
		{"logoUri not a url", func(m map[string]any) { m["logoUri"] = "not-a-url" }, errInvalidURI},
		{"redirectUris empty", func(m map[string]any) { m["redirectUris"] = []string{} }, errInvalidRedirectURI},
		{"redirectUris too many", func(m map[string]any) {
			m["redirectUris"] = []string{"https://a/cb", "https://b/cb", "https://c/cb", "https://d/cb", "https://e/cb", "https://f/cb"}
		}, errInvalidRedirectURI},
		{"redirectUri malformed", func(m map[string]any) { m["redirectUris"] = []string{":::"} }, errInvalidRedirectURI},
		{"redirectUri https without host", func(m map[string]any) { m["redirectUris"] = []string{"https:/callback"} }, errInvalidRedirectURI},
		{"redirectUri http without host", func(m map[string]any) { m["redirectUris"] = []string{"http:/cb"} }, errInvalidRedirectURI},
		{"authContextRefs empty", func(m map[string]any) { m["authContextRefs"] = []string{} }, errInvalidACR},
		{"authContextRefs unknown", func(m map[string]any) { m["authContextRefs"] = []string{"mosip:idp:acr:bogus"} }, errInvalidACR},
		{"userClaims unknown", func(m map[string]any) { m["userClaims"] = []string{"not_a_claim"} }, errInvalidClaim},
		{"grantTypes empty", func(m map[string]any) { m["grantTypes"] = []string{} }, errUnsupportedGrantType},
		{"grantTypes unknown", func(m map[string]any) { m["grantTypes"] = []string{"password"} }, errUnsupportedGrantType},
		{"clientAuthMethods empty", func(m map[string]any) { m["clientAuthMethods"] = []string{} }, errInvalidClientAuth},
		{"clientAuthMethods unknown", func(m map[string]any) { m["clientAuthMethods"] = []string{"client_secret_basic"} }, errInvalidClientAuth},
		{"publicKey missing", func(m map[string]any) { delete(m, "publicKey") }, errInvalidPublicKey},
		{"relyingPartyId too long", func(m map[string]any) { m["relyingPartyId"] = strings.Repeat("r", 101) }, errInvalidRPID},
		{"relyingPartyId missing", func(m map[string]any) { delete(m, "relyingPartyId") }, errInvalidRPID},
		{"relyingPartyId bad chars", func(m map[string]any) { m["relyingPartyId"] = "has spaces" }, errInvalidRPID},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			repo := &fakeClientRepo{existsByID: map[string]bool{}}
			body := validBody(t, tc.mutate)
			env, rec := callCreate(t, body, repo)
			if rec.Code != http.StatusOK {
				t.Errorf("status: got %d, want 200", rec.Code)
			}
			if !containsCode(env.Errors, tc.wantCode) {
				t.Errorf("want code %s, got %v", tc.wantCode, env.Errors)
			}
			if len(repo.insertedRows) != 0 {
				t.Errorf("no row should be inserted on validation failure")
			}
		})
	}
}

// =============================================================================
// clientNameLangMap key/value violations
// =============================================================================

func TestCreate_LangMap_InvalidKey(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["clientNameLangMap"] = map[string]string{"xx": "Bad Code"} // not ISO 639-2/T
	})
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errInvalidLanguageCode) {
		t.Errorf("want %s, got %v", errInvalidLanguageCode, env.Errors)
	}
}

func TestCreate_LangMap_BlankValue(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["clientNameLangMap"] = map[string]string{"eng": ""}
	})
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errInvalidClientNameValue) {
		t.Errorf("want %s, got %v", errInvalidClientNameValue, env.Errors)
	}
}

func TestCreate_LangMap_WhitespaceOnlyValue(t *testing.T) {
	// Whitespace-only values must be rejected by the `not_blank` tag —
	// the built-in `required` tag would otherwise accept them.
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["clientNameLangMap"] = map[string]string{"eng": "   "}
	})
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errInvalidClientNameValue) {
		t.Errorf("want %s, got %v", errInvalidClientNameValue, env.Errors)
	}
}

// =============================================================================
// publicKey paths
// =============================================================================

func TestCreate_PublicKey_Malformed(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["publicKey"] = json.RawMessage(`{"kty":"RSA"}`) // missing n, e
	})
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errInvalidPublicKey) {
		t.Errorf("want %s, got %v", errInvalidPublicKey, env.Errors)
	}
}

// =============================================================================
// additionalConfig paths
// =============================================================================

func TestCreate_AdditionalConfig_NullOK(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["additionalConfig"] = nil
	})
	env, _ := callCreate(t, body, repo)
	if len(env.Errors) != 0 {
		t.Errorf("null additionalConfig should be accepted, got %v", env.Errors)
	}
}

func TestCreate_AdditionalConfig_Valid(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["additionalConfig"] = map[string]any{
			"userinfo_response_type":                "JWS",
			"signup_banner_required":                true,
			"forgot_pwd_link_required":              false,
			"consent_expire_in_mins":                30,
			"require_pushed_authorization_requests": true,
			"dpop_bound_access_tokens":              true,
		}
	})
	env, _ := callCreate(t, body, repo)
	if len(env.Errors) != 0 {
		t.Errorf("valid additionalConfig rejected: %v", env.Errors)
	}
	if len(repo.insertedRows) != 1 || repo.insertedRows[0].AdditionalConfig == nil {
		t.Fatal("expected additionalConfig persisted")
	}
}

func TestCreate_AdditionalConfig_InvalidEnum(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["additionalConfig"] = map[string]any{"userinfo_response_type": "PLAIN"}
	})
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errInvalidAdditionalConfig) {
		t.Errorf("want %s, got %v", errInvalidAdditionalConfig, env.Errors)
	}
}

func TestCreate_AdditionalConfig_BelowMinimum(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["additionalConfig"] = map[string]any{"consent_expire_in_mins": 5}
	})
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errInvalidAdditionalConfig) {
		t.Errorf("want %s, got %v", errInvalidAdditionalConfig, env.Errors)
	}
}

// require_pkce is a boolean knob on additionalConfig; the schema accepts
// it and the request flows through.
func TestCreate_AdditionalConfig_RequirePKCE_Accepted(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["additionalConfig"] = map[string]any{"require_pkce": true}
	})
	env, _ := callCreate(t, body, repo)
	if len(env.Errors) != 0 {
		t.Errorf("require_pkce should be accepted, got %v", env.Errors)
	}
}

// purpose must be a typed object with a required `type` enum
// (none/verify/link/login) or null. `purpose: {}` lacks `type`.
func TestCreate_AdditionalConfig_PurposeEmptyObject_Rejected(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["additionalConfig"] = map[string]any{"purpose": map[string]any{}}
	})
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errInvalidAdditionalConfig) {
		t.Errorf("want %s, got %v", errInvalidAdditionalConfig, env.Errors)
	}
}

func TestCreate_AdditionalConfig_PurposeWithType_Accepted(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["additionalConfig"] = map[string]any{
			"purpose": map[string]any{"type": "verify"},
		}
	})
	env, _ := callCreate(t, body, repo)
	if len(env.Errors) != 0 {
		t.Errorf("purpose with valid type should be accepted, got %v", env.Errors)
	}
}

func TestCreate_AdditionalConfig_PurposeBogusType_Rejected(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["additionalConfig"] = map[string]any{
			"purpose": map[string]any{"type": "bogus"},
		}
	})
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errInvalidAdditionalConfig) {
		t.Errorf("want %s, got %v", errInvalidAdditionalConfig, env.Errors)
	}
}

// Unknown top-level keys are rejected — schema sets additionalProperties: false.
func TestCreate_AdditionalConfig_UnknownProperty_Rejected(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["additionalConfig"] = map[string]any{"some_future_key": true}
	})
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errInvalidAdditionalConfig) {
		t.Errorf("want %s, got %v", errInvalidAdditionalConfig, env.Errors)
	}
}

// =============================================================================
// Duplicate-id paths
// =============================================================================

func TestCreate_DuplicateID_ViaExistsCheck(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{"test-client-001": true}}
	body := validBody(t)
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errDuplicateClientID) {
		t.Errorf("want %s, got %v", errDuplicateClientID, env.Errors)
	}
	if len(repo.insertedRows) != 0 {
		t.Errorf("should not insert on duplicate id")
	}
}

func TestCreate_DuplicateID_ViaInsertRace(t *testing.T) {
	repo := &fakeClientRepo{
		existsByID:    map[string]bool{},
		insertErrOnce: ErrDuplicateClientID,
	}
	body := validBody(t)
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errDuplicateClientID) {
		t.Errorf("want %s, got %v", errDuplicateClientID, env.Errors)
	}
}

func TestCreate_DuplicatePublicKey(t *testing.T) {
	repo := &fakeClientRepo{
		existsByID:    map[string]bool{},
		insertErrOnce: ErrDuplicatePublicKey,
	}
	body := validBody(t)
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errDuplicatePublicKey) {
		t.Errorf("want %s, got %v", errDuplicatePublicKey, env.Errors)
	}
}

// =============================================================================
// Envelope-level errors
// =============================================================================

func TestCreate_MalformedJSON(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	env, _ := callCreate(t, `{not-json`, repo)
	if !containsCode(env.Errors, errInvalidInput) {
		t.Errorf("want %s, got %v", errInvalidInput, env.Errors)
	}
}

func TestCreate_UnknownField(t *testing.T) {
	// DisallowUnknownFields() is enabled — any unexpected top-level key
	// surfaces as invalid_input.
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := `{"requestTime":"2026-05-22T00:00:00Z","request":{"clientId":"x"},"extra":"x"}`
	env, _ := callCreate(t, body, repo)
	if !containsCode(env.Errors, errInvalidInput) {
		t.Errorf("want %s, got %v", errInvalidInput, env.Errors)
	}
}

// =============================================================================
// Validator construction — embedded + override-URL paths
// =============================================================================

func TestNewValidator_Embedded(t *testing.T) {
	// Default path — must always compile. Guards against the //go:embed
	// source going missing or becoming malformed.
	val, err := NewValidator(testValidatorConfig())
	if err != nil {
		t.Fatalf("NewValidator: %v", err)
	}
	if val == nil {
		t.Fatal("validator nil")
	}
}

func TestNewValidatorWithSchema_File(t *testing.T) {
	path := schemasFixturePath(t)
	val, err := NewValidatorWithSchema(context.Background(), testValidatorConfig(), "file://"+filepath.ToSlash(path))
	if err != nil {
		t.Fatalf("file:// load: %v", err)
	}
	if val == nil {
		t.Fatal("validator nil")
	}
}

func TestNewValidatorWithSchema_HTTP(t *testing.T) {
	body, err := os.ReadFile(schemasFixturePath(t))
	if err != nil {
		t.Fatalf("read schema: %v", err)
	}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(body)
	}))
	defer srv.Close()

	val, err := NewValidatorWithSchema(context.Background(), testValidatorConfig(), srv.URL+"/additional_config.schema.json")
	if err != nil {
		t.Fatalf("http load: %v", err)
	}
	if val == nil {
		t.Fatal("validator nil")
	}
}

func TestNewValidatorWithSchema_HTTPNotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.NotFound(w, nil)
	}))
	defer srv.Close()
	_, err := NewValidatorWithSchema(context.Background(), testValidatorConfig(), srv.URL+"/missing")
	if err == nil {
		t.Fatal("expected error on 404")
	}
}

func TestNewValidatorWithSchema_UnsupportedScheme(t *testing.T) {
	_, err := NewValidatorWithSchema(context.Background(), testValidatorConfig(), "ftp://example.com/schema.json")
	if err == nil {
		t.Fatal("expected error on unsupported scheme")
	}
}

// =============================================================================
// Sanity: response envelope is always shaped right
// =============================================================================

func TestErrorEnvelope_NeverNilErrorsArray(t *testing.T) {
	// The `errors` array must always serialise as [] (never null), even
	// on malformed input.
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	_, rec := callCreate(t, `garbage`, repo)

	raw := map[string]json.RawMessage{}
	if err := json.Unmarshal(rec.Body.Bytes(), &raw); err != nil {
		t.Fatalf("decode wire body: %v", err)
	}
	if string(raw["errors"]) == "null" || raw["errors"] == nil {
		t.Errorf("errors must serialise as [], got %s", string(raw["errors"]))
	}
}

// On error responses, `response` must be present and explicitly null
// (not omitted) so clients can rely on the field always being there.
func TestErrorEnvelope_ResponseFieldIsNull(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	_, rec := callCreate(t, `garbage`, repo)

	raw := map[string]json.RawMessage{}
	if err := json.Unmarshal(rec.Body.Bytes(), &raw); err != nil {
		t.Fatalf("decode wire body: %v", err)
	}
	got, present := raw["response"]
	if !present {
		t.Fatal("response key missing from error envelope")
	}
	if string(got) != "null" {
		t.Errorf("response must be null on error, got %s", string(got))
	}
}

// Every error code surfaced on the wire must come with a non-empty
// errorMessage so clients can render it without reverse-mapping codes.
func TestErrorEnvelope_ErrorMessagePopulated(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		m["grantTypes"] = []string{"password"}
	})
	env, _ := callCreate(t, body, repo)
	if len(env.Errors) == 0 {
		t.Fatal("expected at least one error")
	}
	if env.Errors[0].ErrorMessage == "" {
		t.Errorf("errorMessage must be populated, got empty string")
	}
}

// When multiple fields would each fail validation, only the first
// failure (in DTO declaration order) is returned. Mirrors the
// fail-fast semantics the integrating clients expect.
func TestCreate_FailFast_OnFirstError(t *testing.T) {
	repo := &fakeClientRepo{existsByID: map[string]bool{}}
	body := validBody(t, func(m map[string]any) {
		// Two simultaneous failures: bad lang code AND bad redirect uri.
		// ClientNameLangMap is declared before RedirectURIs in the DTO,
		// so we expect ONLY invalid_language_code on the wire.
		m["clientNameLangMap"] = map[string]string{"xx": "bad lang"}
		m["redirectUris"] = []string{"not a url"}
	})
	env, _ := callCreate(t, body, repo)
	if len(env.Errors) != 1 {
		t.Fatalf("expected exactly 1 error, got %d: %v", len(env.Errors), env.Errors)
	}
	if env.Errors[0].ErrorCode != errInvalidLanguageCode {
		t.Errorf("expected %s, got %s", errInvalidLanguageCode, env.Errors[0].ErrorCode)
	}
}

// =============================================================================
// Small helpers
// =============================================================================

func containsCode(entries []errorEntry, code string) bool {
	for _, e := range entries {
		if e.ErrorCode == code {
			return true
		}
	}
	return false
}
