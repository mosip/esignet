/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package httpmiddleware

import (
	"bufio"
	"context"
	"log/slog"
	"net"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	applog "github.com/mosip/esignet/internal/log"
)

// countingHandler is a slog.Handler that counts records emitted at LevelAccess.
// All other levels are silently discarded.
type countingHandler struct {
	mu    sync.Mutex
	count int
}

func (h *countingHandler) Enabled(_ context.Context, level slog.Level) bool {
	return level >= applog.LevelAccess
}

func (h *countingHandler) Handle(_ context.Context, r slog.Record) error {
	if r.Level == applog.LevelAccess {
		h.mu.Lock()
		h.count++
		h.mu.Unlock()
	}
	return nil
}

func (h *countingHandler) WithAttrs(_ []slog.Attr) slog.Handler { return h }
func (h *countingHandler) WithGroup(_ string) slog.Handler      { return h }

func (h *countingHandler) n() int {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.count
}

// installAccessCounter replaces the global logger with one backed by a
// countingHandler for the duration of t, restoring the original on cleanup.
func installAccessCounter(t *testing.T) *countingHandler {
	t.Helper()
	h := &countingHandler{}
	restore := applog.ReplaceLogger(applog.NewLogger(h))
	t.Cleanup(restore)
	return h
}

func TestAccessLog_PassesThroughResponseAndCapturesStatusAndBytes(t *testing.T) {
	h := installAccessCounter(t)
	var capturedStatus, capturedBytes int
	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusCreated)
		n, err := w.Write([]byte("hello"))
		require.NoError(t, err)
		capturedBytes = n
		capturedStatus = http.StatusCreated
	})

	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/foo?bar=1", nil)

	// AccessLog reads the trace ID from context, so it must run inside
	// CorrelationID, matching how it's wired in main.go.
	before := h.n()
	CorrelationID(AccessLog(next)).ServeHTTP(rr, req)

	assert.Equal(t, http.StatusCreated, rr.Code)
	assert.Equal(t, "hello", rr.Body.String())
	assert.Equal(t, http.StatusCreated, capturedStatus)
	assert.Equal(t, 5, capturedBytes)
	assert.Equal(t, 1, h.n()-before, "expected one Access entry for a normal request")
}

func TestAccessLog_DefaultsStatusToOKWhenHandlerNeverCallsWriteHeader(t *testing.T) {
	h := installAccessCounter(t)
	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("ok"))
	})

	rr := httptest.NewRecorder()
	before := h.n()
	CorrelationID(AccessLog(next)).ServeHTTP(rr, httptest.NewRequest(http.MethodGet, "/", nil))

	assert.Equal(t, http.StatusOK, rr.Code)
	assert.Equal(t, 1, h.n()-before, "expected one Access entry even when WriteHeader is never called")
}

func TestStatusRecorder_TracksWriteHeaderAndBytesWritten(t *testing.T) {
	rr := httptest.NewRecorder()
	rec := &statusRecorder{ResponseWriter: rr, statusCode: http.StatusOK}

	rec.WriteHeader(http.StatusNotFound)
	n, err := rec.Write([]byte("abc"))
	require.NoError(t, err)

	assert.Equal(t, 3, n)
	assert.Equal(t, http.StatusNotFound, rec.statusCode)
	assert.Equal(t, 3, rec.bytesWritten)

	// Multiple writes accumulate.
	_, err = rec.Write([]byte("de"))
	require.NoError(t, err)
	assert.Equal(t, 5, rec.bytesWritten)
}

func TestStatusRecorder_Flush(t *testing.T) {
	rr := httptest.NewRecorder()
	rec := &statusRecorder{ResponseWriter: rr, statusCode: http.StatusOK}

	rec.Flush()

	assert.True(t, rr.Flushed)
}

// hijackableRecorder embeds httptest.ResponseRecorder, which does not itself
// implement http.Hijacker, and adds a minimal Hijack so tests can exercise
// the delegation path.
type hijackableRecorder struct {
	*httptest.ResponseRecorder
	hijacked bool
}

func (h *hijackableRecorder) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	h.hijacked = true
	server, _ := net.Pipe()
	return server, bufio.NewReadWriter(bufio.NewReader(server), bufio.NewWriter(server)), nil
}

