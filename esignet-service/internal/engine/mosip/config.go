// Package mosip provides MOSIP IDA authentication and OTP executors for the embedder.
package mosip

import "os"

const defaultMosipEnv = "Staging"

// Config holds MOSIP IDA integration settings.
type Config struct {
	LicenseKey               string
	IDAPartnerCertificateURL string
	SendOTPBaseURL           string
	KYCAuthBaseURL           string
	KYCExchangeBaseURL       string
	DomainURI                string
	Env                      string
	P12Path                  string
	P12Password              string
}

// LoadConfig reads MOSIP auth settings from environment variables.
func LoadConfig() Config {
	licenseKey := envOrDefault("MOSIP_ESIGNET_MISP_KEY", "")
	apiBase := trimTrailingSlash(envOrDefault("MOSIP_API_INTERNAL_HOST", ""))

	return Config{
		LicenseKey: licenseKey,
		IDAPartnerCertificateURL: envOrDefault(
			"MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL",
			apiBase+"/mosip-certs/ida-partner.cer",
		),
		SendOTPBaseURL: envOrDefault(
			"MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND-OTP-URL",
			apiBase+"/idauthentication/v1/otp/"+licenseKey+"/",
		),
		KYCAuthBaseURL: envOrDefault(
			"MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC-AUTH-URL",
			apiBase+"/idauthentication/v1/kyc-auth/delegated/"+licenseKey+"/",
		),
		KYCExchangeBaseURL: envOrDefault(
			"MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC-EXCHANGE-URL",
			apiBase+"/idauthentication/v1/kyc-exchange/delegated/"+licenseKey+"/",
		),
		DomainURI:   envOrDefault("MOSIP_ESIGNET_DOMAIN_URL", apiBase),
		Env:         envOrDefault("IDA_AUTHENTICATOR_ENV", defaultMosipEnv),
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
