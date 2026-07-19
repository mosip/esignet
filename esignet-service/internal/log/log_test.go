/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package log

import (
	"bytes"
	"encoding/json"
	"errors"
	"log/slog"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func (ts *LogTestSuite) TestParseLogLevel() {
	t := ts.T()
	t.Parallel()

	tests := []struct {
		name      string
		input     string
		expected  slog.Level
		wantError bool
	}{
		{"debug", "debug", slog.LevelDebug, false},
		{"info", "info", slog.LevelInfo, false},
		{"warn", "warn", slog.LevelWarn, false},
		{"error", "error", slog.LevelError, false},
		{"invalid", "invalid", slog.LevelError, true},
		{"empty", "", slog.LevelInfo, true},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			t.Parallel()
			level, err := parseLogLevel(tc.input)
			if tc.wantError {
				require.Error(t, err)
				return
			}
			require.NoError(t, err)
			assert.Equal(t, tc.expected, level)
		})
	}
}

func (ts *LogTestSuite) TestInitLogger() {
	t := ts.T()
	origLogger := logger
	t.Cleanup(func() { logger = origLogger })

	t.Run("valid level", func(t *testing.T) {
		t.Setenv("LOG_LEVEL", "debug")
		require.NoError(t, initLogger())
		assert.NotNil(t, logger)
	})

	t.Run("invalid level", func(t *testing.T) {
		t.Setenv("LOG_LEVEL", "not-a-level")
		err := initLogger()
		require.Error(t, err)
		assert.Contains(t, err.Error(), "error parsing log level")
	})

	t.Run("empty defaults to info", func(t *testing.T) {
		t.Setenv("LOG_LEVEL", "")
		require.NoError(t, initLogger())
		assert.NotNil(t, logger)
	})

	t.Run("empty app name defaults to esignet", func(t *testing.T) {
		t.Setenv("LOG_LEVEL", "info")
		t.Setenv("NAMESPACE", "")
		require.NoError(t, initLogger())
		assert.NotNil(t, logger)
	})

	t.Run("custom app name from NAMESPACE", func(t *testing.T) {
		t.Setenv("LOG_LEVEL", "info")
		t.Setenv("NAMESPACE", "my-app")
		require.NoError(t, initLogger())
		assert.NotNil(t, logger)
	})
}

func (ts *LogTestSuite) TestGetLogger() {
	t := ts.T()
	l1 := GetLogger()
	l2 := GetLogger()
	assert.NotNil(t, l1)
	assert.Same(t, l1, l2, "GetLogger should return the same singleton instance")
}

func (ts *LogTestSuite) TestLoggerMethods() {
	t := ts.T()
	l := GetLogger()

	// These exercise the wrapper methods and convertFields; correctness of the
	// underlying slog output is slog's responsibility, not ours.
	assert.NotPanics(t, func() { l.Debug("debug message", String("k", "v")) })
	assert.NotPanics(t, func() { l.Info("info message", Int("k", 1)) })
	assert.NotPanics(t, func() { l.Warn("warn message", Bool("k", true)) })
	assert.NotPanics(t, func() { l.Error("error message", Error(errors.New("boom"))) })
	assert.NotPanics(t, func() { l.Access(String("req.method", "GET")) })

	child := l.With(String("component", "test"))
	assert.NotNil(t, child)
	assert.NotPanics(t, func() { child.Info("child logger message") })

	named := l.Named("clientmgmt")
	assert.NotNil(t, named)
	assert.NotPanics(t, func() { named.Info("named logger message") })
}

// newTestLogger builds a Logger around a JSON handler writing to buf, using
// the same ReplaceAttr wiring as initLogger, so JSON schema assertions don't
// depend on the package's os.Stdout-bound singleton.
func newTestLogger(buf *bytes.Buffer, level slog.Level) *Logger {
	handler := slog.NewJSONHandler(buf, &slog.HandlerOptions{Level: level, ReplaceAttr: replaceAttr})
	return &Logger{internal: slog.New(handler).With(
		slog.String(versionKey, logVersion),
		slog.String(appNameKey, "esignet"),
	)}
}

func (ts *LogTestSuite) TestJSONOutputSchema() {
	t := ts.T()
	buf := &bytes.Buffer{}
	l := newTestLogger(buf, slog.LevelDebug)

	l.Named("clientmgmt").Info("hello", String("k", "v"))

	var out map[string]any
	require.NoError(t, json.Unmarshal(buf.Bytes(), &out))

	assert.Contains(t, out, timestampKey)
	assert.Equal(t, logVersion, out[versionKey])
	assert.Equal(t, "esignet", out[appNameKey])
	assert.Equal(t, "hello", out[messageKey])
	assert.Equal(t, "clientmgmt", out[loggerKey])
	assert.Equal(t, "INFO", out["level"])
	assert.Equal(t, float64(levelValueInfo), out[levelValueKey])
	assert.Equal(t, "v", out["k"])

	assert.NotContains(t, out, "msg", "slog's default message key should be renamed away")
	assert.NotContains(t, out, "time", "slog's default time key should be renamed away")
}

func (ts *LogTestSuite) TestLevelValueMapping() {
	t := ts.T()

	tests := []struct {
		name     string
		log      func(l *Logger)
		wantName string
		wantVal  float64
	}{
		{"debug", func(l *Logger) { l.Debug("m") }, "DEBUG", levelValueDebug},
		{"info", func(l *Logger) { l.Info("m") }, "INFO", levelValueInfo},
		{"warn", func(l *Logger) { l.Warn("m") }, "WARN", levelValueWarn},
		{"error", func(l *Logger) { l.Error("m") }, "ERROR", levelValueError},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			buf := &bytes.Buffer{}
			l := newTestLogger(buf, slog.LevelDebug)

			tc.log(l)

			var out map[string]any
			require.NoError(t, json.Unmarshal(buf.Bytes(), &out))
			assert.Equal(t, tc.wantName, out["level"])
			assert.Equal(t, tc.wantVal, out[levelValueKey])
		})
	}
}

func (ts *LogTestSuite) TestAccessLevelBypassesLogLevelThreshold() {
	t := ts.T()
	buf := &bytes.Buffer{}
	// Configure a threshold above every normal app level to prove ACCESS
	// records are still emitted.
	l := newTestLogger(buf, slog.LevelError+1)

	l.Access(String("req.method", "GET"), Int("statusCode", 200))

	var out map[string]any
	require.NoError(t, json.Unmarshal(buf.Bytes(), &out))
	assert.Equal(t, accessLevelName, out["level"])
	assert.Equal(t, float64(levelValueAccess), out[levelValueKey])
	assert.Equal(t, "GET", out["req.method"])
	assert.Equal(t, float64(200), out["statusCode"])
}

type LogTestSuite struct {
	suite.Suite
}

func TestLogTestSuite(t *testing.T) {
	suite.Run(t, new(LogTestSuite))
}
