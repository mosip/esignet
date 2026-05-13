package middleware

import (
	"context"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
)

// nopLog silences log output in tests.
var nopLog = slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError + 100}))

// withRequestID is a test helper that injects a request-id into a context.
func withRequestID(ctx context.Context, id string) context.Context {
	return context.WithValue(ctx, RequestIDKey, id)
}

// ── RequestID ─────────────────────────────────────────────────────────────────

func TestRequestID_GeneratesID(t *testing.T) {
	var capturedID string
	next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedID, _ = r.Context().Value(RequestIDKey).(string)
	})

	r := httptest.NewRequest(http.MethodGet, "/", nil)
	w := httptest.NewRecorder()
	RequestID(next).ServeHTTP(w, r)

	if capturedID == "" {
		t.Error("expected a generated request-id in context, got empty string")
	}
	if w.Header().Get("X-Request-ID") != capturedID {
		t.Errorf("response header X-Request-ID %q != context id %q",
			w.Header().Get("X-Request-ID"), capturedID)
	}
}

func TestRequestID_HonoursInboundHeader(t *testing.T) {
	const existingID = "my-upstream-id"
	var capturedID string
	next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedID, _ = r.Context().Value(RequestIDKey).(string)
	})

	r := httptest.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("X-Request-ID", existingID)
	w := httptest.NewRecorder()
	RequestID(next).ServeHTTP(w, r)

	if capturedID != existingID {
		t.Errorf("context id = %q, want %q", capturedID, existingID)
	}
	if w.Header().Get("X-Request-ID") != existingID {
		t.Errorf("response header = %q, want %q", w.Header().Get("X-Request-ID"), existingID)
	}
}

// ── Logger ────────────────────────────────────────────────────────────────────

func TestLogger_LogsRequest(t *testing.T) {
	next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte("hello"))
	})

	r := httptest.NewRequest(http.MethodPost, "/test?foo=bar", nil)
	w := httptest.NewRecorder()

	Logger(nopLog)(next).ServeHTTP(w, r)

	if w.Code != http.StatusCreated {
		t.Errorf("status = %d, want 201", w.Code)
	}
}

func TestLogger_WithRequestIDInContext(t *testing.T) {
	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	r := httptest.NewRequest(http.MethodGet, "/ping", nil)
	r = r.WithContext(withRequestID(r.Context(), "test-id-123"))
	w := httptest.NewRecorder()

	Logger(nopLog)(next).ServeHTTP(w, r)

	if w.Code != http.StatusOK {
		t.Errorf("status = %d, want 200", w.Code)
	}
}

// ── Recoverer ─────────────────────────────────────────────────────────────────

func TestRecoverer_CatchesPanic(t *testing.T) {
	panicking := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		panic("test panic")
	})

	r := httptest.NewRequest(http.MethodGet, "/boom", nil)
	w := httptest.NewRecorder()

	Recoverer(nopLog)(panicking).ServeHTTP(w, r)

	if w.Code != http.StatusInternalServerError {
		t.Errorf("status = %d, want 500", w.Code)
	}
	if !strings.Contains(w.Body.String(), http.StatusText(http.StatusInternalServerError)) {
		t.Errorf("body %q missing 500 text", w.Body.String())
	}
}

func TestRecoverer_CatchesPanic_WithRequestID(t *testing.T) {
	panicking := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		panic("panic with request id")
	})

	r := httptest.NewRequest(http.MethodGet, "/boom", nil)
	r = r.WithContext(withRequestID(r.Context(), "panic-req-id"))
	w := httptest.NewRecorder()

	Recoverer(nopLog)(panicking).ServeHTTP(w, r)

	if w.Code != http.StatusInternalServerError {
		t.Errorf("status = %d, want 500", w.Code)
	}
}

func TestRecoverer_NoPanic_PassesThrough(t *testing.T) {
	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	r := httptest.NewRequest(http.MethodGet, "/ok", nil)
	w := httptest.NewRecorder()

	Recoverer(nopLog)(next).ServeHTTP(w, r)

	if w.Code != http.StatusOK {
		t.Errorf("status = %d, want 200", w.Code)
	}
}

// ── responseWriter shim ───────────────────────────────────────────────────────

func TestResponseWriter_DefaultStatus(t *testing.T) {
	rw := newResponseWriter(httptest.NewRecorder())

	if rw.status != http.StatusOK {
		t.Errorf("default status = %d, want 200", rw.status)
	}
	if rw.bytes != 0 {
		t.Errorf("initial bytes = %d, want 0", rw.bytes)
	}
}

func TestResponseWriter_WriteHeader_OnlyFirstTakes(t *testing.T) {
	rw := newResponseWriter(httptest.NewRecorder())

	rw.WriteHeader(http.StatusTeapot)
	if rw.status != http.StatusTeapot {
		t.Errorf("status = %d, want 418", rw.status)
	}

	// Second call must be a no-op.
	rw.WriteHeader(http.StatusOK)
	if rw.status != http.StatusTeapot {
		t.Errorf("second WriteHeader changed status from 418 to %d", rw.status)
	}
}

func TestResponseWriter_Write_CountsBytes(t *testing.T) {
	rw := newResponseWriter(httptest.NewRecorder())

	n, err := rw.Write([]byte("hello"))
	if err != nil {
		t.Fatalf("Write() error: %v", err)
	}
	if n != 5 {
		t.Errorf("Write() n = %d, want 5", n)
	}
	if rw.bytes != 5 {
		t.Errorf("rw.bytes = %d, want 5", rw.bytes)
	}

	_, _ = rw.Write([]byte(" world"))
	if rw.bytes != 11 {
		t.Errorf("rw.bytes after second write = %d, want 11", rw.bytes)
	}
}
