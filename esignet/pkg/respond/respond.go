// Package respond provides JSON response helpers for HTTP handlers.
package respond

import (
	"encoding/json"
	"log/slog"
	"net/http"
)

// errorResponse is the payload shape used by Error.
type errorResponse struct {
	Error  string `json:"error"`
	Status int    `json:"status"`
}

// JSON writes data as a JSON-encoded HTTP response with the given status code.
func JSON[T any](w http.ResponseWriter, status int, data T) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)

	if err := json.NewEncoder(w).Encode(data); err != nil {
		slog.Error("encode response", slog.Any("error", err))
	}
}

// Error writes a JSON error response with the given status code and message.
func Error(w http.ResponseWriter, status int, message string) {
	JSON(w, status, errorResponse{
		Error:  message,
		Status: status,
	})
}

// NoContent writes a 204 No Content response with no body.
func NoContent(w http.ResponseWriter) {
	w.WriteHeader(http.StatusNoContent)
}
