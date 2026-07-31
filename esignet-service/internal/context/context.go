/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package context carries request-scoped values, such as the trace/correlation
// ID, across API boundaries.
package context

import "context"

type contextKey string

const traceIDContextKey contextKey = "trace_id"

// WithTraceID returns a copy of ctx carrying the given trace/correlation ID.
func WithTraceID(ctx context.Context, id string) context.Context {
	return context.WithValue(ctx, traceIDContextKey, id)
}

// TraceIDFromContext returns the trace ID stored in ctx, or
// "-" if none is present, including when ctx is nil — callers threading a
// zero-value context (e.g. an uninitialized providers.NodeContext.Context)
// must not crash the logger.
func TraceIDFromContext(ctx context.Context) string {
	if ctx == nil {
		return "-"
	}
	if id, ok := ctx.Value(traceIDContextKey).(string); ok && id != "" {
		return id
	}
	return "-"
}
