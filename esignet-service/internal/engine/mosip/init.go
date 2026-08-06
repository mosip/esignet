/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"context"
	"net/http"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/signature"
	applog "github.com/mosip/esignet/internal/log"
)

// Init builds the MOSIP IDA authn provider and the mosip-audit-manager
// observability provider. svc/sigSvc are the keymanager services outbound
// IDA requests are signed with (see NewMosipAuthnProvider).
func Init(appConfig *config.AppConfig, clientSvc *clientmgmt.Service, httpClient *http.Client,
	svc *keymanager.Service, sigSvc *signature.Service) (
	shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error) {
	authnProvider, err := NewMosipAuthnProvider(appConfig, clientSvc, httpClient, svc, sigSvc)
	if err != nil {
		return nil, nil, err
	}
	auditor, err := NewAuditor(httpClient)
	if err != nil {
		return nil, nil, err
	}
	applog.GetLogger().Info(context.Background(), "MOSIP IDA authn provider and audit manager initialized")
	return authnProvider, auditor, nil
}
