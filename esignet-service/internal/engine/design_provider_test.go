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
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
)

func (ts *DesignProviderTestSuite) TestParseToLayout() {
	t := ts.T()
	t.Run("map structure", func(t *testing.T) {
		data := []byte("id: layout-1\nhandle: default\ndisplayName: Default Layout\nlayout:\n  type: grid\n  columns: 2\n")
		layout, err := parseToLayout(data)
		if err != nil {
			t.Fatalf("parseToLayout: %v", err)
		}
		if layout.ID != "layout-1" || layout.DisplayName != "Default Layout" {
			t.Errorf("layout = %+v, unexpected ID/DisplayName", layout)
		}
		var decoded map[string]any
		if err := json.Unmarshal(layout.Layout, &decoded); err != nil {
			t.Fatalf("layout.Layout is not valid JSON: %v", err)
		}
		if decoded["type"] != "grid" {
			t.Errorf("layout.Layout[type] = %v, want grid", decoded["type"])
		}
	})

	t.Run("string structure", func(t *testing.T) {
		data := []byte(`id: layout-2
handle: default
layout: '{"type":"stack"}'
`)
		layout, err := parseToLayout(data)
		if err != nil {
			t.Fatalf("parseToLayout: %v", err)
		}
		if string(layout.Layout) != `{"type":"stack"}` {
			t.Errorf("layout.Layout = %s, want {\"type\":\"stack\"}", layout.Layout)
		}
	})

	t.Run("no layout field", func(t *testing.T) {
		layout, err := parseToLayout([]byte("id: layout-3\nhandle: default\n"))
		if err != nil {
			t.Fatalf("parseToLayout: %v", err)
		}
		if layout.Layout != nil {
			t.Errorf("layout.Layout = %s, want nil", layout.Layout)
		}
	})

	t.Run("invalid yaml", func(t *testing.T) {
		if _, err := parseToLayout([]byte("not: [valid: yaml")); err == nil {
			t.Error("expected error for invalid YAML")
		}
	})
}

func (ts *DesignProviderTestSuite) TestParseToTheme() {
	t := ts.T()
	t.Run("map structure", func(t *testing.T) {
		data := []byte("id: theme-1\nhandle: default\ndisplayName: Default Theme\ntheme:\n  color: blue\n")
		theme, err := parseToTheme(data)
		if err != nil {
			t.Fatalf("parseToTheme: %v", err)
		}
		if theme.ID != "theme-1" || theme.DisplayName != "Default Theme" {
			t.Errorf("theme = %+v, unexpected ID/DisplayName", theme)
		}
		var decoded map[string]any
		if err := json.Unmarshal(theme.Theme, &decoded); err != nil {
			t.Fatalf("theme.Theme is not valid JSON: %v", err)
		}
		if decoded["color"] != "blue" {
			t.Errorf("theme.Theme[color] = %v, want blue", decoded["color"])
		}
	})

	t.Run("string structure", func(t *testing.T) {
		data := []byte(`id: theme-2
handle: default
theme: '{"color":"red"}'
`)
		theme, err := parseToTheme(data)
		if err != nil {
			t.Fatalf("parseToTheme: %v", err)
		}
		if string(theme.Theme) != `{"color":"red"}` {
			t.Errorf("theme.Theme = %s, want {\"color\":\"red\"}", theme.Theme)
		}
	})

	t.Run("invalid yaml", func(t *testing.T) {
		if _, err := parseToTheme([]byte("not: [valid: yaml")); err == nil {
			t.Error("expected error for invalid YAML")
		}
	})
}

func (ts *DesignProviderTestSuite) TestDesignProvider_ResolveDesign() {
	t := ts.T()
	t.Run("missing layout or theme id", func(t *testing.T) {
		p := NewDesignProvider(&config.AppConfig{})
		_, svcErr := p.ResolveDesign(context.TODO(), "", "")
		if svcErr == nil {
			t.Fatal("expected error when LayoutID/ThemeID are unset")
		}
	})

	t.Run("success", func(t *testing.T) {
		dir := t.TempDir()
		mustMkdirAll(t, filepath.Join(dir, "layouts"))
		mustMkdirAll(t, filepath.Join(dir, "themes"))
		mustWriteFile(t, filepath.Join(dir, "layouts", "layout-1.yaml"),
			"id: layout-1\nhandle: default\nlayout:\n  type: grid\n")
		mustWriteFile(t, filepath.Join(dir, "themes", "theme-1.yaml"),
			"id: theme-1\nhandle: default\ntheme:\n  color: blue\n")

		p := NewDesignProvider(&config.AppConfig{DataDir: dir, LayoutID: "layout-1", ThemeID: "theme-1"})
		resp, svcErr := p.ResolveDesign(context.TODO(), providers.DesignResolveType(""), "")
		if svcErr != nil {
			t.Fatalf("unexpected service error: %v", svcErr)
		}
		var layout map[string]any
		if err := json.Unmarshal(resp.Layout, &layout); err != nil || layout["type"] != "grid" {
			t.Errorf("resp.Layout = %s, want type=grid", resp.Layout)
		}
	})

	t.Run("missing layout file", func(t *testing.T) {
		dir := t.TempDir()
		p := NewDesignProvider(&config.AppConfig{DataDir: dir, LayoutID: "missing", ThemeID: "missing"})
		_, svcErr := p.ResolveDesign(context.TODO(), "", "")
		if svcErr == nil {
			t.Fatal("expected error when layout file is missing")
		}
	})
}

func mustMkdirAll(t *testing.T, dir string) {
	t.Helper()
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatalf("MkdirAll(%s): %v", dir, err)
	}
}

func mustWriteFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatalf("WriteFile(%s): %v", path, err)
	}
}

type DesignProviderTestSuite struct {
	suite.Suite
}

func TestDesignProviderTestSuite(t *testing.T) {
	suite.Run(t, new(DesignProviderTestSuite))
}
