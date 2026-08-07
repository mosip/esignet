/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package keymanager

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/mosip/esignet/internal/common"
	applog "github.com/mosip/esignet/internal/log"
)

// Error codes mirroring io.mosip.esignet.core.constants.ErrorConstants,
// reused as-is (rather than minted fresh) so system-info error responses
// stay consistent with the rest of the Java-derived eSignet API surface.
const (
	errCodeInvalidRequest     = "invalid_request"
	errCodeInvalidCertificate = "invalid_certificate"
)

// maxReferenceIDLength bounds the caller-supplied referenceId query
// parameter on getCertificate, ahead of the semantic allow-list check
// (Service.IsAllowedCertificateRefID) — cheap input-size defense in depth
// before the DB/allow-list lookup.
const maxReferenceIDLength = 128

// Handler exposes client management HTTP endpoints.
type Handler struct {
	svc    *Service
	logger *applog.Logger
}

// NewHandler creates a new Handler.
func NewHandler(svc *Service, logger *applog.Logger) *Handler {
	return &Handler{svc: svc, logger: logger}
}

// RegisterRoutes mounts the system-info routes on mux, optionally protected
// by the given middleware (typically ScopeMiddleware). Pass nil when scope
// enforcement is not configured. Mirrors SystemInfoController's mapping
// (/system-info/certificate, /system-info/uploadCertificate).
func (h *Handler) RegisterRoutes(mux *http.ServeMux, middleware func(http.Handler) http.Handler) {
	if middleware == nil {
		panic("keymanager: RegisterRoutes requires non-nil middleware; getCertificate can mint new key material for any caller-supplied referenceId and must not be exposed unprotected")
	}
	wrap := func(hf http.HandlerFunc) http.Handler {
		return middleware(hf)
	}

	mux.Handle("GET /system-info/certificate", wrap(h.getCertificate))
	mux.Handle("POST /system-info/uploadCertificate", wrap(h.uploadCertificate))
}

// certificateResponse mirrors KeyPairGenerateResponseDto for JSON encoding.
type certificateResponse struct {
	Certificate     string `json:"certificate,omitempty"`
	CertSignRequest string `json:"certSignRequest,omitempty"`
	IssuedAt        string `json:"issuedAt,omitempty"`
	ExpiryAt        string `json:"expiryAt,omitempty"`
	Timestamp       string `json:"timestamp,omitempty"`
}

// certificateResponseWrapper is the MOSIP envelope for getCertificate.
type certificateResponseWrapper struct {
	common.ResponseWrapper
	Response *certificateResponse `json:"response"`
}

// uploadCertificateRequest mirrors UploadCertificateRequestDto for JSON
// decoding.
type uploadCertificateRequest struct {
	ApplicationID   string `json:"applicationId"`
	ReferenceID     string `json:"referenceId"`
	CertificateData string `json:"certificateData"`
}

// uploadCertificateRequestWrapper is the MOSIP envelope for
// uploadCertificate requests.
type uploadCertificateRequestWrapper struct {
	common.RequestWrapper
	Request uploadCertificateRequest `json:"request"`
}

// uploadCertificateResponse mirrors UploadCertificateResponseDto for JSON
// encoding.
type uploadCertificateResponse struct {
	Status    string `json:"status"`
	Timestamp string `json:"timestamp"`
}

// uploadCertificateResponseWrapper is the MOSIP envelope for
// uploadCertificate responses.
type uploadCertificateResponseWrapper struct {
	common.ResponseWrapper
	Response *uploadCertificateResponse `json:"response"`
}

