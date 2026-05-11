package middleware

import (
	"log/slog"
	"net/http"
	"time"
)

// Logger logs each request with method, path, status code, and duration
// using slog. It captures the status code via a wrapping response writer.
func Logger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rw := &responseWriter{ResponseWriter: w}

		next.ServeHTTP(rw, r)

		if !rw.wrote {
			rw.status = http.StatusOK
		}

		slog.InfoContext(r.Context(), "request",
			slog.String("method", r.Method),
			slog.String("path", r.URL.Path),
			slog.Int("status", rw.status),
			slog.Duration("duration", time.Since(start)),
		)
	})
}

// responseWriter wraps http.ResponseWriter to capture the status code.
type responseWriter struct {
	http.ResponseWriter
	status int
	wrote  bool
}

// WriteHeader captures the status code before delegating.
func (w *responseWriter) WriteHeader(code int) {
	if !w.wrote {
		w.status = code
		w.wrote = true
	}

	w.ResponseWriter.WriteHeader(code)
}

// Write captures a 200 status on first write if WriteHeader was not called.
func (w *responseWriter) Write(b []byte) (int, error) {
	if !w.wrote {
		w.status = http.StatusOK
		w.wrote = true
	}

	return w.ResponseWriter.Write(b)
}

// Unwrap returns the underlying ResponseWriter, allowing
// http.ResponseController to access it.
func (w *responseWriter) Unwrap() http.ResponseWriter {
	return w.ResponseWriter
}
