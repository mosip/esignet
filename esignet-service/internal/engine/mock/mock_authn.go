package mock

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
)

type mockAuthnProvider struct {
}

// NewMockAuthnProvider creates a new mock authentication provider.
func NewMockAuthnProvider() shared.ConsolidatedAuthnProvider {
	return &mockAuthnProvider{}
}

func (p *mockAuthnProvider) SendOTP(_ context.Context, _ map[string]interface{},
	_ *providers.AuthnMetadata) (*shared.SendOTPResult, *common.ServiceError) {
	return &shared.SendOTPResult{
		MaskedEmail:  "maskedEmail",
		MaskedMobile: "maskedMobile",
	}, nil
}

func (p *mockAuthnProvider) AuthenticateUser(_ context.Context, _, _ map[string]interface{},
	_ *providers.RequestedAttributes,
	_ *providers.AuthnMetadata,
	authUser providers.AuthUser) (providers.AuthUser, providers.AuthenticatedClaims, *common.ServiceError) {
	authUser.SetAttributeToken("authToken")
	authUser.SetEntityReferenceToken("entityReferenceToken")
	return authUser, nil, nil
}

func (p *mockAuthnProvider) GetEntityReference(_ context.Context, authUser providers.AuthUser) (
	providers.AuthUser, *providers.EntityReference, *common.ServiceError) {
	return authUser, nil, nil
}

func (p *mockAuthnProvider) GetUserAvailableAttributes(_ context.Context,
	_ providers.AuthUser) (*providers.AttributesResponse, *common.ServiceError) {
	return nil, nil
}

func (p *mockAuthnProvider) GetUserAttributes(_ context.Context,
	_ *providers.RequestedAttributes,
	_ *providers.GetAttributesMetadata,
	authUser providers.AuthUser) (providers.AuthUser, *providers.AttributesResponse, *common.ServiceError) {

	return authUser, &providers.AttributesResponse{
		Attributes: map[string]*providers.AttributeResponse{
			"email": {
				Value: "mock@email.com",
			},
			"mobile": {
				Value: "999999999",
			},
		},
	}, nil
}
