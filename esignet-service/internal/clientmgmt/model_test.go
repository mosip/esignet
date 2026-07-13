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

	t.Run("unknown field ignored", func(t *testing.T) {
		_, fields, err := DecodePatchRequest([]byte(`{"request":{"unknownField":"x"}}`))
		require.NoError(t, err)
		assert.Equal(t, PatchFields{}, fields)
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
