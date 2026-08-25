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
	t.Setenv("MOSIP_IDA_CLIENT_SECRET", "shh")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND_OTP_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_AUTH_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_EXCHANGE_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_GET_CERTIFICATES_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUTH_TOKEN_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUDIT_MANAGER_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_CLIENT_ID", "")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_APP_ID", "")
	t.Setenv("MOSIP_ESIGNET_DOMAIN_URL", "")
	t.Setenv("IDA_AUTHENTICATOR_ENV", "")

	cfg, err := LoadConfig()

	require.NoError(t, err)
	require.Equal(t, "misp-key-1", cfg.LicenseKey)
	require.Equal(t, "http://internal.example.org/mosip-certs/ida-partner.cer", cfg.IDAPartnerCertificateURL)
	require.Equal(t, "http://internal.example.org/idauthentication/v1/otp/misp-key-1/", cfg.SendOTPBaseURL)
	require.Equal(t, "http://internal.example.org/idauthentication/v1/kyc-auth/delegated/misp-key-1/", cfg.KYCAuthBaseURL)
	require.Equal(t, "http://internal.example.org/idauthentication/v1/kyc-exchange/delegated/misp-key-1/", cfg.KYCExchangeBaseURL)
	require.Equal(t, "http://internal.example.org/idauthentication/v1/internal/getAllCertificates?applicationId=IDA_KYC_EXCHANGE&referenceId= ", cfg.IDACertificateURL)
	require.Equal(t, "http://internal.example.org/v1/authmanager/authenticate/clientidsecretkey", cfg.AuthTokenURL)
	require.Equal(t, "http://internal.example.org/v1/auditmanager/audits", cfg.AuditManagerURL)
	require.Equal(t, "http://internal.example.org", cfg.DomainURI)
	require.Equal(t, defaultMosipEnv, cfg.Env)
	require.Equal(t, defaultClientID, cfg.ClientID)
	require.Equal(t, "shh", cfg.SecretKey)
	require.Equal(t, defaultAppID, cfg.AppID)
}

func (ts *ConfigTestSuite) TestLoadConfigExplicitOverridesWin() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "http://internal.example.org")
	t.Setenv("MOSIP_ESIGNET_MISP_KEY", "misp-key-1")
	t.Setenv("MOSIP_IDA_CLIENT_SECRET", "shh")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL", "http://override/cert")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND_OTP_URL", "http://override/otp")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_AUTH_URL", "http://override/kyc-auth")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_EXCHANGE_URL", "http://override/kyc-exchange")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_GET_CERTIFICATES_URL", "http://override/certificates")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUTH_TOKEN_URL", "http://override/auth-token")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUDIT_MANAGER_URL", "http://override/audits")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_CLIENT_ID", "client-x")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_APP_ID", "app-x")
	t.Setenv("MOSIP_ESIGNET_DOMAIN_URL", "http://override/domain")
	t.Setenv("IDA_AUTHENTICATOR_ENV", "Production")

	cfg, err := LoadConfig()

	require.NoError(t, err)
	require.Equal(t, "http://override/cert", cfg.IDAPartnerCertificateURL)
	require.Equal(t, "http://override/otp", cfg.SendOTPBaseURL)
	require.Equal(t, "http://override/kyc-auth", cfg.KYCAuthBaseURL)
	require.Equal(t, "http://override/kyc-exchange", cfg.KYCExchangeBaseURL)
	require.Equal(t, "http://override/certificates", cfg.IDACertificateURL)
	require.Equal(t, "http://override/auth-token", cfg.AuthTokenURL)
	require.Equal(t, "http://override/audits", cfg.AuditManagerURL)
	require.Equal(t, "client-x", cfg.ClientID)
	require.Equal(t, "app-x", cfg.AppID)
	require.Equal(t, "http://override/domain", cfg.DomainURI)
	require.Equal(t, "Production", cfg.Env)
}

func (ts *ConfigTestSuite) TestLoadConfigFailsWithoutAPIBase() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "")
	t.Setenv("MOSIP_ESIGNET_MISP_KEY", "misp-key-1")
	t.Setenv("MOSIP_IDA_CLIENT_SECRET", "shh")

	_, err := LoadConfig()

	require.Error(t, err)
}

func (ts *ConfigTestSuite) TestLoadConfigFailsWithoutMispKey() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "http://internal.example.org")
	t.Setenv("MOSIP_ESIGNET_MISP_KEY", "")
	t.Setenv("MOSIP_IDA_CLIENT_SECRET", "shh")

	_, err := LoadConfig()

	require.Error(t, err)
}

func (ts *ConfigTestSuite) TestLoadConfigFailsWithoutClientSecret() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "http://internal.example.org")
	t.Setenv("MOSIP_ESIGNET_MISP_KEY", "misp-key-1")
	t.Setenv("MOSIP_IDA_CLIENT_SECRET", "")

	_, err := LoadConfig()

	require.Error(t, err)
}

type ConfigTestSuite struct {
	suite.Suite
}

func TestConfigTestSuite(t *testing.T) {
	suite.Run(t, new(ConfigTestSuite))
}
