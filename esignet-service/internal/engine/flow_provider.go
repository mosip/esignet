/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"os"
	"path/filepath"

	"github.com/stretchr/testify/assert/yaml"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
)

type flowProvider struct {
	cfg *config.AppConfig
}

// NewFlowProvider returns a file-based flow provider backed by the configured data directory.
func NewFlowProvider(cfg *config.AppConfig) providers.FlowProvider {
	return &flowProvider{cfg: cfg}
}

func (p *flowProvider) GetFlowByHandle(_ context.Context, _ string, _ providers.FlowType) (
	*providers.CompleteFlowDefinition, *common.ServiceError) {
	return p.parseFlowDefinition()
}

func (p *flowProvider) GetFlow(_ context.Context, _ string) (*providers.CompleteFlowDefinition, *common.ServiceError) {
	return p.parseFlowDefinition()
}

func (p *flowProvider) parseFlowDefinition() (*providers.CompleteFlowDefinition, *common.ServiceError) {
	// Read the flow definition from file in the data directory.
	data, err := os.ReadFile(filepath.Join(p.cfg.DataDir, "flows", p.cfg.AuthFlowID+".yaml"))
	if err != nil {
		return nil, shared.FileNotFoundError
	}
	var flowDef providers.CompleteFlowDefinition
	err = yaml.Unmarshal(data, &flowDef)
	if err != nil {
		return nil, shared.FileUnmarshallError
	}
	return &flowDef, nil
}
