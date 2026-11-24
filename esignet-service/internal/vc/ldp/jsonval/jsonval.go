/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package jsonval reads values of JSON-LD documents decoded into any
// (map[string]any, []any, string, json.Number, bool, nil).
package jsonval

import "time"

// Date layouts: ISO-8601 with an offset, falling back to a local date-time
// read as UTC. Seconds and fractions are optional.
var (
	offsetLayouts = []string{"2006-01-02T15:04:05.999999999Z07:00", "2006-01-02T15:04Z07:00"}
	localLayouts  = []string{"2006-01-02T15:04:05.999999999", "2006-01-02T15:04"}
)

// List treats a value as a set: arrays as-is, nil as empty, anything else as
// a single element.
func List(v any) []any {
	switch t := v.(type) {
	case nil:
		return nil
	case []any:
		return t
	default:
		return []any{v}
	}
}

// String returns v if it is a string, else "".
func String(v any) string {
	s, _ := v.(string)
	return s
}

// Strings returns the string members of a string-or-array value.
func Strings(v any) []string {
	var out []string
	for _, e := range List(v) {
		if s, ok := e.(string); ok {
			out = append(out, s)
		}
	}
	return out
}

// ID returns a string value, or the "id" of an object (issuer, holder).
func ID(v any) string {
	switch t := v.(type) {
	case string:
		return t
	case map[string]any:
		return String(t["id"])
	}
	return ""
}

// Objects accepts an object or an array of objects.
func Objects(v any) ([]map[string]any, bool) {
	if m, ok := v.(map[string]any); ok {
		return []map[string]any{m}, true
	}
	arr, ok := v.([]any)
	if !ok {
		return nil, false
	}
	out := make([]map[string]any, 0, len(arr))
	for _, e := range arr {
		m, ok := e.(map[string]any)
		if !ok {
			return nil, false
		}
		out = append(out, m)
	}
	return out, true
}

// Time parses a date-time string; non-strings and other formats fail.
func Time(v any) (time.Time, bool) {
	s, ok := v.(string)
	if !ok {
		return time.Time{}, false
	}
	for _, layout := range offsetLayouts {
		if t, err := time.Parse(layout, s); err == nil {
			return t, true
		}
	}
	for _, layout := range localLayouts {
		if t, err := time.ParseInLocation(layout, s, time.UTC); err == nil {
			return t, true
		}
	}
	return time.Time{}, false
}
