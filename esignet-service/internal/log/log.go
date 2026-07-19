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
	"log/slog"
	"os"
	"sync"
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

func initLogger() error {
	logLevel := os.Getenv(logLevelEnvVar)
	if logLevel == "" {
		logLevel = defaultLogLevel
	}

	level, err := parseLogLevel(logLevel)
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

// Debug logs a debug message.
func (l *Logger) Debug(msg string, fields ...Field) {
	l.internal.Debug(msg, convertFields(append(fields, Int(levelValueKey, levelValueDebug)))...)
}

// Info logs an informational message.
func (l *Logger) Info(msg string, fields ...Field) {
	l.internal.Info(msg, convertFields(append(fields, Int(levelValueKey, levelValueInfo)))...)
}

// Warn logs a warning message.
func (l *Logger) Warn(msg string, fields ...Field) {
	l.internal.Warn(msg, convertFields(append(fields, Int(levelValueKey, levelValueWarn)))...)
}

// Error logs an error message.
func (l *Logger) Error(msg string, fields ...Field) {
	l.internal.Error(msg, convertFields(append(fields, Int(levelValueKey, levelValueError)))...)
}

// Fatal logs an error message and exits the process.
func (l *Logger) Fatal(msg string, fields ...Field) {
	l.internal.Error(msg, convertFields(append(fields, Int(levelValueKey, levelValueError)))...)
	os.Exit(1)
}

// Access logs an HTTP access record. It always emits regardless of the
// configured LOG_LEVEL, matching the ACCESS pseudo-level convention used by
// the Java services' Tomcat access logs.
func (l *Logger) Access(fields ...Field) {
	l.internal.Log(context.Background(), LevelAccess, "access",
		convertFields(append(fields, Int(levelValueKey, levelValueAccess)))...)
}

func parseLogLevel(logLevel string) (slog.Level, error) {
	var level slog.Level
	if err := level.UnmarshalText([]byte(logLevel)); err != nil {
		return slog.LevelError, err
	}
	return level, nil
}

func convertFields(fields []Field) []any {
	attrs := make([]any, len(fields))
	for i, field := range fields {
		attrs[i] = slog.Any(field.Key, field.Value)
	}
	return attrs
}
