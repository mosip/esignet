/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"fmt"

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

	result, serviceError := e.authn.SendOTP(ctx.Context, map[string]any{usernameAttr: username},
		BuildProviderMetadata(ctx))
	if serviceError != nil {
		// username is the individual's identity number and must not be logged.
		applog.GetLogger().Warn(ctx.Context, "failed to send OTP", applog.String("errorCode", serviceError.Code))
		// Return ExecFailure so the engine surfaces the error to the user without terminating the flow session
		if serviceError.Type == common.ClientErrorType {
			execResp.Status = providers.ExecFailure
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

func usernameFromContext(ctx *providers.NodeContext) (string, error) {
	if username := ctx.UserInputs[usernameAttr]; username != "" {
		return username, nil
	}
	if username := ctx.RuntimeData[usernameAttr]; username != "" {
		return username, nil
	}
	return "", fmt.Errorf("username not found in context")
}
