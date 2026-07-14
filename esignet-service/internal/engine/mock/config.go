/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package mock provides a client for the MOSIP mock-identity-system, used as the
// default AUTHN_PROVIDER for local development and testing.
package mock

import "os"

// Config holds mock-identity-system integration settings.
type Config struct {
	KycAuthURL       string
	KycExchangeURL   string
	KycExchangeV3URL string
	SendOtpURL       string
	OtpChannels      []string
}

// LoadConfig reads mock-identity-system settings from environment variables.
func LoadConfig() Config {
	apiBase := trimTrailingSlash(envOrDefault(
		"MOSIP_ESIGNET_MOCK_DOMAIN_URL", "http://mock-identity-system.mockid",
	))
	base := apiBase + "/v1/mock-identity-system"

	return Config{
		KycAuthURL: envOrDefault(
			"MOSIP_ESIGNET_MOCK_KYC_AUTH_URL", base+"/v2/kyc-auth",
		),
		KycExchangeURL: envOrDefault(
			"MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_URL", base+"/kyc-exchange",
		),
		KycExchangeV3URL: envOrDefault(
			"MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_V3_URL", base+"/v3/kyc-exchange",
		),
		SendOtpURL: envOrDefault(
			"MOSIP_ESIGNET_MOCK_SEND_OTP_URL", base+"/send-otp",
		),
		OtpChannels: []string{"email", "phone"},
	}
}

func envOrDefault(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func trimTrailingSlash(value string) string {
	for len(value) > 0 && value[len(value)-1] == '/' {
		value = value[:len(value)-1]
	}
	return value
}
