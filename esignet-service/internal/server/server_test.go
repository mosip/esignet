package server

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"github.com/mosip/esignet/internal/config"
)

// nopLog silences log output in tests.
var nopLog = slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError + 100}))

// ── mock Pinger ───────────────────────────────────────────────────────────────

type mockPinger struct{ err error }

func (m *mockPinger) Ping(_ context.Context) error { return m.err }

// ── helpers ───────────────────────────────────────────────────────────────────

func testCfg() config.ServerConfig {
	return config.ServerConfig{
		Port:            0, // 0 = OS picks a free port (used for Start tests)
		ReadTimeout:     5 * time.Second,
		WriteTimeout:    5 * time.Second,
		IdleTimeout:     30 * time.Second,
		ShutdownTimeout: 5 * time.Second,
	}
}

// newTestServer builds a Server and wraps its handler in an httptest.Server so
// we can make real HTTP requests without binding a port.
func newTestServer(t *testing.T, deps Dependencies) (*Server, *httptest.Server) {
	t.Helper()
	srv := New(testCfg(), deps, nopLog)
	ts := httptest.NewServer(srv.http.Handler)
	t.Cleanup(ts.Close)
	return srv, ts
}

// ── /ping ─────────────────────────────────────────────────────────────────────

func TestServer_Ping(t *testing.T) {
	_, ts := newTestServer(t, Dependencies{})

	resp, err := ts.Client().Get(ts.URL + "/ping")
	if err != nil {
		t.Fatalf("GET /ping: %v", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
}

// ── /health – DB + Cache healthy ──────────────────────────────────────────────

func TestServer_Health_AllUp(t *testing.T) {
	_, ts := newTestServer(t, Dependencies{
		DB:    &mockPinger{},
		Cache: &mockPinger{},
	})

	resp, err := ts.Client().Get(ts.URL + "/health")
	if err != nil {
		t.Fatalf("GET /health: %v", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
}

// ── /health – DB down ─────────────────────────────────────────────────────────

func TestServer_Health_DBDown(t *testing.T) {
	_, ts := newTestServer(t, Dependencies{
		DB:    &mockPinger{err: errors.New("db unreachable")},
		Cache: &mockPinger{},
	})

	resp, err := ts.Client().Get(ts.URL + "/health")
	if err != nil {
		t.Fatalf("GET /health: %v", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusServiceUnavailable {
		t.Errorf("status = %d, want 503", resp.StatusCode)
	}
}

// ── /health – no dependencies ─────────────────────────────────────────────────

func TestServer_Health_NoDeps(t *testing.T) {
	// Both DB and Cache are nil — pinger map is empty, should return 200.
	_, ts := newTestServer(t, Dependencies{})

	resp, err := ts.Client().Get(ts.URL + "/health")
	if err != nil {
		t.Fatalf("GET /health: %v", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
}

// ── /health – only DB, no Cache ───────────────────────────────────────────────

func TestServer_Health_DBOnly(t *testing.T) {
	_, ts := newTestServer(t, Dependencies{
		DB: &mockPinger{},
	})

	resp, err := ts.Client().Get(ts.URL + "/health")
	if err != nil {
		t.Fatalf("GET /health: %v", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
}

// ── Shutdown ──────────────────────────────────────────────────────────────────

func TestServer_Shutdown(t *testing.T) {
	srv := New(testCfg(), Dependencies{}, nopLog)

	// Start on a random free port.
	errCh := make(chan error, 1)
	go func() { errCh <- srv.Start() }()

	// Give the server a moment to bind.
	time.Sleep(20 * time.Millisecond)

	if err := srv.Shutdown(context.Background()); err != nil {
		t.Errorf("Shutdown() error: %v", err)
	}

	// Start() should return nil (ErrServerClosed is swallowed).
	select {
	case err := <-errCh:
		if err != nil {
			t.Errorf("Start() returned error after shutdown: %v", err)
		}
	case <-time.After(2 * time.Second):
		t.Error("Start() did not return after Shutdown()")
	}
}

// ── Start returns error on bad address ────────────────────────────────────────

func TestServer_Start_BadAddress(t *testing.T) {
	cfg := testCfg()
	cfg.Port = 99999 // invalid port number → bind fails
	srv := New(cfg, Dependencies{}, nopLog)

	err := srv.Start()
	if err == nil {
		t.Error("Start() with invalid port should return an error")
	}
}
