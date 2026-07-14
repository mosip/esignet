/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"

	"github.com/mosip/esignet/internal/config"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

type idpProvider struct {
	cfg *config.AppConfig
}

// NewIDPProvider returns a file-based provider backed by the configured data directory.
func NewIDPProvider(cfg *config.AppConfig) providers.IDPProvider {
	return &idpProvider{cfg: cfg}
}

func (p *idpProvider) GetIdentityProvidersByProperty(_ context.Context, _,
	_ string) ([]providers.IDPDTO, *common.ServiceError) {
	return nil, nil
}

func (p *idpProvider) GetIdentityProvider(_ context.Context, _ string) (*providers.IDPDTO,
	*common.ServiceError) {
	return nil, nil
}
