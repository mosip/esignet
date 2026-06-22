package engine

import (
	"context"
	"log"

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
	log.Println("ResolveConsent called", ouID, appID, appName, userID, essentialAttributes, optionalAttributes,
		authorizedPermissions, availableAttributes, forceReprompt, runtimeMetadata)
	return nil, nil
}

func (*consentEnforcer) RecordConsent(
	_ context.Context, ouID, appID, userID string,
	decisions *thunderidengine.ConsentDecisions, sessionToken string, validityPeriod int64,
	runtimeMetadata map[string]string,
) (*thunderidengine.ConsentRecord, *thunderidengine.ServiceError) {
	log.Println("RecordConsent called", ouID, appID, userID, decisions, sessionToken, validityPeriod,
		runtimeMetadata)
	return nil, nil
}
