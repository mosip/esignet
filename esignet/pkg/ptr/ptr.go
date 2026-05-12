// Package ptr provides nil-safe pointer dereferencing and comparison utilities.
package ptr

// To creates a pointer to a value.
//
// Deprecated: Use new(expr) instead (Go 1.26+).
func To[T any](v T) *T {
	return new(v)
}

// ValueOrZero returns the value of the pointer, or the zero value of T if nil.
func ValueOrZero[T any](p *T) T {
	if p == nil {
		var zero T
		return zero
	}

	return *p
}

// ValueOr returns the value of the pointer, or the fallback if nil.
func ValueOr[T any](p *T, fallback T) T {
	if p == nil {
		return fallback
	}

	return *p
}

// Equal reports whether two pointers point to equal values.
// Returns true if both are nil, false if only one is nil.
func Equal[T comparable](a, b *T) bool {
	if a == nil || b == nil {
		return a == nil && b == nil
	}

	return *a == *b
}
