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

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
)

func (ts *TransactionIDExecutorTestSuite) TestNameAndType() {
	t := ts.T()
	e := NewTransactionIDExecutor(&config.AppConfig{})
	if e.GetName() != ExecutorNameEsignetTransactionID {
		t.Errorf("GetName() = %q, want %q", e.GetName(), ExecutorNameEsignetTransactionID)
	}
	if e.GetType() != providers.ExecutorTypeUtility {
		t.Errorf("GetType() = %q, want %q", e.GetType(), providers.ExecutorTypeUtility)
	}
}

func (ts *TransactionIDExecutorTestSuite) TestExecuteSeedsTransactionIDFromExecutionID() {
	t := ts.T()
	appConfig := &config.AppConfig{}
	e := NewTransactionIDExecutor(appConfig)
	ctx := &providers.NodeContext{
		ExecutionID: "0199183a-8f2e-7abc-def0-123456789abc",
		RuntimeData: map[string]string{"unrelated": "keep"},
	}

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecComplete {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecComplete)
	}
	want, err2 := shared.DeriveAuthTransactionID(ctx.ExecutionID, appConfig.AuthTransactionIDLength)
	if err2 != nil {
		t.Fatalf("DeriveAuthTransactionID(%q) error = %v", ctx.ExecutionID, err2)
	}
	if got := ctx.RuntimeData[shared.TransactionIDKey]; got != want {
		t.Errorf("RuntimeData[%q] = %q, want %q", shared.TransactionIDKey, got, want)
	}
	if got, ok := ctx.RuntimeData["unrelated"]; !ok || got != "keep" {
		t.Errorf("expected unrelated RuntimeData to be preserved, got %q, ok=%v", got, ok)
	}
}

func (ts *TransactionIDExecutorTestSuite) TestExecuteUsesConfiguredTransactionIDLength() {
	t := ts.T()
	appConfig := &config.AppConfig{AuthTransactionIDLength: 6}
	e := NewTransactionIDExecutor(appConfig)
	ctx := &providers.NodeContext{ExecutionID: "0199183a-8f2e-7abc-def0-123456789abc"}

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecComplete {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecComplete)
	}
	if got := ctx.RuntimeData[shared.TransactionIDKey]; len(got) != 6 {
		t.Errorf("RuntimeData[%q] = %q, want length 6", shared.TransactionIDKey, got)
	}
}

func (ts *TransactionIDExecutorTestSuite) TestExecuteWithNilRuntimeData() {
	t := ts.T()
	appConfig := &config.AppConfig{}
	e := NewTransactionIDExecutor(appConfig)
	ctx := &providers.NodeContext{ExecutionID: "execution-id"}

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecComplete {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecComplete)
	}
	want, err2 := shared.DeriveAuthTransactionID(ctx.ExecutionID, appConfig.AuthTransactionIDLength)
	if err2 != nil {
		t.Fatalf("DeriveAuthTransactionID(%q) error = %v", ctx.ExecutionID, err2)
	}
	if got := ctx.RuntimeData[shared.TransactionIDKey]; got != want {
		t.Errorf("RuntimeData[%q] = %q, want %q", shared.TransactionIDKey, got, want)
	}
}

func (ts *TransactionIDExecutorTestSuite) TestExecuteFailsOnEmptyExecutionID() {
	t := ts.T()
	e := NewTransactionIDExecutor(&config.AppConfig{})
	ctx := &providers.NodeContext{ExecutionID: ""}

	resp, err := e.Execute(ctx)
	if err == nil {
		t.Fatal("Execute() error = nil, want error")
	}
	if resp != nil {
		t.Errorf("Execute() response = %v, want nil", resp)
	}
}

type TransactionIDExecutorTestSuite struct {
	suite.Suite
}

func TestTransactionIDExecutorTestSuite(t *testing.T) {
	suite.Run(t, new(TransactionIDExecutorTestSuite))
}
