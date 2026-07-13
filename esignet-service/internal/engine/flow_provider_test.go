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

	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/config"
)

func (ts *FlowProviderTestSuite) TestFlowProvider() {
	t := ts.T()
	t.Run("success", func(t *testing.T) {
		dir := t.TempDir()
		mustMkdirAll(t, filepath.Join(dir, "flows"))
		mustWriteFile(t, filepath.Join(dir, "flows", "flow-esignet.yaml"), "id: flow-1\nhandle: default\n")

		p := NewFlowProvider(&config.AppConfig{DataDir: dir, AuthFlowID: "flow-esignet"})

		def, svcErr := p.GetFlow(context.Background(), "flow-esignet")
		if svcErr != nil {
			t.Fatalf("GetFlow: %v", svcErr)
		}
		if def.ID != "flow-1" {
			t.Errorf("def.ID = %q, want flow-1", def.ID)
		}

		def, svcErr = p.GetFlowByHandle(context.Background(), "default", "")
		if svcErr != nil {
			t.Fatalf("GetFlowByHandle: %v", svcErr)
		}
		if def.ID != "flow-1" {
			t.Errorf("def.ID = %q, want flow-1", def.ID)
		}
	})

	t.Run("missing file", func(t *testing.T) {
		p := NewFlowProvider(&config.AppConfig{DataDir: t.TempDir(), AuthFlowID: "missing"})
		if _, svcErr := p.GetFlow(context.Background(), "missing"); svcErr == nil {
			t.Fatal("expected error when flow file is missing")
		}
	})

	t.Run("invalid yaml", func(t *testing.T) {
		dir := t.TempDir()
		mustMkdirAll(t, filepath.Join(dir, "flows"))
		mustWriteFile(t, filepath.Join(dir, "flows", "bad.yaml"), "not: [valid: yaml")

		p := NewFlowProvider(&config.AppConfig{DataDir: dir, AuthFlowID: "bad"})
		if _, svcErr := p.GetFlow(context.Background(), "bad"); svcErr == nil {
			t.Fatal("expected error for invalid YAML")
		}
	})
}

type FlowProviderTestSuite struct {
	suite.Suite
}

func TestFlowProviderTestSuite(t *testing.T) {
	suite.Run(t, new(FlowProviderTestSuite))
}
