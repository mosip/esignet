/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package main

import (
	"context"
	"database/sql"
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

// fakeDB implements dbStatter with configurable stats for testing.
type fakeDB struct{ stats sql.DBStats }

func (f fakeDB) Stats() sql.DBStats { return f.stats }

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
	stub := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusOK) })
	mux := newDebugMux(stub)

	paths := []string{
		"/debug/pprof/",
		"/debug/pprof/cmdline",
		"/debug/pprof/goroutine",
		"/debug/pprof/heap",
		"/debug/pprof/symbol",
		"/debug/pool-config",
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
	stub := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusOK) })
	mux := newDebugMux(stub)

	for _, p := range []string{"/debug/pprof/profile?seconds=1", "/debug/pprof/trace?seconds=1"} {
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, p, nil))
		ts.Equal(http.StatusOK, rec.Code, "path %s", p)
	}
}

func (ts *MainTestSuite) TestPoolConfigHandler() {
	db := fakeDB{stats: sql.DBStats{OpenConnections: 3, InUse: 1, Idle: 2}}
	appCfg := &config.AppConfig{RuntimeDBType: "redis"}
	appCfg.DB.Pool.ConnMaxLifetimeSecs = 1800
	appCfg.DB.Pool.MaxOpenConns = 25
	appCfg.DB.Pool.MaxIdleConns = 5
	appCfg.Redis.ConnMaxLifetime = 30 * time.Minute

	rec := httptest.NewRecorder()
	newPoolConfigHandler(db, appCfg).ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/debug/pool-config", nil))

	ts.Equal(http.StatusOK, rec.Code)
	ts.Equal("application/json", rec.Header().Get("Content-Type"))
	body := rec.Body.String()
	ts.Contains(body, `"connMaxLifetime":"30m0s"`)
	ts.Contains(body, `"maxOpenConns":25`)
	ts.Contains(body, `"maxIdleConns":5`)
	ts.Contains(body, `"openConns":3`)
	ts.Contains(body, `"inUse":1`)
	ts.Contains(body, `"idle":2`)
	ts.Contains(body, `"enabled":true`)
}

func (ts *MainTestSuite) TestPoolConfigHandler_RedisDisabled() {
	rec := httptest.NewRecorder()
	newPoolConfigHandler(fakeDB{}, &config.AppConfig{RuntimeDBType: "inmemory"}).
		ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/debug/pool-config", nil))
	ts.Contains(rec.Body.String(), `"enabled":false`)
}

func (ts *MainTestSuite) TestPprofEnabled_DefaultOff() {
	ts.False((&config.AppConfig{}).PprofEnabled, "pprof must be opt-in; default should be false")
}

func (ts *MainTestSuite) TestPprofEnabled_MuxReadyWhenEnabled() {
	appCfg := &config.AppConfig{PprofEnabled: true}
	mux := newDebugMux(newPoolConfigHandler(fakeDB{}, appCfg))

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
