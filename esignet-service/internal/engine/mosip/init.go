/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"net/http"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

// Init builds the MOSIP IDA authn provider and the mosip-audit-manager
// observability provider.
func Init(appConfig *config.AppConfig, clientSvc *clientmgmt.Service, httpClient *http.Client) (
	shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error) {
	authnProvider, err := NewMosipAuthnProvider(appConfig, clientSvc, httpClient)
	if err != nil {
		return nil, nil, err
	}
	auditor, err := NewAuditor(httpClient)
	if err != nil {
		return nil, nil, err
	}
	applog.GetLogger().Info("MOSIP IDA authn provider and audit manager initialized")
	return authnProvider, auditor, nil
}
