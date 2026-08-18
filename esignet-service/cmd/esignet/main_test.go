/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package main

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/config"
	applog "github.com/mosip/esignet/internal/log"
	"github.com/mosip/esignet/internal/metrics"
)

// fakeDriver is a minimal database/sql driver that never actually connects.
// sql.Open() only registers a name/dsn pair lazily, so this is enough to
// exercise startMetricsServer's metrics.RegisterDBStats wiring without a
// real database.
type fakeDriver struct{}

func (fakeDriver) Open(_ string) (driver.Conn, error) {
	return nil, errors.New("fakeDriver: connections are not supported")
}

func init() {
	sql.Register("esignet-cmd-fake-driver", fakeDriver{})
}

func freePort(t *testing.T) int {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	require.NoError(t, err)
	defer func() { _ = ln.Close() }()
	return ln.Addr().(*net.TCPAddr).Port
}

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

func (ts *MainTestSuite) TestStartMetricsServer() {
	t := ts.T()

	db, err := sql.Open("esignet-cmd-fake-driver", "unused-dsn")
	require.NoError(t, err)
	t.Cleanup(func() { _ = db.Close() })

	redisClient := redis.NewClient(&redis.Options{Addr: "127.0.0.1:0"})
	t.Cleanup(func() { _ = redisClient.Close() })

	port := freePort(t)
	appCfg := &config.AppConfig{MetricsPort: port}

	srv := startMetricsServer(db, redisClient, appCfg, applog.GetLogger())
	require.Equal(t, fmt.Sprintf(":%d", port), srv.Addr)
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = srv.Shutdown(ctx)
	})

	require.Eventually(t, func() bool {
		resp, err := http.Get(fmt.Sprintf("http://127.0.0.1:%d/metrics", port))
		if err != nil {
			return false
		}
		defer func() { _ = resp.Body.Close() }()
		body, err := io.ReadAll(resp.Body)
		return err == nil &&
			resp.StatusCode == http.StatusOK &&
			strings.Contains(string(body), "esignet_db_open_connections") &&
			strings.Contains(string(body), "esignet_redis_total_conns")
	}, 2*time.Second, 20*time.Millisecond, "metrics server did not become ready")
}

func (ts *MainTestSuite) TestStartDebugServer() {
	t := ts.T()

	port := freePort(t)
	appCfg := &config.AppConfig{PProfConfig: config.PProfConfig{Enabled: true, Port: port}}

	go startDebugServer(appCfg, applog.GetLogger())

	require.Eventually(t, func() bool {
		resp, err := http.Get(fmt.Sprintf("http://127.0.0.1:%d/debug/pprof/", port))
		if err != nil {
			return false
		}
		defer func() { _ = resp.Body.Close() }()
		return resp.StatusCode == http.StatusOK
	}, 2*time.Second, 20*time.Millisecond, "debug pprof server did not become ready")
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
