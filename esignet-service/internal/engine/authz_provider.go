package engine

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
)

type authorizationProvider struct {
	cfg *config.AppConfig
}

// NewAuthorizationProvider returns a permissive authorization provider for local development.
func NewAuthorizationProvider(cfg *config.AppConfig) providers.AuthorizationProvider {
	return &authorizationProvider{cfg: cfg}
}

func (*authorizationProvider) EvaluateAccess(
	_ context.Context,
	request providers.AccessEvaluationRequest,
) (*providers.AccessEvaluationResponse, *common.ServiceError) {
	return &providers.AccessEvaluationResponse{
		Decision: true,
		Context:  request.Context,
	}, nil
}

func (*authorizationProvider) EvaluateAccessBatch(
	_ context.Context,
	_ providers.AccessEvaluationsRequest,
) (*providers.AccessEvaluationsResponse, *common.ServiceError) {
	return &providers.AccessEvaluationsResponse{
		Evaluations: []providers.AccessEvaluationResponse{
			{
				Decision: true,
			},
		},
	}, nil
}
