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
