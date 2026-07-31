/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package clientmgmt

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"

	"github.com/mosip/esignet/internal/common"
	applog "github.com/mosip/esignet/internal/log"
)

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
			writeSpecError(r.Context(), w, "invalid_input", "malformed request body")
			return
		}
		var req CreateRequestWrapper
		if err := json.Unmarshal(body, &req); err != nil {
			writeSpecError(r.Context(), w, "invalid_input", "malformed JSON body")
			return
		}
		if strings.TrimSpace(req.RequestTime) == "" {
			writeSpecError(r.Context(), w, "invalid_input", "requestTime is required")
			return
		}

		resp, err := h.svc.CreateClient(r.Context(), profile, req.Request)
		if err != nil {
			h.handleServiceError(r.Context(), w, err, "create client")
			return
		}

		h.logger.Debug(r.Context(), "client created", applog.String("profile", string(profile)))
		common.WriteJSON(r.Context(), w, http.StatusOK, ResponseWrapper{
			ResponseWrapper: common.ResponseWrapper{ResponseTime: common.GetResponseTime()},
			Response:        resp.APIResponse(),
		})
	}
}

func (h *Handler) updateClient(profile Profile) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		clientID := r.PathValue("client_id")
		if clientID == "" {
			writeSpecError(r.Context(), w, "invalid_input", "client_id is required")
			return
		}

		r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
		body, err := io.ReadAll(r.Body)
		if err != nil {
			writeSpecError(r.Context(), w, "invalid_input", "malformed request body")
			return
		}
		var req UpdateRequestWrapper
		if err := json.Unmarshal(body, &req); err != nil {
			writeSpecError(r.Context(), w, "invalid_input", "malformed JSON body")
			return
		}
		if strings.TrimSpace(req.RequestTime) == "" {
			writeSpecError(r.Context(), w, "invalid_input", "requestTime is required")
			return
		}

		resp, err := h.svc.UpdateClient(r.Context(), profile, clientID, req.Request)
		if err != nil {
			h.handleServiceError(r.Context(), w, err, "update client")
			return
		}

		h.logger.Debug(r.Context(), "client updated", applog.String("clientId", clientID), applog.String("profile", string(profile)))
		common.WriteJSON(r.Context(), w, http.StatusOK, ResponseWrapper{
			ResponseWrapper: common.ResponseWrapper{ResponseTime: common.GetResponseTime()},
			Response:        resp.APIResponse(),
		})
	}
}

func (h *Handler) patchClient(w http.ResponseWriter, r *http.Request) {
	clientID := r.PathValue("client_id")
	if clientID == "" {
		writeSpecError(r.Context(), w, "invalid_input", "client_id is required")
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	body, err := io.ReadAll(r.Body)
	if err != nil {
		writeSpecError(r.Context(), w, "invalid_input", "malformed request body")
		return
	}
	var wrapper PatchRequestWrapper
	if err := json.Unmarshal(body, &wrapper); err != nil {
		writeSpecError(r.Context(), w, "invalid_input", "malformed JSON body")
		return
	}
	if strings.TrimSpace(wrapper.RequestTime) == "" {
		writeSpecError(r.Context(), w, "invalid_input", "requestTime is required")
		return
	}
	req, fields, err := DecodePatchRequest(body)
	if err != nil {
		writeSpecError(r.Context(), w, "invalid_input", err.Error())
		return
	}

	resp, err := h.svc.PatchClient(r.Context(), clientID, req, fields)
	if err != nil {
		h.handleServiceError(r.Context(), w, err, "patch client")
		return
	}

	h.logger.Debug(r.Context(), "client patched", applog.String("clientId", clientID), applog.Any("fields", fields))
	common.WriteJSON(r.Context(), w, http.StatusOK, ResponseWrapper{
		ResponseWrapper: common.ResponseWrapper{ResponseTime: common.GetResponseTime()},
		Response:        resp.APIResponse(),
	})
}

func (h *Handler) getClient(w http.ResponseWriter, r *http.Request) {
	clientID := r.PathValue("client_id")
	if clientID == "" {
		writeSpecError(r.Context(), w, "invalid_input", "client_id is required")
		return
	}

	resp, err := h.svc.GetClient(r.Context(), clientID)
	if err != nil {
		h.handleServiceError(r.Context(), w, err, "get client")
		return
	}

	h.logger.Debug(r.Context(), "client fetched", applog.String("clientId", clientID))
	common.WriteJSON(r.Context(), w, http.StatusOK, ResponseWrapper{
		ResponseWrapper: common.ResponseWrapper{ResponseTime: common.GetResponseTime()},
		Response:        resp.APIResponse(),
	})
}

func (h *Handler) handleServiceError(ctx context.Context, w http.ResponseWriter, err error, op string) {
	var ve *ValidationError
	switch {
	case errors.As(err, &ve):
		h.logger.Debug(ctx, op+": validation error", applog.String("code", ve.Code))
		writeSpecError(ctx, w, ve.Code, ve.Message)
	case errors.Is(err, ErrClientNotFound):
		h.logger.Debug(ctx, op+": client not found")
		writeSpecError(ctx, w, "invalid_client_id", "client not found")
	case errors.Is(err, ErrDuplicateClientID):
		h.logger.Debug(ctx, op+": duplicate client id")
		writeSpecError(ctx, w, "duplicate_client_id", "client id already exists")
	case errors.Is(err, ErrDuplicatePublicKey):
		h.logger.Debug(ctx, op+": duplicate public key")
		writeSpecError(ctx, w, "invalid_public_key", "public key is already registered")
	case errors.Is(err, ErrClientConflict):
		h.logger.Debug(ctx, op+": concurrent modification conflict")
		writeSpecError(ctx, w, "patch_conflict", "client was modified concurrently; retry the request")
	default:
		h.logger.Error(ctx, op, applog.Error(err))
		common.WriteJSON(ctx, w, http.StatusInternalServerError, ResponseWrapper{
			ResponseWrapper: common.ResponseWrapper{
				Errors:       []common.Error{{ErrorCode: "server_error", ErrorMessage: "an unexpected error occurred"}},
				ResponseTime: common.GetResponseTime(),
			},
		})
	}
}

func writeSpecError(ctx context.Context, w http.ResponseWriter, code, msg string) {
	if msg == "" {
		msg = code
	}
	common.WriteJSON(ctx, w, http.StatusOK, ResponseWrapper{
		ResponseWrapper: common.ResponseWrapper{
			Errors:       []common.Error{{ErrorCode: code, ErrorMessage: msg}},
			ResponseTime: common.GetResponseTime(),
		},
	})
}
