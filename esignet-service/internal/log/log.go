/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package log provides structured JSON logging via log/slog with LOG_LEVEL
// configuration. The output schema mirrors the logstash-style JSON emitted by
// MOSIP's Java services (@timestamp, message, logger_name, level, level_value,
// appName) so logs from this service line up with the rest of the stack in a
// shared ELK pipeline.
package log

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"strings"
	"sync"

	"github.com/mosip/esignet/internal/reqcontext"
)

const (
	logLevelEnvVar  = "LOG_LEVEL"
	defaultLogLevel = "info"

	appNameEnvVar  = "NAMESPACE"
	defaultAppName = "esignet"

	logVersion = "1"

	timestampKey  = "@timestamp"
	versionKey    = "@version"
	messageKey    = "message"
	loggerKey     = "logger_name"
	appNameKey    = "appName"
	levelValueKey = "level_value"

	levelValueDebug  = 10000
	levelValueInfo   = 20000
	levelValueWarn   = 30000
	levelValueError  = 40000
	levelValueAccess = 70000

	accessLevelName = "ACCESS"
)

// LevelAccess is a pseudo log level for HTTP access logs. It is set above
// slog.LevelError so access log records are never suppressed by LOG_LEVEL.
const LevelAccess slog.Level = slog.LevelError + 32

// traceIDFieldKey is the structured logging field name the Logger's logging
// methods use to carry the trace ID, matching the "traceId" field already
// emitted by Logger.Access.
const traceIDFieldKey = "traceId"

var (
	logger *Logger
	once   sync.Once
)

// Logger is a wrapper around the slog logger.
type Logger struct {
	internal *slog.Logger
}

// GetLogger returns the singleton logger instance.
func GetLogger() *Logger {
	once.Do(func() {
		if err := initLogger(); err != nil {
			panic("failed to initialize logger: " + err.Error())
		}
	})
	return logger
}

// resolveLevel reads the log level from the LOG_LEVEL environment variable
// (falling back to defaultLogLevel when unset), normalizes casing, and
// validates it against supportedLevels. It returns the canonical, lower-cased
// name together with its slog.Level. This service's logger and the embedded
// ThunderID engine both resolve through it, so they cannot diverge on casing
// or an unsupported level.
func resolveLevel() (string, slog.Level, error) {
	name := defaultLogLevel
	if logLevel := os.Getenv(logLevelEnvVar); logLevel != "" {
		name = strings.ToLower(logLevel)
	}
	level, ok := supportedLevels[name]
	if !ok {
		return "", 0, fmt.Errorf("unsupported log level %q", name)
	}
	return name, level, nil
}

// ConfiguredLevel returns the canonical, lower-cased log level name so the
// embedded ThunderID engine is configured from the same value this service's
// logger uses.
func ConfiguredLevel() (string, error) {
	name, _, err := resolveLevel()
	return name, err
}

func initLogger() error {
	_, level, err := resolveLevel()
	if err != nil {
		return errors.New("error parsing log level: " + err.Error())
	}

	appName := os.Getenv(appNameEnvVar)
	if appName == "" {
		appName = defaultAppName
	}

	handler := slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level:       level,
		ReplaceAttr: replaceAttr,
	})
	logger = &Logger{internal: slog.New(handler).With(
		slog.String(versionKey, logVersion),
		slog.String(appNameKey, appName),
	)}
	return nil
}

// replaceAttr renames slog's built-in keys to match the logstash-style
// schema used across MOSIP services, and renders LevelAccess as "ACCESS"
// instead of slog's default "ERROR+32" style formatting.
func replaceAttr(_ []string, a slog.Attr) slog.Attr {
	switch a.Key {
	case slog.TimeKey:
		a.Key = timestampKey
	case slog.MessageKey:
		a.Key = messageKey
	case slog.LevelKey:
		if lvl, ok := a.Value.Any().(slog.Level); ok && lvl == LevelAccess {
			a.Value = slog.StringValue(accessLevelName)
		}
	}
	return a
}

// Named returns a child logger that tags every record with a logger_name
// field, mirroring the class-name-as-logger convention used by the Java
// MOSIP services (e.g. applog.GetLogger().Named("clientmgmt")).
func (l *Logger) Named(name string) *Logger {
	return l.With(String(loggerKey, name))
}

