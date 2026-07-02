package engine

import (
	"context"

	engineconfig "github.com/thunder-id/thunderid/pkg/thunderidengine/config"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

type observabilityProvider struct {
	enabled bool
}

// NewObservabilityProvider returns a no-op observability provider for local development.
func NewObservabilityProvider(cfg engineconfig.ObservabilityConfig) providers.ObservabilityProvider {
	return &observabilityProvider{enabled: cfg.Enabled}
}

func (p *observabilityProvider) PublishEvent(_ context.Context, _ *providers.Event) {}

func (p *observabilityProvider) IsEnabled() bool {
	return p.enabled
}
