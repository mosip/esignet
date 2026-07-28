/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package main

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/config"
	applog "github.com/mosip/esignet/internal/log"
)

func testHTTPClientConfig() config.HTTPClientConfig {
	return config.HTTPClientConfig{
		TimeoutSecs:               30,
		DialTimeoutSecs:           5,
		DialKeepAliveSecs:         30,
		TLSHandshakeTimeoutSecs:   10,
		ResponseHeaderTimeoutSecs: 10,
		IdleConnTimeoutSecs:       90,
		MaxConnsPerHost:           100,
	}
}

func (ts *MainTestSuite) TestNewHTTPClient() {
	cfg := testHTTPClientConfig()
	c := newHTTPClient(cfg)
	require.Equal(ts.T(), 30*time.Second, c.Timeout)

	transport, ok := c.Transport.(*http.Transport)
	require.True(ts.T(), ok)
	require.Equal(ts.T(), 10*time.Second, transport.TLSHandshakeTimeout)
	require.Equal(ts.T(), 10*time.Second, transport.ResponseHeaderTimeout)
	require.Equal(ts.T(), 90*time.Second, transport.IdleConnTimeout)
	require.Equal(ts.T(), 100, transport.MaxConnsPerHost)
}

func (ts *MainTestSuite) TestNewHTTPClientReturnsDistinctInstances() {
	cfg := testHTTPClientConfig()
	require.NotSame(ts.T(), newHTTPClient(cfg), newHTTPClient(cfg))
}

func (ts *MainTestSuite) TestGetSecurityMiddleware() {
	logger := applog.GetLogger()

	t := ts.T()
	t.Run("scope enforcement disabled", func(t *testing.T) {
		mw := getSecurityMiddleware(&config.AppConfig{}, logger)
		require.NotNil(t, mw)

		called := false
		h := mw(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { called = true; w.WriteHeader(http.StatusOK) }))
		req := httptest.NewRequest(http.MethodGet, "/", nil)
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		require.True(t, called)
		require.Equal(t, http.StatusOK, rec.Code)
	})

	t.Run("scope enforcement enabled wraps request time middleware", func(t *testing.T) {
		mw := getSecurityMiddleware(&config.AppConfig{
			SecurityConfig: config.SecurityConfig{IssuerURL: "https://issuer", JwksURL: "https://jwks.invalid/jwks.json"},
		}, logger)
		require.NotNil(t, mw)

		h := mw(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusOK)
		}))
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))
		require.Equal(t, http.StatusUnauthorized, rec.Code)
	})
}

func (ts *MainTestSuite) TestScopeEnforcementEnabled() {
	require.True(ts.T(), scopeEnforcementEnabled(&config.AppConfig{
		SecurityConfig: config.SecurityConfig{IssuerURL: "https://issuer", JwksURL: "https://jwks"},
	}))
	require.False(ts.T(), scopeEnforcementEnabled(&config.AppConfig{
		SecurityConfig: config.SecurityConfig{IssuerURL: "https://issuer"},
	}))
	require.False(ts.T(), scopeEnforcementEnabled(&config.AppConfig{}))
}

type MainTestSuite struct {
	suite.Suite
}

func TestMainTestSuite(t *testing.T) {
	suite.Run(t, new(MainTestSuite))
}
