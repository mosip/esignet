/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

func (ts *ClearInputsExecutorTestSuite) TestNameAndType() {
	t := ts.T()
	e := NewClearInputsExecutor()
	if e.GetName() != ExecutorNameEsignetClearInputs {
		t.Errorf("GetName() = %q, want %q", e.GetName(), ExecutorNameEsignetClearInputs)
	}
	if e.GetType() != providers.ExecutorTypeUtility {
		t.Errorf("GetType() = %q, want %q", e.GetType(), providers.ExecutorTypeUtility)
	}
}

func (ts *ClearInputsExecutorTestSuite) TestExecuteClearsConfiguredInputs() {
	t := ts.T()
	e := NewClearInputsExecutor()
	ctx := &providers.NodeContext{
		NodeInputs:    []providers.Input{{Identifier: "otp"}, {Identifier: "username"}},
		UserInputs:    map[string]string{"otp": "123456", "username": "user1", "unrelated": "keep"},
		CurrentAction: "submit",
	}

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecComplete {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecComplete)
	}
	if _, ok := ctx.UserInputs["otp"]; ok {
		t.Error("expected otp to be cleared from UserInputs")
	}
	if _, ok := ctx.UserInputs["username"]; ok {
		t.Error("expected username to be cleared from UserInputs")
	}
	if got, ok := ctx.UserInputs["unrelated"]; !ok || got != "keep" {
		t.Errorf("expected unrelated input to be preserved, got %q, ok=%v", got, ok)
	}
	if ctx.CurrentAction != "" {
		t.Errorf("CurrentAction = %q, want empty after Execute", ctx.CurrentAction)
	}
}

func (ts *ClearInputsExecutorTestSuite) TestExecuteWithNoConfiguredInputs() {
	t := ts.T()
	e := NewClearInputsExecutor()
	ctx := &providers.NodeContext{
		UserInputs:    map[string]string{"keep": "me"},
		CurrentAction: "submit",
	}

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecComplete {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecComplete)
	}
	if got := ctx.UserInputs["keep"]; got != "me" {
		t.Errorf("UserInputs[keep] = %q, want me", got)
	}
	if ctx.CurrentAction != "" {
		t.Errorf("CurrentAction = %q, want empty", ctx.CurrentAction)
	}
}

type ClearInputsExecutorTestSuite struct {
	suite.Suite
}

func TestClearInputsExecutorTestSuite(t *testing.T) {
	suite.Run(t, new(ClearInputsExecutorTestSuite))
}
