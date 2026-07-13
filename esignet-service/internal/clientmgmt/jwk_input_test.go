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

func (ts *JwkInputTestSuite) TestCreateRequest_PublicKeyAsJWKObject() {
	t := ts.T()
	// Matches Java eSignet client-mgmt: publicKey is a JWK JSON object (not a string).
	body := `{
		"requestTime": "2026-06-29T16:20:10.980Z",
		"request": {
			"clientId": "U38vZUnWLQ3TqVyND-z0z8lnbom_uiMxsFxEsBEKlzM",
			"clientName": "Kemmer, Jacobi and Bradtke",
			"clientNameLangMap": {"eng": "Kemmer, Jacobi and Bradtke"},
			"publicKey": {"kty":"RSA","n":"hLKi9RkVn1WVIrHPODM0_vNfW--bfbPRuFUtIxc18JxR7tf39_30vd9NSBePhSd4VmBRs479IiuGAKfvBiFNkouk8lXGcdcQkuyEQtacXdi7phtYrXvwDaEyMWliEtjt3cr7caCU1VOupKyiIVS_ZujYQ1h8dNk09uYNngj7LdBZiRNeUtPKEQ8b-PzJb9DvjwD7Hqr26u11vZNoHh1AMpXyxO6Wq48qqHotuD3ewPRmGr-LzaWWc4trVpY7oGj3UNDZ2VIRDwvMRrTcJZ0zFu1z6Pn0bwrZxlSUHX6L_qP22cOXmIEFzg0OSNDdwaU9Tk2-69lYaQeXjRHbrIcH-w","e":"AQAB","kid":"6h6WQOY6JCd1msuooMtj80z_YqwJmWcjoQnL5xJkNVw","alg":"RS256","use":"sig"},
			"relyingPartyId": "mock-relying-party-id",
			"userClaims": ["name","email","gender","phone_number","picture","birthdate"],
			"authContextRefs": ["mosip:idp:acr:generated-code","mosip:idp:acr:password","mosip:idp:acr:linked-wallet","mosip:idp:acr:knowledge"],
			"logoUri": "http://placeimg.com/640/480",
			"redirectUris": ["http://localhost:5000/test","io.mosip.residentapp://oauth","http://localhost:5000/**","http://localhost:3000/registration/*"],
			"grantTypes": ["authorization_code"],
			"clientAuthMethods": ["private_key_jwt"],
			"additionalConfig": {
				"userinfo_response_type": "JWS",
				"purpose": {"type": "verify"},
				"signup_banner_required": true,
				"forgot_pwd_link_required": true,
				"consent_expire_in_mins": 20
			}
		}
	}`
	var wrapper CreateRequestWrapper
	require.NoError(t, json.Unmarshal([]byte(body), &wrapper))
	assert.Equal(t, "RSA", wrapper.Request.PublicKey["kty"])
	assert.Equal(t, "6h6WQOY6JCd1msuooMtj80z_YqwJmWcjoQnL5xJkNVw", wrapper.Request.PublicKey["kid"])

	require.NoError(t, ValidateCreate(ProfileClient, wrapper.Request))

	pkJSON, err := marshalJWK(wrapper.Request.PublicKey)
	require.NoError(t, err)
	assert.Contains(t, pkJSON, `"kid":"6h6WQOY6JCd1msuooMtj80z_YqwJmWcjoQnL5xJkNVw"`)
	assert.NotEmpty(t, hashJWK(wrapper.Request.PublicKey))
}

func (ts *JwkInputTestSuite) TestCreateRequest_PublicKeyStringRejected() {
	t := ts.T()
	body := `{"requestTime":"2026-06-29T16:20:10.980Z","request":{"clientId":"c1","clientName":"n","publicKey":"{\"kty\":\"RSA\",\"n\":\"abc\",\"e\":\"AQAB\"}","relyingPartyId":"rp","userClaims":["name"],"authContextRefs":["mosip:idp:acr:static-code"],"logoUri":"https://example.com/logo.png","grantTypes":["authorization_code"],"clientAuthMethods":["private_key_jwt"]}}`
	var wrapper CreateRequestWrapper
	err := json.Unmarshal([]byte(body), &wrapper)
	require.Error(t, err, "string-encoded publicKey should not be accepted; use a JWK object")
}

type JwkInputTestSuite struct {
	suite.Suite
}

func TestJwkInputTestSuite(t *testing.T) {
	suite.Run(t, new(JwkInputTestSuite))
}
