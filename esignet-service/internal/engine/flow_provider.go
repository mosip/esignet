/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"

	"github.com/stretchr/testify/assert/yaml"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

// flowDefinitionCacheNamespace isolates cached flow definition JSON in the shared runtime store.
const flowDefinitionCacheNamespace providers.RuntimeStoreNamespace = "flow:definition"

type flowProvider struct {
	cfg          *config.AppConfig
	cache        providers.RuntimeStoreProvider
	cacheTTLSecs int64
}

// NewFlowProvider returns a file-based flow provider backed by the configured data directory.
// The parsed flow definition is cached in the runtime store under cacheTTLSecs so the flow
// file isn't re-read and re-parsed on every request.
func NewFlowProvider(cfg *config.AppConfig, cache providers.RuntimeStoreProvider, cacheTTLSecs int64) providers.FlowProvider {
	return &flowProvider{cfg: cfg, cache: cache, cacheTTLSecs: cacheTTLSecs}
}

func (p *flowProvider) GetFlowByHandle(ctx context.Context, _ string, _ providers.FlowType) (
	*providers.CompleteFlowDefinition, *common.ServiceError) {
	return p.parseFlowDefinition(ctx)
}

func (p *flowProvider) GetFlow(ctx context.Context, _ string) (*providers.CompleteFlowDefinition, *common.ServiceError) {
	return p.parseFlowDefinition(ctx)
}

func (p *flowProvider) parseFlowDefinition(ctx context.Context) (*providers.CompleteFlowDefinition, *common.ServiceError) {
	if flowDef, ok := p.getCached(ctx); ok {
		return flowDef, nil
	}

	// Read the flow definition from file in the data directory.
	data, err := os.ReadFile(filepath.Join(p.cfg.DataDir, "flows", p.cfg.AuthFlowID+".yaml"))
	if err != nil {
		applog.GetLogger().Warn(ctx, "flow definition file not found", applog.String("flowId", p.cfg.AuthFlowID), applog.Error(err))
		return nil, shared.FileNotFoundError
	}
	var flowDef providers.CompleteFlowDefinition
	expanded := os.ExpandEnv(string(data))
	err = yaml.Unmarshal([]byte(expanded), &flowDef)
	if err != nil {
		applog.GetLogger().Warn(ctx, "failed to parse flow definition file", applog.String("flowId", p.cfg.AuthFlowID), applog.Error(err))
		return nil, shared.FileUnmarshallError
	}

	p.setCached(ctx, &flowDef)
	return &flowDef, nil
}

// getCached returns the cached flow definition for p.cfg.AuthFlowID, if present. Any cache
// read or unmarshal error is logged and treated as a miss: the flow file remains the source
// of truth, so a cache problem must never fail the request.
func (p *flowProvider) getCached(ctx context.Context) (*providers.CompleteFlowDefinition, bool) {
	if p.cache == nil {
		return nil, false
	}
	data, err := p.cache.Get(ctx, flowDefinitionCacheNamespace, p.cfg.AuthFlowID)
	if err != nil {
		applog.GetLogger().Warn(ctx, "flow cache get failed", applog.String("flowId", p.cfg.AuthFlowID), applog.Error(err))
		return nil, false
	}
	if data == nil {
		return nil, false
	}
	var flowDef providers.CompleteFlowDefinition
	if err := json.Unmarshal(data, &flowDef); err != nil {
		applog.GetLogger().Warn(ctx, "failed to unmarshal cached flow definition", applog.String("flowId", p.cfg.AuthFlowID), applog.Error(err))
		return nil, false
	}
	return &flowDef, true
}

// setCached populates the cache with the parsed flow definition. Failures are logged, not
// returned: caching is a pure optimization on top of the successful file read.
func (p *flowProvider) setCached(ctx context.Context, flowDef *providers.CompleteFlowDefinition) {
	if p.cache == nil {
		return
	}
	data, err := json.Marshal(flowDef)
	if err != nil {
		applog.GetLogger().Warn(ctx, "failed to marshal flow definition for cache", applog.String("flowId", p.cfg.AuthFlowID), applog.Error(err))
		return
	}
	if err := p.cache.Put(ctx, flowDefinitionCacheNamespace, p.cfg.AuthFlowID, data, p.cacheTTLSecs); err != nil {
		applog.GetLogger().Warn(ctx, "flow cache put failed", applog.String("flowId", p.cfg.AuthFlowID), applog.Error(err))
	}
}
