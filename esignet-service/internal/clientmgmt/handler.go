package clientmgmt

import (
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"strings"
	"time"

	applog "github.com/mosip/esignet/internal/log"
)

const mosipTimeLayout = "2006-01-02T15:04:05.000Z"

func mosipResponseTime() string {
	return time.Now().UTC().Format(mosipTimeLayout)
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

	mux.Handle("POST /client-mgmt/oidc-client", wrap(h.createClient(ProfileOIDC)))
	mux.Handle("PUT /client-mgmt/oidc-client/{client_id}", wrap(h.updateClient(ProfileOIDC)))
	mux.Handle("POST /client-mgmt/oauth-client", wrap(h.createClient(ProfileOAuth)))
	mux.Handle("PUT /client-mgmt/oauth-client/{client_id}", wrap(h.updateClient(ProfileOAuth)))
	mux.Handle("POST /client-mgmt/client", wrap(h.createClient(ProfileClient)))
	mux.Handle("PUT /client-mgmt/client/{client_id}", wrap(h.updateClient(ProfileClient)))
	mux.Handle("PATCH /client-mgmt/client/{client_id}", wrap(h.patchClient))
	mux.Handle("GET /client-mgmt/client/{client_id}", wrap(h.getClient))
}

func (h *Handler) createClient(profile Profile) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
		body, err := io.ReadAll(r.Body)
		if err != nil {
			writeSpecError(w, "invalid_input", "malformed request body")
			return
		}
		var req CreateRequestWrapper
		if err := json.Unmarshal(body, &req); err != nil {
			writeSpecError(w, "invalid_input", "malformed JSON body")
			return
		}
		if strings.TrimSpace(req.RequestTime) == "" {
			writeSpecError(w, "invalid_input", "requestTime is required")
			return
		}

		resp, err := h.svc.CreateClient(r.Context(), profile, req.Request)
		if err != nil {
			h.handleServiceError(w, err, "create client")
			return
		}

		writeJSON(w, http.StatusOK, ResponseWrapper{
			Response:     resp.APIResponse(),
			ResponseTime: mosipResponseTime(),
		})
	}
}

func (h *Handler) updateClient(profile Profile) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		clientID := r.PathValue("client_id")
		if clientID == "" {
			writeSpecError(w, "invalid_input", "client_id is required")
			return
		}

		r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
		body, err := io.ReadAll(r.Body)
		if err != nil {
			writeSpecError(w, "invalid_input", "malformed request body")
			return
		}
		var req UpdateRequestWrapper
		if err := json.Unmarshal(body, &req); err != nil {
			writeSpecError(w, "invalid_input", "malformed JSON body")
			return
		}
		if strings.TrimSpace(req.RequestTime) == "" {
			writeSpecError(w, "invalid_input", "requestTime is required")
			return
		}

		resp, err := h.svc.UpdateClient(r.Context(), profile, clientID, req.Request)
		if err != nil {
			h.handleServiceError(w, err, "update client")
			return
		}

		writeJSON(w, http.StatusOK, ResponseWrapper{
			Response:     resp.APIResponse(),
			ResponseTime: mosipResponseTime(),
		})
	}
}

func (h *Handler) patchClient(w http.ResponseWriter, r *http.Request) {
	clientID := r.PathValue("client_id")
	if clientID == "" {
		writeSpecError(w, "invalid_input", "client_id is required")
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	body, err := io.ReadAll(r.Body)
	if err != nil {
		writeSpecError(w, "invalid_input", "malformed request body")
		return
	}
	var wrapper PatchRequestWrapper
	if err := json.Unmarshal(body, &wrapper); err != nil {
		writeSpecError(w, "invalid_input", "malformed JSON body")
		return
	}
	if strings.TrimSpace(wrapper.RequestTime) == "" {
		writeSpecError(w, "invalid_input", "requestTime is required")
		return
	}
	req, fields, err := DecodePatchRequest(body)
	if err != nil {
		writeSpecError(w, "invalid_input", err.Error())
		return
	}

	resp, err := h.svc.PatchClient(r.Context(), clientID, req, fields)
	if err != nil {
		h.handleServiceError(w, err, "patch client")
		return
	}

	writeJSON(w, http.StatusOK, ResponseWrapper{
		Response:     resp.APIResponse(),
		ResponseTime: mosipResponseTime(),
	})
}

func (h *Handler) getClient(w http.ResponseWriter, r *http.Request) {
	clientID := r.PathValue("client_id")
	if clientID == "" {
		writeSpecError(w, "invalid_input", "client_id is required")
		return
	}

	resp, err := h.svc.GetClient(r.Context(), clientID)
	if err != nil {
		h.handleServiceError(w, err, "get client")
		return
	}

	writeJSON(w, http.StatusOK, ResponseWrapper{
		Response:     resp.APIResponse(),
		ResponseTime: mosipResponseTime(),
	})
}

func (h *Handler) handleServiceError(w http.ResponseWriter, err error, op string) {
	var ve *ValidationError
	switch {
	case errors.As(err, &ve):
		writeSpecError(w, ve.Code, ve.Message)
	case errors.Is(err, ErrClientNotFound):
		writeSpecError(w, "invalid_client_id", "client not found")
	case errors.Is(err, ErrDuplicateClientID):
		writeSpecError(w, "duplicate_client_id", "client id already exists")
	case errors.Is(err, ErrDuplicatePublicKey):
		writeSpecError(w, "invalid_public_key", "public key is already registered")
	default:
		h.logger.Error(op, applog.Error(err))
		writeJSON(w, http.StatusInternalServerError, ResponseWrapper{
			Errors:       []Error{{ErrorCode: "server_error", ErrorMessage: "an unexpected error occurred"}},
			ResponseTime: mosipResponseTime(),
		})
	}
}

func writeJSON(w http.ResponseWriter, status int, body interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(body); err != nil {
		log.Printf("writeJSON encode error: %v", err)
	}
}

func writeSpecError(w http.ResponseWriter, code, msg string) {
	if msg == "" {
		msg = code
	}
	writeJSON(w, http.StatusOK, ResponseWrapper{
		Errors:       []Error{{ErrorCode: code, ErrorMessage: msg}},
		ResponseTime: mosipResponseTime(),
	})
}

func writeError(w http.ResponseWriter, status int, code, msg string) {
	writeJSON(w, status, ResponseWrapper{
		Errors:       []Error{{ErrorCode: code, ErrorMessage: msg}},
		ResponseTime: mosipResponseTime(),
	})
}
