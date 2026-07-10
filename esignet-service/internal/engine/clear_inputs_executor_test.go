package engine

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

func TestClearInputsExecutor_Execute_clearsConfiguredInputs(t *testing.T) {
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

func TestClearInputsExecutor_Execute_noConfiguredInputsIsNoop(t *testing.T) {
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

func TestClearInputsExecutor_Execute_nilMapsAreSafe(t *testing.T) {
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

func TestClearInputsExecutor_metadata(t *testing.T) {
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
