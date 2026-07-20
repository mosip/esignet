/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	applog "github.com/mosip/esignet/internal/log"
)

type authorizationProvider struct {
	cfg *config.AppConfig
}

// NewAuthorizationProvider returns a permissive authorization provider for local development.
func NewAuthorizationProvider(cfg *config.AppConfig) providers.AuthorizationProvider {
	applog.GetLogger().Warn("authorization provider running in permissive mode: every access evaluation is granted; not for production use")
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
