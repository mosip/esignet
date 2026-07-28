/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/config"
)

func (ts *CaptchaProviderTestSuite) TestCaptchaProvider_Verify_EmptyToken() {
	p := NewCaptchaProvider(&config.CaptchaConfig{ValidatorURL: "http://example.invalid/validate"}, http.DefaultClient)

	result, svcErr := p.Verify(context.Background(), "   ")
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), result)
	require.False(ts.T(), result.Success)
}

func (ts *CaptchaProviderTestSuite) TestCaptchaProvider_Verify_NotConfigured() {
	p := NewCaptchaProvider(&config.CaptchaConfig{}, http.DefaultClient)

	result, svcErr := p.Verify(context.Background(), "some-token")
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), result)
	require.True(ts.T(), result.Success)
}

func (ts *CaptchaProviderTestSuite) TestCaptchaProvider_Verify_Success() {
	var gotBody captchaRequestWrapper
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		require.Equal(ts.T(), http.MethodPost, r.Method)
		require.Equal(ts.T(), "application/json", r.Header.Get("Content-Type"))
		require.NoError(ts.T(), json.NewDecoder(r.Body).Decode(&gotBody))
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(captchaResponse{Response: "success"})
	}))
	defer server.Close()

	p := NewCaptchaProvider(&config.CaptchaConfig{ValidatorURL: server.URL, ModuleName: "esignet"}, server.Client())

	result, svcErr := p.Verify(context.Background(), "valid-token")
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), result)
	require.True(ts.T(), result.Success)
	require.NotEmpty(ts.T(), gotBody.RequestTime)
	require.Equal(ts.T(), "esignet", gotBody.Request.ModuleName)
	require.Equal(ts.T(), "valid-token", gotBody.Request.CaptchaToken)
}

func (ts *CaptchaProviderTestSuite) TestCaptchaProvider_Verify_ServiceReportsErrors() {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(captchaResponse{
			Errors: []captchaServiceError{{ErrorCode: "invalid_token", Message: "token rejected"}},
		})
	}))
	defer server.Close()

	p := NewCaptchaProvider(&config.CaptchaConfig{ValidatorURL: server.URL}, server.Client())

	result, svcErr := p.Verify(context.Background(), "bad-token")
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), result)
	require.False(ts.T(), result.Success)
}

func (ts *CaptchaProviderTestSuite) TestCaptchaProvider_Verify_NoResponseBody() {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(captchaResponse{})
	}))
	defer server.Close()

	p := NewCaptchaProvider(&config.CaptchaConfig{ValidatorURL: server.URL}, server.Client())

	result, svcErr := p.Verify(context.Background(), "bad-token")
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), result)
	require.False(ts.T(), result.Success)
}

func (ts *CaptchaProviderTestSuite) TestCaptchaProvider_Verify_NonOKStatus() {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	p := NewCaptchaProvider(&config.CaptchaConfig{ValidatorURL: server.URL}, server.Client())

	result, svcErr := p.Verify(context.Background(), "some-token")
	require.Nil(ts.T(), result)
	require.NotNil(ts.T(), svcErr)
	require.Equal(ts.T(), "captcha_provider_error", svcErr.Code)
}

func (ts *CaptchaProviderTestSuite) TestCaptchaProvider_Verify_MalformedResponseBody() {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte("not-json"))
	}))
	defer server.Close()

	p := NewCaptchaProvider(&config.CaptchaConfig{ValidatorURL: server.URL}, server.Client())

	result, svcErr := p.Verify(context.Background(), "some-token")
	require.Nil(ts.T(), result)
	require.NotNil(ts.T(), svcErr)
	require.Equal(ts.T(), "captcha_provider_error", svcErr.Code)
}

func (ts *CaptchaProviderTestSuite) TestCaptchaProvider_Verify_TransportFailure() {
	p := NewCaptchaProvider(&config.CaptchaConfig{ValidatorURL: "http://127.0.0.1:0/validate"}, http.DefaultClient)

	result, svcErr := p.Verify(context.Background(), "some-token")
	require.Nil(ts.T(), result)
	require.NotNil(ts.T(), svcErr)
	require.Equal(ts.T(), "captcha_provider_error", svcErr.Code)
}

func (ts *CaptchaProviderTestSuite) TestIsEnabled() {
	require.True(ts.T(), isEnabled(&config.CaptchaConfig{ValidatorURL: "http://example.com"}))
	require.False(ts.T(), isEnabled(&config.CaptchaConfig{ValidatorURL: "   "}))
	require.False(ts.T(), isEnabled(&config.CaptchaConfig{}))
}

type CaptchaProviderTestSuite struct {
	suite.Suite
}

func TestCaptchaProviderTestSuite(t *testing.T) {
	suite.Run(t, new(CaptchaProviderTestSuite))
}
