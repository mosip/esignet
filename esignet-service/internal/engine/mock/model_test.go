/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mock

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

func (ts *ModelTestSuite) TestKycAuthResponseDtoV2RoundTrip() {
	t := ts.T()
	raw := []byte(`{"authStatus":true,"kycToken":"tok-1","partnerSpecificUserToken":"psut-1"}`)

	var resp KycAuthResponseDtoV2
	require.NoError(t, json.Unmarshal(raw, &resp))
	require.True(t, resp.AuthStatus)
	require.Equal(t, "tok-1", resp.KycToken)
	require.Equal(t, "psut-1", resp.PartnerSpecificUserToken)
}

func (ts *ModelTestSuite) TestResponseWrapperDecodesGenericResponseAndErrors() {
	t := ts.T()

	t.Run("with response", func(t *testing.T) {
		raw := []byte(`{"id":"req-1","response":{"maskedEmail":"j***@example.com"}}`)
		var wrapper ResponseWrapper[SendOtpResult]
		require.NoError(t, json.Unmarshal(raw, &wrapper))
		require.NotNil(t, wrapper.Response)
		require.Equal(t, "j***@example.com", wrapper.Response.MaskedEmail)
		require.Empty(t, wrapper.Errors)
	})

	t.Run("with errors", func(t *testing.T) {
		raw := []byte(`{"errors":[{"errorCode":"IDA-001","message":"failed"}]}`)
		var wrapper ResponseWrapper[SendOtpResult]
		require.NoError(t, json.Unmarshal(raw, &wrapper))
		require.Nil(t, wrapper.Response)
		require.Len(t, wrapper.Errors, 1)
		require.Equal(t, "IDA-001", wrapper.Errors[0].ErrorCode)
		require.Equal(t, "failed", wrapper.Errors[0].Message)
	})
}

func (ts *ModelTestSuite) TestKycAuthRequestDtoMarshalsOnlySetFields() {
	t := ts.T()
	req := KycAuthRequestDto{TransactionID: "txn-1", IndividualID: "ind-1", Otp: "111111"}

	data, err := json.Marshal(req)
	require.NoError(t, err)

	var decoded map[string]interface{}
	require.NoError(t, json.Unmarshal(data, &decoded))
	require.Equal(t, "111111", decoded["otp"])
	_, hasPassword := decoded["password"]
	require.False(t, hasPassword)
	_, hasPin := decoded["pin"]
	require.False(t, hasPin)
}

type ModelTestSuite struct {
	suite.Suite
}

func TestModelTestSuite(t *testing.T) {
	suite.Run(t, new(ModelTestSuite))
}
