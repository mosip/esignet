/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mock

import (
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

// Init builds the mock authn provider and its observability provider. Mock
// has no external audit sink, so observability falls back to the logging
// noop auditor.
func Init(appConfig *config.AppConfig, clientSvc *clientmgmt.Service) (
	shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error) {
	authnProvider, err := NewMockAuthnProvider(appConfig, clientSvc)
	if err != nil {
		return nil, nil, err
	}
	applog.GetLogger().Warn("mock identity system provider active: no real OTP/KYC verification is performed; not for production use")
	return authnProvider, shared.NewNoopAuditor(), nil
}
