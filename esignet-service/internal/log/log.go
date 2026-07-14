/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package log provides structured logging via log/slog with LOG_LEVEL configuration.
package log

import (
	"errors"
	"log/slog"
	"os"
	"sync"
)

const (
	logLevelEnvVar  = "LOG_LEVEL"
	defaultLogLevel = "info"
)

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

	handler := slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: level})
	logger = &Logger{internal: slog.New(handler)}
	return nil
}

// With creates a new logger with additional fields.
func (l *Logger) With(fields ...Field) *Logger {
	return &Logger{internal: l.internal.With(convertFields(fields)...)}
}

// Debug logs a debug message.
func (l *Logger) Debug(msg string, fields ...Field) {
	l.internal.Debug(msg, convertFields(fields)...)
}

// Info logs an informational message.
func (l *Logger) Info(msg string, fields ...Field) {
	l.internal.Info(msg, convertFields(fields)...)
}

// Warn logs a warning message.
func (l *Logger) Warn(msg string, fields ...Field) {
	l.internal.Warn(msg, convertFields(fields)...)
}

// Error logs an error message.
func (l *Logger) Error(msg string, fields ...Field) {
	l.internal.Error(msg, convertFields(fields)...)
}

// Fatal logs an error message and exits the process.
func (l *Logger) Fatal(msg string, fields ...Field) {
	l.internal.Error(msg, convertFields(fields)...)
	os.Exit(1)
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
