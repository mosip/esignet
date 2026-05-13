// Package logger provides a thin wrapper around [log/slog] that honours
// LOG_LEVEL and LOG_FORMAT environment variables already parsed into strings.
package logger

import (
	"log/slog"
	"os"
	"strings"
)

// New creates a *slog.Logger configured with the supplied level and format.
//
//   - level:  "debug" | "info" | "warn" | "error"  (unknown → info)
//   - format: "json" | "text"                       (unknown → json)
func New(level, format string) *slog.Logger {
	var l slog.Level
	switch strings.ToLower(level) {
	case "debug":
		l = slog.LevelDebug
	case "warn":
		l = slog.LevelWarn
	case "error":
		l = slog.LevelError
	default:
		l = slog.LevelInfo
	}

	opts := &slog.HandlerOptions{
		Level:     l,
		AddSource: l == slog.LevelDebug, // include file:line only in debug mode
	}

	var h slog.Handler
	if strings.ToLower(format) == "text" {
		h = slog.NewTextHandler(os.Stdout, opts)
	} else {
		h = slog.NewJSONHandler(os.Stdout, opts)
	}

	return slog.New(h)
}
