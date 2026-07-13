/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"net"
	"net/http"
	"time"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
)

// newHTTPClient returns a tuned HTTP client for outbound MOSIP calls. Each
// caller (the IDA authenticator, the audit-manager client) gets its own
// instance.
func newHTTPClient() *http.Client {
	return &http.Client{
		Timeout: 30 * time.Second,
		Transport: &http.Transport{
			DialContext: (&net.Dialer{
				Timeout:   5 * time.Second,
				KeepAlive: 30 * time.Second,
			}).DialContext,
			TLSHandshakeTimeout:   10 * time.Second,
			ResponseHeaderTimeout: 10 * time.Second,
			IdleConnTimeout:       90 * time.Second,
		},
	}
}

// Init builds the MOSIP IDA authn provider and the mosip-audit-manager
// observability provider. Each gets its own HTTP client (see newHTTPClient).
func Init(appConfig *config.AppConfig, clientSvc *clientmgmt.Service) (
	shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error) {
	client := newHTTPClient()
	authnProvider, err := NewMosipAuthnProvider(appConfig, clientSvc, client)
	if err != nil {
		return nil, nil, err
	}
	auditor, err := NewAuditor(client)
	if err != nil {
		return nil, nil, err
	}
	return authnProvider, auditor, nil
}
