/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/config"
)

func (ts *I18nProviderTestSuite) TestI18nProvider_ListLanguages() {
	t := ts.T()

	t.Run("missing directory returns FileNotFoundError", func(t *testing.T) {
		p := NewI18nProvider(&config.AppConfig{DataDir: t.TempDir()})
		langs, svcErr := p.ListLanguages(context.Background())
		require.NotNil(t, svcErr)
		require.Nil(t, langs)
	})

	t.Run("lists yaml files without extension", func(t *testing.T) {
		dir := t.TempDir()
		mustMkdirAll(t, filepath.Join(dir, "i18n"))
		mustWriteFile(t, filepath.Join(dir, "i18n", "en.yaml"), "system:\n  hello: Hello\n")
		mustWriteFile(t, filepath.Join(dir, "i18n", "hi.yaml"), "system:\n  hello: Namaste\n")
		mustWriteFile(t, filepath.Join(dir, "i18n", "not-yaml.txt"), "ignore me")

		p := NewI18nProvider(&config.AppConfig{DataDir: dir})
		langs, svcErr := p.ListLanguages(context.Background())
		require.Nil(t, svcErr)
		require.ElementsMatch(t, []string{"en", "hi"}, langs)
	})

	t.Run("empty directory falls back to default", func(t *testing.T) {
		dir := t.TempDir()
		mustMkdirAll(t, filepath.Join(dir, "i18n"))
		p := NewI18nProvider(&config.AppConfig{DataDir: dir})
		langs, svcErr := p.ListLanguages(context.Background())
		require.Nil(t, svcErr)
		require.Equal(t, []string{"en"}, langs)
	})
}

func (ts *I18nProviderTestSuite) TestI18nProvider_ResolveTranslations() {
	t := ts.T()

	t.Run("success exact match", func(t *testing.T) {
		dir := t.TempDir()
		mustMkdirAll(t, filepath.Join(dir, "i18n"))
		mustWriteFile(t, filepath.Join(dir, "i18n", "en.yaml"), "system:\n  hello: Hello\n  bye: Bye\n")

		p := NewI18nProvider(&config.AppConfig{DataDir: dir})
		resp, svcErr := p.ResolveTranslations(context.Background(), "en", "")
		require.Nil(t, svcErr)
		require.Equal(t, "en", resp.Language)
		require.Equal(t, 2, resp.TotalResults)
		require.Equal(t, "Hello", resp.Translations["system"]["hello"])
	})

	t.Run("BCP47 fallback resolves regional tag to base language", func(t *testing.T) {
		dir := t.TempDir()
		mustMkdirAll(t, filepath.Join(dir, "i18n"))
		mustWriteFile(t, filepath.Join(dir, "i18n", "en.yaml"), "system:\n  hello: Hello\n")

		p := NewI18nProvider(&config.AppConfig{DataDir: dir})
		resp, svcErr := p.ResolveTranslations(context.Background(), "en-US", "")
		require.Nil(t, svcErr)
		require.Equal(t, "en", resp.Language)
	})

	t.Run("no available languages returns FileNotFoundError", func(t *testing.T) {
		p := NewI18nProvider(&config.AppConfig{DataDir: t.TempDir()})
		_, svcErr := p.ResolveTranslations(context.Background(), "en", "")
		require.NotNil(t, svcErr)
	})

	t.Run("invalid yaml returns FileUnmarshallError", func(t *testing.T) {
		dir := t.TempDir()
		mustMkdirAll(t, filepath.Join(dir, "i18n"))
		mustWriteFile(t, filepath.Join(dir, "i18n", "en.yaml"), "not: [valid: yaml")

		p := NewI18nProvider(&config.AppConfig{DataDir: dir})
		_, svcErr := p.ResolveTranslations(context.Background(), "en", "")
		require.NotNil(t, svcErr)
	})
}

func (ts *I18nProviderTestSuite) TestBestMatchLanguage() {
	t := ts.T()

	t.Run("no available languages", func(t *testing.T) {
		require.Equal(t, "en", bestMatchLanguage("fr", nil))
	})

	t.Run("exact match", func(t *testing.T) {
		require.Equal(t, "hi", bestMatchLanguage("hi", []string{"en", "hi"}))
	})

	t.Run("no match falls back to english regardless of available-language order", func(t *testing.T) {
		require.Equal(t, "en", bestMatchLanguage("zz", []string{"en", "hi"}))
		// "ar" sorts before "en" alphabetically, which is the exact order
		// ListLanguages returns from os.ReadDir in production (data/i18n has
		// ar, en, es, fr, hi, km, kn, si, ta). The fallback must still resolve
		// to "en", not "ar", for a language none of them match.
		production := []string{"ar", "en", "es", "fr", "hi", "km", "kn", "si", "ta"}
		require.Equal(t, "en", bestMatchLanguage("zz", production))
		require.Equal(t, "en", bestMatchLanguage("de", production))
		require.Equal(t, "en", bestMatchLanguage("", production))
	})

	t.Run("no match falls back to first available when english isn't configured", func(t *testing.T) {
		require.Equal(t, "ar", bestMatchLanguage("zz", []string{"ar", "hi"}))
	})
}

type I18nProviderTestSuite struct {
	suite.Suite
}

func TestI18nProviderTestSuite(t *testing.T) {
	suite.Run(t, new(I18nProviderTestSuite))
}
