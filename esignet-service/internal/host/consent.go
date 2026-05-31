package host

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"
)

type consentEnforcer struct{}

// NewConsentEnforcer returns a no-op consent enforcer for local development.
func NewConsentEnforcer() host.ConsentEnforcer {
	return &consentEnforcer{}
}

func (c *consentEnforcer) ResolveConsent(ctx context.Context, ouID, appID, agentID, userID string,
	requestedScopes []string) (*host.ConsentResolution, error) {
	_ = ctx
	_ = ouID
	_ = appID
	_ = agentID
	_ = userID
	_ = requestedScopes
	return &host.ConsentResolution{Required: false}, nil
}

func (c *consentEnforcer) RecordConsent(ctx context.Context, ouID, appID, userID string,
	decisions []host.ConsentDecision) error {
	_ = ctx
	_ = ouID
	_ = appID
	_ = userID
	_ = decisions
	return nil
}
