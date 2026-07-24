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
	"strings"

	"golang.org/x/text/language"
	"gopkg.in/yaml.v3"

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

// ListLanguages scans the data/i18n directory and returns all available language codes.
func (p *i18nProvider) ListLanguages(_ context.Context) ([]string, *common.ServiceError) {
	dir := filepath.Join(p.cfg.DataDir, "i18n")
	entries, err := os.ReadDir(dir)
	if err != nil {
		applog.GetLogger().Warn("failed to read i18n directory", applog.String("dir", dir), applog.Error(err))
		return nil, shared.FileNotFoundError
	}

	var langs []string
	for _, entry := range entries {
		if !entry.IsDir() && strings.HasSuffix(entry.Name(), ".yaml") {
			langs = append(langs, strings.TrimSuffix(entry.Name(), ".yaml"))
		}
	}

	if len(langs) == 0 {
		return []string{"en"}, nil
	}
	return langs, nil
}

// ResolveTranslations reads and parses the best-matching i18n YAML file for the
// requested language tag. BCP47 matching is used so that "en-US" resolves to "en",
// "hi-IN" resolves to "hi", etc.
func (p *i18nProvider) ResolveTranslations(
	ctx context.Context,
	requestedLang string,
	_ string,
) (*providers.LanguageTranslationsResponse, *common.ServiceError) {
	available, svcErr := p.ListLanguages(ctx)
	if svcErr != nil {
		return nil, svcErr
	}

	resolved := bestMatchLanguage(requestedLang, available)

	data, err := os.ReadFile(filepath.Join(p.cfg.DataDir, "i18n", resolved+".yaml"))
	if err != nil {
		applog.GetLogger().Warn("i18n file not found",
			applog.String("requested", requestedLang),
			applog.String("resolved", resolved),
			applog.Error(err))
		return nil, shared.FileNotFoundError
	}

	// YAML top-level keys are namespaces (e.g. "system:"), not struct fields.
	// Unmarshal as map[namespace]map[key]value, then build the response.
	var raw map[string]map[string]string
	if err := yaml.Unmarshal(data, &raw); err != nil {
		applog.GetLogger().Warn("failed to parse i18n file",
			applog.String("language", resolved),
			applog.Error(err))
		return nil, shared.FileUnmarshallError
	}

	total := 0
	for _, ns := range raw {
		total += len(ns)
	}

	return &providers.LanguageTranslationsResponse{
		Language:     resolved,
		TotalResults: total,
		Translations: raw,
	}, nil
}

// bestMatchLanguage returns the closest available language for the requested tag
// using BCP47 matching. Falls back to the first available language if no match.
func bestMatchLanguage(requested string, available []string) string {
	if len(available) == 0 {
		return "en"
	}

	tags := make([]language.Tag, len(available))
	for i, l := range available {
		tags[i] = language.Make(l)
	}

	matcher := language.NewMatcher(tags)
	_, idx, _ := matcher.Match(language.Make(requested))
	return available[idx]
}
