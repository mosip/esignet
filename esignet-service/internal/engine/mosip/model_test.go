/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

func (ts *ModelTestSuite) TestIdaKycAuthRequestClaimsMetadataRequiredRoundTrips() {
	t := ts.T()
	claimsRequired := false
	req := IdaKycAuthRequest{
		ID:                     mosipKycAuthRequestID,
		ConsentObtained:        true,
		ClaimsMetadataRequired: &claimsRequired,
	}

	b, err := json.Marshal(req)
	require.NoError(t, err)

	var decoded IdaKycAuthRequest
	require.NoError(t, json.Unmarshal(b, &decoded))
	require.NotNil(t, decoded.ClaimsMetadataRequired)
	require.False(t, *decoded.ClaimsMetadataRequired)
	require.True(t, decoded.ConsentObtained)
}

func (ts *ModelTestSuite) TestIdaKycAuthRequestClaimsMetadataRequiredOmittedWhenNil() {
	t := ts.T()
	req := IdaKycAuthRequest{ID: mosipKycAuthRequestID}

	b, err := json.Marshal(req)
	require.NoError(t, err)
	require.NotContains(t, string(b), "claimsMetadataRequired")
}

func (ts *ModelTestSuite) TestIdaResponseWrapperDecodesSuccessAndErrorShapes() {
	t := ts.T()
	successJSON := `{"id":"x","response":{"kycToken":"tok","authToken":"auth","kycStatus":true}}`
	var success IdaResponseWrapper
	require.NoError(t, json.Unmarshal([]byte(successJSON), &success))
	require.NotNil(t, success.Response)
	require.True(t, success.Response.KycStatus)
	require.Equal(t, "tok", success.Response.KycToken)

	errJSON := `{"id":"x","errors":[{"errorCode":"E1","errorMessage":"bad","actionMessage":"retry"}]}`
	var withErrors IdaResponseWrapper
	require.NoError(t, json.Unmarshal([]byte(errJSON), &withErrors))
	require.Nil(t, withErrors.Response)
	require.Len(t, withErrors.Errors, 1)
	require.Equal(t, "E1", withErrors.Errors[0].ErrorCode)
	require.Equal(t, "retry", withErrors.Errors[0].ActionMessage)
}

func (ts *ModelTestSuite) TestIdaSendOtpResponseDecodesSuccessAndErrorShapes() {
	t := ts.T()
	successJSON := `{"id":"x","response":{"maskedEmail":"a***@b.com","maskedMobile":"***1234"}}`
	var success IdaSendOtpResponse
	require.NoError(t, json.Unmarshal([]byte(successJSON), &success))
	require.NotNil(t, success.Response)
	require.Equal(t, "a***@b.com", success.Response.MaskedEmail)

	errJSON := `{"id":"x","errors":[{"errorCode":"E2","errorMessage":"boom"}]}`
	var withErrors IdaSendOtpResponse
	require.NoError(t, json.Unmarshal([]byte(errJSON), &withErrors))
	require.Nil(t, withErrors.Response)
	require.Len(t, withErrors.Errors, 1)
	require.Equal(t, "boom", withErrors.Errors[0].ErrorMessage)
}

func (ts *ModelTestSuite) TestIdaKycExchangeResponseWrapperRoundTrips() {
	t := ts.T()
	wrapper := IdaKycExchangeResponseWrapper{
		ID:      "x",
		Version: mosipRequestVersion,
		Response: &IdaKycExchangeResponse{
			EncryptedKyc: "header.payload.sig",
		},
	}
	b, err := json.Marshal(wrapper)
	require.NoError(t, err)

	var decoded IdaKycExchangeResponseWrapper
	require.NoError(t, json.Unmarshal(b, &decoded))
	require.NotNil(t, decoded.Response)
	require.Equal(t, "header.payload.sig", decoded.Response.EncryptedKyc)
}

func (ts *ModelTestSuite) TestAuditRequestWrapperGenericOmitsVersionWhenEmpty() {
	t := ts.T()
	wrapper := AuditRequestWrapper[ClientIDSecretKeyRequest]{
		ID:          "ida",
		RequestTime: "2024-01-01T00:00:00.000Z",
		Request:     ClientIDSecretKeyRequest{ClientID: "c", SecretKey: "s", AppID: "a"},
	}
	b, err := json.Marshal(wrapper)
	require.NoError(t, err)
	require.NotContains(t, string(b), `"version"`)
	require.Contains(t, string(b), `"clientId":"c"`)
}

func (ts *ModelTestSuite) TestAuditResponseWrapperDecodesErrors() {
	t := ts.T()
	var wrapper AuditResponseWrapper
	require.NoError(t, json.Unmarshal([]byte(`{"errors":[{"errorCode":"KER-ATH-401","message":"unauthorized"}]}`), &wrapper))
	require.Len(t, wrapper.Errors, 1)
	require.Equal(t, "KER-ATH-401", wrapper.Errors[0].ErrorCode)
	require.Equal(t, "unauthorized", wrapper.Errors[0].Message)
}

type ModelTestSuite struct {
	suite.Suite
}

func TestModelTestSuite(t *testing.T) {
	suite.Run(t, new(ModelTestSuite))
}
