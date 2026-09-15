/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
)

// ExecutorNameEsignetTransactionID seeds the flow's IDA transaction id.
const ExecutorNameEsignetTransactionID = "eSignetTransactionIDExecutor"

type transactionIDExecutor struct {
	baseExecutor
	appConfig *config.AppConfig
}

var _ providers.Executor = (*transactionIDExecutor)(nil)

// NewTransactionIDExecutor creates an executor that seeds provider_ext_TransactionID in
// RuntimeData from the flow's execution id, before any authentication node runs. This is
// the only reliable hook for authentication methods handled by the engine's built-in
// CredentialsAuthExecutor (password, biometric, KBI, etc.): that executor builds its own
// provider metadata internally, bypassing BuildProviderMetadata in this package, but it
// still forwards any provider_ext_* key already present in RuntimeData. Placing this
// executor once at the start of every flow (before the branch into OTP/credentials/
// biometric/KBI) guarantees the key is set regardless of which authentication method the
// user takes, so all authentication methods share one IDA transaction id per flow
// execution, matching mock-identity-system/MOSIP IDA's requirement (see
// shared.GenerateTransactionID).
func NewTransactionIDExecutor(appConfig *config.AppConfig) providers.Executor {
	return &transactionIDExecutor{appConfig: appConfig}
}

func (e *transactionIDExecutor) GetName() string {
	return ExecutorNameEsignetTransactionID
}

func (e *transactionIDExecutor) GetType() providers.ExecutorType {
	return providers.ExecutorTypeUtility
}

func (e *transactionIDExecutor) Execute(ctx *providers.NodeContext) (*providers.ExecutorResponse, error) {
	transactionID, err := shared.DeriveAuthTransactionID(ctx.ExecutionID, e.appConfig.AuthTransactionIDLength)
	if err != nil {
		return nil, err
	}

	if ctx.RuntimeData == nil {
		ctx.RuntimeData = make(map[string]string)
	}
	ctx.RuntimeData[shared.TransactionIDKey] = transactionID

	return &providers.ExecutorResponse{Status: providers.ExecComplete}, nil
}