// With creates a new logger with additional fields.
func (l *Logger) With(fields ...Field) *Logger {
	return &Logger{internal: l.internal.With(convertFields(fields)...)}
}

// Debug logs a debug message, tagging it with the trace ID carried by ctx
// (see WithTraceID), or "-" if ctx carries none.
func (l *Logger) Debug(ctx context.Context, msg string, fields ...Field) {
	l.internal.DebugContext(ctx, msg, withLevelValue(withTraceID(ctx, fields), levelValueDebug)...)
}

// Info logs an informational message, tagging it with the trace ID carried
// by ctx (see WithTraceID), or "-" if ctx carries none.
func (l *Logger) Info(ctx context.Context, msg string, fields ...Field) {
	l.internal.InfoContext(ctx, msg, withLevelValue(withTraceID(ctx, fields), levelValueInfo)...)
}

// Warn logs a warning message, tagging it with the trace ID carried by ctx
// (see WithTraceID), or "-" if ctx carries none.
func (l *Logger) Warn(ctx context.Context, msg string, fields ...Field) {
	l.internal.WarnContext(ctx, msg, withLevelValue(withTraceID(ctx, fields), levelValueWarn)...)
}

// Error logs an error message, tagging it with the trace ID carried by ctx
// (see WithTraceID), or "-" if ctx carries none.
func (l *Logger) Error(ctx context.Context, msg string, fields ...Field) {
	l.internal.ErrorContext(ctx, msg, withLevelValue(withTraceID(ctx, fields), levelValueError)...)
}

// Fatal logs an error message and exits the process. It takes no context: it
// is only ever used for unrecoverable startup failures, before any request
// (and its trace ID) exists.
func (l *Logger) Fatal(msg string, fields ...Field) {
	l.internal.Error(msg, withLevelValue(fields, levelValueError)...)
	os.Exit(1)
}

// withTraceID prepends a traceId field (from ctx) to fields. It allocates a
// new slice rather than prepending in place so the caller-supplied fields
// slice is never mutated or aliased.
func withTraceID(ctx context.Context, fields []Field) []Field {
	out := make([]Field, 0, len(fields)+1)
	out = append(out, String(traceIDFieldKey, reqcontext.TraceIDFromContext(ctx)))
	return append(out, fields...)
}

// Access logs an HTTP access record, tagging it with the trace ID carried by
// ctx (see WithTraceID), or "-" if ctx carries none. It
// always emits regardless of the configured LOG_LEVEL, matching the ACCESS
// pseudo-level
// convention used by the Java services' Tomcat access logs.
func (l *Logger) Access(ctx context.Context, fields ...Field) {
	l.internal.Log(ctx, LevelAccess, "access", withLevelValue(withTraceID(ctx, fields), levelValueAccess)...)
}

// withLevelValue converts fields to slog attrs with levelValue appended.
// It converts fields first and appends to the resulting []any rather than
// appending to the caller-supplied fields slice, which could otherwise
// alias and mutate the caller's backing array if it has spare capacity.
func withLevelValue(fields []Field, levelValue int) []any {
	return append(convertFields(fields), slog.Any(levelValueKey, levelValue))
}

// supportedLevels is the single source of truth for the level names this
// service and the embedded ThunderID engine both accept, mapped to their
// slog.Level. Only these values are honored; anything else is rejected by
// resolveLevel rather than silently downgraded.
var supportedLevels = map[string]slog.Level{
	"debug": slog.LevelDebug,
	"info":  slog.LevelInfo,
	"warn":  slog.LevelWarn,
	"error": slog.LevelError,
}

func convertFields(fields []Field) []any {
	attrs := make([]any, len(fields))
	for i, field := range fields {
		attrs[i] = slog.Any(field.Key, field.Value)
	}
	return attrs
}

// NewLogger constructs a Logger from an arbitrary slog.Handler. Useful in
// tests to inject a custom handler (e.g. a counting or buffered handler).
func NewLogger(h slog.Handler) *Logger {
	return &Logger{internal: slog.New(h)}
}

// ReplaceLogger swaps the package-level singleton for l and returns a function
// that restores the original. It ensures the singleton has been initialised
// first so the restore function always has a valid logger to reinstate.
// Intended for tests.
func ReplaceLogger(l *Logger) (restore func()) {
	_ = GetLogger() // ensure once.Do has fired before we swap
	prev := logger
	logger = l
	return func() { logger = prev }
}
