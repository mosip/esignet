// Package cycle provides a thread-safe round-robin line rotator backed by a
// file on disk.
package cycle

import (
	"bytes"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"sync"
)

// File reads lines from a file and serves them in round-robin order.
// All methods are safe for concurrent use.
type File struct {
	logger   *slog.Logger
	mu       sync.RWMutex
	filename string
	index    int
	lines    []string
}

// New creates a File that reads lines from filePath.
func New(filePath string) (*File, error) {
	absPath, err := filepath.Abs(filePath)
	if err != nil {
		return nil, fmt.Errorf("failed to resolve file path: %w", err)
	}

	logger := slog.With(
		slog.String("file", filepath.Base(absPath)),
	)

	logger.Debug("creating file cycle")

	f := &File{
		index:    0,
		filename: absPath,
		logger:   logger,
	}

	if err := f.load(); err != nil {
		return nil, err
	}

	return f, nil
}

// Reload re-reads the file from disk, replacing the current line set.
func (f *File) Reload() error {
	return f.load()
}

// Next returns the next line in round-robin order. It returns an empty
// string if the rotator has no lines.
func (f *File) Next() string {
	f.mu.Lock()
	defer f.mu.Unlock()

	if len(f.lines) == 0 {
		return ""
	}

	line := f.lines[f.index]
	f.index = (f.index + 1) % len(f.lines)

	if line != "" {
		f.logger.Debug("returning next line", slog.String("line", line), slog.Int("index", f.index-1))
	}

	return line
}

// Count returns the number of lines loaded from the file.
func (f *File) Count() int {
	f.mu.RLock()
	defer f.mu.RUnlock()

	return len(f.lines)
}

// Reset moves the rotation index back to the first line.
func (f *File) Reset() {
	f.mu.Lock()
	defer f.mu.Unlock()

	f.index = 0
}

func (f *File) load() error {
	f.mu.Lock()
	defer f.mu.Unlock()

	fileData, err := os.ReadFile(f.filename)
	if err != nil {
		return fmt.Errorf("read file %s: %w", f.filename, err)
	}

	fileData = bytes.ReplaceAll(fileData, []byte("\r"), []byte(""))

	f.lines = []string{}
	for line := range bytes.SplitSeq(fileData, []byte("\n")) {
		trimmed := bytes.TrimSpace(line)
		if len(trimmed) == 0 {
			continue
		}
		f.lines = append(f.lines, string(trimmed))
	}

	f.logger.Info("loaded lines from file", slog.Int("count", len(f.lines)))

	if len(f.lines) == 0 {
		f.logger.Warn("no lines found in file")
	}

	return nil
}
