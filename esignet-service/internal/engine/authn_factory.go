package engine

import (
	"fmt"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/mock"
	"github.com/mosip/esignet/internal/engine/mosip"
	"github.com/mosip/esignet/internal/engine/sunbird"
)

// NewAuthnProvider creates a providers.AuthnProviderManager based on configuration.
func NewAuthnProvider(provider string, appConfig *config.AppConfig, clientSvc *clientmgmt.Service) (providers.AuthnProviderManager, error) {
	switch provider {
	case "mosip":
		authnProvider, err := mosip.NewMosipAuthnProvider(appConfig, clientSvc)
		if err != nil {
			return nil, err
		}
		return authnProvider, nil
	case "sunbird":
		authnProvider, err := sunbird.NewSunbirdAuthnProvider()
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
