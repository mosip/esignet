/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"fmt"
	"strconv"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

const (
	// ExecutorNameEsignetOTP sends OTP.
	ExecutorNameEsignetOTP = "eSignetOtpExecutor"
	usernameAttr           = "username"
	maskedEmail            = "maskedEmail"
	maskedMobile           = "maskedMobile"

	// propertyKeyMaxAttempts is the NodeProperties key for the per-flow-session cap on OTP
	// generate/resend calls, mirroring the ThunderID engine's own default OTP executor.
	propertyKeyMaxAttempts = "maxAttempts"
	// otpAttemptCountKey stores the running OTP generate/resend count in RuntimeData across
	// node executions within a flow session.
	otpAttemptCountKey = "otpAttemptCount"
	// defaultMaxOTPAttempts is used when the maxAttempts node property is absent or invalid.
	defaultMaxOTPAttempts = 3
)

var individualIDInput = providers.Input{
	Identifier: usernameAttr,
	Type:       providers.InputTypeText,
	Required:   true,
}

type otpExecutor struct {
	baseExecutor
	authn shared.ConsolidatedAuthnProvider
}

var _ providers.Executor = (*otpExecutor)(nil)

// NewOtpExecutor creates an executor that sends OTP via the MOSIP IDA API.
func NewOtpExecutor(authn shared.ConsolidatedAuthnProvider) providers.Executor {
	return &otpExecutor{authn: authn}
}

func (e *otpExecutor) GetName() string {
	return ExecutorNameEsignetOTP
}

func (e *otpExecutor) GetType() providers.ExecutorType {
	return providers.ExecutorTypeAuthentication
}

func (e *otpExecutor) GetMeta() *providers.ExecutorMeta {
	return &providers.ExecutorMeta{
		SupportedProperties: []providers.ExecutorSupportedProperties{
			{Property: propertyKeyMaxAttempts},
		},
	}
}

func (e *otpExecutor) GetPrerequisites() []providers.Input {
	return []providers.Input{individualIDInput}
}

func (e *otpExecutor) GetRequiredInputs(ctx *providers.NodeContext) []providers.Input {
	if len(ctx.NodeInputs) > 0 {
		return ctx.NodeInputs
	}
	return e.GetDefaultInputs()
}

func (e *otpExecutor) ValidatePrerequisites(ctx *providers.NodeContext, _ *providers.ExecutorResponse,
	_ providers.AuthnProviderManager) bool {
	_, err := usernameFromContext(ctx)
	return err == nil
}

func (e *otpExecutor) Execute(ctx *providers.NodeContext) (*providers.ExecutorResponse, error) {
	execResp := &providers.ExecutorResponse{ForwardedData: make(map[string]any)}

	if !e.ensurePrerequisites(ctx, execResp) {
		return execResp, nil
	}

	username, err := usernameFromContext(ctx)
	if err != nil {
		return nil, err
	}

	attemptCount := otpAttemptCountFromContext(ctx)
	if attemptCount >= maxOTPAttemptsFromContext(ctx) {
		applog.GetLogger().Warn(ctx.Context, "maximum OTP attempts reached")
		execResp.Status = providers.ExecFailure
		execResp.Error = shared.MaxOTPAttemptsReachedError
		return execResp, nil
	}

	result, serviceError := e.authn.SendOTP(ctx.Context, map[string]any{usernameAttr: username},
		BuildProviderMetadata(ctx))
	if serviceError != nil {
		// username is the individual's identity number and must not be logged.
		applog.GetLogger().Warn(ctx.Context, "failed to send OTP", applog.String("errorCode", serviceError.Code))
		// Return ExecUserInputRequired (not ExecFailure) so the flow stays alive and re-prompts for
		// the individual ID: ExecFailure maps to NodeStatusFailure, and since this node has no
		// onFailure target configured, that would terminate the flow session (flowStatus: ERROR)
		// instead of letting the user correct a mistyped username and retry.
		if serviceError.Type == common.ClientErrorType {
			execResp.Status = providers.ExecUserInputRequired
			execResp.Inputs = []providers.Input{individualIDInput}
			execResp.Error = serviceError
			return execResp, nil
		}
		// Genuine server-side failures (infrastructure, unexpected API errors) propagate
		// as a Go error so the engine converts them to an HTTP 500.
		return execResp, fmt.Errorf("failed to send OTP: %s", serviceError.Code)
	}

	// Set transaction ID in RuntimeData, all the keys prefixed providerExtendedKeyPrefix will be
	// passed to Authenticate and GetAttribute method of AuthnProvider
	ctx.RuntimeData[providerExtendedKeyPrefix+"TransactionID"] = result.TransactionID
	ctx.RuntimeData[otpAttemptCountKey] = strconv.Itoa(attemptCount + 1)

	execResp.ForwardedData[maskedEmail] = result.MaskedEmail
	execResp.ForwardedData[maskedMobile] = result.MaskedMobile
	execResp.Status = providers.ExecComplete
	return execResp, nil
}

func (e *otpExecutor) ensurePrerequisites(
	ctx *providers.NodeContext, execResp *providers.ExecutorResponse,
) bool {
	if e.ValidatePrerequisites(ctx, execResp, nil) {
		return true
	}

	execResp.Status = providers.ExecUserInputRequired
	execResp.Inputs = []providers.Input{individualIDInput}
	return false
}

// otpAttemptCountFromContext reads the running OTP generate/resend count from RuntimeData,
// defaulting to 0 if absent or unparsable.
func otpAttemptCountFromContext(ctx *providers.NodeContext) int {
	count, err := strconv.Atoi(ctx.RuntimeData[otpAttemptCountKey])
	if err != nil {
		return 0
	}
	return count
}

// maxOTPAttemptsFromContext returns the configured maxAttempts NodeProperty, falling back to
// defaultMaxOTPAttempts if not set or invalid.
func maxOTPAttemptsFromContext(ctx *providers.NodeContext) int {
	switch v := ctx.NodeProperties[propertyKeyMaxAttempts].(type) {
	case string:
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			return n
		}
	case int:
		if v > 0 {
			return v
		}
	case float64:
		if n := int(v); n > 0 {
			return n
		}
	}
	return defaultMaxOTPAttempts
}

func usernameFromContext(ctx *providers.NodeContext) (string, error) {
	if username := ctx.UserInputs[usernameAttr]; username != "" {
		return username, nil
	}
	if username := ctx.RuntimeData[usernameAttr]; username != "" {
		return username, nil
	}
	return "", fmt.Errorf("username not found in context")
}
