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

type resourceProvider struct {
	cfg *config.AppConfig
}

// NewResourceProvider returns a providers.ResourceServerProvider backed by the deployment's
// configured resource_servers registry (config.AppConfig.ResourceServers).
func NewResourceProvider(cfg *config.AppConfig) providers.ResourceServerProvider {
	return &resourceProvider{cfg: cfg}
}

func (p *resourceProvider) GetResourceServerByIdentifier(
	_ context.Context, identifier string,
) (*providers.ResourceServer, *common.ServiceError) {
	rs := p.cfg.ResourceServerByIdentifier(identifier)
	if rs == nil {
		return nil, clientError("resource_server_not_found", nil)
	}
	return toProviderResourceServer(rs), nil
}

func (p *resourceProvider) GetResourceServer(
	_ context.Context, id string,
) (*providers.ResourceServer, *common.ServiceError) {
	rs := p.cfg.ResourceServerByID(id)
	if rs == nil {
		return nil, clientError("resource_server_not_found", nil)
	}
	return toProviderResourceServer(rs), nil
}

// ValidatePermissions returns the subset of permissions not defined on the given resource
// server's configured scopes. An unknown resourceServerID invalidates every permission.
func (p *resourceProvider) ValidatePermissions(
	_ context.Context, resourceServerID string, permissions []string,
) ([]string, *common.ServiceError) {
	rs := p.cfg.ResourceServerByID(resourceServerID)
	if rs == nil {
		return permissions, nil
	}
	invalid := make([]string, 0, len(permissions))
	for _, permission := range permissions {
		if _, ok := rs.Scopes[permission]; !ok {
			invalid = append(invalid, permission)
		}
	}
	return invalid, nil
}

func toProviderResourceServer(rs *config.ResourceServerConfig) *providers.ResourceServer {
	return &providers.ResourceServer{
		ID:         rs.ID,
		Name:       rs.ID,
		Identifier: rs.Identifier,
	}
}
