/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package main

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/config"
	applog "github.com/mosip/esignet/internal/log"
	"github.com/mosip/esignet/internal/metrics"
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

func (ts *MainTestSuite) TestNewDebugMux() {
	mux := newDebugMux()

	paths := []string{
		"/debug/pprof/",
		"/debug/pprof/cmdline",
		"/debug/pprof/goroutine",
		"/debug/pprof/heap",
		"/debug/pprof/symbol",
	}
	for _, p := range paths {
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, p, nil))
		ts.Equal(http.StatusOK, rec.Code, "path %s", p)
	}
}

func (ts *MainTestSuite) TestNewDebugMux_BoundedProfileAndTrace() {
	if testing.Short() {
		ts.T().Skip("skipping blocking CPU/trace endpoints in short mode")
	}
	mux := newDebugMux()

	for _, p := range []string{"/debug/pprof/profile?seconds=1", "/debug/pprof/trace?seconds=1"} {
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, p, nil))
		ts.Equal(http.StatusOK, rec.Code, "path %s", p)
	}
}

func (ts *MainTestSuite) TestPprofEnabled_DefaultOff() {
	ts.False((&config.AppConfig{}).PProfConfig.Enabled, "pprof must be opt-in; default should be false")
}

func (ts *MainTestSuite) TestPprofEnabled_MuxReadyWhenEnabled() {
	appCfg := &config.AppConfig{PProfConfig: config.PProfConfig{Enabled: true}}
	ts.True(appCfg.PProfConfig.Enabled)

	mux := newDebugMux()
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/debug/pprof/", nil))
	ts.Equal(http.StatusOK, rec.Code)
}

func (ts *MainTestSuite) TestNewHTTPClient() {
	cfg := testHTTPClientConfig()
	c := config.NewHTTPClient(cfg)
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
	require.NotSame(ts.T(), config.NewHTTPClient(cfg), config.NewHTTPClient(cfg))
}

func (ts *MainTestSuite) TestGetSecurityMiddleware() {
	logger := applog.GetLogger()

	t := ts.T()
	t.Run("scope enforcement disabled", func(t *testing.T) {
		mw := getSecurityMiddleware(&config.AppConfig{}, http.DefaultClient, logger)
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
		}, http.DefaultClient, logger)
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

func (ts *MainTestSuite) TestMetricsMuxRegistersMetricsRoute() {
	metricsMux := http.NewServeMux()
	metricsMux.Handle("GET /metrics", metrics.Handler())

	req := httptest.NewRequest(http.MethodGet, "/metrics", nil)
	rec := httptest.NewRecorder()
	metricsMux.ServeHTTP(rec, req)
	require.Equal(ts.T(), http.StatusOK, rec.Code)
}

func (ts *MainTestSuite) TestMetricsSrvUsesConfiguredPort() {
	const port = 9091
	addr := fmt.Sprintf(":%d", port)
	metricsMux := http.NewServeMux()
	metricsMux.Handle("GET /metrics", metrics.Handler())
	srv := &http.Server{Addr: addr, Handler: metricsMux}
	require.Equal(ts.T(), addr, srv.Addr)
}

func (ts *MainTestSuite) TestMetricsSrvShutdownGraceful() {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	require.NoError(ts.T(), err)

	metricsMux := http.NewServeMux()
	metricsMux.Handle("GET /metrics", metrics.Handler())
	srv := &http.Server{Handler: metricsMux}

	done := make(chan error, 1)
	go func() { done <- srv.Serve(ln) }()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	require.NoError(ts.T(), srv.Shutdown(ctx))
	require.ErrorIs(ts.T(), <-done, http.ErrServerClosed)
}

func (ts *MainTestSuite) TestMetricsSrvCloseReturnsServerClosed() {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	require.NoError(ts.T(), err)

	metricsMux := http.NewServeMux()
	metricsMux.Handle("GET /metrics", metrics.Handler())
	srv := &http.Server{Handler: metricsMux}

	done := make(chan error, 1)
	go func() { done <- srv.Serve(ln) }()

	require.NoError(ts.T(), srv.Close())
	require.ErrorIs(ts.T(), <-done, http.ErrServerClosed)
}

type MainTestSuite struct {
	suite.Suite
}

func TestMainTestSuite(t *testing.T) {
	suite.Run(t, new(MainTestSuite))
}
