/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package httpmiddleware

import (
	"bufio"
	"fmt"
	"net"
	"net/http"
	"time"

	applog "github.com/mosip/esignet/internal/log"
)

// AccessLog records one Logger.Access entry per request: status code, bytes
// sent, duration, and request metadata, mirroring the Tomcat access log
// format emitted by MOSIP's Java services. Must be nested inside
// CorrelationID so the trace ID is already present in the request context.
func AccessLog(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, statusCode: http.StatusOK}

		next.ServeHTTP(rec, r)

		applog.GetLogger().Access(
			applog.String("traceId", TraceIDFromContext(r.Context())),
			applog.Int("statusCode", rec.statusCode),
			applog.String("req.requestURI", r.RequestURI),
			applog.Int("bytesSent", rec.bytesWritten),
			applog.Any("timeTaken", time.Since(start).Seconds()),
			applog.String("req.userAgent", orDash(r.UserAgent())),
			applog.String("req.xForwardedFor", orDash(r.Header.Get("X-Forwarded-For"))),
			applog.String("req.referer", orDash(r.Referer())),
			applog.String("req.method", r.Method),
			applog.String("req.remoteHost", remoteHost(r.RemoteAddr)),
		)
	})
}

type statusRecorder struct {
	http.ResponseWriter
	statusCode   int
	bytesWritten int
}

func (r *statusRecorder) WriteHeader(code int) {
	r.statusCode = code
	r.ResponseWriter.WriteHeader(code)
}

func (r *statusRecorder) Write(b []byte) (int, error) {
	n, err := r.ResponseWriter.Write(b)
	r.bytesWritten += n
	return n, err
}

// Flush implements http.Flusher by delegating to the wrapped ResponseWriter,
// so streaming handlers (e.g. SSE) still work when wrapped by AccessLog.
func (r *statusRecorder) Flush() {
	if f, ok := r.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}

// Hijack implements http.Hijacker by delegating to the wrapped
// ResponseWriter, so protocol-upgrade handlers (e.g. WebSocket) still work
// when wrapped by AccessLog.
func (r *statusRecorder) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	hj, ok := r.ResponseWriter.(http.Hijacker)
	if !ok {
		return nil, nil, fmt.Errorf("underlying ResponseWriter does not support hijacking")
	}
	return hj.Hijack()
}

func orDash(s string) string {
	if s == "" {
		return "-"
	}
	return s
}

func remoteHost(remoteAddr string) string {
	host, _, err := net.SplitHostPort(remoteAddr)
	if err != nil {
		return orDash(remoteAddr)
	}
	return orDash(host)
}
