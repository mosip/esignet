/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/stretchr/testify/assert/yaml"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

// layoutRequestWithID represents the request structure for creating a layout from file-based config.
type layoutRequestWithID struct {
	ID          string      `yaml:"id"`
	Handle      string      `yaml:"handle"`
	DisplayName string      `yaml:"displayName"`
	Description string      `yaml:"description,omitempty"`
	Layout      interface{} `yaml:"layout"`
}

// themeRequestWithID represents the request structure for creating a theme from file-based config.
type themeRequestWithID struct {
	ID          string      `yaml:"id"`
	Handle      string      `yaml:"handle"`
	DisplayName string      `yaml:"displayName"`
	Description string      `yaml:"description,omitempty"`
	Theme       interface{} `yaml:"theme"`
}

// Layout represents a layout configuration.
type Layout struct {
	ID          string          `json:"id" yaml:"id,omitempty"`
	Handle      string          `json:"handle" yaml:"handle"`
	DisplayName string          `json:"displayName" yaml:"displayName"`
	Description string          `json:"description,omitempty" yaml:"description,omitempty"`
	Layout      json.RawMessage `json:"layout" yaml:"layout"`
	CreatedAt   string          `json:"createdAt" yaml:"createdAt,omitempty"`
	UpdatedAt   string          `json:"updatedAt" yaml:"updatedAt,omitempty"`
	IsReadOnly  bool            `json:"isReadOnly" yaml:"isReadOnly"`
}

// Theme represents a theme configuration.
type Theme struct {
	ID          string          `json:"id" yaml:"id,omitempty"`
	Handle      string          `json:"handle" yaml:"handle"`
	DisplayName string          `json:"displayName" yaml:"displayName"`
	Description string          `json:"description" yaml:"description,omitempty"`
	Theme       json.RawMessage `json:"theme" yaml:"theme"`
	CreatedAt   string          `json:"createdAt" yaml:"createdAt,omitempty"`
	UpdatedAt   string          `json:"updatedAt" yaml:"updatedAt,omitempty"`
	IsReadOnly  bool            `json:"isReadOnly" yaml:"isReadOnly"`
}

// designLayoutCacheNamespace and designThemeCacheNamespace isolate cached
// layout/theme JSON in the shared runtime store.
const (
	designLayoutCacheNamespace providers.RuntimeStoreNamespace = "design:layout"
	designThemeCacheNamespace  providers.RuntimeStoreNamespace = "design:theme"
)

type designProvider struct {
	cfg          *config.AppConfig
	cache        providers.RuntimeStoreProvider
	cacheTTLSecs int64
}

// NewDesignProvider returns a file-based design provider backed by the configured
// data directory. Parsed layout/theme JSON is cached in the runtime store under
// cacheTTLSecs so the files aren't re-read and re-parsed on every request.
func NewDesignProvider(cfg *config.AppConfig, cache providers.RuntimeStoreProvider, cacheTTLSecs int64) providers.DesignProvider {
	return &designProvider{cfg: cfg, cache: cache, cacheTTLSecs: cacheTTLSecs}
}

func (p *designProvider) ResolveDesign(
	ctx context.Context, _ providers.DesignResolveType, _ string,
) (*providers.DesignResponse, *common.ServiceError) {

	if p.cfg.LayoutID == "" || p.cfg.ThemeID == "" {
		return nil, shared.InvalidRequestError
	}

	layoutJSON, svcErr := p.getLayout(ctx)
	if svcErr != nil {
		return nil, svcErr
	}

	themeJSON, svcErr := p.getTheme(ctx)
	if svcErr != nil {
		return nil, svcErr
	}

	designResponse := &providers.DesignResponse{
		Layout: layoutJSON,
		Theme:  themeJSON,
	}
	return designResponse, nil
}

// getLayout returns the layout JSON for p.cfg.LayoutID, serving from cache when possible.
func (p *designProvider) getLayout(ctx context.Context) (json.RawMessage, *common.ServiceError) {
	if data, ok := p.getCached(ctx, designLayoutCacheNamespace, p.cfg.LayoutID); ok {
		return data, nil
	}

	layoutData, err := os.ReadFile(filepath.Join(p.cfg.DataDir, "layouts", p.cfg.LayoutID+".yaml"))
	if err != nil {
		applog.GetLogger().Warn("layout file not found", applog.String("layoutId", p.cfg.LayoutID), applog.Error(err))
		return nil, shared.FileNotFoundError
	}
	layout, err := parseToLayout(layoutData)
	if err != nil {
		applog.GetLogger().Warn("failed to parse layout file", applog.String("layoutId", p.cfg.LayoutID), applog.Error(err))
		return nil, shared.FileUnmarshallError
	}

	p.setCached(ctx, designLayoutCacheNamespace, p.cfg.LayoutID, layout.Layout)
	return layout.Layout, nil
}

