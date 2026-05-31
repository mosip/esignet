package config

import "os"

const (
	defaultMosipAPIBase = "https://api-internal.collab.mosip.net"
	defaultLicenseKey   = "S1NfjLsrh2ng8eJ73Z1x8L7ryBBmzi0H2d2jGgLQOiE0h2X7Sv"
)

// MosipAuthn holds MOSIP IDA integration settings.
type MosipAuthn struct {
	LicenseKey               string
	IDAPartnerCertificateURL string
	SendOTPBaseURL           string
	KYCAuthBaseURL           string
	KYCExchangeBaseURL       string
	P12Path                  string
	P12Password              string
}

// LoadMosipAuthn reads MOSIP auth settings from environment variables.
func LoadMosipAuthn() MosipAuthn {
	licenseKey := envOrDefault("MOSIP_LICENSE_KEY", defaultLicenseKey)
	apiBase := trimTrailingSlash(envOrDefault("MOSIP_API_BASE_URL", defaultMosipAPIBase))

	return MosipAuthn{
		LicenseKey: licenseKey,
		IDAPartnerCertificateURL: envOrDefault(
			"MOSIP_IDA_PARTNER_CERT_URL",
			apiBase+"/mosip-certs/ida-partner.cer",
		),
		SendOTPBaseURL: envOrDefault(
			"MOSIP_SEND_OTP_BASE_URL",
			apiBase+"/idauthentication/v1/otp/"+licenseKey+"/",
		),
		KYCAuthBaseURL: envOrDefault(
			"MOSIP_KYC_AUTH_BASE_URL",
			apiBase+"/idauthentication/v1/kyc-auth/delegated/"+licenseKey+"/",
		),
		KYCExchangeBaseURL: envOrDefault(
			"MOSIP_KYC_EXCHANGE_BASE_URL",
			apiBase+"/idauthentication/v1/kyc-exchange/delegated/"+licenseKey+"/",
		),
		P12Path:     os.Getenv("MOSIP_P12_PATH"),
		P12Password: os.Getenv("MOSIP_P12_PASSWORD"),
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
