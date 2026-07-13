/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

func (ts *ClearInputsExecutorTestSuite) TestClearInputsExecutor_Execute_clearsConfiguredInputs() {
	t := ts.T()
	ctx := &providers.NodeContext{
		CurrentAction: "action_submit",
		NodeInputs: []providers.Input{
			{Identifier: "username", Type: providers.InputTypeText},
		},
		UserInputs: map[string]string{
			"username": "bob",
			"otp":      "123456",
		},
	}

	execResp, err := NewClearInputsExecutor().Execute(ctx)
	require.NoError(t, err)
	require.NotNil(t, execResp)

	assert.Equal(t, providers.ExecComplete, execResp.Status)
	assert.Empty(t, ctx.CurrentAction)

	_, present := ctx.UserInputs["username"]
	assert.False(t, present, "username should be cleared from UserInputs")
	assert.Equal(t, "123456", ctx.UserInputs["otp"], "unrelated inputs must be left untouched")

}

func (ts *ClearInputsExecutorTestSuite) TestClearInputsExecutor_Execute_noConfiguredInputsIsNoop() {
	t := ts.T()
	ctx := &providers.NodeContext{
		CurrentAction: "action_submit",
		UserInputs: map[string]string{
			"otp": "123456",
		},
	}

	execResp, err := NewClearInputsExecutor().Execute(ctx)
	require.NoError(t, err)
	require.NotNil(t, execResp)

	assert.Equal(t, providers.ExecComplete, execResp.Status)
	assert.Empty(t, ctx.CurrentAction)
	assert.Equal(t, "123456", ctx.UserInputs["otp"])
}

func (ts *ClearInputsExecutorTestSuite) TestClearInputsExecutor_Execute_nilMapsAreSafe() {
	t := ts.T()
	ctx := &providers.NodeContext{
		NodeInputs: []providers.Input{
			{Identifier: "username", Type: providers.InputTypeText},
		},
	}

	assert.NotPanics(t, func() {
		execResp, err := NewClearInputsExecutor().Execute(ctx)
		require.NoError(t, err)
		require.NotNil(t, execResp)
		assert.Equal(t, providers.ExecComplete, execResp.Status)
	})
}

func (ts *ClearInputsExecutorTestSuite) TestClearInputsExecutor_metadata() {
	t := ts.T()
	e := NewClearInputsExecutor()

	assert.Equal(t, ExecutorNameClearInputs, e.GetName())
	assert.Equal(t, providers.ExecutorTypeUtility, e.GetType())
	assert.Nil(t, e.GetDefaultInputs())
	assert.Nil(t, e.GetPrerequisites())
	assert.Nil(t, e.GetRequiredInputs(&providers.NodeContext{}))
	assert.Nil(t, e.GetExecutionPolicy(""))
	assert.True(t, e.HasRequiredInputs(&providers.NodeContext{}, &providers.ExecutorResponse{}))
	assert.True(t, e.ValidatePrerequisites(&providers.NodeContext{}, &providers.ExecutorResponse{}, nil))
	assert.Empty(t, e.GetUserIDFromContext(&providers.NodeContext{}, &providers.ExecutorResponse{}, nil))
}

type ClearInputsExecutorTestSuite struct {
	suite.Suite
}

func TestClearInputsExecutorTestSuite(t *testing.T) {
	suite.Run(t, new(ClearInputsExecutorTestSuite))
}
