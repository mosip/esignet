package middleware

import (
	"log/slog"
	"net/http"
	"runtime/debug"
)

// Recover catches panics in downstream handlers, logs the stack trace,
// and returns a 500 JSON error response.
func Recover(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if v := recover(); v != nil {
				slog.ErrorContext(r.Context(), "panic recovered",
					slog.Any("panic", v),
					slog.String("stack", string(debug.Stack())),
				)

				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusInternalServerError)
				_, _ = w.Write([]byte(`{"error":"internal error","status":500}`))
			}
		}()

		next.ServeHTTP(w, r)
	})
}
