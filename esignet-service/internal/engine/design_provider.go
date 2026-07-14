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

type designProvider struct {
	cfg *config.AppConfig
}

// NewDesignProvider returns a file-based design provider backed by the configured data directory.
func NewDesignProvider(cfg *config.AppConfig) providers.DesignProvider {
	return &designProvider{cfg: cfg}
}

func (p *designProvider) ResolveDesign(
	_ context.Context, _ providers.DesignResolveType, _ string,
) (*providers.DesignResponse, *common.ServiceError) {

	if p.cfg.LayoutID == "" || p.cfg.ThemeID == "" {
		return nil, shared.InvalidRequestError
	}

	layoutData, err := os.ReadFile(filepath.Join(p.cfg.DataDir, "layouts", p.cfg.LayoutID+".yaml"))
	if err != nil {
		return nil, shared.FileNotFoundError
	}
	layout, err := parseToLayout(layoutData)
	if err != nil {
		return nil, shared.FileUnmarshallError
	}

	themeData, err := os.ReadFile(filepath.Join(p.cfg.DataDir, "themes", p.cfg.ThemeID+".yaml"))
	if err != nil {
		return nil, shared.FileNotFoundError
	}
	theme, err := parseToTheme(themeData)
	if err != nil {
		return nil, shared.FileUnmarshallError
	}

	designResponse := &providers.DesignResponse{
		Layout: layout.Layout,
		Theme:  theme.Theme,
	}
	return designResponse, nil
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
