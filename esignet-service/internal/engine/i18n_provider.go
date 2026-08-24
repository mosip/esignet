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
	"sort"
	"strings"

	"golang.org/x/text/language"
	"gopkg.in/yaml.v3"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

// fallbackLanguage is returned by bestMatchLanguage when no confident match is found.
const fallbackLanguage = "en"

// clientNameNamespace is the fixed namespace a client's localized name is exposed under,
// matching the static "{{t(client:name)}}" reference GetActor emits.
const clientNameNamespace = "client"

type i18nProvider struct {
	cfg       *config.AppConfig
	clientSvc *clientmgmt.Service
}

// NewI18nProvider returns a file-based i18n provider backed by the configured data directory.
// clientSvc resolves a client's localized name when a client id is passed as namespace.
func NewI18nProvider(cfg *config.AppConfig, clientSvc *clientmgmt.Service) providers.I18nProvider {
	return &i18nProvider{cfg: cfg, clientSvc: clientSvc}
}

// ListLanguages scans the data/i18n directory and returns all available language codes.
func (p *i18nProvider) ListLanguages(ctx context.Context) ([]string, *common.ServiceError) {
	dir := filepath.Join(p.cfg.DataDir, "i18n")
	entries, err := os.ReadDir(dir)
	if err != nil {
		applog.GetLogger().Warn(ctx, "failed to read i18n directory", applog.String("dir", dir), applog.Error(err))
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
// "hi-IN" resolves to "hi", etc. namespace doubles as an optional client id (see injectClientName).
func (p *i18nProvider) ResolveTranslations(
	ctx context.Context,
	requestedLang string,
	namespace string,
) (*providers.LanguageTranslationsResponse, *common.ServiceError) {
	available, svcErr := p.ListLanguages(ctx)
	if svcErr != nil {
		return nil, svcErr
	}

	resolved := bestMatchLanguage(requestedLang, available)

	data, err := os.ReadFile(filepath.Join(p.cfg.DataDir, "i18n", resolved+".yaml"))
	if err != nil {
		applog.GetLogger().Warn(ctx, "i18n file not found",
			applog.String("requested", requestedLang),
			applog.String("resolved", resolved),
			applog.Error(err))
		return nil, shared.FileNotFoundError
	}

	// YAML top-level keys are namespaces (e.g. "system:"), not struct fields.
	// Unmarshal as map[namespace]map[key]value, then build the response.
	var raw map[string]map[string]string
	if err := yaml.Unmarshal(data, &raw); err != nil {
		applog.GetLogger().Warn(ctx, "failed to parse i18n file",
			applog.String("language", resolved),
			applog.Error(err))
		return nil, shared.FileUnmarshallError
	}
	if raw == nil {
		// Empty/null YAML documents unmarshal to a nil map; writing into it below would panic.
		raw = make(map[string]map[string]string)
	}

	p.injectClientName(ctx, raw, namespace, requestedLang)

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

// injectClientName looks up namespace as a client id and, if that client has a localized name,
// exposes it under clientNameNamespace. A no-op when namespace is empty, clientSvc isn't
// configured, or the client has no per-language names.
func (p *i18nProvider) injectClientName(ctx context.Context, raw map[string]map[string]string, namespace, language string) {
	if namespace == "" || p.clientSvc == nil {
		return
	}

	client, err := p.clientSvc.GetClient(ctx, namespace)
	if err != nil || len(client.ClientNameLangMap) == 0 {
		return
	}

	clientName := bestMatchName(client.ClientNameLangMap, language)
	if clientName == "" {
		clientName = client.ClientName
	}

	if raw[clientNameNamespace] == nil {
		raw[clientNameNamespace] = make(map[string]string)
	}
	raw[clientNameNamespace]["name"] = clientName
}

// bestMatchLanguage returns the closest available language for the requested tag: an exact
// tag match if available, else the requested tag's
// own base language if that's supported, else fallbackLanguage.
func bestMatchLanguage(requested string, available []string) string {
	if len(available) == 0 {
		return fallbackLanguage
	}

	sorted := make([]string, len(available))
	copy(sorted, available)
	sort.Strings(sorted)

	if match := bestMatchCode(requested, sorted); match != "" {
		return match
	}
	for _, code := range sorted {
		if code == fallbackLanguage {
			return fallbackLanguage
		}
	}
	return sorted[0]
}

// bestMatchCode returns the entry in sorted (must already be sorted) that best matches requested:
// an exact tag match if present, else a base-language match, else "". language.NewMatcher isn't
// used here since its CLDR scoring can prefer a sibling dialect (e.g. "en-IN") over the plain
// base tag (e.g. "en") for an unrelated request like "en-GB" — wrong for this use case.
func bestMatchCode(requested string, sorted []string) string {
	want := language.Make(requested)
	wantBase, _ := want.Base()

	baseMatch := ""
	for _, code := range sorted {
		tag := language.Make(code)
		if tag.String() == want.String() {
			return code
		}
		if baseMatch == "" {
			if base, _ := tag.Base(); base.String() == wantBase.String() {
				baseMatch = code
			}
		}
	}
	return baseMatch
}

// bestMatchName returns the value in names whose key best matches requested (see bestMatchCode),
// or "" if nothing matches.
func bestMatchName(names map[string]string, requested string) string {
	codes := make([]string, 0, len(names))
	for code := range names {
		codes = append(codes, code)
	}
	sort.Strings(codes)
	return names[bestMatchCode(requested, codes)]
}
