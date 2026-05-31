package config

import (
	"os"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestLoadMosipAuthn_defaults(t *testing.T) {
	t.Setenv("MOSIP_LICENSE_KEY", "")
	t.Setenv("MOSIP_API_BASE_URL", "")
	t.Setenv("MOSIP_IDA_PARTNER_CERT_URL", "")
	t.Setenv("MOSIP_SEND_OTP_BASE_URL", "")
	t.Setenv("MOSIP_KYC_AUTH_BASE_URL", "")
	t.Setenv("MOSIP_KYC_EXCHANGE_BASE_URL", "")
	t.Setenv("MOSIP_P12_PATH", "")
	t.Setenv("MOSIP_P12_PASSWORD", "")

	cfg := LoadMosipAuthn()
	require.Equal(t, defaultLicenseKey, cfg.LicenseKey)
	require.Equal(t, defaultMosipAPIBase+"/mosip-certs/ida-partner.cer", cfg.IDAPartnerCertificateURL)
	require.Contains(t, cfg.SendOTPBaseURL, "/idauthentication/v1/otp/"+defaultLicenseKey+"/")
	require.Contains(t, cfg.KYCAuthBaseURL, "/idauthentication/v1/kyc-auth/delegated/"+defaultLicenseKey+"/")
	require.Contains(t, cfg.KYCExchangeBaseURL, "/idauthentication/v1/kyc-exchange/delegated/"+defaultLicenseKey+"/")
}

func TestLoadMosipAuthn_overrides(t *testing.T) {
	t.Setenv("MOSIP_LICENSE_KEY", "license-123")
	t.Setenv("MOSIP_API_BASE_URL", "https://example.test/")
	t.Setenv("MOSIP_IDA_PARTNER_CERT_URL", "https://example.test/cert.cer")
	t.Setenv("MOSIP_SEND_OTP_BASE_URL", "https://example.test/otp/")
	t.Setenv("MOSIP_KYC_AUTH_BASE_URL", "https://example.test/kyc-auth/")
	t.Setenv("MOSIP_KYC_EXCHANGE_BASE_URL", "https://example.test/kyc-exchange/")
	t.Setenv("MOSIP_P12_PATH", "/tmp/partner.p12")
	t.Setenv("MOSIP_P12_PASSWORD", "secret")

	cfg := LoadMosipAuthn()
	require.Equal(t, "license-123", cfg.LicenseKey)
	require.Equal(t, "https://example.test/cert.cer", cfg.IDAPartnerCertificateURL)
	require.Equal(t, "https://example.test/otp/", cfg.SendOTPBaseURL)
	require.Equal(t, "https://example.test/kyc-auth/", cfg.KYCAuthBaseURL)
	require.Equal(t, "https://example.test/kyc-exchange/", cfg.KYCExchangeBaseURL)
	require.Equal(t, "/tmp/partner.p12", cfg.P12Path)
	require.Equal(t, "secret", cfg.P12Password)
}

func TestLoadMosipAuthn_buildsEndpointURLsFromBase(t *testing.T) {
	t.Setenv("MOSIP_LICENSE_KEY", "abc")
	t.Setenv("MOSIP_API_BASE_URL", "https://ida.example")
	for _, key := range []string{
		"MOSIP_IDA_PARTNER_CERT_URL",
		"MOSIP_SEND_OTP_BASE_URL",
		"MOSIP_KYC_AUTH_BASE_URL",
		"MOSIP_KYC_EXCHANGE_BASE_URL",
	} {
		require.NoError(t, os.Unsetenv(key))
	}

	cfg := LoadMosipAuthn()
	require.Equal(t, "https://ida.example/idauthentication/v1/otp/abc/", cfg.SendOTPBaseURL)
}
