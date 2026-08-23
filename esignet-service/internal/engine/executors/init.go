/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/engine/shared"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

// Initialize builds the map of executors registered for this engine, keyed by executor name.
func Initialize(
	authnProvider shared.ConsolidatedAuthnProvider,
	clientSvc *clientmgmt.Service,
	resourceSvc providers.ResourceServerProvider,
) map[string]providers.Executor {
	executors := map[string]providers.Executor{
		ExecutorNameEsignetClearInputs:   NewClearInputsExecutor(),
		ExecutorNameEsignetOTP:           NewOtpExecutor(authnProvider),
		ExecutorNameEsignetAuthorization: NewAuthorizationExecutor(clientSvc, resourceSvc),
	}
	return executors
}
