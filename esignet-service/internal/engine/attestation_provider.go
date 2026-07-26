/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package engine provides ThunderID engine host integrations for the embedder.
package engine

import (
	"context"

	"github.com/mosip/esignet/internal/config"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

type attestationProvider struct {
	config *config.AppConfig
}

// NewAttestationProvider returns a providers.AttestationProvider implementation
func NewAttestationProvider(config *config.AppConfig) providers.AttestationProvider {
	return &attestationProvider{config: config}
}

func (p attestationProvider) Verify(ctx context.Context, cfg *providers.AttestationConfig,
	token string) (bool, *common.ServiceError) {
	return true, nil
}
