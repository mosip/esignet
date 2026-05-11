// Package apperr defines application-level error types and sentinel errors
// for consistent error handling across service and handler layers.
package apperr

import "errors"

// Sentinel errors for common application error conditions.
var (
	// ErrNotFound indicates the requested resource does not exist.
	ErrNotFound = errors.New("not found")

	// ErrUnauthorized indicates the caller lacks valid credentials.
	ErrUnauthorized = errors.New("unauthorized")

	// ErrConflict indicates the request conflicts with existing state.
	ErrConflict = errors.New("conflict")

	// ErrValidation indicates the input failed validation.
	ErrValidation = errors.New("invalid input")
)

// Error is a structured application error that carries an HTTP status code,
// a human-readable message, and an optional wrapped error.
type Error struct {
	Status  int
	Message string
	Err     error
}

// Error returns the human-readable error message.
func (e *Error) Error() string {
	return e.Message
}

// Unwrap returns the underlying wrapped error.
func (e *Error) Unwrap() error {
	return e.Err
}

// New creates a new Error with the given status code, message, and wrapped error.
func New(status int, message string, err error) *Error {
	return &Error{
		Status:  status,
		Message: message,
		Err:     err,
	}
}
