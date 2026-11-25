/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package clientmgmt

import (
	"encoding/json"
	"slices"
	"strings"
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
		PublicKey:    map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "kid": "key-1"},
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
		assert.NoError(t, ValidateCreate(ProfileOIDC, req, nil))
	})

	t.Run("oidc profile rejects client name lang map", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		assert.Equal(t, "invalid_input", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("oidc profile rejects additional config", func(t *testing.T) {
		req := validCreateRequest()
		req.AdditionalConfig = json.RawMessage(`{}`)
		assert.Equal(t, "invalid_input", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("non-oidc profile requires client name lang map", func(t *testing.T) {
		req := validCreateRequest()
		assert.Equal(t, "invalid_input", errCode(t, ValidateCreate(ProfileOAuth, req, nil)))
	})

	t.Run("oauth profile rejects additional config", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`{}`)
		assert.Equal(t, "invalid_input", errCode(t, ValidateCreate(ProfileOAuth, req, nil)))
	})

	t.Run("client profile with additional config", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`{"consent_expire_in_mins":30}`)
		assert.NoError(t, ValidateCreate(ProfileClient, req, nil))
	})

	t.Run("invalid client id", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientID = ""
		assert.Equal(t, "invalid_client_id", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("invalid client name", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientName = ""
		assert.Equal(t, "invalid_client_name", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("invalid rp id", func(t *testing.T) {
		req := validCreateRequest()
		req.RpID = ""
		assert.Equal(t, "invalid_rp_id", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("invalid logo uri", func(t *testing.T) {
		req := validCreateRequest()
		req.LogoURI = "not a uri"
		assert.Equal(t, "invalid_uri", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("empty redirect uris", func(t *testing.T) {
		req := validCreateRequest()
		req.RedirectURIs = nil
		assert.Equal(t, "invalid_redirect_uri", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("invalid redirect uri", func(t *testing.T) {
		req := validCreateRequest()
		req.RedirectURIs = []string{"not a uri"}
		assert.Equal(t, "invalid_redirect_uri", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("empty claims", func(t *testing.T) {
		req := validCreateRequest()
		req.Claims = nil
		assert.Equal(t, "invalid_claim", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("invalid claims", func(t *testing.T) {
		req := validCreateRequest()
		req.Claims = []string{"not_allowed"}
		assert.Equal(t, "invalid_claim", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("empty acr values", func(t *testing.T) {
		req := validCreateRequest()
		req.AcrValues = nil
		assert.Equal(t, "invalid_acr", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("invalid acr values", func(t *testing.T) {
		req := validCreateRequest()
		req.AcrValues = []string{"not_allowed"}
		assert.Equal(t, "invalid_acr", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("invalid public key", func(t *testing.T) {
		req := validCreateRequest()
		req.PublicKey = nil
		assert.Equal(t, "invalid_public_key", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("public key without kid rejected", func(t *testing.T) {
		req := validCreateRequest()
		req.PublicKey = map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB"}
		assert.Equal(t, "invalid_public_key", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("empty grant types", func(t *testing.T) {
		req := validCreateRequest()
		req.GrantTypes = nil
		assert.Equal(t, "invalid_grant_type", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("invalid grant types", func(t *testing.T) {
		req := validCreateRequest()
		req.GrantTypes = []string{"implicit"}
		assert.Equal(t, "invalid_grant_type", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("refresh_token and token_exchange grant types accepted", func(t *testing.T) {
		req := validCreateRequest()
		req.GrantTypes = []string{"authorization_code", "refresh_token", "token_exchange"}
		assert.NoError(t, ValidateCreate(ProfileOIDC, req, nil))
	})

	t.Run("empty auth methods", func(t *testing.T) {
		req := validCreateRequest()
		req.AuthMethods = nil
		assert.Equal(t, "invalid_client_auth", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("invalid auth methods", func(t *testing.T) {
		req := validCreateRequest()
		req.AuthMethods = []string{"client_secret_basic"}
		assert.Equal(t, "invalid_client_auth", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("client profile invalid additional config", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`not-json`)
		assert.Equal(t, "invalid_additional_config", errCode(t, ValidateCreate(ProfileClient, req, nil)))
	})

	t.Run("client profile jwe userinfo response type without enc public key rejected", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`{"userinfo_response_type":"JWE"}`)
		assert.Equal(t, "invalid_additional_config", errCode(t, ValidateCreate(ProfileClient, req, nil)))
	})

	t.Run("client profile jwe userinfo response type with enc public key accepted", func(t *testing.T) {
		req := validCreateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`{"userinfo_response_type":"JWE"}`)
		req.EncPublicKey = map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "alg": "RSA-OAEP-256", "kid": "enc-1"}
		assert.NoError(t, ValidateCreate(ProfileClient, req, nil))
	})

	t.Run("invalid enc public key", func(t *testing.T) {
		req := validCreateRequest()
		req.EncPublicKey = map[string]string{"kty": "bogus"}
		assert.Equal(t, "invalid_public_key", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("enc public key missing alg", func(t *testing.T) {
		req := validCreateRequest()
		req.EncPublicKey = map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "kid": "enc-1"}
		assert.Equal(t, "invalid_public_key", errCode(t, ValidateCreate(ProfileOIDC, req, nil)))
	})

	t.Run("enc public key with alg accepted", func(t *testing.T) {
		req := validCreateRequest()
		req.EncPublicKey = map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "alg": "RSA-OAEP-256", "kid": "enc-1"}
		assert.NoError(t, ValidateCreate(ProfileOIDC, req, nil))
	})

	t.Run("enc public key alg not in supported list rejected", func(t *testing.T) {
		req := validCreateRequest()
		req.EncPublicKey = map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "alg": "RSA-OAEP", "kid": "enc-1"}
		err := ValidateCreate(ProfileOIDC, req, []string{"RSA-OAEP-256", "AES-GCM"})
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})

	t.Run("enc public key alg in supported list accepted", func(t *testing.T) {
		req := validCreateRequest()
		req.EncPublicKey = map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "alg": "RSA-OAEP-256", "kid": "enc-1"}
		assert.NoError(t, ValidateCreate(ProfileOIDC, req, []string{"RSA-OAEP-256", "AES-GCM"}))
	})
}

func (ts *ValidateTestSuite) TestValidateUpdate() {
	t := ts.T()
	t.Run("valid oidc profile", func(t *testing.T) {
		req := validUpdateRequest()
		assert.NoError(t, ValidateUpdate(ProfileOIDC, req, false))
	})

	t.Run("oidc profile rejects client name lang map", func(t *testing.T) {
		req := validUpdateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		assert.Equal(t, "invalid_input", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("non-oidc requires client name lang map", func(t *testing.T) {
		req := validUpdateRequest()
		assert.Equal(t, "invalid_input", errCode(t, ValidateUpdate(ProfileOAuth, req, false)))
	})

	t.Run("invalid status", func(t *testing.T) {
		req := validUpdateRequest()
		req.Status = "bogus"
		assert.Equal(t, "invalid_input", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("empty claims below minimum", func(t *testing.T) {
		req := validUpdateRequest()
		req.Claims = nil
		assert.Equal(t, "invalid_claim", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("oidc acr must be in restricted set", func(t *testing.T) {
		req := validUpdateRequest()
		req.AcrValues = []string{"mosip:idp:acr:password"}
		assert.Equal(t, "invalid_acr", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("client profile additional config", func(t *testing.T) {
		req := validUpdateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`{"dpop_bound_access_tokens":true}`)
		assert.NoError(t, ValidateUpdate(ProfileClient, req, false))
	})

	t.Run("oauth profile rejects additional config", func(t *testing.T) {
		req := validUpdateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`{}`)
		assert.Equal(t, "invalid_input", errCode(t, ValidateUpdate(ProfileOAuth, req, false)))
	})

	t.Run("invalid client name", func(t *testing.T) {
		req := validUpdateRequest()
		req.ClientName = ""
		assert.Equal(t, "invalid_client_name", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("invalid logo uri", func(t *testing.T) {
		req := validUpdateRequest()
		req.LogoURI = "not a uri"
		assert.Equal(t, "invalid_uri", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("empty redirect uris", func(t *testing.T) {
		req := validUpdateRequest()
		req.RedirectURIs = nil
		assert.Equal(t, "invalid_redirect_uri", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("invalid redirect uri", func(t *testing.T) {
		req := validUpdateRequest()
		req.RedirectURIs = []string{"not a uri"}
		assert.Equal(t, "invalid_redirect_uri", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("invalid claims", func(t *testing.T) {
		req := validUpdateRequest()
		req.Claims = []string{"not_allowed"}
		assert.Equal(t, "invalid_claim", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("empty acr values", func(t *testing.T) {
		req := validUpdateRequest()
		req.AcrValues = nil
		assert.Equal(t, "invalid_acr", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("empty grant types", func(t *testing.T) {
		req := validUpdateRequest()
		req.GrantTypes = nil
		assert.Equal(t, "invalid_grant_type", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("invalid grant types", func(t *testing.T) {
		req := validUpdateRequest()
		req.GrantTypes = []string{"implicit"}
		assert.Equal(t, "invalid_grant_type", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("empty auth methods", func(t *testing.T) {
		req := validUpdateRequest()
		req.AuthMethods = nil
		assert.Equal(t, "invalid_client_auth", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("invalid auth methods", func(t *testing.T) {
		req := validUpdateRequest()
		req.AuthMethods = []string{"client_secret_basic"}
		assert.Equal(t, "invalid_client_auth", errCode(t, ValidateUpdate(ProfileOIDC, req, false)))
	})

	t.Run("client profile invalid additional config", func(t *testing.T) {
		req := validUpdateRequest()
		req.ClientNameLangMap = map[string]string{"eng": "x"}
		req.AdditionalConfig = json.RawMessage(`not-json`)
		assert.Equal(t, "invalid_additional_config", errCode(t, ValidateUpdate(ProfileClient, req, false)))
	})
}

func (ts *ValidateTestSuite) TestValidatePatch() {
	t := ts.T()

	t.Run("supplied names and status", func(t *testing.T) {
		req, err := ValidatePatch(PatchClientRequest{
			ClientName: "Updated", ClientNameLangMap: map[string]string{"eng": "Updated"}, Status: "inactive",
		}, PatchFields{ClientName: true, ClientNameLangMap: true, Status: true}, nil, false, nil)
		assert.NoError(t, err)
		assert.Equal(t, "INACTIVE", req.Status)
	})

	for _, test := range []struct {
		name   string
		req    PatchClientRequest
		fields PatchFields
		code   string
	}{
		{"empty client name", PatchClientRequest{ClientName: ""}, PatchFields{ClientName: true}, "invalid_client_name"},
		{"invalid language code", PatchClientRequest{ClientNameLangMap: map[string]string{"xx": "value"}}, PatchFields{ClientNameLangMap: true}, "invalid_language_code"},
		{"nil language map", PatchClientRequest{}, PatchFields{ClientNameLangMap: true}, "invalid_input"},
		{"long language value", PatchClientRequest{ClientNameLangMap: map[string]string{"eng": strings.Repeat("a", 51)}}, PatchFields{ClientNameLangMap: true}, "invalid_client_name_value"},
		{"invalid status", PatchClientRequest{Status: "bogus"}, PatchFields{Status: true}, "invalid_input"},
		{"invalid logo", PatchClientRequest{LogoURI: "not-a-uri"}, PatchFields{LogoURI: true}, "invalid_uri"},
		{"empty redirects", PatchClientRequest{RedirectURIs: []string{}}, PatchFields{RedirectURIs: true}, "invalid_redirect_uri"},
		{"too many redirects", PatchClientRequest{RedirectURIs: []string{"https://a.example", "https://b.example", "https://c.example", "https://d.example", "https://e.example", "https://f.example"}}, PatchFields{RedirectURIs: true}, "invalid_redirect_uri"},
		{"duplicate redirects", PatchClientRequest{RedirectURIs: []string{"https://a.example", "https://a.example"}}, PatchFields{RedirectURIs: true}, "invalid_redirect_uri"},
		{"invalid redirect", PatchClientRequest{RedirectURIs: []string{"not a uri"}}, PatchFields{RedirectURIs: true}, "invalid_redirect_uri"},
		{"empty claims", PatchClientRequest{Claims: []string{}}, PatchFields{Claims: true}, "invalid_claim"},
		{"too many claims", PatchClientRequest{Claims: slices.Repeat([]string{"name"}, 31)}, PatchFields{Claims: true}, "invalid_claim"},
		{"invalid claim", PatchClientRequest{Claims: []string{"unknown"}}, PatchFields{Claims: true}, "invalid_claim"},
		{"empty acr values", PatchClientRequest{AcrValues: []string{}}, PatchFields{AcrValues: true}, "invalid_acr"},
		{"too many acr values", PatchClientRequest{AcrValues: slices.Repeat([]string{"mosip:idp:acr:static-code"}, 31)}, PatchFields{AcrValues: true}, "invalid_acr"},
		{"duplicate acr values", PatchClientRequest{AcrValues: []string{"mosip:idp:acr:static-code", "mosip:idp:acr:static-code"}}, PatchFields{AcrValues: true}, "invalid_acr"},
		{"invalid acr value", PatchClientRequest{AcrValues: []string{"unknown"}}, PatchFields{AcrValues: true}, "invalid_acr"},
		{"empty grants", PatchClientRequest{GrantTypes: []string{}}, PatchFields{GrantTypes: true}, "invalid_grant_type"},
		{"too many grants", PatchClientRequest{GrantTypes: slices.Repeat([]string{"authorization_code"}, 4)}, PatchFields{GrantTypes: true}, "invalid_grant_type"},
		{"invalid grant", PatchClientRequest{GrantTypes: []string{"client_credentials"}}, PatchFields{GrantTypes: true}, "invalid_grant_type"},
		{"empty auth methods", PatchClientRequest{AuthMethods: []string{}}, PatchFields{AuthMethods: true}, "invalid_client_auth"},
		{"too many auth methods", PatchClientRequest{AuthMethods: slices.Repeat([]string{"private_key_jwt"}, 4)}, PatchFields{AuthMethods: true}, "invalid_client_auth"},
		{"invalid auth method", PatchClientRequest{AuthMethods: []string{"client_secret_basic"}}, PatchFields{AuthMethods: true}, "invalid_client_auth"},
		{"invalid config", PatchClientRequest{AdditionalConfig: json.RawMessage(`not-json`)}, PatchFields{AdditionalConfig: true}, "invalid_additional_config"},
		{"jwe without key", PatchClientRequest{AdditionalConfig: json.RawMessage(`{"userinfo_response_type":"JWE"}`)}, PatchFields{AdditionalConfig: true}, "invalid_additional_config"},
		{"invalid key", PatchClientRequest{EncPublicKey: NullableJWK{Value: map[string]string{"kty": "bogus"}}}, PatchFields{EncPublicKey: true}, "invalid_public_key"},
		{"missing key alg", PatchClientRequest{EncPublicKey: NullableJWK{Value: map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "kid": "enc-1"}}}, PatchFields{EncPublicKey: true}, "invalid_public_key"},
		{"clear key retaining jwe", PatchClientRequest{EncPublicKey: NullableJWK{IsNull: true}}, PatchFields{EncPublicKey: true}, "invalid_additional_config"},
	} {
		t.Run(test.name, func(t *testing.T) {
			var retained json.RawMessage
			if test.name == "clear key retaining jwe" {
				retained = json.RawMessage(`{"id_token_response_type":"JWE"}`)
			}
			_, err := ValidatePatch(test.req, test.fields, nil, false, retained)
			assert.Equal(t, test.code, errCode(t, err))
		})
	}

	t.Run("accepted list boundaries", func(t *testing.T) {
		for _, test := range []struct {
			req    PatchClientRequest
			fields PatchFields
		}{
			{PatchClientRequest{RedirectURIs: []string{"https://a.example"}}, PatchFields{RedirectURIs: true}},
			{PatchClientRequest{Claims: slices.Repeat([]string{"name"}, 30)}, PatchFields{Claims: true}},
			{PatchClientRequest{AcrValues: []string{"mosip:idp:acr:static-code"}}, PatchFields{AcrValues: true}},
			{PatchClientRequest{GrantTypes: []string{"authorization_code"}}, PatchFields{GrantTypes: true}},
			{PatchClientRequest{AuthMethods: []string{"private_key_jwt"}}, PatchFields{AuthMethods: true}},
		} {
			_, err := ValidatePatch(test.req, test.fields, nil, false, nil)
			assert.NoError(t, err)
		}
	})

	t.Run("supported key algorithm", func(t *testing.T) {
		_, err := ValidatePatch(PatchClientRequest{EncPublicKey: NullableJWK{Value: validJWK()}},
			PatchFields{EncPublicKey: true}, []string{"RSA-OAEP"}, false, nil)
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})

	t.Run("jwe with supplied key", func(t *testing.T) {
		_, err := ValidatePatch(PatchClientRequest{
			AdditionalConfig: json.RawMessage(`{"userinfo_response_type":"JWE"}`),
			EncPublicKey:     NullableJWK{Value: validJWK()},
		}, PatchFields{AdditionalConfig: true, EncPublicKey: true}, nil, false, nil)
		assert.NoError(t, err)
	})

	t.Run("jwe with stored key", func(t *testing.T) {
		_, err := ValidatePatch(PatchClientRequest{AdditionalConfig: json.RawMessage(`{"userinfo_response_type":"JWE"}`)},
			PatchFields{AdditionalConfig: true}, nil, true, nil)
		assert.NoError(t, err)
	})

	t.Run("clear key and replace configuration", func(t *testing.T) {
		_, err := ValidatePatch(PatchClientRequest{
			AdditionalConfig: json.RawMessage(`{"userinfo_response_type":"JWS"}`),
			EncPublicKey:     NullableJWK{IsNull: true},
		}, PatchFields{AdditionalConfig: true, EncPublicKey: true}, nil, true,
			json.RawMessage(`{"userinfo_response_type":"JWE"}`))
		assert.NoError(t, err)
	})

	t.Run("unrelated change ignores retained config", func(t *testing.T) {
		_, err := ValidatePatch(PatchClientRequest{LogoURI: "https://example.com/new.png"},
			PatchFields{LogoURI: true}, nil, false, json.RawMessage(`{"userinfo_response_type":"JWE"}`))
		assert.NoError(t, err)
	})

	t.Run("key replacement ignores retained config", func(t *testing.T) {
		_, err := ValidatePatch(PatchClientRequest{EncPublicKey: NullableJWK{Value: validJWK()}},
			PatchFields{EncPublicKey: true}, nil, false, json.RawMessage(`{"userinfo_response_type":"JWE"}`))
		assert.NoError(t, err)
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
		err := validateAdditionalConfig(json.RawMessage(`not-json`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("valid userinfo_response_type JWS", func(t *testing.T) {
		assert.NoError(t, validateAdditionalConfig(json.RawMessage(`{"userinfo_response_type":"JWS"}`), false))
	})

	t.Run("userinfo_response_type JWE without enc public key rejected", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"userinfo_response_type":"JWE"}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("userinfo_response_type JWE with enc public key accepted", func(t *testing.T) {
		assert.NoError(t, validateAdditionalConfig(json.RawMessage(`{"userinfo_response_type":"JWE"}`), true))
	})

	t.Run("invalid userinfo_response_type", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"userinfo_response_type":"XML"}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("valid id_token_response_type JWS", func(t *testing.T) {
		assert.NoError(t, validateAdditionalConfig(json.RawMessage(`{"id_token_response_type":"JWS"}`), false))
	})

	t.Run("id_token_response_type JWE without enc public key rejected", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"id_token_response_type":"JWE"}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("id_token_response_type JWE with enc public key accepted", func(t *testing.T) {
		assert.NoError(t, validateAdditionalConfig(json.RawMessage(`{"id_token_response_type":"JWE"}`), true))
	})

	t.Run("invalid id_token_response_type", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"id_token_response_type":"XML"}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("consent_expire_in_mins too low", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"consent_expire_in_mins":5}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("bool fields validated", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"signup_banner_required":"not-a-bool"}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("valid require_pkce", func(t *testing.T) {
		assert.NoError(t, validateAdditionalConfig(json.RawMessage(`{"require_pkce":true}`), false))
	})

	t.Run("invalid require_pkce", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"require_pkce":"not-a-bool"}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("unknown key rejected", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"unknown_field":true}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("valid allowed_authorization_scopes", func(t *testing.T) {
		assert.NoError(t, validateAdditionalConfig(json.RawMessage(`{"allowed_authorization_scopes":["custom_scope","other_scope"]}`), false))
	})

	t.Run("empty allowed_authorization_scopes accepted", func(t *testing.T) {
		assert.NoError(t, validateAdditionalConfig(json.RawMessage(`{"allowed_authorization_scopes":[]}`), false))
	})

	t.Run("duplicate allowed_authorization_scopes rejected", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"allowed_authorization_scopes":["custom_scope","custom_scope"]}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("blank allowed_authorization_scopes entry rejected", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"allowed_authorization_scopes":["custom_scope"," "]}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("non-array allowed_authorization_scopes rejected", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"allowed_authorization_scopes":"not-an-array"}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("valid allowed_child_clients", func(t *testing.T) {
		assert.NoError(t, validateAdditionalConfig(json.RawMessage(`{"allowed_child_clients":["health-app","tax-app"]}`), false))
	})

	t.Run("empty allowed_child_clients rejected", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"allowed_child_clients":[]}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("duplicate allowed_child_clients rejected", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"allowed_child_clients":["health-app","health-app"]}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("blank allowed_child_clients entry rejected", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"allowed_child_clients":["health-app"," "]}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("non-array allowed_child_clients rejected", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"allowed_child_clients":"health-app"}`), false)
		assert.Equal(t, "invalid_additional_config", errCode(t, err))
	})

	t.Run("purpose validated", func(t *testing.T) {
		assert.NoError(t, validateAdditionalConfig(json.RawMessage(
			`{"purpose":{"type":"consent","title":{"@none":"Title"}}}`), false))
	})

	t.Run("invalid purpose propagates", func(t *testing.T) {
		err := validateAdditionalConfig(json.RawMessage(`{"purpose":{}}`), false)
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

func (ts *ValidateTestSuite) TestIsValidLogoURI() {
	t := ts.T()
	for _, uri := range []string{
		"https://example.com/logo.png",
		"http://localhost:5000/images/logo.png",
		"HTTPS://example.com/logo.png",
		"https://example.com/" + strings.Repeat("a", 1004),
	} {
		assert.True(t, isValidLogoURI(uri), uri)
	}

	for _, uri := range []string{
		"",
		"not a uri",
		"notauri",
		"http://",
		"https://",
		"/images/logo.png",
		"javascript:alert(1)",
		"JavaScript:alert(1)",
		"javascript://example.com/%0aalert(1)",
		"data:text/html,<script>alert(1)</script>",
		"vbscript:msgbox(1)",
		"file:///etc/passwd",
		"file:etc/passwd",
		"mailto:a@b.com",
		"io.mosip.residentapp://logo",
		"https://example.com/" + strings.Repeat("a", 1005),
	} {
		assert.False(t, isValidLogoURI(uri), uri)
	}
}

func (ts *ValidateTestSuite) TestValidateLogoURIErrorCode() {
	t := ts.T()
	assert.NoError(t, validateLogoURI("https://example.com/logo.png"))
	assert.Equal(t, "invalid_uri", errCode(t, validateLogoURI("javascript:alert(1)")))
	assert.Equal(t, "invalid_uri", errCode(t, validateLogoURI("data:text/html,<script>alert(1)</script>")))
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

func (ts *ValidateTestSuite) TestValidateGrantTypes_TokenExchangeIsSupported() {
	t := ts.T()
	assert.NoError(t, validateGrantTypes([]string{"authorization_code", "token_exchange"}, 0, 0))
	assert.NoError(t, validateGrantTypes([]string{"token_exchange"}, 1, 0))
	assert.Equal(t, "invalid_grant_type", errCode(t, validateGrantTypes([]string{"implicit"}, 0, 0)))
	assert.Equal(t, "invalid_grant_type",
		errCode(t, validateGrantTypes([]string{"urn:ietf:params:oauth:grant-type:token-exchange"}, 0, 0)),
		"clients register the short name; the URN is the engine identifier")
}
