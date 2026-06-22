package engine

import (
	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"

	"github.com/mosip/esignet/internal/engine/mock"
	"github.com/mosip/esignet/internal/engine/mosip"
)

// CustomExecutors returns embedder-supplied flow executors keyed by executor name.
func CustomExecutors(authn host.AuthnProvider, provider string) map[string]thunderidengine.ExecutorInterface {
	if provider == "mosip" {
		otpAuthn, ok := authn.(mosip.OTPAuthnProvider)
		if !ok {
			return nil
		}
		return map[string]thunderidengine.ExecutorInterface{
			mosip.ExecutorNameMosipOTP: mosip.NewMosipOtpExecutor(otpAuthn),
		}
	}

	if provider == "mock" {
		mockOTPAuthn, ok := authn.(mock.OTPAuthnProvider)
		if !ok {
			return nil
		}
		return map[string]thunderidengine.ExecutorInterface{
			mosip.ExecutorNameMosipOTP: mosip.NewMosipOtpExecutor(mockOTPAuthn),
		}
	}
	return nil
}
