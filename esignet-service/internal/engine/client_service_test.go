/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/clientmgmt/db"
	"github.com/mosip/esignet/internal/engine/shared"
)

// newTestClientService builds a clientmgmt.Service with the auditor wired, as
// in production, where the auditor is always set.
func newTestClientService(q db.Querier, cache providers.RuntimeStoreProvider, cacheTTLSecs int64, supportedEncAlgs []string) *clientmgmt.Service {
	svc := clientmgmt.NewServiceWithQuerier(q, cache, cacheTTLSecs, supportedEncAlgs)
	svc.SetAuditor(shared.NewNoopAuditor())
	return svc
}
