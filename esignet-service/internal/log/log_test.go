/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package log

import (
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

	child := l.With(String("component", "test"))
	assert.NotNil(t, child)
	assert.NotPanics(t, func() { child.Info("child logger message") })
}

type LogTestSuite struct {
	suite.Suite
}

func TestLogTestSuite(t *testing.T) {
	suite.Run(t, new(LogTestSuite))
}
