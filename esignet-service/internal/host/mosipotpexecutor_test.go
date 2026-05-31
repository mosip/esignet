package host

import (
	"context"
	"errors"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/flow"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"

	"github.com/mosip/esignet/internal/catalog"
	"github.com/mosip/esignet/internal/config"
)

type stubOTPAuthnProvider struct {
	result *SendOTPResult
	err    error
	called bool
}

func (s *stubOTPAuthnProvider) Authenticate(context.Context, map[string]interface{}, map[string]interface{},
	*host.AuthnMetadata) (*host.AuthnResult, error) {
	return nil, errors.New("not implemented")
}

func (s *stubOTPAuthnProvider) GetAttributes(context.Context, string, *host.RequestedAttributes,
	*host.GetAttributesMetadata) (*host.GetAttributesResult, error) {
	return nil, errors.New("not implemented")
}

func (s *stubOTPAuthnProvider) SendOTP(_ context.Context, identifiers map[string]interface{},
	_ *host.AuthnMetadata) (*SendOTPResult, error) {
	s.called = true
	if _, ok := identifiers["username"].(string); !ok {
		return nil, errors.New("missing username")
	}
	return s.result, s.err
}

var _ OTPAuthnProvider = (*stubOTPAuthnProvider)(nil)

func newTestMosipOtpExecutor(authn OTPAuthnProvider) *mosipOtpExecutor {
	factory := thunderidengine.NewDefaultFlowFactory()
	return newMosipOtpExecutor(factory, authn).(*mosipOtpExecutor)
}

func TestMosipOtpExecutor_ValidatePrerequisitesPromptsForIndividualID(t *testing.T) {
	exec := newTestMosipOtpExecutor(&stubOTPAuthnProvider{})

	ctx := &flow.NodeContext{
		Context:     context.Background(),
		UserInputs:  map[string]string{},
		RuntimeData: map[string]string{},
	}
	execResp := &flow.ExecutorResponse{ForwardedData: make(map[string]any)}

	ok := exec.ValidatePrerequisites(ctx, execResp)
	assert.False(t, ok)
	assert.Equal(t, flow.ExecUserInputRequired, execResp.Status)
	assert.Len(t, execResp.Inputs, 1)
	assert.Equal(t, usernameAttr, execResp.Inputs[0].Identifier)
}

func TestMosipOtpExecutor_ExecuteSendOTPPopulatesForwardedData(t *testing.T) {
	authn := &stubOTPAuthnProvider{
		result: &SendOTPResult{
			MaskedEmail:  "a***@example.com",
			MaskedMobile: "******1234",
		},
	}
	exec := newTestMosipOtpExecutor(authn)

	ctx := &flow.NodeContext{
		Context:     context.Background(),
		ExecutionID: "exec-1",
		UserInputs:  map[string]string{usernameAttr: "1234567890"},
	}
	resp, err := exec.Execute(ctx)
	require.NoError(t, err)
	require.NotNil(t, resp)
	assert.True(t, authn.called)
	assert.Equal(t, flow.ExecComplete, resp.Status)
	assert.Equal(t, "a***@example.com", resp.ForwardedData["maskedEmail"])
	assert.Equal(t, "******1234", resp.ForwardedData["maskedMobile"])
}

func TestMosipOtpExecutor_ExecuteSendOTPFailure(t *testing.T) {
	authn := &stubOTPAuthnProvider{err: errors.New("ida unavailable")}
	exec := newTestMosipOtpExecutor(authn)

	ctx := &flow.NodeContext{
		Context:     context.Background(),
		ExecutionID: "exec-2",
		UserInputs:  map[string]string{usernameAttr: "1234567890"},
	}
	resp, err := exec.Execute(ctx)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "ida unavailable")
	assert.NotNil(t, resp)
}

func TestRegisterCustomExecutors_SkipsNonMosipProvider(t *testing.T) {
	reg := newTestExecutorRegistry(t)

	err := RegisterCustomExecutors(reg, testFlowFactory(t), CustomExecutorDeps{
		Authn:    NewAuthnProvider(&catalog.Catalog{}),
		AuthnCfg: config.Authn{Provider: config.AuthnProviderCatalog},
	})
	require.NoError(t, err)
	assert.False(t, reg.IsRegistered(ExecutorNameMosipOTP))
}

func TestRegisterCustomExecutors_RegistersMosipExecutor(t *testing.T) {
	reg := newTestExecutorRegistry(t)

	err := RegisterCustomExecutors(reg, testFlowFactory(t), CustomExecutorDeps{
		Authn:    NewMosipAuthnProvider(config.MosipAuthn{}),
		AuthnCfg: config.Authn{Provider: config.AuthnProviderMosip},
	})
	require.NoError(t, err)
	assert.True(t, reg.IsRegistered(ExecutorNameMosipOTP))
}
