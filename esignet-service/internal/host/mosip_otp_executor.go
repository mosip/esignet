package host

import (
	"fmt"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/flow"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"

	"github.com/mosip/esignet/internal/config"
	applog "github.com/mosip/esignet/internal/log"
)

const (
	// ExecutorNameMosipOTP sends OTP via the MOSIP IDA API.
	ExecutorNameMosipOTP = "MosipOtpExecutor"
	usernameAttr         = "username"
)

var individualIDInput = flow.Input{
	Identifier: usernameAttr,
	Type:       flow.InputTypeText,
	Required:   true,
}

type mosipOtpExecutor struct {
	flow.Executor
	authn OTPAuthnProvider
}

func newMosipOtpExecutor(factory thunderidengine.FlowFactory, authn OTPAuthnProvider) flow.Executor {
	return &mosipOtpExecutor{
		Executor: factory.CreateExecutor(ExecutorNameMosipOTP, flow.ExecutorTypeAuthentication,
			nil, []flow.Input{individualIDInput}),
		authn: authn,
	}
}

func (e *mosipOtpExecutor) Execute(ctx *flow.NodeContext) (*flow.ExecutorResponse, error) {
	execResp := &flow.ExecutorResponse{ForwardedData: make(map[string]any)}

	if !e.ValidatePrerequisites(ctx, execResp) {
		return execResp, nil
	}

	username, err := usernameFromContext(ctx)
	if err != nil {
		execResp.Status = flow.ExecFailure
		execResp.FailureReason = err.Error()
		return execResp, nil
	}

	result, err := e.authn.SendOTP(ctx.Context, map[string]any{"username": username}, buildAuthnMetadata(ctx))
	if err != nil {
		return execResp, fmt.Errorf("failed to send OTP via MOSIP API: %w", err)
	}

	execResp.ForwardedData["maskedEmail"] = result.MaskedEmail
	execResp.ForwardedData["maskedMobile"] = result.MaskedMobile
	execResp.Status = flow.ExecComplete
	return execResp, nil
}

func (e *mosipOtpExecutor) ValidatePrerequisites(ctx *flow.NodeContext,
	execResp *flow.ExecutorResponse) bool {
	if e.Executor.ValidatePrerequisites(ctx, execResp) {
		return true
	}

	execResp.Status = flow.ExecUserInputRequired
	execResp.Inputs = []flow.Input{individualIDInput}
	return false
}

func buildAuthnMetadata(ctx *flow.NodeContext) *host.AuthnMetadata {
	appMetadata := make(map[string]interface{})

	if ctx.Application.Metadata != nil {
		for key, value := range ctx.Application.Metadata {
			appMetadata[key] = value
		}
	}

	var clientIDs []string
	for _, inboundConfig := range ctx.Application.InboundAuthConfig {
		if inboundConfig.OAuthConfig != nil && inboundConfig.OAuthConfig.ClientID != "" {
			clientIDs = append(clientIDs, inboundConfig.OAuthConfig.ClientID)
		}
	}
	if len(clientIDs) > 0 {
		appMetadata["client_ids"] = clientIDs
	}

	mosipTransactionID, err := GenerateTransactionID(nil)
	if err != nil {
		applog.GetLogger().Warn("failed to generate transaction ID", applog.Error(err))
	} else {
		ctx.RuntimeData["mosipTransactionID"] = mosipTransactionID
	}

	runtimeMetadata := make(map[string]interface{}, len(ctx.RuntimeData))
	for k, v := range ctx.RuntimeData {
		runtimeMetadata[k] = v
	}

	meta := &host.AuthnMetadata{AppMetadata: appMetadata, RuntimeMetadata: runtimeMetadata}

	return meta
}

func usernameFromContext(ctx *flow.NodeContext) (string, error) {
	if username := ctx.UserInputs[usernameAttr]; username != "" {
		return username, nil
	}
	if username := ctx.RuntimeData[usernameAttr]; username != "" {
		return username, nil
	}
	if ctx.AuthenticatedUser.Attributes != nil {
		if id, ok := ctx.AuthenticatedUser.Attributes[usernameAttr]; ok {
			if username, ok := id.(string); ok && username != "" {
				return username, nil
			}
		}
	}
	return "", fmt.Errorf("username not found in context")
}

func registerMosipOtpExecutor(reg thunderidengine.ExecutorRegistry, factory thunderidengine.FlowFactory,
	deps CustomExecutorDeps) error {
	if deps.AuthnCfg.Provider != config.AuthnProviderMosip {
		return nil
	}

	otpAuthn, ok := deps.Authn.(OTPAuthnProvider)
	if !ok {
		return fmt.Errorf("authn provider %q does not support OTP", deps.AuthnCfg.Provider)
	}

	reg.Register(ExecutorNameMosipOTP, newMosipOtpExecutor(factory, otpAuthn))
	return nil
}
