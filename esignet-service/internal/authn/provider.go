// Package authn holds the eSignet-side [pkg/authnprovider.AuthnProvider] used when
// Thunder is embedded. Replace the bodies of Authenticate / GetAttributes with
// MOSIP-backed logic.
package authn

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/authnprovider"
)

// Provider is the custom authentication provider passed into Thunder OAuth / flow.
type Provider struct{}

// New returns a Provider suitable for [github.com/thunder-id/thunderid/pkg/embed.WireThunder].
func New() *Provider {
	return &Provider{}
}

// Authenticate implements [authnprovider.AuthnProvider].
func (p *Provider) Authenticate(
	_ context.Context,
	_, _ map[string]interface{},
	_ *authnprovider.AuthnMetadata,
) (*authnprovider.AuthnResult, *authnprovider.ServiceError) {
	return nil, authnprovider.NewClientError(
		authnprovider.ErrorCodeNotImplemented,
		"authentication not implemented",
		"replace internal/authn.Provider with MOSIP integration",
	)
}

// GetAttributes implements [authnprovider.AuthnProvider].
func (p *Provider) GetAttributes(
	_ context.Context,
	_ string,
	_ *authnprovider.RequestedAttributes,
	_ *authnprovider.GetAttributesMetadata,
) (*authnprovider.GetAttributesResult, *authnprovider.ServiceError) {
	return nil, authnprovider.NewClientError(
		authnprovider.ErrorCodeNotImplemented,
		"get attributes not implemented",
		"replace internal/authn.Provider with MOSIP integration",
	)
}
