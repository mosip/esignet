package mock

import (
	"context"
	"encoding/json"
	"log"

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

func (p *mockAuthnProvider) SendOTP(_ context.Context, identifiers map[string]any,
	metadata *host.AuthnMetadata) (*mosip.SendOTPResult, error) {
	log.Println("SendOTP called", identifiers, metadata)
	return &mosip.SendOTPResult{
		MaskedEmail:  "maskedEmail",
		MaskedMobile: "maskedMobile",
	}, nil
}

func (p *mockAuthnProvider) Authenticate(_ context.Context, identifiers, credentials map[string]any, metadata *host.AuthnMetadata) (*host.AuthnResult, error) {
	log.Println("Authenticate called", identifiers, credentials, metadata)
	return &host.AuthnResult{
		Authenticated: true,
		UserID:        identifiers["username"].(string),
		AuthToken:     "authToken",
		Attributes:    json.RawMessage(`{"email": "mock@email.com", "mobile": "999999999"}`),
	}, nil
}

func (p *mockAuthnProvider) GetAttributes(_ context.Context, token string, requested *host.RequestedAttributes,
	getAttributesMetadata *host.GetAttributesMetadata) (*host.GetAttributesResult, error) {
	log.Println("GetAttributes called", token, requested, getAttributesMetadata)
	return &host.GetAttributesResult{
		Attributes: json.RawMessage(`{"email": "mock@email.com", "mobile": "999999999"}`),
	}, nil
}
