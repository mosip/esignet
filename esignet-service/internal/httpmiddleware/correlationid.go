/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package httpmiddleware provides HTTP middleware for correlating and
// logging requests across the service.
package httpmiddleware

import (
	"context"
	"crypto/rand"
	"fmt"
	"net/http"
	"strings"
)

// CorrelationIDHeader is the header used to propagate the request's
// correlation/trace ID. It matches the header thunderidengine's own
// correlation-ID middleware checks first, so a request that has already
// been tagged here resolves to the same ID inside the engine.
const CorrelationIDHeader = "X-Correlation-ID"

// B3 (Zipkin) propagation headers, checked as a last-resort fallback. MOSIP
// Java services on older Spring Cloud Sleuth (Brave/Zipkin) versions
// propagate trace IDs this way; Sleuth's successor, Micrometer Tracing,
// defaults to W3C traceparent but MOSIP deployments may still have
// unupgraded services or infra (gateways, meshes) emitting B3. Without this
// fallback, a request arriving with only B3 headers would get a brand-new
// ID here, breaking the trace chain across the two propagation styles.
const (
	b3SingleHeader  = "b3"
	b3TraceIDHeader = "X-B3-TraceId"
)

type contextKey string

const traceIDContextKey contextKey = "trace_id"

// CorrelationID extracts a correlation/trace ID from the incoming request
// (checking X-Correlation-ID, X-Request-ID, X-Trace-ID, then falling back to
// the B3 "b3" single header or "X-B3-TraceId", in that order) or generates a
// new UUIDv4 if none is present. The ID is stored in the request
// context, echoed back as a response header, and normalized onto the
// X-Correlation-ID request header so that downstream components — including
// thunderidengine's own correlation-ID middleware — resolve to the same ID.
//
// Must wrap the handler outermost (before AccessLog and before mux) so the
// trace ID is already in the request context by the time inner handlers,
// including thunderidengine's routes, run.
func CorrelationID(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := extractTraceID(r)
		if id == "" {
			id = generateUUIDv4()
		}

		r.Header.Set(CorrelationIDHeader, id)
		w.Header().Set(CorrelationIDHeader, id)
		r = r.WithContext(context.WithValue(r.Context(), traceIDContextKey, id))

		next.ServeHTTP(w, r)
	})
}

// TraceIDFromContext returns the correlation/trace ID stored in the context
// by CorrelationID, or "-" if none is present.
func TraceIDFromContext(ctx context.Context) string {
	if id, ok := ctx.Value(traceIDContextKey).(string); ok && id != "" {
		return id
	}
	return "-"
}

func extractTraceID(r *http.Request) string {
	for _, header := range []string{CorrelationIDHeader, "X-Request-ID", "X-Trace-ID"} {
		if id := r.Header.Get(header); id != "" {
			return id
		}
	}
	return extractB3TraceID(r)
}

// extractB3TraceID reads the trace ID out of B3 propagation headers. The
// single-header form packs "{TraceId}-{SpanId}-{SamplingState}-{ParentSpanId}"
// (SpanId onward optional) into one "b3" header; only the TraceId segment is
// relevant here. Falls back to the multi-header "X-B3-TraceId" form.
func extractB3TraceID(r *http.Request) string {
	if b3 := r.Header.Get(b3SingleHeader); b3 != "" {
		traceID, _, _ := strings.Cut(b3, "-")
		return traceID
	}
	return r.Header.Get(b3TraceIDHeader)
}

func generateUUIDv4() string {
	var uuid [16]byte
	if _, err := rand.Read(uuid[:]); err != nil {
		// crypto/rand read failures are effectively unrecoverable on any
		// supported platform; fall back to a fixed sentinel rather than panic.
		return "00000000-0000-4000-8000-000000000000"
	}
	uuid[6] = (uuid[6] & 0x0f) | 0x40 // version 4
	uuid[8] = (uuid[8] & 0x3f) | 0x80 // variant 10

	return fmt.Sprintf("%x-%x-%x-%x-%x", uuid[0:4], uuid[4:6], uuid[6:8], uuid[8:10], uuid[10:])
}
