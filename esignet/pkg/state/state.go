// Package state provides generic, file-backed JSON state persistence with
// cross-platform file locking.
package state

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
)

// File manages a JSON file that persists state of type T across restarts.
// It holds an exclusive lock on the file for its entire lifetime to prevent
// concurrent access from multiple process instances.
type File[T any] struct {
	f *os.File
}

// Open creates or opens the file at path, acquires a non-blocking exclusive
// lock, and returns a handle for loading and saving state. If another process
// holds the lock, an error is returned immediately. Call Close to release.
func Open[T any](path string) (*File[T], error) {
	f, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0644)
	if err != nil {
		return nil, fmt.Errorf("opening state file: %w", err)
	}

	if err := lockFile(f); err != nil {
		_ = f.Close()
		return nil, fmt.Errorf("acquiring state file lock: %w", err)
	}

	return &File[T]{f: f}, nil
}

// Load reads and unmarshals the persisted state from disk. Returns
// (nil, nil) if the file is empty (first run).
func (f *File[T]) Load() (*T, error) {
	if _, err := f.f.Seek(0, io.SeekStart); err != nil {
		return nil, fmt.Errorf("seeking state file: %w", err)
	}

	data, err := io.ReadAll(f.f)
	if err != nil {
		return nil, fmt.Errorf("reading state file: %w", err)
	}

	if len(data) == 0 {
		return nil, nil
	}

	var v T
	if err := json.Unmarshal(data, &v); err != nil {
		return nil, fmt.Errorf("decoding state file: %w", err)
	}

	return &v, nil
}

// Save marshals the state, truncates the file, writes the new content,
// and flushes to disk.
func (f *File[T]) Save(v *T) error {
	data, err := json.Marshal(v)
	if err != nil {
		return fmt.Errorf("encoding state: %w", err)
	}

	if err := f.f.Truncate(0); err != nil {
		return fmt.Errorf("truncating state file: %w", err)
	}

	if _, err := f.f.Seek(0, io.SeekStart); err != nil {
		return fmt.Errorf("seeking state file: %w", err)
	}

	if _, err := f.f.Write(data); err != nil {
		return fmt.Errorf("writing state file: %w", err)
	}

	return f.f.Sync()
}

// Close releases the exclusive lock and closes the file.
func (f *File[T]) Close() error {
	_ = unlockFile(f.f)
	return f.f.Close()
}
