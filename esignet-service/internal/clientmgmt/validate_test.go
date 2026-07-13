/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package clientmgmt

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func errCode(t *testing.T, err error) string {
	t.Helper()
	require.Error(t, err)
	var ve *ValidationError
	require.ErrorAs(t, err, &ve)
	return ve.Code
}

func validCreateRequest() CreateClientRequest {
	return CreateClientRequest{
		ClientID:     "client-1",
		ClientName:   "Test App",
		RpID:         "rp-1",
		LogoURI:      "https://example.com/logo.png",
		RedirectURIs: []string{"https://example.com/callback"},
		Claims:       []string{"name", "email"},
		AcrValues:    []string{"mosip:idp:acr:static-code"},
		PublicKey:    map[string]string{"kty": "RSA", "n": "abc", "e": "AQAB"},
		GrantTypes:   []string{"authorization_code"},
		AuthMethods:  []string{"private_key_jwt"},
	}
}

func validUpdateRequest() UpdateClientRequest {
	return UpdateClientRequest{
		ClientName:   "Test App",
		Status:       "active",
		LogoURI:      "https://example.com/logo.png",
		RedirectURIs: []string{"https://example.com/callback"},
		Claims:       []string{"name"},
		AcrValues:    []string{"mosip:idp:acr:static-code"},
		GrantTypes:   []string{"authorization_code"},
		AuthMethods:  []string{"private_key_jwt"},
	}
}

