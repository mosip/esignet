package env

import (
	"fmt"
	"os"
	"reflect"
	"strconv"
	"strings"
	"time"
)

var durationType = reflect.TypeFor[time.Duration]()

// New loads the .env file and returns a T populated from the environment.
// T must be a struct with at least one field tagged `env:"VAR_NAME"`.
//
// Tag format:
//
//	`env:"VAR_NAME"`                — optional, zero value default
//	`env:"VAR_NAME,required"`       — must be set and non-empty
//	`env:"VAR_NAME,default=value"`  — uses value when unset
//
// Supported field types: string, int, int64, bool, time.Duration.
func New[T any](path ...string) (*T, error) {
	if err := Load(path...); err != nil {
		return nil, fmt.Errorf("env: load: %w", err)
	}

	var config T
	if err := populate(&config); err != nil {
		return nil, err
	}

	return &config, nil
}

// populate reads environment variables into a struct using its field tags.
// It returns an error if config is not a pointer to a struct or has no
// fields with env tags.
func populate(config any) error {
	v := reflect.ValueOf(config)
	if v.Kind() != reflect.Pointer || v.Elem().Kind() != reflect.Struct {
		return fmt.Errorf("env: expected pointer to struct, got %T", config)
	}

	v = v.Elem()
	t := v.Type()

	hasTag := false
	for field := range t.Fields() {
		if field.Tag.Get("env") != "" {
			hasTag = true
			break
		}
	}

	if !hasTag {
		return fmt.Errorf("env: %s has no fields with env tags", t.Name())
	}

	var missing []string
	for field := range t.Fields() {
		tag := field.Tag.Get("env")
		if tag == "" {
			continue
		}

		name, rest, hasOpts := strings.Cut(tag, ",")
		var required bool
		var defaultVal string
		if hasOpts {
			required, defaultVal = parseOpts(rest)
		}

		val := os.Getenv(name)
		if val == "" {
			if required {
				missing = append(missing, name)
				continue
			}
			if defaultVal != "" {
				val = defaultVal
			}
		}

		if val == "" {
			continue
		}

		if err := setField(v.FieldByIndex(field.Index), val); err != nil {
			return fmt.Errorf("env: set %s: %w", name, err)
		}
	}

	if len(missing) > 0 {
		return fmt.Errorf("env: missing required variables: %s", strings.Join(missing, ", "))
	}

	return nil
}

// parseOpts extracts the required flag and default value from tag options.
func parseOpts(opts string) (required bool, defaultVal string) {
	for opt := range strings.SplitSeq(opts, ",") {
		switch {
		case opt == "required":
			required = true
		case strings.HasPrefix(opt, "default="):
			defaultVal = strings.TrimPrefix(opt, "default=")
		}
	}

	return required, defaultVal
}

// setField assigns a string value to a reflect.Value based on its type.
func setField(field reflect.Value, val string) error {
	if field.Type() == durationType {
		d, err := time.ParseDuration(val)
		if err != nil {
			return fmt.Errorf("parse duration: %w", err)
		}
		field.SetInt(int64(d))

		return nil
	}

	switch field.Kind() {
	case reflect.String:
		field.SetString(val)

	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		n, err := strconv.ParseInt(val, 10, 64)
		if err != nil {
			return fmt.Errorf("parse int: %w", err)
		}
		field.SetInt(n)

	case reflect.Bool:
		b, err := strconv.ParseBool(val)
		if err != nil {
			return fmt.Errorf("parse bool: %w", err)
		}
		field.SetBool(b)

	default:
		return fmt.Errorf("unsupported type: %s", field.Kind())
	}

	return nil
}
