/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package clientmgmt

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/common"
)

func (ts *ModelTestSuite) TestResponseWrapper_errorResponseIsNull() {
	t := ts.T()
	body, err := json.Marshal(ResponseWrapper{
		ResponseWrapper: common.ResponseWrapper{
			Errors:       []common.Error{{ErrorCode: "invalid_grant_type", ErrorMessage: "invalid_grant_type"}},
			ResponseTime: time.Date(2026, 6, 29, 16, 20, 10, 980_000_000, time.UTC).Format(common.MOSIPTimeLayout),
		},
	})
	require.NoError(t, err)

	var decoded map[string]any
	require.NoError(t, json.Unmarshal(body, &decoded))
	assert.Nil(t, decoded["response"])
	assert.Equal(t, "2026-06-29T16:20:10.980Z", decoded["responseTime"])
}

func (ts *ModelTestSuite) TestValidationError_Error() {
	t := ts.T()
	assert.Equal(t, "invalid_input", (&ValidationError{Code: "invalid_input"}).Error())
	assert.Equal(t, "custom message", (&ValidationError{Code: "invalid_input", Message: "custom message"}).Error())
}

func (ts *ModelTestSuite) TestNullableJWK_UnmarshalJSON() {
	t := ts.T()
	t.Run("null", func(t *testing.T) {
		var n NullableJWK
		require.NoError(t, json.Unmarshal([]byte("null"), &n))
		assert.True(t, n.Defined)
		assert.True(t, n.IsNull)
		assert.Nil(t, n.Value)
	})

	t.Run("object", func(t *testing.T) {
		var n NullableJWK
		require.NoError(t, json.Unmarshal([]byte(`{"kty":"RSA"}`), &n))
		assert.True(t, n.Defined)
		assert.False(t, n.IsNull)
		assert.Equal(t, map[string]string{"kty": "RSA"}, n.Value)
	})

	t.Run("invalid json", func(t *testing.T) {
		var n NullableJWK
		assert.Error(t, json.Unmarshal([]byte(`"not-an-object"`), &n))
	})
}

func (ts *ModelTestSuite) TestClientResponse_APIResponse() {
	t := ts.T()
	resp := ClientResponse{
		ClientID: "c1", Status: "ACTIVE",
		ClientName: "should not leak", RpID: "rp1",
	}
	api := resp.APIResponse()
	assert.Equal(t, "c1", api.ClientID)
	assert.Equal(t, "ACTIVE", api.Status)
	assert.Empty(t, api.ClientName)
	assert.Empty(t, api.RpID)
}

