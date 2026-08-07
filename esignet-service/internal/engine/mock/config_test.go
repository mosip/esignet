/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mock

import (
	"testing"

	"github.com/stretchr/testify/suite"
)

func (ts *ConfigTestSuite) TestLoadConfigDefaults() {
	t := ts.T()
	// Force the fallback path regardless of ambient env — a dev .env file
	// (sourced by make.sh) commonly sets MOSIP_ESIGNET_MOCK_DOMAIN_URL to
	// point at a local mock-identity-system instance, which would otherwise
	// leak into this test and mask the actual hardcoded defaults.
	t.Setenv("MOSIP_ESIGNET_MOCK_DOMAIN_URL", "")
	t.Setenv("MOSIP_ESIGNET_MOCK_KYC_AUTH_URL", "")
	t.Setenv("MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_URL", "")
	t.Setenv("MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_V3_URL", "")
	t.Setenv("MOSIP_ESIGNET_MOCK_SEND_OTP_URL", "")
	cfg := LoadConfig()

	if cfg.KycAuthURL != "http://mock-identity-system.mockid/v1/mock-identity-system/v2/kyc-auth" {
		t.Errorf("KycAuthURL = %q, unexpected default", cfg.KycAuthURL)
	}
	if cfg.KycExchangeURL != "http://mock-identity-system.mockid/v1/mock-identity-system/kyc-exchange" {
		t.Errorf("KycExchangeURL = %q, unexpected default", cfg.KycExchangeURL)
	}
	if cfg.KycExchangeV3URL != "http://mock-identity-system.mockid/v1/mock-identity-system/v3/kyc-exchange" {
		t.Errorf("KycExchangeV3URL = %q, unexpected default", cfg.KycExchangeV3URL)
	}
	if cfg.SendOtpURL != "http://mock-identity-system.mockid/v1/mock-identity-system/send-otp" {
		t.Errorf("SendOtpURL = %q, unexpected default", cfg.SendOtpURL)
	}
	if len(cfg.OtpChannels) != 2 || cfg.OtpChannels[0] != "email" || cfg.OtpChannels[1] != "phone" {
		t.Errorf("OtpChannels = %v, want [email phone]", cfg.OtpChannels)
	}
}

func (ts *ConfigTestSuite) TestLoadConfigHonorsEnvOverrides() {
	t := ts.T()
	t.Setenv("MOSIP_ESIGNET_MOCK_DOMAIN_URL", "http://custom.example.com/")
	t.Setenv("MOSIP_ESIGNET_MOCK_KYC_AUTH_URL", "http://custom.example.com/kyc-auth")
	t.Setenv("MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_URL", "http://custom.example.com/kyc-exchange")
	t.Setenv("MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_V3_URL", "http://custom.example.com/v3/kyc-exchange")
	t.Setenv("MOSIP_ESIGNET_MOCK_SEND_OTP_URL", "http://custom.example.com/send-otp")

	cfg := LoadConfig()
	if cfg.KycAuthURL != "http://custom.example.com/kyc-auth" {
		t.Errorf("KycAuthURL = %q, want overridden value", cfg.KycAuthURL)
	}
	if cfg.KycExchangeURL != "http://custom.example.com/kyc-exchange" {
		t.Errorf("KycExchangeURL = %q, want overridden value", cfg.KycExchangeURL)
	}
	if cfg.KycExchangeV3URL != "http://custom.example.com/v3/kyc-exchange" {
		t.Errorf("KycExchangeV3URL = %q, want overridden value", cfg.KycExchangeV3URL)
	}
	if cfg.SendOtpURL != "http://custom.example.com/send-otp" {
		t.Errorf("SendOtpURL = %q, want overridden value", cfg.SendOtpURL)
	}
}

func (ts *ConfigTestSuite) TestLoadConfigDomainURLOverrideOnly() {
	t := ts.T()
	t.Setenv("MOSIP_ESIGNET_MOCK_DOMAIN_URL", "http://custom.example.com")

	cfg := LoadConfig()
	if cfg.KycAuthURL != "http://custom.example.com/v1/mock-identity-system/v2/kyc-auth" {
		t.Errorf("KycAuthURL = %q, want domain-derived URL", cfg.KycAuthURL)
	}
}

func (ts *ConfigTestSuite) TestEnvOrDefault() {
	t := ts.T()

	t.Run("uses default when unset", func(t *testing.T) {
		if got := envOrDefault("MOSIP_ESIGNET_MOCK_TEST_UNSET_VAR", "fallback"); got != "fallback" {
			t.Errorf("envOrDefault() = %q, want fallback", got)
		}
	})

	t.Run("uses env value when set", func(t *testing.T) {
		t.Setenv("MOSIP_ESIGNET_MOCK_TEST_SET_VAR", "override")
		if got := envOrDefault("MOSIP_ESIGNET_MOCK_TEST_SET_VAR", "fallback"); got != "override" {
			t.Errorf("envOrDefault() = %q, want override", got)
		}
	})
}

func (ts *ConfigTestSuite) TestTrimTrailingSlash() {
	t := ts.T()
	cases := map[string]string{
		"http://example.com/":   "http://example.com",
		"http://example.com///": "http://example.com",
		"http://example.com":    "http://example.com",
		"":                      "",
	}
	for input, want := range cases {
		if got := trimTrailingSlash(input); got != want {
			t.Errorf("trimTrailingSlash(%q) = %q, want %q", input, got, want)
		}
	}
}

type ConfigTestSuite struct {
	suite.Suite
}

func TestConfigTestSuite(t *testing.T) {
	suite.Run(t, new(ConfigTestSuite))
}
