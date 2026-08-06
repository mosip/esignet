/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

func (ts *ConfigTestSuite) TestLoadConfigDefaultsFromAPIBase() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "http://internal.example.org/")
	t.Setenv("MOSIP_ESIGNET_MISP_KEY", "misp-key-1")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND_OTP_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_AUTH_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_EXCHANGE_URL", "")
	t.Setenv("MOSIP_ESIGNET_DOMAIN_URL", "")
	t.Setenv("IDA_AUTHENTICATOR_ENV", "")

	cfg := LoadConfig()

	require.Equal(t, "misp-key-1", cfg.LicenseKey)
	require.Equal(t, "http://internal.example.org/mosip-certs/ida-partner.cer", cfg.IDAPartnerCertificateURL)
	require.Equal(t, "http://internal.example.org/idauthentication/v1/otp/misp-key-1/", cfg.SendOTPBaseURL)
	require.Equal(t, "http://internal.example.org/idauthentication/v1/kyc-auth/delegated/misp-key-1/", cfg.KYCAuthBaseURL)
	require.Equal(t, "http://internal.example.org/idauthentication/v1/kyc-exchange/delegated/misp-key-1/", cfg.KYCExchangeBaseURL)
	require.Equal(t, "http://internal.example.org", cfg.DomainURI)
	require.Equal(t, defaultMosipEnv, cfg.Env)
}

func (ts *ConfigTestSuite) TestLoadConfigExplicitOverridesWin() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "http://internal.example.org")
	t.Setenv("MOSIP_ESIGNET_MISP_KEY", "misp-key-1")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL", "http://override/cert")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND_OTP_URL", "http://override/otp")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_AUTH_URL", "http://override/kyc-auth")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_EXCHANGE_URL", "http://override/kyc-exchange")
	t.Setenv("MOSIP_ESIGNET_DOMAIN_URL", "http://override/domain")
	t.Setenv("IDA_AUTHENTICATOR_ENV", "Production")

	cfg := LoadConfig()

	require.Equal(t, "http://override/cert", cfg.IDAPartnerCertificateURL)
	require.Equal(t, "http://override/otp", cfg.SendOTPBaseURL)
	require.Equal(t, "http://override/kyc-auth", cfg.KYCAuthBaseURL)
	require.Equal(t, "http://override/kyc-exchange", cfg.KYCExchangeBaseURL)
	require.Equal(t, "http://override/domain", cfg.DomainURI)
	require.Equal(t, "Production", cfg.Env)
}

func (ts *ConfigTestSuite) TestLoadConfigNoAPIBaseYieldsBareSuffixes() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "")
	t.Setenv("MOSIP_ESIGNET_MISP_KEY", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND_OTP_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_AUTH_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_EXCHANGE_URL", "")
	t.Setenv("MOSIP_ESIGNET_DOMAIN_URL", "")
	t.Setenv("IDA_AUTHENTICATOR_ENV", "")

	cfg := LoadConfig()

	require.Equal(t, "/mosip-certs/ida-partner.cer", cfg.IDAPartnerCertificateURL)
	require.Equal(t, "/idauthentication/v1/otp//", cfg.SendOTPBaseURL)
	require.Equal(t, defaultMosipEnv, cfg.Env)
	require.Empty(t, cfg.DomainURI)
}

func (ts *ConfigTestSuite) TestLoadAuditConfigDerivesFromAPIBase() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "http://internal.example.org/")
	t.Setenv("MOSIP_ESIGNET_AUDIT_MANAGER_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTH_TOKEN_URL", "")
	t.Setenv("MOSIP_ESIGNET_IDA_CLIENT_ID", "")
	t.Setenv("MOSIP_ESIGNET_IDA_CLIENT_SECRET", "shh")
	t.Setenv("MOSIP_ESIGNET_IDA_APP_ID", "")

	cfg, err := LoadAuditConfig()

	require.NoError(t, err)
	require.Equal(t, "http://internal.example.org/v1/auditmanager/audits", cfg.AuditManagerURL)
	require.Equal(t, "http://internal.example.org/v1/authmanager/authenticate/clientidsecretkey", cfg.AuthTokenURL)
	require.Equal(t, auditDefaultClientID, cfg.ClientID)
	require.Equal(t, "shh", cfg.SecretKey)
	require.Equal(t, auditDefaultAppID, cfg.AppID)
}

func (ts *ConfigTestSuite) TestLoadAuditConfigExplicitOverrides() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "")
	t.Setenv("MOSIP_ESIGNET_AUDIT_MANAGER_URL", "http://audit/override")
	t.Setenv("MOSIP_ESIGNET_AUTH_TOKEN_URL", "http://token/override")
	t.Setenv("MOSIP_ESIGNET_IDA_CLIENT_ID", "client-x")
	t.Setenv("MOSIP_ESIGNET_IDA_CLIENT_SECRET", "sec")
	t.Setenv("MOSIP_ESIGNET_IDA_APP_ID", "app-x")

	cfg, err := LoadAuditConfig()

	require.NoError(t, err)
	require.Equal(t, "http://audit/override", cfg.AuditManagerURL)
	require.Equal(t, "http://token/override", cfg.AuthTokenURL)
	require.Equal(t, "client-x", cfg.ClientID)
	require.Equal(t, "app-x", cfg.AppID)
}

func (ts *ConfigTestSuite) TestLoadAuditConfigFailsWithoutAnyEndpoint() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "")
	t.Setenv("MOSIP_ESIGNET_AUDIT_MANAGER_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTH_TOKEN_URL", "")

	_, err := LoadAuditConfig()

	require.Error(t, err)
}

func (ts *ConfigTestSuite) TestLoadAuditConfigTokenURLEmptyWithoutAPIBaseOrOverride() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "")
	t.Setenv("MOSIP_ESIGNET_AUDIT_MANAGER_URL", "http://audit/only")
	t.Setenv("MOSIP_ESIGNET_AUTH_TOKEN_URL", "")

	cfg, err := LoadAuditConfig()

	require.NoError(t, err)
	require.Equal(t, "http://audit/only", cfg.AuditManagerURL)
	require.Empty(t, cfg.AuthTokenURL)
}

type ConfigTestSuite struct {
	suite.Suite
}

func TestConfigTestSuite(t *testing.T) {
	suite.Run(t, new(ConfigTestSuite))
}
