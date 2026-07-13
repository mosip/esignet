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
)

type ouProvider struct {
	cfg *config.AppConfig
}

// NewOUProvider returns a stub organization unit provider for local development.
func NewOUProvider(cfg *config.AppConfig) providers.OrganizationUnitProvider {
	return &ouProvider{cfg: cfg}
}

func (p *ouProvider) GetOrganizationUnit(_ context.Context, _ string) (providers.OrganizationUnit, *common.ServiceError) {
	return providers.OrganizationUnit{}, nil
}

func (p *ouProvider) GetOrganizationUnitList(_ context.Context, _ int, _ int, _ *common.FilterGroup) (*providers.OrganizationUnitListResponse, *common.ServiceError) {
	return &providers.OrganizationUnitListResponse{}, nil
}

func (p *ouProvider) CreateOrganizationUnit(_ context.Context, _ providers.OrganizationUnitRequestWithID) (providers.OrganizationUnit, *common.ServiceError) {
	return providers.OrganizationUnit{}, nil
}

func (p *ouProvider) IsParent(_ context.Context, _, _ string) (bool, *common.ServiceError) {
	return true, nil
}

func (p *ouProvider) IsOrganizationUnitExists(_ context.Context, _ string) (bool, *common.ServiceError) {
	return true, nil
}

func (p *ouProvider) GetOrganizationUnitChildren(_ context.Context, _ string, _ int, _ int, _ *common.FilterGroup) (*providers.OrganizationUnitListResponse, *common.ServiceError) {
	return &providers.OrganizationUnitListResponse{}, nil
}