func (ts *ModelTestSuite) TestDecodePatchRequest() {
	t := ts.T()
	t.Run("all fields", func(t *testing.T) {
		body := []byte(`{"request":{
			"clientName":"App",
			"clientNameLangMap":{"@none":"App"},
			"status":"ACTIVE",
			"logoUri":"https://example.com/logo.png",
			"redirectUris":["https://example.com/cb"],
			"userClaims":["name"],
			"authContextRefs":["mosip:idp:acr:static-code"],
			"grantTypes":["authorization_code"],
			"clientAuthMethods":["private_key_jwt"],
			"additionalConfig":{"userinfo_response_type":"JWS"},
			"encPublicKey":{"kty":"RSA"}
		}}`)
		req, fields, err := DecodePatchRequest(body)
		require.NoError(t, err)
		assert.True(t, fields.ClientName)
		assert.Equal(t, "App", req.ClientName)
		assert.True(t, fields.ClientNameLangMap)
		assert.True(t, fields.Status)
		assert.Equal(t, "ACTIVE", req.Status)
		assert.True(t, fields.LogoURI)
		assert.True(t, fields.RedirectURIs)
		assert.True(t, fields.Claims)
		assert.True(t, fields.AcrValues)
		assert.True(t, fields.GrantTypes)
		assert.True(t, fields.AuthMethods)
		assert.True(t, fields.AdditionalConfig)
		assert.True(t, fields.EncPublicKey)
		assert.Equal(t, map[string]string{"kty": "RSA"}, req.EncPublicKey.Value)
	})

	t.Run("encPublicKey null", func(t *testing.T) {
		req, fields, err := DecodePatchRequest([]byte(`{"request":{"encPublicKey":null}}`))
		require.NoError(t, err)
		assert.True(t, fields.EncPublicKey)
		assert.True(t, req.EncPublicKey.IsNull)
	})

	t.Run("no fields set", func(t *testing.T) {
		req, fields, err := DecodePatchRequest([]byte(`{"request":{}}`))
		require.NoError(t, err)
		assert.Equal(t, PatchFields{}, fields)
		assert.Equal(t, PatchClientRequest{}, req)
	})

	for _, field := range []string{"clientId", "relyingPartyId", "publicKey", "encPublicKeyCert", "unknownField"} {
		t.Run(field+" rejected", func(t *testing.T) {
			_, fields, err := DecodePatchRequest([]byte(`{"request":{"logoUri":"https://example.com/logo.png","` + field + `":"x"}}`))
			require.ErrorIs(t, err, ErrUnknownField)
			assert.Equal(t, `unknown field "`+field+`"`, err.Error())
			assert.Equal(t, PatchFields{}, fields)
		})
	}

	t.Run("envelope metadata ignored", func(t *testing.T) {
		_, fields, err := DecodePatchRequest([]byte(`{"id":"x","version":"1.0","requestTime":"t","request":{"status":"ACTIVE"}}`))
		require.NoError(t, err)
		assert.True(t, fields.Status)
	})

	t.Run("missing request key", func(t *testing.T) {
		_, _, err := DecodePatchRequest([]byte(`{}`))
		assert.Error(t, err)
	})

	t.Run("invalid top-level json", func(t *testing.T) {
		_, _, err := DecodePatchRequest([]byte(`not-json`))
		assert.Error(t, err)
	})

	t.Run("request not an object", func(t *testing.T) {
		_, _, err := DecodePatchRequest([]byte(`{"request":null}`))
		assert.Error(t, err)
	})

	t.Run("request field not an object", func(t *testing.T) {
		_, _, err := DecodePatchRequest([]byte(`{"request":"not-an-object"}`))
		assert.Error(t, err)
	})

	for _, tt := range []struct {
		name string
		body string
	}{
		{"clientName wrong type", `{"request":{"clientName":123}}`},
		{"clientNameLangMap wrong type", `{"request":{"clientNameLangMap":"x"}}`},
		{"status wrong type", `{"request":{"status":123}}`},
		{"logoUri wrong type", `{"request":{"logoUri":123}}`},
		{"redirectUris wrong type", `{"request":{"redirectUris":"x"}}`},
		{"userClaims wrong type", `{"request":{"userClaims":"x"}}`},
		{"authContextRefs wrong type", `{"request":{"authContextRefs":"x"}}`},
		{"grantTypes wrong type", `{"request":{"grantTypes":"x"}}`},
		{"clientAuthMethods wrong type", `{"request":{"clientAuthMethods":"x"}}`},
		{"encPublicKey wrong type", `{"request":{"encPublicKey":"x"}}`},
	} {
		t.Run(tt.name, func(t *testing.T) {
			_, _, err := DecodePatchRequest([]byte(tt.body))
			assert.Error(t, err)
		})
	}
}

func (ts *ModelTestSuite) TestDecodeUpdateRequest() {
	t := ts.T()
	t.Run("mutable fields decoded", func(t *testing.T) {
		req, err := DecodeUpdateRequest([]byte(`{"requestTime":"x","request":{"clientName":"App","status":"active","additionalConfig":{"a":1}}}`))
		require.NoError(t, err)
		assert.Equal(t, "App", req.ClientName)
		assert.Equal(t, "active", req.Status)
		assert.JSONEq(t, `{"a":1}`, string(req.AdditionalConfig))
	})

	t.Run("missing or null request", func(t *testing.T) {
		for _, body := range []string{`{}`, `{"request":null}`} {
			req, err := DecodeUpdateRequest([]byte(body))
			require.NoError(t, err)
			assert.Equal(t, UpdateClientRequest{}, req)
		}
	})

	t.Run("extensible additionalConfig and envelope metadata accepted", func(t *testing.T) {
		req, err := DecodeUpdateRequest([]byte(`{"id":"x","version":"1.0","request":{"additionalConfig":{"anyKey":{"nested":true}}}}`))
		require.NoError(t, err)
		assert.JSONEq(t, `{"anyKey":{"nested":true}}`, string(req.AdditionalConfig))
	})

	for _, field := range []string{"clientId", "relyingPartyId", "publicKey", "unknownField"} {
		t.Run(field+" rejected", func(t *testing.T) {
			_, err := DecodeUpdateRequest([]byte(`{"request":{"clientName":"App","` + field + `":"x"}}`))
			require.ErrorIs(t, err, ErrUnknownField)
			assert.Equal(t, `unknown field "`+field+`"`, err.Error())
		})
	}

	for _, tt := range []struct {
		name string
		body string
	}{
		{"invalid top-level json", `not-json`},
		{"request not an object", `{"request":"x"}`},
		{"field wrong type", `{"request":{"clientName":123}}`},
	} {
		t.Run(tt.name, func(t *testing.T) {
			_, err := DecodeUpdateRequest([]byte(tt.body))
			assert.Error(t, err)
		})
	}
}

