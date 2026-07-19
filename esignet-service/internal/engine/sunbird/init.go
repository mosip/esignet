/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package sunbird

import (
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

// Init builds the SunbirdRC KBI authn provider and its observability
// provider. Sunbird has no audit-manager integration, so observability falls
// back to the logging noop auditor.
func Init() (
	shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error) {

	authnProvider, err := NewSunbirdAuthnProvider()
	if err != nil {
		return nil, nil, err
	}
	applog.GetLogger().Info("Sunbird KBI authn provider initialized")
	return authnProvider, shared.NewNoopAuditor(), nil
}
