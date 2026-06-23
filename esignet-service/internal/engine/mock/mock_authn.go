package mock

import (
	"context"
	"encoding/json"
	"errors"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"

	"github.com/mosip/esignet/internal/engine/mosip"
)

// OTPAuthnProvider extends host.AuthnProvider with OTP send capability.
type OTPAuthnProvider interface {
	host.AuthnProvider
	SendOTP(_ context.Context, identifiers map[string]any, metadata *host.AuthnMetadata) (*mosip.SendOTPResult, error)
}

type mockAuthnProvider struct {
}

// NewMockAuthnProvider creates a new mock authentication provider.
func NewMockAuthnProvider() host.AuthnProvider {
	return &mockAuthnProvider{}
}

var _ OTPAuthnProvider = (*mockAuthnProvider)(nil)

func (p *mockAuthnProvider) SendOTP(_ context.Context, _ map[string]any,
	_ *host.AuthnMetadata) (*mosip.SendOTPResult, error) {
	return &mosip.SendOTPResult{
		MaskedEmail:  "maskedEmail",
		MaskedMobile: "maskedMobile",
	}, nil
}

func (p *mockAuthnProvider) Authenticate(_ context.Context, identifiers, _ map[string]any, _ *host.AuthnMetadata) (*host.AuthnResult, error) {
	username, ok := identifiers["username"].(string)
	if !ok || username == "" {
		return nil, errors.New("missing or invalid username in identifiers")
	}
	return &host.AuthnResult{
		Authenticated: true,
		UserID:        username,
		AuthToken:     "authToken",
		Attributes:    json.RawMessage(`{"email": "mock@email.com", "mobile": "999999999"}`),
	}, nil
}

func (p *mockAuthnProvider) GetAttributes(_ context.Context, _ string, _ *host.RequestedAttributes,
	_ *host.GetAttributesMetadata) (*host.GetAttributesResult, error) {
	return &host.GetAttributesResult{
		Attributes: json.RawMessage(`{"email": "mock@email.com", "mobile": "999999999"}`),
	}, nil
}
