package mosip

import (
	"fmt"
	"strings"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"

	applog "github.com/mosip/esignet/internal/log"
)

const (
	// ExecutorNameMosipOTP sends OTP via the MOSIP IDA API.
	ExecutorNameMosipOTP = "MosipOtpExecutor"
	usernameAttr         = "username"
)

var individualIDInput = thunderidengine.ExecutorInput{
	Identifier: usernameAttr,
	Type:       "TEXT_INPUT",
	Required:   true,
}

type mosipOtpExecutor struct {
	thunderidengine.ExecutorInterface
	authn OTPAuthnProvider
}

// NewMosipOtpExecutor creates an executor that sends OTP via the MOSIP IDA API.
func NewMosipOtpExecutor(authn OTPAuthnProvider) thunderidengine.ExecutorInterface {
	return &mosipOtpExecutor{
		ExecutorInterface: thunderidengine.NewBaseExecutor(
			ExecutorNameMosipOTP,
			thunderidengine.ExecutorTypeAuthentication,
			nil,
			[]thunderidengine.ExecutorInput{individualIDInput},
		),
		authn: authn,
	}
}

func (e *mosipOtpExecutor) Execute(ctx *thunderidengine.ExecutorNodeContext) (*thunderidengine.ExecutorResponse, error) {
	execResp := &thunderidengine.ExecutorResponse{ForwardedData: make(map[string]any)}

	if !e.ensurePrerequisites(ctx, execResp) {
		return execResp, nil
	}

	username, err := usernameFromContext(ctx)
	if err != nil {
		return nil, err
	}

	result, err := e.authn.SendOTP(ctx.Context, map[string]any{"username": username}, buildAuthnMetadata(ctx))
	if err != nil {
		return execResp, fmt.Errorf("failed to send OTP via MOSIP API: %w", err)
	}

	execResp.ForwardedData["maskedEmail"] = result.MaskedEmail
	execResp.ForwardedData["maskedMobile"] = result.MaskedMobile
	execResp.Status = thunderidengine.ExecComplete
	return execResp, nil
}

func (e *mosipOtpExecutor) ensurePrerequisites(
	ctx *thunderidengine.ExecutorNodeContext, execResp *thunderidengine.ExecutorResponse,
) bool {
	if e.ValidatePrerequisites(ctx, execResp, nil) {
		return true
	}

	execResp.Status = thunderidengine.ExecUserInputRequired
	execResp.Inputs = []thunderidengine.ExecutorInput{individualIDInput}
	return false
}

func buildAuthnMetadata(ctx *thunderidengine.ExecutorNodeContext) *host.AuthnMetadata {
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
		mosipTransactionID, err := GenerateTransactionID(nil)
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

	return &host.AuthnMetadata{AppMetadata: appMetadata, RuntimeMetadata: runtimeMetadata}
}

func usernameFromContext(ctx *thunderidengine.ExecutorNodeContext) (string, error) {
	if username := ctx.UserInputs[usernameAttr]; username != "" {
		return username, nil
	}
	if username := ctx.RuntimeData[usernameAttr]; username != "" {
		return username, nil
	}
	return "", fmt.Errorf("username not found in context")
}
