package engine

import (
	"crypto/rand"
	"fmt"
	"strings"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

const (
	// ExecutorNameMosipOTP sends OTP via the MOSIP IDA API.
	ExecutorNameMosipOTP = "MosipOtpExecutor"
	usernameAttr         = "username"
)

var individualIDInput = providers.Input{
	Identifier: usernameAttr,
	Type:       providers.InputTypeText,
	Required:   true,
}

type mosipOtpExecutor struct {
	authn shared.ConsolidatedAuthnProvider
}

var _ providers.Executor = (*mosipOtpExecutor)(nil)

// NewMosipOtpExecutor creates an executor that sends OTP via the MOSIP IDA API.
func NewMosipOtpExecutor(authn shared.ConsolidatedAuthnProvider) providers.Executor {
	return &mosipOtpExecutor{authn: authn}
}

func (e *mosipOtpExecutor) GetName() string {
	return ExecutorNameMosipOTP
}

func (e *mosipOtpExecutor) GetType() providers.ExecutorType {
	return providers.ExecutorTypeAuthentication
}

func (e *mosipOtpExecutor) GetDefaultInputs() []providers.Input {
	return nil
}

func (e *mosipOtpExecutor) GetPrerequisites() []providers.Input {
	return []providers.Input{individualIDInput}
}

func (e *mosipOtpExecutor) GetRequiredInputs(ctx *providers.NodeContext) []providers.Input {
	if len(ctx.NodeInputs) > 0 {
		return ctx.NodeInputs
	}
	return e.GetDefaultInputs()
}

func (e *mosipOtpExecutor) GetExecutionPolicy(_ string) *providers.ExecutionPolicy {
	return nil
}

func (e *mosipOtpExecutor) HasRequiredInputs(_ *providers.NodeContext, _ *providers.ExecutorResponse) bool {
	return true
}

func (e *mosipOtpExecutor) GetUserIDFromContext(_ *providers.NodeContext, _ *providers.ExecutorResponse,
	_ providers.AuthnProviderManager) string {
	return ""
}

func (e *mosipOtpExecutor) ValidatePrerequisites(ctx *providers.NodeContext, _ *providers.ExecutorResponse,
	_ providers.AuthnProviderManager) bool {
	_, err := usernameFromContext(ctx)
	return err == nil
}

func (e *mosipOtpExecutor) Execute(ctx *providers.NodeContext) (*providers.ExecutorResponse, error) {
	execResp := &providers.ExecutorResponse{ForwardedData: make(map[string]any)}

	if !e.ensurePrerequisites(ctx, execResp) {
		return execResp, nil
	}

	username, err := usernameFromContext(ctx)
	if err != nil {
		return nil, err
	}

	result, serviceError := e.authn.SendOTP(ctx.Context, map[string]any{"username": username}, buildAuthnMetadata(ctx))
	if serviceError != nil {
		return execResp, fmt.Errorf("failed to send OTP via MOSIP API: %s", serviceError.Code)
	}

	execResp.ForwardedData["maskedEmail"] = result.MaskedEmail
	execResp.ForwardedData["maskedMobile"] = result.MaskedMobile
	execResp.Status = providers.ExecComplete
	return execResp, nil
}

func (e *mosipOtpExecutor) ensurePrerequisites(
	ctx *providers.NodeContext, execResp *providers.ExecutorResponse,
) bool {
	if e.ValidatePrerequisites(ctx, execResp, nil) {
		return true
	}

	execResp.Status = providers.ExecUserInputRequired
	execResp.Inputs = []providers.Input{individualIDInput}
	return false
}

func buildAuthnMetadata(ctx *providers.NodeContext) *providers.AuthnMetadata {
	appMetadata := make(map[string]any)

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

	if ctx.RuntimeData == nil {
		ctx.RuntimeData = make(map[string]string)
	}

	if _, exists := ctx.RuntimeData["ext_TransactionID"]; !exists {
		mosipTransactionID, err := generateTransactionID()
		if err != nil {
			applog.GetLogger().Warn("failed to generate transaction ID", applog.Error(err))
		} else {
			ctx.RuntimeData["ext_TransactionID"] = mosipTransactionID
		}
	}

	runtimeMetadata := map[string]string{
		"authorization_request_id": ctx.RuntimeData["authorizationRequestId"],
		"current_client_id":        ctx.RuntimeData["clientId"],
	}

	for key, value := range ctx.RuntimeData {
		// Only the ext_* runtime data keys are passed to the authn provider.
		if strings.HasPrefix(key, "ext_") {
			runtimeMetadata[key] = value
		}
	}

	return &providers.AuthnMetadata{AppMetadata: appMetadata, RuntimeMetadata: runtimeMetadata}
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

func generateTransactionID() (string, error) {
	const digitCount = 10
	b := make([]byte, digitCount)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	for i := range b {
		b[i] = '0' + b[i]%10
	}
	return string(b), nil
}
