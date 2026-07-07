// Package mosip provides MOSIP IDA authentication and OTP executors for the embedder.
package mosip

import (
	"fmt"

	"github.com/kelseyhightower/envconfig"
)

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

// mosipSpec is the environment-variable layout for MOSIP IDA settings.
//
// The P12 keystore is required: the provider cannot sign any IDA request
// without it, so a missing value fails startup (this spec is only processed
// when AUTHN_PROVIDER=mosip).
//
// LicenseKey and APIInternalHost are optional: they are only used to derive the
// endpoint URLs when those are not supplied individually. A deployment that sets
// every *_IDA_*_URL explicitly never reads them, so they carry no required tag.
type mosipSpec struct {
	LicenseKey         string `envconfig:"MOSIP_ESIGNET_MISP_KEY"`
	APIInternalHost    string `envconfig:"MOSIP_API_INTERNAL_HOST"`
	IDAPartnerCertURL  string `envconfig:"MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL"`
	SendOTPBaseURL     string `envconfig:"MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND_OTP_URL"`
	KYCAuthBaseURL     string `envconfig:"MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_AUTH_URL"`
	KYCExchangeBaseURL string `envconfig:"MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_EXCHANGE_URL"`
	DomainURI          string `envconfig:"MOSIP_ESIGNET_DOMAIN_URL"`
	Env                string `envconfig:"IDA_AUTHENTICATOR_ENV" default:"Staging"`
	P12Path            string `envconfig:"MOSIP_P12_PATH" required:"true"`
	P12Password        string `envconfig:"MOSIP_P12_PASSWORD" required:"true"`
}

// LoadConfig reads MOSIP auth settings from environment variables.
//
// The IDA endpoint URLs and the domain URI default to values derived from
// MOSIP_API_INTERNAL_HOST (and the license key) when not overridden explicitly.
func LoadConfig() (*Config, error) {
	var s mosipSpec
	if err := envconfig.Process("", &s); err != nil {
		return nil, fmt.Errorf("loading mosip config: %w", err)
	}

	licenseKey := s.LicenseKey
	apiBase := trimTrailingSlash(s.APIInternalHost)

	certURL := s.IDAPartnerCertURL
	if certURL == "" {
		certURL = apiBase + "/mosip-certs/ida-partner.cer"
	}
	sendOTP := s.SendOTPBaseURL
	if sendOTP == "" {
		sendOTP = apiBase + "/idauthentication/v1/otp/" + licenseKey + "/"
	}
	kycAuth := s.KYCAuthBaseURL
	if kycAuth == "" {
		kycAuth = apiBase + "/idauthentication/v1/kyc-auth/delegated/" + licenseKey + "/"
	}
	kycExchange := s.KYCExchangeBaseURL
	if kycExchange == "" {
		kycExchange = apiBase + "/idauthentication/v1/kyc-exchange/delegated/" + licenseKey + "/"
	}
	domainURI := s.DomainURI
	if domainURI == "" {
		domainURI = apiBase
	}

	return &Config{
		LicenseKey:               licenseKey,
		IDAPartnerCertificateURL: certURL,
		SendOTPBaseURL:           sendOTP,
		KYCAuthBaseURL:           kycAuth,
		KYCExchangeBaseURL:       kycExchange,
		DomainURI:                domainURI,
		Env:                      s.Env,
		P12Path:                  s.P12Path,
		P12Password:              s.P12Password,
	}, nil
}

func trimTrailingSlash(value string) string {
	for len(value) > 0 && value[len(value)-1] == '/' {
		value = value[:len(value)-1]
	}
	return value
}
