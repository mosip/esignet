package engine

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
)

type resourceProvider struct {
	cfg *config.AppConfig
}

// NewResourceProvider returns a stub resource server provider for local development.
func NewResourceProvider(cfg *config.AppConfig) providers.ResourceServerProvider {
	return &resourceProvider{cfg: cfg}
}

func (p *resourceProvider) GetResourceServerByIdentifier(
	_ context.Context, _ string,
) (*providers.ResourceServer, *common.ServiceError) {
	return &providers.ResourceServer{}, nil
}

func (p *resourceProvider) ValidatePermissions(
	_ context.Context, _ string, _ []string,
) ([]string, *common.ServiceError) {
	return []string{}, nil
}

func (p *resourceProvider) FindResourceServersByPermissions(
	_ context.Context, _ []string,
) ([]providers.ResourceServer, *common.ServiceError) {
	return []providers.ResourceServer{}, nil
}
