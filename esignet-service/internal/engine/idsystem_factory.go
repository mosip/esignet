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

// NewIDSystemProviders builds the authn provider and its matching observability
// provider for the configured ID system backend. Each backend package
// (mock, mosip, sunbird) owns its own construction, including its own HTTP
// client where one is needed.
func NewIDSystemProviders(appConfig *config.AppConfig, clientSvc *clientmgmt.Service) (
	providers.AuthnProviderManager, providers.ObservabilityProvider, error) {

	switch appConfig.Provider {
	case "mosip":
		return mosip.Init(appConfig, clientSvc)
	case "sunbird":
		return sunbird.Init()
	case "mock":
		return mock.Init(appConfig, clientSvc)
	default:
		return nil, nil, fmt.Errorf("unsupported authn provider %q (use mosip, sunbird, or mock)", appConfig.Provider)
	}
}
