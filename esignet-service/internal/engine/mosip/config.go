/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package mosip provides MOSIP IDA authentication and OTP executors for the embedder.
package mosip

import (
	"errors"
	"os"
)

const (
	defaultMosipEnv = "Staging"
	defaultAppID    = "ida"
	defaultClientID = "mosip-ida-client"
)

// Config holds MOSIP IDA integration settings.
type Config struct {
	LicenseKey               string
	IDAPartnerCertificateURL string
	SendOTPBaseURL           string
	KYCAuthBaseURL           string
	KYCExchangeBaseURL       string
	DomainURI                string
	IDACertificateURL        string
	Env                      string
	AuthTokenURL             string
	ClientID                 string
	SecretKey                string
	AppID                    string
	AuditManagerURL          string
}

// LoadConfig reads MOSIP auth settings from environment variables.
func LoadConfig() (Config, error) {
	licenseKey := envOrDefault("MOSIP_ESIGNET_MISP_KEY", "")
	apiBase := trimTrailingSlash(envOrDefault("MOSIP_API_INTERNAL_HOST", ""))
	clientSecret := envOrDefault("MOSIP_IDA_CLIENT_SECRET", "")

	if clientSecret == "" {
		return Config{}, errors.New("mosip: MOSIP_IDA_CLIENT_SECRET is required")
	}
	if licenseKey == "" {
		return Config{}, errors.New("mosip: MOSIP_ESIGNET_MISP_KEY is required")
	}
	if apiBase == "" {
		return Config{}, errors.New("mosip: MOSIP_API_INTERNAL_HOST is required")
	}

	return Config{
		LicenseKey: licenseKey,
		IDAPartnerCertificateURL: envOrDefault(
			"MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL",
			apiBase+"/mosip-certs/ida-partner.cer",
		),
		SendOTPBaseURL: envOrDefault(
			"MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND_OTP_URL",
			apiBase+"/idauthentication/v1/otp/"+licenseKey+"/",
		),
		KYCAuthBaseURL: envOrDefault(
			"MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_AUTH_URL",
			apiBase+"/idauthentication/v1/kyc-auth/delegated/"+licenseKey+"/",
		),
		KYCExchangeBaseURL: envOrDefault(
			"MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_EXCHANGE_URL",
			apiBase+"/idauthentication/v1/kyc-exchange/delegated/"+licenseKey+"/",
		),
		IDACertificateURL: envOrDefault(
			"MOSIP_ESIGNET_AUTHENTICATOR_IDA_GET_CERTIFICATES_URL",
			apiBase+"/idauthentication/v1/internal/getAllCertificates?applicationId=IDA_KYC_EXCHANGE&referenceId= ",
		),
		AuthTokenURL: envOrDefault(
			"MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUTH_TOKEN_URL",
			apiBase+"/v1/authmanager/authenticate/clientidsecretkey",
		),
		AuditManagerURL: envOrDefault(
			"MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUDIT_MANAGER_URL",
			apiBase+"/v1/auditmanager/audits",
		),
		DomainURI: envOrDefault("MOSIP_ESIGNET_DOMAIN_URL", apiBase),
		Env:       envOrDefault("IDA_AUTHENTICATOR_ENV", defaultMosipEnv),
		ClientID:  envOrDefault("MOSIP_ESIGNET_AUTHENTICATOR_IDA_CLIENT_ID", defaultClientID),
		SecretKey: clientSecret,
		AppID:     envOrDefault("MOSIP_ESIGNET_AUTHENTICATOR_IDA_APP_ID", defaultAppID),
	}, nil
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
