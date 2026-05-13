package handler

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
)

// nopLog silences log output in tests.
var nopLog = slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError + 100}))

// ── mock Pinger ───────────────────────────────────────────────────────────────

type mockPinger struct{ err error }

func (m *mockPinger) Ping(_ context.Context) error { return m.err }

// ── helpers ───────────────────────────────────────────────────────────────────

func callHealth(t *testing.T, pingers map[string]Pinger) *httptest.ResponseRecorder {
	t.Helper()
	r := httptest.NewRequest(http.MethodGet, "/health", nil)
	w := httptest.NewRecorder()
	HealthHandler(nopLog, pingers).ServeHTTP(w, r)
	return w
}

func decodeBody(t *testing.T, w *httptest.ResponseRecorder) HealthResponse {
	t.Helper()
	var resp HealthResponse
	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	return resp
}

// ── tests ─────────────────────────────────────────────────────────────────────

func TestHealthHandler_AllUp(t *testing.T) {
	pingers := map[string]Pinger{
		"postgres": &mockPinger{},
		"redis":    &mockPinger{},
	}

	w := callHealth(t, pingers)

	if w.Code != http.StatusOK {
		t.Errorf("status = %d, want 200", w.Code)
	}
	if ct := w.Header().Get("Content-Type"); ct != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", ct)
	}

	resp := decodeBody(t, w)
	if resp.Status != "ok" {
		t.Errorf("status = %q, want ok", resp.Status)
	}
	if resp.Timestamp == "" {
		t.Error("timestamp should not be empty")
	}
	if resp.Components["postgres"].Status != "up" {
		t.Errorf("postgres component = %q, want up", resp.Components["postgres"].Status)
	}
	if resp.Components["redis"].Status != "up" {
		t.Errorf("redis component = %q, want up", resp.Components["redis"].Status)
	}
}

func TestHealthHandler_OneDown(t *testing.T) {
	pingers := map[string]Pinger{
		"postgres": &mockPinger{},
		"redis":    &mockPinger{err: errors.New("connection refused")},
	}

	w := callHealth(t, pingers)

	if w.Code != http.StatusServiceUnavailable {
		t.Errorf("status = %d, want 503", w.Code)
	}

	resp := decodeBody(t, w)
	if resp.Status != "degraded" {
		t.Errorf("status = %q, want degraded", resp.Status)
	}
	if resp.Components["postgres"].Status != "up" {
		t.Errorf("postgres = %q, want up", resp.Components["postgres"].Status)
	}
	if resp.Components["redis"].Status != "down" {
		t.Errorf("redis = %q, want down", resp.Components["redis"].Status)
	}
	if resp.Components["redis"].Message == "" {
		t.Error("redis message should contain the error")
	}
}

func TestHealthHandler_AllDown(t *testing.T) {
	pingErr := errors.New("timeout")
	pingers := map[string]Pinger{
		"postgres": &mockPinger{err: pingErr},
		"redis":    &mockPinger{err: pingErr},
	}

	w := callHealth(t, pingers)

	if w.Code != http.StatusServiceUnavailable {
		t.Errorf("status = %d, want 503", w.Code)
	}

	resp := decodeBody(t, w)
	if resp.Status != "degraded" {
		t.Errorf("status = %q, want degraded", resp.Status)
	}
	for name, cs := range resp.Components {
		if cs.Status != "down" {
			t.Errorf("%s = %q, want down", name, cs.Status)
		}
	}
}

func TestHealthHandler_NoPingers(t *testing.T) {
	// No dependencies registered — should still return 200.
	w := callHealth(t, map[string]Pinger{})

	if w.Code != http.StatusOK {
		t.Errorf("status = %d, want 200 (no pingers → ok)", w.Code)
	}

	resp := decodeBody(t, w)
	if resp.Status != "ok" {
		t.Errorf("status = %q, want ok", resp.Status)
	}
}

func TestHealthHandler_SinglePingerDown(t *testing.T) {
	pingers := map[string]Pinger{
		"db": &mockPinger{err: errors.New("dial error")},
	}

	w := callHealth(t, pingers)

	if w.Code != http.StatusServiceUnavailable {
		t.Errorf("status = %d, want 503", w.Code)
	}
	resp := decodeBody(t, w)
	if resp.Components["db"].Status != "down" {
		t.Errorf("db status = %q, want down", resp.Components["db"].Status)
	}
	if resp.Components["db"].Message != "dial error" {
		t.Errorf("db message = %q, want 'dial error'", resp.Components["db"].Message)
	}
}
