package logger

import (
	"context"
	"log/slog"
	"testing"
)

func TestNew_LevelsAndFormats(t *testing.T) {
	cases := []struct {
		level     string
		format    string
		wantLevel slog.Level
	}{
		{"debug", "json", slog.LevelDebug},
		{"DEBUG", "json", slog.LevelDebug},
		{"info", "json", slog.LevelInfo},
		{"INFO", "text", slog.LevelInfo},
		{"warn", "text", slog.LevelWarn},
		{"WARN", "json", slog.LevelWarn},
		{"error", "json", slog.LevelError},
		{"ERROR", "text", slog.LevelError},
		// unknown → info
		{"", "json", slog.LevelInfo},
		{"verbose", "json", slog.LevelInfo},
		// unknown format → json (just ensure it doesn't panic)
		{"info", "logfmt", slog.LevelInfo},
		{"info", "TEXT", slog.LevelInfo},
		{"info", "text", slog.LevelInfo},
	}

	for _, tc := range cases {
		t.Run(tc.level+"/"+tc.format, func(t *testing.T) {
			log := New(tc.level, tc.format)
			if log == nil {
				t.Fatal("New() returned nil")
			}
			if !log.Enabled(context.TODO(), tc.wantLevel) {
				t.Errorf("New(%q,%q): level %v should be enabled", tc.level, tc.format, tc.wantLevel)
			}
			// One level above the minimum should also be enabled.
			if tc.wantLevel < slog.LevelError && !log.Enabled(context.TODO(), tc.wantLevel+4) {
				t.Errorf("New(%q,%q): next level should be enabled too", tc.level, tc.format)
			}
		})
	}
}

func TestNew_DebugAddsSource(t *testing.T) {
	// debug mode enables source — just ensure no panic and logger works.
	log := New("debug", "json")
	if log == nil {
		t.Fatal("New() returned nil for debug/json")
	}
	log.Debug("source test message", slog.String("key", "val"))
}
