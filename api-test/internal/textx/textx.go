// Package textx holds the text-truncation helper shared by every call-trace and log renderer.
package textx

import "unicode/utf8"

// Truncate cuts s to at most n bytes, backing off to the last full rune so a
// multi-byte UTF-8 character is never split, and appends suffix when cut.
func Truncate(s string, n int, suffix string) string {
	if len(s) <= n {
		return s
	}
	cut := n
	for cut > 0 && !utf8.RuneStart(s[cut]) {
		cut--
	}
	return s[:cut] + suffix
}
