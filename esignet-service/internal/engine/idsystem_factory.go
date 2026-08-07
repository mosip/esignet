/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"fmt"
	"net/http"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/mock"
	"github.com/mosip/esignet/internal/engine/mosip"
	"github.com/mosip/esignet/internal/engine/shared"
	"github.com/mosip/esignet/internal/engine/sunbird"
	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/signature"
)

// NewIDSystemProviders builds the authn provider and its matching observability
// provider for the configured ID system backend. Each backend package
// (mock, mosip, sunbird) owns its own construction, including its own HTTP
// client where one is needed. keyMgrSvc/sigSvc are only used by the mosip
// backend, to sign outbound IDA requests with the OIDC_PARTNER / RSA_2048
// component master key.
func NewIDSystemProviders(appConfig *config.AppConfig, clientSvc *clientmgmt.Service, httpClient *http.Client,
	keyMgrSvc *keymanager.Service, sigSvc *signature.Service) (
	shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error) {

	switch appConfig.Provider {
	case "mosip":
		return mosip.Init(appConfig, clientSvc, httpClient, keyMgrSvc, sigSvc)
	case "sunbird":
		return sunbird.Init(httpClient)
	case "mock":
		return mock.Init(appConfig, clientSvc, httpClient)
	default:
		return nil, nil, fmt.Errorf("unsupported authn provider %q (use mosip, sunbird, or mock)", appConfig.Provider)
	}
}
