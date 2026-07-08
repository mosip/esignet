package mock

import (
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
)

// Init builds the mock authn provider and its observability provider. Mock
// has no external audit sink, so observability falls back to the logging
// noop auditor.
func Init(appConfig *config.AppConfig, clientSvc *clientmgmt.Service) (
	shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error) {
	authnProvider, err := NewMockAuthnProvider(appConfig, clientSvc)
	if err != nil {
		return nil, nil, err
	}
	return authnProvider, shared.NewNoopAuditor(), nil
}
