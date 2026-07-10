package mock

import (
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
)

// Init builds the mock authn provider and its observability provider. Mock
// has no external audit sink, so observability falls back to the logging
// noop auditor.
func Init() (shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider) {
	return NewMockAuthnProvider(), shared.NewNoopAuditor()
}
