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
	applog "github.com/mosip/esignet/internal/log"
)

type i18nProvider struct {
	cfg *config.AppConfig
}

// NewI18nProvider returns a file-based i18n provider backed by the configured data directory.
func NewI18nProvider(cfg *config.AppConfig) providers.I18nProvider {
	return &i18nProvider{cfg: cfg}
}

func (p *i18nProvider) ResolveTranslations(
	_ context.Context,
	language string,
	_ string,
) (*providers.LanguageTranslationsResponse, *common.ServiceError) {
	data, err := os.ReadFile(filepath.Join(p.cfg.DataDir, "i18n", language+".yaml"))
	if err != nil {
		applog.GetLogger().Warn("i18n translations file not found", applog.String("language", language), applog.Error(err))
		return nil, shared.FileNotFoundError
	}
	var translations providers.LanguageTranslationsResponse
	err = yaml.Unmarshal(data, &translations)
	if err != nil {
		applog.GetLogger().Warn("failed to parse i18n translations file", applog.String("language", language), applog.Error(err))
		return nil, shared.FileUnmarshallError
	}
	return &translations, nil
}

func (p *i18nProvider) ListLanguages(_ context.Context) ([]string, *common.ServiceError) {
	return []string{"en"}, nil
}