func (ts *ModelTestSuite) TestDecodeCreateRequest() {
	t := ts.T()
	t.Run("all fields decoded", func(t *testing.T) {
		body, err := json.Marshal(map[string]any{"id": "x", "version": "1.0", "requestTime": "t", "request": validCreateRequest()})
		require.NoError(t, err)
		req, err := DecodeCreateRequest(body)
		require.NoError(t, err)
		assert.Equal(t, validCreateRequest(), req)
	})

	t.Run("extensible maps accept arbitrary keys", func(t *testing.T) {
		req, err := DecodeCreateRequest([]byte(`{"request":{"publicKey":{"kty":"RSA","kid":"k1"},"clientNameLangMap":{"fra":"App"},"additionalConfig":{"anyKey":1}}}`))
		require.NoError(t, err)
		assert.Equal(t, map[string]string{"kty": "RSA", "kid": "k1"}, req.PublicKey)
		assert.Equal(t, map[string]string{"fra": "App"}, req.ClientNameLangMap)
		assert.JSONEq(t, `{"anyKey":1}`, string(req.AdditionalConfig))
	})

	t.Run("missing or null request", func(t *testing.T) {
		for _, body := range []string{`{}`, `{"request":null}`} {
			req, err := DecodeCreateRequest([]byte(body))
			require.NoError(t, err)
			assert.Equal(t, CreateClientRequest{}, req)
		}
	})

	for _, field := range []string{"status", "unknownField"} {
		t.Run(field+" rejected", func(t *testing.T) {
			_, err := DecodeCreateRequest([]byte(`{"request":{"clientId":"c1","` + field + `":"x"}}`))
			require.ErrorIs(t, err, ErrUnknownField)
			assert.Equal(t, `unknown field "`+field+`"`, err.Error())
		})
	}

	for _, tt := range []struct {
		name string
		body string
	}{
		{"invalid top-level json", `not-json`},
		{"request not an object", `{"request":"x"}`},
		{"field wrong type", `{"request":{"clientId":123}}`},
	} {
		t.Run(tt.name, func(t *testing.T) {
			_, err := DecodeCreateRequest([]byte(tt.body))
			require.Error(t, err)
			assert.NotErrorIs(t, err, ErrUnknownField)
		})
	}
}

func (ts *ModelTestSuite) TestHasRequestObject() {
	t := ts.T()
	for _, tt := range []struct {
		name string
		body string
		want bool
	}{
		{"object present", `{"request":{"clientId":"c1"}}`, true},
		{"request missing", `{}`, false},
		{"request null", `{"request":null}`, false},
		{"invalid json", `not-json`, false},
	} {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.want, hasRequestObject([]byte(tt.body)))
		})
	}
}

func (ts *ModelTestSuite) TestResponseWrapper_successResponse() {
	t := ts.T()
	resp := ClientResponse{ClientID: "c1", Status: "ACTIVE"}
	body, err := json.Marshal(ResponseWrapper{
		Response:        resp.APIResponse(),
		ResponseWrapper: common.ResponseWrapper{ResponseTime: "2026-06-29T16:20:10.980Z"},
	})
	require.NoError(t, err)

	var decoded map[string]any
	require.NoError(t, json.Unmarshal(body, &decoded))
	response, ok := decoded["response"].(map[string]any)
	require.True(t, ok)
	assert.Equal(t, "c1", response["clientId"])
	assert.Equal(t, "ACTIVE", response["status"])
}

type ModelTestSuite struct {
	suite.Suite
}

func TestModelTestSuite(t *testing.T) {
	suite.Run(t, new(ModelTestSuite))
}
