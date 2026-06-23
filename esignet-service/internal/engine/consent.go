package engine

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"
)

type consentEnforcer struct{}

// NewConsentEnforcer returns a no-op consent enforcer for local development.
func NewConsentEnforcer() thunderidengine.ConsentEnforcer {
	return &consentEnforcer{}
}

func (*consentEnforcer) ResolveConsent(
	_ context.Context, ouID, appID, appName, userID string,
	essentialAttributes, optionalAttributes, authorizedPermissions []string,
	availableAttributes *thunderidengine.AttributesResponse, forceReprompt bool,
	runtimeMetadata map[string]string,
) (*thunderidengine.ConsentPromptData, *thunderidengine.ServiceError) {
	_ = ouID
	_ = appID
	_ = appName
	_ = userID
	_ = essentialAttributes
	_ = optionalAttributes
	_ = authorizedPermissions
	_ = availableAttributes
	_ = forceReprompt
	_ = runtimeMetadata
	return nil, nil
}

func (*consentEnforcer) RecordConsent(
	_ context.Context, ouID, appID, userID string,
	decisions *thunderidengine.ConsentDecisions, sessionToken string, validityPeriod int64,
	runtimeMetadata map[string]string,
) (*thunderidengine.ConsentRecord, *thunderidengine.ServiceError) {
	_ = ouID
	_ = appID
	_ = userID
	_ = decisions
	_ = sessionToken
	_ = validityPeriod
	_ = runtimeMetadata
	return nil, nil
}
