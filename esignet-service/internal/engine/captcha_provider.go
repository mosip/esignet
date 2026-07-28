/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package engine provides ThunderID engine host integrations for the embedder.
package engine

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	applog "github.com/mosip/esignet/internal/log"
)

type captchaProvider struct {
	config *config.CaptchaConfig
	client *http.Client
	logger *applog.Logger
}

// NewCaptchaProvider returns a providers.CaptchaProvider implementation
func NewCaptchaProvider(config *config.CaptchaConfig, client *http.Client) providers.CaptchaValidationProvider {
	logger := applog.GetLogger()
	if !isEnabled(config) {
		logger.Warn("Captcha Validator URL not configured! captch token validation will be ignored.")
	}
	return &captchaProvider{config: config, client: client, logger: logger}
}

// errCaptchaProviderServer is the server-side ServiceError returned for all
// operational failures (transport, non-2xx, malformed response). It signals
// that verification could not be completed and the request should be rejected
// with a server error rather than a client-level captcha-invalid response.
var errCaptchaProviderServer = &common.ServiceError{
	Code: "captcha_provider_error",
	Type: common.ServerErrorType,
	Error: common.I18nMessage{
		Key:          "error.captcha.provider_error",
		DefaultValue: "Captcha service error",
	},
	ErrorDescription: common.I18nMessage{
		Key:          "error.captcha.provider_error_description",
		DefaultValue: "The captcha validation service could not be reached or returned an unexpected response",
	},
}

func (p captchaProvider) Verify(ctx context.Context, token string) (*providers.CaptchaVerificationResult,
	*common.ServiceError) {
	if strings.TrimSpace(token) == "" {
		p.logger.Debug("captcha: token is empty or whitespace-only, skipping verification call",
			applog.Int("tokenLen", len(token)),
		)
		return &providers.CaptchaVerificationResult{Success: false}, nil
	}

	if !isEnabled(p.config) {
		p.logger.Warn("Captcha Validator URL not configured! captch token validation will be ignored.")
		return &providers.CaptchaVerificationResult{Success: true}, nil
	}

	payload, err := json.Marshal(captchaRequest{
		ModuleName:   p.config.ModuleName,
		CaptchaToken: token,
	})
	if err != nil {
		p.logger.Error("captcha: failed to marshal request", applog.Error(err))
		return nil, errCaptchaProviderServer
	}

	if p.config.TimeoutSecs > 0 {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, time.Duration(p.config.TimeoutSecs)*time.Second)
		defer cancel()
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, p.config.ValidatorURL, bytes.NewReader(payload))
	if err != nil {
		p.logger.Error("captcha: failed to build HTTP request", applog.Error(err))
		return nil, errCaptchaProviderServer
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	p.logger.Debug("captcha: sending verification request",
		applog.Int("tokenLen", len(token)),
	)

	resp, err := p.client.Do(req)
	if err != nil {
		p.logger.Error("captcha: HTTP request to validation service failed",
			applog.String("url", p.config.ValidatorURL),
			applog.Error(err),
		)
		return nil, errCaptchaProviderServer
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		p.logger.Error("captcha: validation service returned non-2xx status",
			applog.String("status", fmt.Sprintf("%d", resp.StatusCode)),
		)
		return nil, errCaptchaProviderServer
	}

	var result captchaResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		p.logger.Error("captcha: failed to decode response from validation service",
			applog.Error(err),
		)
		return nil, errCaptchaProviderServer
	}

	// Service returned errors or omitted the response body — negative verdict.
	if len(result.Errors) > 0 || result.Response == nil {
		p.logger.Debug("captcha: validation service returned errors or missing response field",
			applog.Int("errorCount", len(result.Errors)),
			applog.Bool("responseFieldPresent", result.Response != nil),
		)
		for _, svcErr := range result.Errors {
			p.logger.Debug("captcha: validation service error detail",
				applog.String("errorCode", svcErr.ErrorCode),
				applog.String("message", svcErr.Message),
			)
		}
		return &providers.CaptchaVerificationResult{Success: false}, nil
	}

	if !result.Response.Success {
		p.logger.Debug("captcha: validation service returned success=false")
	}

	return &providers.CaptchaVerificationResult{Success: result.Response.Success}, nil
}

// IsEnabled reports whether the provider is configured and should be active.
// Returns false when ValidatorURL is empty.
func isEnabled(cfg *config.CaptchaConfig) bool {
	return strings.TrimSpace(cfg.ValidatorURL) != ""
}

// captchaRequest is the payload sent to the external captcha validation service.
// Modelled on the MOSIP CaptchaHelper POST /v1/captcha/validatecaptcha contract.
type captchaRequest struct {
	ModuleName   string `json:"moduleName"`
	CaptchaToken string `json:"captchaToken"`
}

// captchaResponse is the response returned by the external captcha validation service.
type captchaResponse struct {
	// Errors contains service-level error entries when validation could not
	// succeed (e.g. token rejected, module unknown). A non-empty slice means
	// the token was not accepted, which is a negative verdict — not a server
	// failure.
	Errors []captchaServiceError `json:"errors"`

	// Response carries the verification outcome on a successful 2xx with no
	// errors. It is nil when the service returns errors or an unexpected body.
	Response *captchaVerdict `json:"response"`
}

type captchaVerdict struct {
	Success bool `json:"success"`
}

type captchaServiceError struct {
	ErrorCode string `json:"errorCode"`
	Message   string `json:"message"`
}
