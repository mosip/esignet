package sunbird

import (
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
)

// Init builds the SunbirdRC KBI authn provider and its observability
// provider. Sunbird has no audit-manager integration, so observability falls
// back to the logging noop auditor.
func Init() (
	shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error) {

	authnProvider, err := NewSunbirdAuthnProvider()
	if err != nil {
		return nil, nil, err
	}
	return authnProvider, shared.NewNoopAuditor(), nil
}
