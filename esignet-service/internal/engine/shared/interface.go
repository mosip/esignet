/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

// ConsolidatedAuthnProvider extends providers.AuthnProviderManager with OTP send capability.
type ConsolidatedAuthnProvider interface {
	providers.AuthnProviderManager
	SendOTP(_ context.Context, identifiers map[string]interface{},
		metadata *providers.AuthnMetadata) (*SendOTPResult, *common.ServiceError)
}
