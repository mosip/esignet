/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package httpmiddleware

import (
	"context"
	"net/http"
	"net/http/httptest"
	"regexp"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

var uuidV4Pattern = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)

func TestCorrelationID_GeneratesWhenAbsent(t *testing.T) {
	var gotFromContext, gotFromHeader string
	next := http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) {
		gotFromContext = TraceIDFromContext(r.Context())
		gotFromHeader = r.Header.Get(CorrelationIDHeader)
	})

	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	CorrelationID(next).ServeHTTP(rr, req)

	assert.Regexp(t, uuidV4Pattern, gotFromContext)
	assert.Equal(t, gotFromContext, gotFromHeader, "generated ID should be set on the request header for downstream consumers")
	assert.Equal(t, gotFromContext, rr.Header().Get(CorrelationIDHeader), "generated ID should be echoed on the response")
}

func TestCorrelationID_ReusesExistingCorrelationIDHeader(t *testing.T) {
	var got string
	next := http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) {
		got = TraceIDFromContext(r.Context())
	})

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set(CorrelationIDHeader, "caller-supplied-id")
	rr := httptest.NewRecorder()
	CorrelationID(next).ServeHTTP(rr, req)

	assert.Equal(t, "caller-supplied-id", got)
	assert.Equal(t, "caller-supplied-id", rr.Header().Get(CorrelationIDHeader))
}

func TestCorrelationID_FallsBackToOtherHeadersInPriorityOrder(t *testing.T) {
	tests := []struct {
		name    string
		headers map[string]string
		want    string
	}{
		{"X-Request-ID used when X-Correlation-ID absent", map[string]string{"X-Request-ID": "req-1"}, "req-1"},
		{"X-Trace-ID used when the others are absent", map[string]string{"X-Trace-ID": "trace-1"}, "trace-1"},
		{"X-Correlation-ID takes priority over X-Request-ID", map[string]string{"X-Correlation-ID": "corr-1", "X-Request-ID": "req-1"}, "corr-1"},
		{"X-Request-ID takes priority over X-Trace-ID", map[string]string{"X-Request-ID": "req-1", "X-Trace-ID": "trace-1"}, "req-1"},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			var got string
			next := http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) {
				got = TraceIDFromContext(r.Context())
			})

			req := httptest.NewRequest(http.MethodGet, "/", nil)
			for k, v := range tc.headers {
				req.Header.Set(k, v)
			}
			CorrelationID(next).ServeHTTP(httptest.NewRecorder(), req)

			assert.Equal(t, tc.want, got)
		})
	}
}

// This is exactly the contract thunderidengine's own correlation-ID
// middleware relies on to resolve the same trace ID as esignet-service's
// outer middleware: it checks X-Correlation-ID first.
func TestCorrelationID_NormalizesOntoCorrelationIDHeaderForDownstreamReuse(t *testing.T) {
	var gotHeader string
	next := http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) {
		gotHeader = r.Header.Get(CorrelationIDHeader)
	})

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("X-Request-ID", "req-1")
	CorrelationID(next).ServeHTTP(httptest.NewRecorder(), req)

	assert.Equal(t, "req-1", gotHeader)
}

func TestTraceIDFromContext_DefaultsToDash(t *testing.T) {
	assert.Equal(t, "-", TraceIDFromContext(context.Background()))
}

func TestGenerateUUIDv4_ProducesValidUniqueUUIDs(t *testing.T) {
	seen := make(map[string]bool)
	for i := 0; i < 100; i++ {
		id := generateUUIDv4()
		require.Regexp(t, uuidV4Pattern, id)
		assert.False(t, seen[id], "unexpected duplicate UUID")
		seen[id] = true
	}
}