// getTheme returns the theme JSON for p.cfg.ThemeID, serving from cache when possible.
func (p *designProvider) getTheme(ctx context.Context) (json.RawMessage, *common.ServiceError) {
	if data, ok := p.getCached(ctx, designThemeCacheNamespace, p.cfg.ThemeID); ok {
		return data, nil
	}

	themeData, err := os.ReadFile(filepath.Join(p.cfg.DataDir, "themes", p.cfg.ThemeID+".yaml"))
	if err != nil {
		applog.GetLogger().Warn("theme file not found", applog.String("themeId", p.cfg.ThemeID), applog.Error(err))
		return nil, shared.FileNotFoundError
	}
	theme, err := parseToTheme(themeData)
	if err != nil {
		applog.GetLogger().Warn("failed to parse theme file", applog.String("themeId", p.cfg.ThemeID), applog.Error(err))
		return nil, shared.FileUnmarshallError
	}

	p.setCached(ctx, designThemeCacheNamespace, p.cfg.ThemeID, theme.Theme)
	return theme.Theme, nil
}

// getCached returns the cached value for key, if present. Any cache read
// error is logged and treated as a miss: the data files remain the source
// of truth, so a cache problem must never fail the request.
func (p *designProvider) getCached(ctx context.Context, namespace providers.RuntimeStoreNamespace, key string) (json.RawMessage, bool) {
	if p.cache == nil {
		return nil, false
	}
	data, err := p.cache.Get(ctx, namespace, key)
	if err != nil {
		applog.GetLogger().Warn("design cache get failed", applog.String("key", key), applog.Error(err))
		return nil, false
	}
	if data == nil {
		return nil, false
	}
	return json.RawMessage(data), true
}

// setCached populates the cache with value. Failures are logged, not
// returned: caching is a pure optimization on top of the successful file read.
func (p *designProvider) setCached(ctx context.Context, namespace providers.RuntimeStoreNamespace, key string, value json.RawMessage) {
	if p.cache == nil || len(value) == 0 {
		return
	}
	if err := p.cache.Put(ctx, namespace, key, value, p.cacheTTLSecs); err != nil {
		applog.GetLogger().Warn("design cache put failed", applog.String("key", key), applog.Error(err))
	}
}

// parseToLayout converts YAML data into a Layout object.
func parseToLayout(data []byte) (*Layout, error) {
	var layoutRequest layoutRequestWithID

	err := yaml.Unmarshal(data, &layoutRequest)
	if err != nil {
		return nil, err
	}

	// Convert layout to JSON bytes
	var layoutJSON json.RawMessage
	if layoutRequest.Layout != nil {
		// Handle both map structure and string format
		switch v := layoutRequest.Layout.(type) {
		case string:
			// JSON string format
			layoutJSON = []byte(v)
		default:
			// Map structure - marshal to JSON
			layoutBytes, err := json.Marshal(layoutRequest.Layout)
			if err != nil {
				return nil, fmt.Errorf("failed to marshal layout to JSON: %w", err)
			}
			layoutJSON = layoutBytes
		}
	}

	layout := &Layout{
		ID:          layoutRequest.ID,
		DisplayName: layoutRequest.DisplayName,
		Description: layoutRequest.Description,
		Layout:      layoutJSON,
	}

	return layout, nil
}

// parseToTheme converts YAML data into a Theme object.
func parseToTheme(data []byte) (*Theme, error) {
	var themeRequest themeRequestWithID

	err := yaml.Unmarshal(data, &themeRequest)
	if err != nil {
		return nil, err
	}

	// Convert theme to JSON bytes
	var themeJSON json.RawMessage
	if themeRequest.Theme != nil {
		// Handle both map structure and string format
		switch v := themeRequest.Theme.(type) {
		case string:
			// JSON string format
			themeJSON = []byte(v)
		default:
			// Map structure - marshal to JSON
			themeBytes, err := json.Marshal(themeRequest.Theme)
			if err != nil {
				return nil, fmt.Errorf("failed to marshal theme to JSON: %w", err)
			}
			themeJSON = themeBytes
		}
	}

	theme := &Theme{
		ID:          themeRequest.ID,
		DisplayName: themeRequest.DisplayName,
		Description: themeRequest.Description,
		Theme:       themeJSON,
	}

	return theme, nil
}
