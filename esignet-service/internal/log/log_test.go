package log

import (
	"log/slog"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseLogLevel(t *testing.T) {
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