// getCertificate returns the current certificate for (applicationId,
// referenceId) — mirrors SystemInfoController.getCertificate. referenceId
// is optional (blank is meaningful only for ROOT, see ErrBlankReferenceID);
// applicationId is required.
func (h *Handler) getCertificate(w http.ResponseWriter, r *http.Request) {
	applicationID := strings.TrimSpace(r.URL.Query().Get("applicationId"))
	if applicationID == "" {
		common.WriteError(r.Context(), w, http.StatusOK, errCodeInvalidRequest, "applicationId is required")
		return
	}
	referenceID := strings.TrimSpace(r.URL.Query().Get("referenceId"))
	if len(referenceID) > maxReferenceIDLength {
		common.WriteError(r.Context(), w, http.StatusOK, errCodeInvalidRequest, "referenceId is too long")
		return
	}
	if !h.svc.IsAllowedCertificateRefID(referenceID) {
		common.WriteError(r.Context(), w, http.StatusOK, errCodeInvalidRequest, "referenceId is not permitted")
		return
	}

	resp, err := h.svc.GetCertificate(r.Context(), applicationID, referenceID)
	if err != nil {
		h.handleServiceError(r.Context(), w, err, "get certificate")
		return
	}

	h.logger.Debug(r.Context(), "certificate fetched",
		applog.String("applicationId", applicationID), applog.String("referenceId", referenceID))
	common.WriteJSON(r.Context(), w, http.StatusOK, certificateResponseWrapper{
		ResponseWrapper: common.ResponseWrapper{ResponseTime: common.GetResponseTime()},
		Response: &certificateResponse{
			Certificate:     resp.Certificate,
			CertSignRequest: resp.CertSignRequest,
			IssuedAt:        formatMOSIPTime(resp.IssuedAt),
			ExpiryAt:        formatMOSIPTime(resp.ExpiryAt),
			Timestamp:       formatMOSIPTime(resp.Timestamp),
		},
	})
}

// uploadCertificate replaces the certificate for the current alias —
// mirrors SystemInfoController.uploadSignedCertificate.
func (h *Handler) uploadCertificate(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	body, err := io.ReadAll(r.Body)
	if err != nil {
		common.WriteError(r.Context(), w, http.StatusOK, errCodeInvalidRequest, "malformed request body")
		return
	}
	var req uploadCertificateRequestWrapper
	if err := json.Unmarshal(body, &req); err != nil {
		common.WriteError(r.Context(), w, http.StatusOK, errCodeInvalidRequest, "malformed JSON body")
		return
	}
	if strings.TrimSpace(req.Request.ApplicationID) == "" {
		common.WriteError(r.Context(), w, http.StatusOK, errCodeInvalidRequest, "applicationId is required")
		return
	}
	if strings.TrimSpace(req.Request.CertificateData) == "" {
		common.WriteError(r.Context(), w, http.StatusOK, errCodeInvalidRequest, "certificateData is required")
		return
	}

	resp, err := h.svc.UploadCertificate(r.Context(), UploadCertificateRequest{
		ApplicationID:   req.Request.ApplicationID,
		ReferenceID:     req.Request.ReferenceID,
		CertificateData: req.Request.CertificateData,
	})
	if err != nil {
		h.handleServiceError(r.Context(), w, err, "upload certificate")
		return
	}

	h.logger.Debug(r.Context(), "certificate uploaded",
		applog.String("applicationId", req.Request.ApplicationID), applog.String("referenceId", req.Request.ReferenceID))
	common.WriteJSON(r.Context(), w, http.StatusOK, uploadCertificateResponseWrapper{
		ResponseWrapper: common.ResponseWrapper{ResponseTime: common.GetResponseTime()},
		Response: &uploadCertificateResponse{
			Status:    resp.Status,
			Timestamp: formatMOSIPTime(resp.Timestamp),
		},
	})
}

// handleServiceError maps keymanager sentinel errors to the MOSIP error
// codes SystemInfoController would surface, logging at Debug for
// caller-facing validation failures and Error for anything unexpected.
func (h *Handler) handleServiceError(ctx context.Context, w http.ResponseWriter, err error, op string) {
	switch {
	case errors.Is(err, ErrBlankReferenceID),
		errors.Is(err, ErrUnknownApplicationID),
		errors.Is(err, ErrReferenceIDReservedForSymmetricKey),
		errors.Is(err, ErrKeyNotFound):
		h.logger.Debug(ctx, op+": invalid request", applog.Error(err))
		common.WriteError(ctx, w, http.StatusOK, errCodeInvalidRequest, err.Error())
	case errors.Is(err, ErrThumbprintMismatch), errors.Is(err, ErrCertificateAlreadyExists):
		h.logger.Debug(ctx, op+": invalid certificate", applog.Error(err))
		common.WriteError(ctx, w, http.StatusOK, errCodeInvalidCertificate, err.Error())
	default:
		h.logger.Error(ctx, op, applog.Error(err))
		common.WriteError(ctx, w, http.StatusInternalServerError, "server_error", "an unexpected error occurred")
	}
}

// formatMOSIPTime renders t in the MOSIP wire format, or "" for the zero
// value (e.g. certificateResponse.CertSignRequest's IssuedAt/ExpiryAt on the
// CSR path, which buildKeyPairResponse never populates).
func formatMOSIPTime(t time.Time) string {
	if t.IsZero() {
		return ""
	}
	return t.UTC().Format(common.MOSIPTimeLayout)
}
