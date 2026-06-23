package engine

import (
	"fmt"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/engine/mock"
	"github.com/mosip/esignet/internal/engine/mosip"
	"github.com/mosip/esignet/internal/engine/sunbird"
)

// NewAuthnProviderFromConfig creates a host.AuthnProvider based on configuration.
func NewAuthnProviderFromConfig(provider string, clientSvc *clientmgmt.Service) (host.AuthnProvider, error) {

	switch provider {
	case "mosip":
		cfg := mosip.LoadConfig()
		authnProvider, err := mosip.NewMosipAuthnProvider(cfg, clientSvc)
		if err != nil {
			return nil, err
		}
		return authnProvider, nil
	case "sunbird":
		cfg := sunbird.LoadConfig()
		authnProvider, err := sunbird.NewSunbirdAuthnProvider(cfg)
		if err != nil {
			return nil, err
		}
		return authnProvider, nil
	case "mock":
		return mock.NewMockAuthnProvider(), nil
	default:
		return nil, fmt.Errorf("unsupported authn provider %q (use mosip, sunbird, or mock)", provider)
	}
}
