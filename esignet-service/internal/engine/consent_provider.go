package engine

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

type consentEnforcer struct{}

// NewConsentEnforcer returns a no-op consent enforcer for local development.
func NewConsentEnforcer() providers.ConsentProvider {
	return &consentEnforcer{}
}

func (*consentEnforcer) ResolveConsent(_ context.Context, _, _, _, _ string,
	_, _, _ []string,
	_ *providers.AttributesResponse, _ bool,
	_ map[string]string) (
	*providers.ConsentPromptData, *common.ServiceError) {

	return nil, nil
}

// RecordConsent records the user's consent decisions and returns the persisted consent record.
// If the user denied any essential attribute, ErrorEssentialConsentDenied is returned.
func (*consentEnforcer) RecordConsent(_ context.Context, _, _, _ string,
	_ *providers.ConsentDecisions, _ string, _ int64,
	_ map[string]string) (
	*providers.Consent, *common.ServiceError) {

	return nil, nil
}
