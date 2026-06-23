package clientmgmt

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"

	applog "github.com/mosip/esignet/internal/log"
)

// apiResponse is the standard envelope for all client management API responses.
type apiResponse struct {
	Errors   []apiError  `json:"errors,omitempty"`
	Response interface{} `json:"response,omitempty"`
}

type apiError struct {
	ErrorCode    string `json:"errorCode"`
	ErrorMessage string `json:"errorMessage"`
}

// Handler exposes client management HTTP endpoints.
type Handler struct {
	svc    *Service
	logger *applog.Logger
}

// NewHandler creates a new Handler.
func NewHandler(svc *Service, logger *applog.Logger) *Handler {
	return &Handler{svc: svc, logger: logger}
}

// RegisterRoutes mounts the client management routes on mux, optionally protected
// by the given middleware (typically ScopeMiddleware). Pass nil when scope
// enforcement is not configured.
func (h *Handler) RegisterRoutes(mux *http.ServeMux, middleware func(http.Handler) http.Handler) {
	wrap := func(hf http.HandlerFunc) http.Handler {
		if middleware == nil {
			return hf
		}
		return middleware(hf)
	}
	mux.Handle("POST /client-mgmt/oidc-client", wrap(h.createClient))
	mux.Handle("PUT /client-mgmt/oidc-client/{client_id}", wrap(h.updateClient))
	mux.Handle("GET /client-mgmt/oidc-client/{client_id}", wrap(h.getClient))
}

func (h *Handler) createClient(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20) // 1 MB limit
	var req CreateClientRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_request", err.Error())
		return
	}

	resp, err := h.svc.CreateClient(r.Context(), req)
	if err != nil {
		h.handleServiceError(w, err, "create client")
		return
	}

	writeJSON(w, http.StatusOK, apiResponse{Response: resp})
}

func (h *Handler) updateClient(w http.ResponseWriter, r *http.Request) {
	clientID := r.PathValue("client_id")
	if clientID == "" {
		writeError(w, http.StatusBadRequest, "invalid_request", "client_id is required")
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, 1<<20) // 1 MB limit
	var req UpdateClientRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_request", "malformed JSON body")
		return
	}

	resp, err := h.svc.UpdateClient(r.Context(), clientID, req)
	if err != nil {
		h.handleServiceError(w, err, "update client")
		return
	}

	writeJSON(w, http.StatusOK, apiResponse{Response: resp})
}

func (h *Handler) getClient(w http.ResponseWriter, r *http.Request) {
	clientID := r.PathValue("client_id")
	if clientID == "" {
		writeError(w, http.StatusBadRequest, "invalid_request", "client_id is required")
		return
	}

	resp, err := h.svc.GetClient(r.Context(), clientID)
	if err != nil {
		h.handleServiceError(w, err, "get client")
		return
	}

	writeJSON(w, http.StatusOK, apiResponse{Response: resp})
}

func (h *Handler) handleServiceError(w http.ResponseWriter, err error, op string) {
	switch {
	case errors.Is(err, ErrClientNotFound):
		writeError(w, http.StatusNotFound, "client_not_found", "client not found")
	case errors.Is(err, ErrDuplicatePublicKey):
		writeError(w, http.StatusConflict, "duplicate_public_key", "public key is already registered")
	default:
		h.logger.Error(op, applog.Error(err))
		writeError(w, http.StatusInternalServerError, "server_error", "an unexpected error occurred")
	}
}

func writeJSON(w http.ResponseWriter, status int, body interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(body); err != nil {
		// Response headers already sent; log only.
		log.Printf("writeJSON encode error: %v", err)
	}
}

func writeError(w http.ResponseWriter, status int, code, msg string) {
	writeJSON(w, status, apiResponse{
		Errors: []apiError{{ErrorCode: code, ErrorMessage: msg}},
	})
}
