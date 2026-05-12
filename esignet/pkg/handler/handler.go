// Package handler provides HTTP handlers for the application.
package handler

import (
	"context"
	"errors"
	"log/slog"
	"net/http"

	"github.com/mosip/esignet/pkg/apperr"
	"github.com/mosip/esignet/pkg/respond"
)

// ExampleServicer defines the service methods used by Handler.
type ExampleServicer interface {
	Example(ctx context.Context) (int32, error)
}

// Handler serves HTTP requests using the service layer.
type Handler struct {
	svc ExampleServicer
}

// New creates a Handler backed by the given service.
func New(svc ExampleServicer) *Handler {
	return &Handler{svc: svc}
}

// GetExample handles GET /api/example and returns the result of the
// example query.
func (h *Handler) GetExample(w http.ResponseWriter, r *http.Request) {
	result, err := h.svc.Example(r.Context())
	if err != nil {
		handleError(r.Context(), w, err)
		return
	}

	respond.JSON(w, http.StatusOK, exampleResponse{Result: result})
}

type exampleResponse struct {
	Result int32 `json:"result"`
}

// handleError maps application errors to HTTP responses.
func handleError(ctx context.Context, w http.ResponseWriter, err error) {
	if apiErr, ok := errors.AsType[*apperr.Error](err); ok {
		respond.Error(w, apiErr.Status, apiErr.Message)
		return
	}

	if errors.Is(err, apperr.ErrNotFound) {
		respond.Error(w, http.StatusNotFound, "not found")
		return
	}

	slog.ErrorContext(ctx, "unhandled error", slog.Any("error", err))
	respond.Error(w, http.StatusInternalServerError, "internal error")
}
