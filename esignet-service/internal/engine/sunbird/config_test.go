/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package sunbird

import (
	"testing"

	"github.com/stretchr/testify/suite"
)

func clearSunbirdEnv(t *testing.T) {
	t.Helper()
	for _, key := range []string{
		envSunbirdIDField, envSunbirdFieldDetails, envSunbirdSearchURL,
		envSunbirdEntityIDField, envSunbirdClaimsMapping, envSunbirdEntityURL, envSunbirdTimeout,
	} {
		t.Setenv(key, "")
	}
}

func (ts *ConfigTestSuite) TestLoadConfigDefaults() {
	t := ts.T()
	clearSunbirdEnv(t)

	cfg := LoadConfig()
	if cfg.SearchURL != "" {
		t.Errorf("SearchURL = %q, want empty", cfg.SearchURL)
	}
	if cfg.IDField != defaultSunbirdIDField {
		t.Errorf("IDField = %q, want %q", cfg.IDField, defaultSunbirdIDField)
	}
	if cfg.EntityIDField != defaultSunbirdEntityIDField {
		t.Errorf("EntityIDField = %q, want %q", cfg.EntityIDField, defaultSunbirdEntityIDField)
	}
	if cfg.FieldDetails != defaultSunbirdFieldDetails {
		t.Errorf("FieldDetails = %q, want default", cfg.FieldDetails)
	}
	if cfg.ClaimsMapping != defaultSunbirdClaimsMapping {
		t.Errorf("ClaimsMapping = %q, want default", cfg.ClaimsMapping)
	}
	if cfg.TimeoutSecs != defaultSunbirdTimeoutSecs {
		t.Errorf("TimeoutSecs = %d, want %d", cfg.TimeoutSecs, defaultSunbirdTimeoutSecs)
	}
}

func (ts *ConfigTestSuite) TestLoadConfigOverrides() {
	t := ts.T()
	clearSunbirdEnv(t)
	t.Setenv(envSunbirdSearchURL, "  http://example.com/search  ")
	t.Setenv(envSunbirdEntityURL, "http://example.com/entity///")
	t.Setenv(envSunbirdIDField, "custom_id")
	t.Setenv(envSunbirdEntityIDField, "custom_osid")
	t.Setenv(envSunbirdTimeout, "45")

	cfg := LoadConfig()
	if cfg.SearchURL != "http://example.com/search" {
		t.Errorf("SearchURL = %q, want trimmed", cfg.SearchURL)
	}
	if cfg.EntityURL != "http://example.com/entity" {
		t.Errorf("EntityURL = %q, want trailing slashes trimmed", cfg.EntityURL)
	}
	if cfg.IDField != "custom_id" {
		t.Errorf("IDField = %q, want custom_id", cfg.IDField)
	}
	if cfg.EntityIDField != "custom_osid" {
		t.Errorf("EntityIDField = %q, want custom_osid", cfg.EntityIDField)
	}
	if cfg.TimeoutSecs != 45 {
		t.Errorf("TimeoutSecs = %d, want 45", cfg.TimeoutSecs)
	}
}

func (ts *ConfigTestSuite) TestLoadConfigInvalidTimeoutFallsBackToDefault() {
	t := ts.T()
	clearSunbirdEnv(t)
	t.Setenv(envSunbirdTimeout, "not-a-number")
	if cfg := LoadConfig(); cfg.TimeoutSecs != defaultSunbirdTimeoutSecs {
		t.Errorf("TimeoutSecs = %d, want default %d", cfg.TimeoutSecs, defaultSunbirdTimeoutSecs)
	}

	t.Setenv(envSunbirdTimeout, "-5")
	if cfg := LoadConfig(); cfg.TimeoutSecs != defaultSunbirdTimeoutSecs {
		t.Errorf("TimeoutSecs = %d, want default %d for negative value", cfg.TimeoutSecs, defaultSunbirdTimeoutSecs)
	}
}

func (ts *ConfigTestSuite) TestInit() {
	t := ts.T()
	clearSunbirdEnv(t)

	t.Run("missing search url fails", func(t *testing.T) {
		if _, _, err := Init(); err == nil {
			t.Fatal("expected error when SUNBIRD_SEARCH_URL is unset")
		}
	})

	t.Run("success", func(t *testing.T) {
		t.Setenv(envSunbirdSearchURL, "http://example.com/search")
		authnProvider, observabilityProvider, err := Init()
		if err != nil {
			t.Fatalf("Init: %v", err)
		}
		if authnProvider == nil {
			t.Error("expected non-nil authn provider")
		}
		if observabilityProvider == nil {
			t.Error("expected non-nil observability provider")
		}
	})
}

func (ts *ConfigTestSuite) TestConfigValidate() {
	t := ts.T()
	tests := []struct {
		name    string
		cfg     Config
		wantErr bool
	}{
		{
			name:    "valid",
			cfg:     Config{SearchURL: "http://example.com", IDField: "id", EntityIDField: "osid"},
			wantErr: false,
		},
		{
			name:    "missing search url",
			cfg:     Config{SearchURL: "  ", IDField: "id", EntityIDField: "osid"},
			wantErr: true,
		},
		{
			name:    "missing id field",
			cfg:     Config{SearchURL: "http://example.com", IDField: "", EntityIDField: "osid"},
			wantErr: true,
		},
		{
			name:    "missing entity id field",
			cfg:     Config{SearchURL: "http://example.com", IDField: "id", EntityIDField: ""},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.cfg.Validate()
			if (err != nil) != tt.wantErr {
				t.Errorf("Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

type ConfigTestSuite struct {
	suite.Suite
}

func TestConfigTestSuite(t *testing.T) {
	suite.Run(t, new(ConfigTestSuite))
}