func TestStatusRecorder_Hijack_Supported(t *testing.T) {
	rr := &hijackableRecorder{ResponseRecorder: httptest.NewRecorder()}
	rec := &statusRecorder{ResponseWriter: rr, statusCode: http.StatusOK}

	conn, buf, err := rec.Hijack()

	require.NoError(t, err)
	require.NotNil(t, conn)
	require.NotNil(t, buf)
	assert.True(t, rr.hijacked)
	_ = conn.Close()
}

func TestStatusRecorder_Hijack_NotSupported(t *testing.T) {
	rr := httptest.NewRecorder()
	rec := &statusRecorder{ResponseWriter: rr, statusCode: http.StatusOK}

	_, _, err := rec.Hijack()

	assert.Error(t, err)
}

func TestAccessLog_SkipsLoggingForExcludedPaths(t *testing.T) {
	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	skippedPaths := []string{"/health", "/health/live", "/health/ready", "/metrics"}
	for _, path := range skippedPaths {
		t.Run(path, func(t *testing.T) {
			h := installAccessCounter(t)
			before := h.n()
			rr := httptest.NewRecorder()
			req := httptest.NewRequest(http.MethodGet, path, nil)
			CorrelationID(AccessLog(next, WithSkipPrefixes("/health", "/metrics"))).ServeHTTP(rr, req)
			assert.Equal(t, http.StatusOK, rr.Code)
			assert.Equal(t, 0, h.n()-before, "expected no Access entry for skipped path %s", path)
		})
	}
}

func TestAccessLog_LogsNonSkippedPaths(t *testing.T) {
	// Paths that must NOT be suppressed even though they share a prefix with "/health".
	paths := []string{"/oauth2/token", "/healthcheck", "/health-status"}
	for _, path := range paths {
		t.Run(path, func(t *testing.T) {
			h := installAccessCounter(t)
			before := h.n()
			next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				w.WriteHeader(http.StatusOK)
			})
			rr := httptest.NewRecorder()
			req := httptest.NewRequest(http.MethodGet, path, nil)
			CorrelationID(AccessLog(next, WithSkipPrefixes("/health", "/metrics"))).ServeHTTP(rr, req)
			assert.Equal(t, http.StatusOK, rr.Code)
			assert.Equal(t, 1, h.n()-before, "expected one Access entry for non-skipped path %s", path)
		})
	}
}

func TestAccessLog_NoOptionsLogsAllPaths(t *testing.T) {
	h := installAccessCounter(t)
	before := h.n()
	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	// Without WithSkipPrefixes the health path is logged like any other request.
	CorrelationID(AccessLog(next)).ServeHTTP(rr, req)

	assert.Equal(t, http.StatusOK, rr.Code)
	assert.Equal(t, 1, h.n()-before, "expected one Access entry when no skip prefixes are configured")
}

func TestAccessLog_TrailingSlashInPrefixIsNormalized(t *testing.T) {
	// "/health/" (with trailing slash) must behave identically to "/health".
	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	paths := []string{"/health", "/health/live", "/health/ready"}
	for _, path := range paths {
		t.Run(path, func(t *testing.T) {
			h := installAccessCounter(t)
			before := h.n()
			rr := httptest.NewRecorder()
			req := httptest.NewRequest(http.MethodGet, path, nil)
			CorrelationID(AccessLog(next, WithSkipPrefixes("/health/"))).ServeHTTP(rr, req)
			assert.Equal(t, http.StatusOK, rr.Code)
			assert.Equal(t, 0, h.n()-before, `trailing-slash prefix "/health/" should skip logging for %s`, path)
		})
	}
}

func TestAccessLog_RootPrefixSkipsAllPaths(t *testing.T) {
	h := installAccessCounter(t)
	before := h.n()
	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	CorrelationID(AccessLog(next, WithSkipPrefixes("/"))).ServeHTTP(rr, req)

	assert.Equal(t, http.StatusOK, rr.Code)
	assert.Equal(t, 0, h.n()-before, `root prefix "/" should suppress logging for all paths`)
}

func TestOrDash(t *testing.T) {
	assert.Equal(t, "-", orDash(""))
	assert.Equal(t, "val", orDash("val"))
}

func TestRemoteHost(t *testing.T) {
	assert.Equal(t, "172.31.0.184", remoteHost("172.31.0.184:54321"))
	assert.Equal(t, "not-a-host-port-pair", remoteHost("not-a-host-port-pair"))
	assert.Equal(t, "-", remoteHost(""))
}
