// Package log initializes the default slog logger with tint for colorized,
// structured output and injects request-scoped values from context.
package log

import (
	"context"
	"log/slog"
	"os"
	"strings"

	"github.com/lmittmann/tint"
	"github.com/mattn/go-colorable"
	"github.com/mattn/go-isatty"
)

type contextKey int

const (
	idempotencyKey contextKey = iota
	requestIDKey
)

// WithIdempotencyKey returns a copy of ctx carrying the given idempotency key.
func WithIdempotencyKey(ctx context.Context, key string) context.Context {
	return context.WithValue(ctx, idempotencyKey, key)
}

// WithRequestID returns a copy of ctx carrying the given request ID.
func WithRequestID(ctx context.Context, id string) context.Context {
	return context.WithValue(ctx, requestIDKey, id)
}

// RequestIDFromContext extracts the request ID from ctx, or returns an
// empty string if none is set.
func RequestIDFromContext(ctx context.Context) string {
	id, _ := ctx.Value(requestIDKey).(string)

	return id
}

// ContextHandler wraps an slog.Handler to inject request-scoped values
// from the context into every log record.
type ContextHandler struct {
	slog.Handler
}

// Handle logs a slog.Record with request-scoped values from the context.
func (h *ContextHandler) Handle(ctx context.Context, r slog.Record) error {
	if key, ok := ctx.Value(idempotencyKey).(string); ok {
		r.AddAttrs(slog.String("idempotency_key", key))
	}

	if id, ok := ctx.Value(requestIDKey).(string); ok {
		r.AddAttrs(slog.String("request_id", id))
	}

	return h.Handler.Handle(ctx, r)
}

func init() {
	w := os.Stdout

	slog.SetDefault(slog.New(
		&ContextHandler{
			Handler: tint.NewHandler(colorable.NewColorable(w), &tint.Options{
				TimeFormat: "Mon, Jan 2 2006, 3:04:05 pm MST",
				NoColor:    !isatty.IsTerminal(w.Fd()),
				Level:      logLevelFromEnv(),
			}),
		}),
	)
}

func logLevelFromEnv() slog.Level {
	level := strings.ToLower(strings.TrimSpace(os.Getenv("LOG_LEVEL")))

	switch level {
	case "debug":
		return slog.LevelDebug
	case "info":
		return slog.LevelInfo
	case "warn", "warning":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}
