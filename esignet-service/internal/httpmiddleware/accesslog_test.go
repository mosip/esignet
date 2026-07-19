/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package httpmiddleware

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestAccessLog_PassesThroughResponseAndCapturesStatusAndBytes(t *testing.T) {
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
	CorrelationID(AccessLog(next)).ServeHTTP(rr, req)

	assert.Equal(t, http.StatusCreated, rr.Code)
	assert.Equal(t, "hello", rr.Body.String())
	assert.Equal(t, http.StatusCreated, capturedStatus)
	assert.Equal(t, 5, capturedBytes)
}

func TestAccessLog_DefaultsStatusToOKWhenHandlerNeverCallsWriteHeader(t *testing.T) {
	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("ok"))
	})

	rr := httptest.NewRecorder()
	CorrelationID(AccessLog(next)).ServeHTTP(rr, httptest.NewRequest(http.MethodGet, "/", nil))

	assert.Equal(t, http.StatusOK, rr.Code)
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

func TestOrDash(t *testing.T) {
	assert.Equal(t, "-", orDash(""))
	assert.Equal(t, "val", orDash("val"))
}

func TestRemoteHost(t *testing.T) {
	assert.Equal(t, "172.31.0.184", remoteHost("172.31.0.184:54321"))
	assert.Equal(t, "not-a-host-port-pair", remoteHost("not-a-host-port-pair"))
	assert.Equal(t, "-", remoteHost(""))
}
