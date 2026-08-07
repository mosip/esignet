/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"context"
	"errors"
	"net/http"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/signature"
	applog "github.com/mosip/esignet/internal/log"
)

// ErrNilCryptoService is returned when Init is given a nil keymanager
// Service or signature Service. Storing either as nil would surface only as
// a nil-pointer panic on the first outbound IDA signing call, well after
// startup — reject it here instead.
var ErrNilCryptoService = errors.New("mosip: keymanager service and signature service are required")

// Init builds the MOSIP IDA authn provider and the mosip-audit-manager
// observability provider. svc/sigSvc are the keymanager services outbound
// IDA requests are signed with (see NewMosipAuthnProvider).
func Init(appConfig *config.AppConfig, clientSvc *clientmgmt.Service, httpClient *http.Client,
	svc *keymanager.Service, sigSvc *signature.Service) (
	shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error) {
	if svc == nil || sigSvc == nil {
		return nil, nil, ErrNilCryptoService
	}
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
