/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	applog "github.com/mosip/esignet/internal/log"
)

// ExecutorNameClearInputs clears this node's configured inputs from the engine
// context so a subsequent PROMPT node re-asks the user for them.
const ExecutorNameClearInputs = "ClearInputsExecutor"

type clearInputsExecutor struct{}

var _ providers.Executor = (*clearInputsExecutor)(nil)

// NewClearInputsExecutor creates an executor that removes its configured inputs
// (the node's executor.inputs) from UserInputs/RuntimeData/ForwardedData.
func NewClearInputsExecutor() providers.Executor {
	return &clearInputsExecutor{}
}

func (e *clearInputsExecutor) GetName() string {
	return ExecutorNameClearInputs
}

func (e *clearInputsExecutor) GetType() providers.ExecutorType {
	return providers.ExecutorTypeUtility
}

func (e *clearInputsExecutor) GetDefaultInputs() []providers.Input {
	return nil
}

func (e *clearInputsExecutor) GetPrerequisites() []providers.Input {
	return nil
}

func (e *clearInputsExecutor) GetRequiredInputs(_ *providers.NodeContext) []providers.Input {
	return nil
}

func (e *clearInputsExecutor) GetExecutionPolicy(_ string) *providers.ExecutionPolicy {
	return nil
}

func (e *clearInputsExecutor) HasRequiredInputs(_ *providers.NodeContext, _ *providers.ExecutorResponse) bool {
	return true
}

func (e *clearInputsExecutor) ValidatePrerequisites(_ *providers.NodeContext, _ *providers.ExecutorResponse,
	_ providers.AuthnProviderManager) bool {
	return true
}

func (e *clearInputsExecutor) GetUserIDFromContext(_ *providers.NodeContext, _ *providers.ExecutorResponse,
	_ providers.AuthnProviderManager) string {
	return ""
}

// BuildRuntimeMetadata returns nil: this utility executor never calls an authn provider.
func (e *clearInputsExecutor) BuildRuntimeMetadata(_ *providers.NodeContext) map[string]string {
	return nil
}

// BuildAuthnMetadata returns nil: this utility executor never calls an authn provider.
func (e *clearInputsExecutor) BuildAuthnMetadata(_ *providers.NodeContext) *providers.AuthnMetadata {
	return nil
}

// BuildGetAttributesMetadata returns nil: this utility executor never fetches attributes.
func (e *clearInputsExecutor) BuildGetAttributesMetadata(_ *providers.NodeContext) *providers.GetAttributesMetadata {
	return nil
}

// Execute deletes each of this node's configured inputs from UserInputs, RuntimeData,
// and ForwardedData, and resets the selected action. This forces a downstream PROMPT
// node to treat those inputs as unsatisfied and re-ask the user for them.
func (e *clearInputsExecutor) Execute(ctx *providers.NodeContext) (*providers.ExecutorResponse, error) {
	for _, input := range ctx.NodeInputs {
		delete(ctx.UserInputs, input.Identifier)
		applog.GetLogger().Debug("cleared input from engine userinput context",
			applog.String("identifier", input.Identifier))
	}
	// Reset the selected action so the target PROMPT node re-renders its inputs
	// instead of auto-advancing on a stale action.
	ctx.CurrentAction = ""

	return &providers.ExecutorResponse{Status: providers.ExecComplete}, nil
}
