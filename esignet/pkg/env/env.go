// Package env loads environment variables from a .env file into the process
// environment and validates required variables into a typed Config.
// Existing process environment variables take precedence over file-defined values.
package env

import (
	"bufio"
	"fmt"
	"log/slog"
	"os"
	"strings"
	"sync"
)

var (
	mu     sync.Mutex
	loaded = make(map[string]error)
)

// Load reads key=value pairs from a .env file and sets them in the process
// environment. Existing variables are not overwritten. Repeated calls with
// the same path are no-ops and return the cached result.
//
// If no path is provided, ".env" is used.
func Load(path ...string) error {
	filename := ".env"
	if len(path) > 0 && path[0] != "" {
		filename = path[0]
	}

	mu.Lock()
	defer mu.Unlock()

	if err, ok := loaded[filename]; ok {
		return err
	}

	err := load(filename)
	loaded[filename] = err

	return err
}

func load(filename string) error {
	_, err := os.Stat(filename)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return fmt.Errorf("stat %q: %w", filename, err)
	}

	f, err := os.Open(filename)
	if err != nil {
		return fmt.Errorf("open %q: %w", filename, err)
	}
	defer func() { _ = f.Close() }()

	vars := make(map[string]string)
	scanner := bufio.NewScanner(f)

	for n := 1; scanner.Scan(); n++ {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}

		key, value, ok := strings.Cut(line, "=")
		if !ok {
			slog.Warn("env: skipping invalid line", "file", filename, "line", n)
			continue
		}

		key = strings.TrimSpace(key)
		value = strings.TrimSpace(value)

		if len(value) >= 2 && (value[0] == '"' || value[0] == '\'') && value[0] == value[len(value)-1] {
			value = value[1 : len(value)-1]
		}

		vars[key] = value
	}

	if err := scanner.Err(); err != nil {
		return fmt.Errorf("scan %q: %w", filename, err)
	}

	for key, value := range vars {
		if _, exists := os.LookupEnv(key); exists {
			continue
		}
		if err := os.Setenv(key, value); err != nil {
			slog.Warn("env: failed to set variable", "key", key, "error", err)
		}
	}

	return nil
}