func (ts *ValidateTestSuite) TestValidateCreate() {
	t := ts.T()
	t.Run("valid oidc profile", func(t *testing.T) {
		req := validCreateRequest()
		assert.NoError(t, ValidateCreate(ProfileOIDC, req))
	})

	t.Run("oidc profile rejects client name lang map", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		assert.Equal(t, "invalid_input", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("oidc profile rejects additional config", func(t *testing.T) {
		req := validCreateRequest()
		req.AdditionalConfig = json.RawMessage(`{}`)
		assert.Equal(t, "invalid_input", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("non-oidc profile requires client name lang map", func(t *testing.T) {
		req := validCreateRequest()
		assert.Equal(t, "invalid_input", errCode(t, ValidateCreate(ProfileOAuth, req)))
	})

	t.Run("oauth profile rejects additional config", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`{}`)
		assert.Equal(t, "invalid_input", errCode(t, ValidateCreate(ProfileOAuth, req)))
	})

	t.Run("client profile with additional config", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`{"consent_expire_in_mins":30}`)
		assert.NoError(t, ValidateCreate(ProfileClient, req))
	})

	t.Run("invalid client id", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientID = ""
		assert.Equal(t, "invalid_client_id", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("invalid client name", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientName = ""
		assert.Equal(t, "invalid_client_name", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("invalid rp id", func(t *testing.T) {
		req := validCreateRequest()
		req.RpID = ""
		assert.Equal(t, "invalid_rp_id", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("invalid logo uri", func(t *testing.T) {
		req := validCreateRequest()
		req.LogoURI = "not a uri"
		assert.Equal(t, "invalid_uri", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("empty redirect uris", func(t *testing.T) {
		req := validCreateRequest()
		req.RedirectURIs = nil
		assert.Equal(t, "invalid_redirect_uri", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("invalid redirect uri", func(t *testing.T) {
		req := validCreateRequest()
		req.RedirectURIs = []string{"not a uri"}
		assert.Equal(t, "invalid_redirect_uri", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("empty claims", func(t *testing.T) {
		req := validCreateRequest()
		req.Claims = nil
		assert.Equal(t, "invalid_claim", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("invalid claims", func(t *testing.T) {
		req := validCreateRequest()
		req.Claims = []string{"not_allowed"}
		assert.Equal(t, "invalid_claim", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("empty acr values", func(t *testing.T) {
		req := validCreateRequest()
		req.AcrValues = nil
		assert.Equal(t, "invalid_acr", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("invalid acr values", func(t *testing.T) {
		req := validCreateRequest()
		req.AcrValues = []string{"not_allowed"}
		assert.Equal(t, "invalid_acr", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("invalid public key", func(t *testing.T) {
		req := validCreateRequest()
		req.PublicKey = nil
		assert.Equal(t, "invalid_public_key", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("empty grant types", func(t *testing.T) {
		req := validCreateRequest()
		req.GrantTypes = nil
		assert.Equal(t, "invalid_grant_type", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("invalid grant types", func(t *testing.T) {
		req := validCreateRequest()
		req.GrantTypes = []string{"implicit"}
		assert.Equal(t, "invalid_grant_type", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("empty auth methods", func(t *testing.T) {
		req := validCreateRequest()
		req.AuthMethods = nil
		assert.Equal(t, "invalid_client_auth", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("invalid auth methods", func(t *testing.T) {
		req := validCreateRequest()
		req.AuthMethods = []string{"client_secret_basic"}
		assert.Equal(t, "invalid_client_auth", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})

	t.Run("client profile invalid additional config", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`not-json`)
		assert.Equal(t, "invalid_additional_config", errCode(t, ValidateCreate(ProfileClient, req)))
	})

	t.Run("invalid enc public key", func(t *testing.T) {
		req := validCreateRequest()
		req.EncPublicKey = map[string]string{"kty": "bogus"}
		assert.Equal(t, "invalid_public_key", errCode(t, ValidateCreate(ProfileOIDC, req)))
	})
}

func (ts *ValidateTestSuite) TestValidateUpdate() {
	t := ts.T()
	t.Run("valid oidc profile", func(t *testing.T) {
		req := validUpdateRequest()
		assert.NoError(t, ValidateUpdate(ProfileOIDC, req))
	})

	t.Run("oidc profile rejects client name lang map", func(t *testing.T) {
		req := validUpdateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		assert.Equal(t, "invalid_input", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("non-oidc requires client name lang map", func(t *testing.T) {
		req := validUpdateRequest()
		assert.Equal(t, "invalid_input", errCode(t, ValidateUpdate(ProfileOAuth, req)))
	})

	t.Run("invalid status", func(t *testing.T) {
		req := validUpdateRequest()
		req.Status = "bogus"
		assert.Equal(t, "invalid_input", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("empty claims below minimum", func(t *testing.T) {
		req := validUpdateRequest()
		req.Claims = nil
		assert.Equal(t, "invalid_claim", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("oidc acr must be in restricted set", func(t *testing.T) {
		req := validUpdateRequest()
		req.AcrValues = []string{"mosip:idp:acr:password"}
		assert.Equal(t, "invalid_acr", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("client profile additional config", func(t *testing.T) {
		req := validUpdateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`{"dpop_bound_access_tokens":true}`)
		assert.NoError(t, ValidateUpdate(ProfileClient, req))
	})

	t.Run("oauth profile rejects additional config", func(t *testing.T) {
		req := validUpdateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`{}`)
		assert.Equal(t, "invalid_input", errCode(t, ValidateUpdate(ProfileOAuth, req)))
	})

	t.Run("invalid client name", func(t *testing.T) {
		req := validUpdateRequest()
		req.ClientName = ""
		assert.Equal(t, "invalid_client_name", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("invalid logo uri", func(t *testing.T) {
		req := validUpdateRequest()
		req.LogoURI = "not a uri"
		assert.Equal(t, "invalid_uri", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("empty redirect uris", func(t *testing.T) {
		req := validUpdateRequest()
		req.RedirectURIs = nil
		assert.Equal(t, "invalid_redirect_uri", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("invalid redirect uri", func(t *testing.T) {
		req := validUpdateRequest()
		req.RedirectURIs = []string{"not a uri"}
		assert.Equal(t, "invalid_redirect_uri", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("invalid claims", func(t *testing.T) {
		req := validUpdateRequest()
		req.Claims = []string{"not_allowed"}
		assert.Equal(t, "invalid_claim", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("empty acr values", func(t *testing.T) {
		req := validUpdateRequest()
		req.AcrValues = nil
		assert.Equal(t, "invalid_acr", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("empty grant types", func(t *testing.T) {
		req := validUpdateRequest()
		req.GrantTypes = nil
		assert.Equal(t, "invalid_grant_type", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("invalid grant types", func(t *testing.T) {
		req := validUpdateRequest()
		req.GrantTypes = []string{"implicit"}
		assert.Equal(t, "invalid_grant_type", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("empty auth methods", func(t *testing.T) {
		req := validUpdateRequest()
		req.AuthMethods = nil
		assert.Equal(t, "invalid_client_auth", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("invalid auth methods", func(t *testing.T) {
		req := validUpdateRequest()
		req.AuthMethods = []string{"client_secret_basic"}
		assert.Equal(t, "invalid_client_auth", errCode(t, ValidateUpdate(ProfileOIDC, req)))
	})

	t.Run("client profile invalid additional config", func(t *testing.T) {
		req := validUpdateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`not-json`)
		assert.Equal(t, "invalid_additional_config", errCode(t, ValidateUpdate(ProfileClient, req)))
	})
}

func (ts *ValidateTestSuite) TestValidatePatch() {
	t := ts.T()
	base := validUpdateRequest()

	t.Run("no fields set falls through to ValidateUpdate", func(t *testing.T) {
		assert.NoError(t, ValidatePatch(ProfileOIDC, base, PatchFields{}, NullableJWK{}))
	})

	t.Run("patched client name validated", func(t *testing.T) {
		merged := base
		merged.ClientName = ""
		err := ValidatePatch(ProfileOIDC, merged, PatchFields{ClientName: true}, NullableJWK{})
		assert.Equal(t, "invalid_client_name", errCode(t, err))
	})

	t.Run("patched status validated", func(t *testing.T) {
		merged := base
		merged.Status = "bogus"
		err := ValidatePatch(ProfileOIDC, merged, PatchFields{Status: true}, NullableJWK{})
		assert.Equal(t, "invalid_input", errCode(t, err))
	})

	t.Run("patched redirect uris validated", func(t *testing.T) {
		merged := base
		merged.RedirectURIs = []string{"not a uri"}
		err := ValidatePatch(ProfileOIDC, merged, PatchFields{RedirectURIs: true}, NullableJWK{})
		assert.Equal(t, "invalid_redirect_uri", errCode(t, err))
	})

	t.Run("patched enc public key validated", func(t *testing.T) {
		err := ValidatePatch(ProfileOIDC, base, PatchFields{EncPublicKey: true},
			NullableJWK{Value: map[string]string{"kty": "bogus"}})
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})

	t.Run("null enc public key skips validation", func(t *testing.T) {
		err := ValidatePatch(ProfileOIDC, base, PatchFields{EncPublicKey: true}, NullableJWK{IsNull: true})
		assert.NoError(t, err)
	})

	t.Run("status normalized before final ValidateUpdate", func(t *testing.T) {
		merged := base
		merged.Status = "ACTIVE"
		assert.NoError(t, ValidatePatch(ProfileOIDC, merged, PatchFields{}, NullableJWK{}))
	})

	t.Run("patched client name lang map validated", func(t *testing.T) {
		merged := base
		merged.ClientNameLangMap = map[string]string{"xx": "bad code"}
		err := ValidatePatch(ProfileOIDC, merged, PatchFields{ClientNameLangMap: true}, NullableJWK{})
		assert.Equal(t, "invalid_language_code", errCode(t, err))
	})

	t.Run("patched claims exceeding max rejected", func(t *testing.T) {
		merged := base
		many := make([]string, 0, 31)
		for range 31 {
			many = append(many, "name")
		}
		merged.Claims = many
		err := ValidatePatch(ProfileOIDC, merged, PatchFields{Claims: true}, NullableJWK{})
		assert.Equal(t, "invalid_claim", errCode(t, err))
	})

	t.Run("patched acr values exceeding max rejected", func(t *testing.T) {
		merged := base
		many := make([]string, 0, 31)
		for range 31 {
			many = append(many, "mosip:idp:acr:static-code")
		}
		merged.AcrValues = many
		err := ValidatePatch(ProfileOIDC, merged, PatchFields{AcrValues: true}, NullableJWK{})
		assert.Equal(t, "invalid_acr", errCode(t, err))
	})

	t.Run("patched grant types exceeding max rejected", func(t *testing.T) {
		merged := base
		merged.GrantTypes = []string{"authorization_code", "authorization_code", "authorization_code", "authorization_code"}
		err := ValidatePatch(ProfileOIDC, merged, PatchFields{GrantTypes: true}, NullableJWK{})
		assert.Equal(t, "invalid_grant_type", errCode(t, err))
	})

	t.Run("patched auth methods exceeding max rejected", func(t *testing.T) {
		merged := base
		merged.AuthMethods = []string{"private_key_jwt", "private_key_jwt", "private_key_jwt", "private_key_jwt"}
		err := ValidatePatch(ProfileOIDC, merged, PatchFields{AuthMethods: true}, NullableJWK{})
		assert.Equal(t, "invalid_client_auth", errCode(t, err))
	})

	t.Run("patched additional config validated for client profile", func(t *testing.T) {
		merged := base
		merged.ClientNameLangMap = map[string]string{"eng": "x"}
		merged.AdditionalConfig = json.RawMessage(`not-json`)
		err := ValidatePatch(ProfileClient, merged, PatchFields{AdditionalConfig: true}, NullableJWK{})
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})
}

func (ts *ValidateTestSuite) TestValidateClientNameLangMap() {
	t := ts.T()
	t.Run("nil map is invalid", func(t *testing.T) {
		assert.Equal(t, "invalid_input", errCode(t, validateClientNameLangMap(nil, false)))
	})

	t.Run("invalid language code", func(t *testing.T) {
		err := validateClientNameLangMap(map[string]string{"xx": "value"}, false)
		assert.Equal(t, "invalid_language_code", errCode(t, err))
	})

	t.Run("blank value rejected", func(t *testing.T) {
		err := validateClientNameLangMap(map[string]string{"eng": "  "}, false)
		assert.Equal(t, "invalid_client_name_value", errCode(t, err))
	})

	t.Run("patch value bounds enforced", func(t *testing.T) {
		long := ""
		for range 60 {
			long += "a"
		}
		err := validateClientNameLangMap(map[string]string{"eng": long}, true)
		assert.Equal(t, "invalid_client_name_value", errCode(t, err))
	})

	t.Run("valid map", func(t *testing.T) {
		assert.NoError(t, validateClientNameLangMap(map[string]string{"eng": "App"}, false))
	})
}

func (ts *ValidateTestSuite) TestValidateAdditionalConfig() {
	t := ts.T()
	t.Run("invalid json", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`not-json`))
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("valid userinfo_response_type", func(t *testing.T) {
		assert.NoError(t, validateAdditionalConfig(json.RawMessage(`{"userinfo_response_type":"JWE"}`)))
	})

	t.Run("invalid userinfo_response_type", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"userinfo_response_type":"XML"}`))
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("consent_expire_in_mins too low", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"consent_expire_in_mins":5}`))
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("bool fields validated", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"signup_banner_required":"not-a-bool"}`))
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("purpose validated", func(t *testing.T) {
		assert.NoError(t, validateAdditionalConfig(json.RawMessage(
			`{"purpose":{"type":"consent","title":{"@none":"Title"}}}`)))
	})

	t.Run("invalid purpose propagates", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"purpose":{}}`))
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})
}

func (ts *ValidateTestSuite) TestValidatePurpose() {
	t := ts.T()
	t.Run("invalid json", func(t *testing.T) {
		assert.Equal(t, "invalid_additional_config", errCode(t, validatePurpose(json.RawMessage(`not-json`))))
	})

	t.Run("missing type", func(t *testing.T) {
		assert.Equal(t, "invalid_additional_config", errCode(t, validatePurpose(json.RawMessage(`{}`))))
	})

	t.Run("empty type", func(t *testing.T) {
		err := validatePurpose(json.RawMessage(`{"type":""}`))
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("title missing @none", func(t *testing.T) {
		err := validatePurpose(json.RawMessage(`{"type":"consent","title":{"eng":"Title"}}`))
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("subTitle invalid json", func(t *testing.T) {
		err := validatePurpose(json.RawMessage(`{"type":"consent","subTitle":"not-a-map"}`))
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("valid purpose", func(t *testing.T) {
		err := validatePurpose(json.RawMessage(`{"type":"consent","title":{"@none":"Title"},"subTitle":{"@none":"Sub"}}`))
		assert.NoError(t, err)
	})
}

func (ts *ValidateTestSuite) TestNormalizeStatus() {
	t := ts.T()
	tests := []struct {
		in      string
		want    string
		wantErr bool
	}{
		{"active", "ACTIVE", false},
		{"inactive", "INACTIVE", false},
		{"ACTIVE", "ACTIVE", false},
		{"INACTIVE", "INACTIVE", false},
		{"Active", "ACTIVE", false},
		{"bogus", "", true},
		{"", "", true},
	}
	for _, tt := range tests {
		got, err := normalizeStatus(tt.in)
		if tt.wantErr {
			assert.Equal(t, "invalid_input", errCode(t, err))
		} else {
			assert.NoError(t, err)
			assert.Equal(t, tt.want, got)
		}
	}
}

func (ts *ValidateTestSuite) TestIsValidURI() {
	t := ts.T()
	assert.True(t, isValidURI("https://example.com/logo.png"))
	assert.True(t, isValidURI("io.mosip.residentapp://logo"))
	assert.False(t, isValidURI(""))
	assert.False(t, isValidURI("not a uri"))
	assert.False(t, isValidURI("http://"))
	assert.False(t, isValidURI("https://"))
}

func (ts *ValidateTestSuite) TestHasUniqueStrings() {
	t := ts.T()
	assert.True(t, hasUniqueStrings([]string{"a", "b", "c"}))
	assert.False(t, hasUniqueStrings([]string{"a", "b", "a"}))
	assert.True(t, hasUniqueStrings(nil))
}

func (ts *ValidateTestSuite) TestContainsAll() {
	t := ts.T()
	allowed := map[string]struct{}{"a": {}, "b": {}}
	assert.True(t, containsAll([]string{"a", "b"}, allowed))
	assert.False(t, containsAll([]string{"a", "c"}, allowed))
	assert.True(t, containsAll(nil, allowed))
}

func (ts *ValidateTestSuite) TestIsAlpha() {
	t := ts.T()
	assert.True(t, isAlpha("eng"))
	assert.True(t, isAlpha("ENG"))
	assert.False(t, isAlpha("en1"))
	assert.False(t, isAlpha("e n"))
}

func (ts *ValidateTestSuite) TestIsValidRedirectURI() {
	t := ts.T()
	valid := []string{
		"http://localhost:5000/*",
		"http://127.0.0.1:5000/*",
		"http://192.168.1.10:5000/*",
		"http://localhost:5000/**",
		"http://localhost:3000/registration/*",
		"http://localhost:5000/test",
		"https://example.com/callback",
		"io.mosip.residentapp://oauth",
		"my.phone.app://oauth/*",
	}
	for _, uri := range valid {
		if !isValidRedirectURI(uri) {
			t.Errorf("expected %q to be a valid redirect URI", uri)
		}
	}

	invalid := []string{
		"",
		`\*`,
		"http*",
		"http*://example.com",
		"https://*",
		"https://domain*",
		"residentapp://*",
		"not a uri",
		"http://",
		"http://localhost:5000/foo*bar",
	}
	for _, uri := range invalid {
		if isValidRedirectURI(uri) {
			t.Errorf("expected %q to be an invalid redirect URI", uri)
		}
	}
}

func (ts *ValidateTestSuite) TestValidateRedirectURIsBounds() {
	t := ts.T()
	assert.Equal(t, "invalid_redirect_uri", errCode(t, validateRedirectURIs([]string{"a"}, 2, 0)))
	assert.Equal(t, "invalid_redirect_uri", errCode(t, validateRedirectURIs([]string{"a", "b"}, 0, 1)))
	assert.Equal(t, "invalid_redirect_uri",
		errCode(t, validateRedirectURIs([]string{"https://a.com", "https://a.com"}, 0, 0)))
	assert.NoError(t, validateRedirectURIs(nil, 0, 0))
}

func (ts *ValidateTestSuite) TestValidateClaimsBounds() {
	t := ts.T()
	assert.Equal(t, "invalid_claim", errCode(t, validateClaims([]string{"name"}, 2, 0)))
	assert.Equal(t, "invalid_claim", errCode(t, validateClaims([]string{"name", "email"}, 0, 1)))
	assert.NoError(t, validateClaims(nil, 0, 0))
}

func (ts *ValidateTestSuite) TestValidateACRsBounds() {
	t := ts.T()
	assert.Equal(t, "invalid_acr", errCode(t, validateACRs([]string{"mosip:idp:acr:password"}, allowedACRAll, 2, 0)))
	assert.Equal(t, "invalid_acr",
		errCode(t, validateACRs([]string{"mosip:idp:acr:password", "mosip:idp:acr:knowledge"}, allowedACRAll, 0, 1)))
	assert.Equal(t, "invalid_acr", errCode(t, validateACRs(
		[]string{"mosip:idp:acr:password", "mosip:idp:acr:password"}, allowedACRAll, 0, 0)))
}

func (ts *ValidateTestSuite) TestValidateGrantTypesBounds() {
	t := ts.T()
	assert.Equal(t, "invalid_grant_type", errCode(t, validateGrantTypes([]string{"authorization_code"}, 2, 0)))
	assert.Equal(t, "invalid_grant_type",
		errCode(t, validateGrantTypes([]string{"authorization_code", "authorization_code"}, 0, 1)))
	assert.Equal(t, "invalid_grant_type",
		errCode(t, validateGrantTypes([]string{"authorization_code", "authorization_code"}, 0, 0)))
}

func (ts *ValidateTestSuite) TestValidateAuthMethodsBounds() {
	t := ts.T()
	assert.Equal(t, "invalid_client_auth", errCode(t, validateAuthMethods([]string{"private_key_jwt"}, 2, 0)))
	assert.Equal(t, "invalid_client_auth",
		errCode(t, validateAuthMethods([]string{"private_key_jwt", "private_key_jwt"}, 0, 1)))
	assert.Equal(t, "invalid_client_auth",
		errCode(t, validateAuthMethods([]string{"private_key_jwt", "private_key_jwt"}, 0, 0)))
}

type ValidateTestSuite struct {
	suite.Suite
}

func TestValidateTestSuite(t *testing.T) {
	suite.Run(t, new(ValidateTestSuite))
}
