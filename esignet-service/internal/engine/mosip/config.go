/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package mosip provides MOSIP IDA authentication and OTP executors for the embedder.
package mosip

import (
	"fmt"
	"os"
)

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
		DomainURI:   envOrDefault("MOSIP_ESIGNET_DOMAIN_URL", apiBase),
		Env:         envOrDefault("IDA_AUTHENTICATOR_ENV", defaultMosipEnv),
		P12Path:     envOrDefault("MOSIP_P12_PATH", ""),
		P12Password: envOrDefault("MOSIP_P12_PASSWORD", ""),
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

// AuditConfig holds mosip-audit-manager integration settings.
//
// The auditor authenticates to MOSIP authmanager with a client-id/secret,
// then POSTs an AuditRequest (wrapped in an AuditRequestWrapper) to the audit
// manager. Authentication tokens are cached in memory and purged on 401/403
// from the audit endpoint.
type AuditConfig struct {
	// AuditManagerURL is the audit ingestion endpoint (POST).
	AuditManagerURL string
	// AuthTokenURL is the authmanager clientidsecretkey endpoint (POST).
	AuthTokenURL string
	// ClientID, SecretKey and AppID are the authmanager client credentials.
	ClientID  string
	SecretKey string
	AppID     string
}

const (
	auditDefaultClientID = "mosip-ida-client"
	auditDefaultAppID    = "ida"
)

// LoadAuditConfig reads audit settings from environment variables. URLs are
// derived from MOSIP_API_INTERNAL_HOST unless overridden individually. It
// fails if no audit manager endpoint can be resolved.
func LoadAuditConfig() (AuditConfig, error) {
	apiBase := trimTrailingSlash(os.Getenv("MOSIP_API_INTERNAL_HOST"))

	auditURL := os.Getenv("MOSIP_ESIGNET_AUDIT_MANAGER_URL")
	if auditURL == "" && apiBase != "" {
		auditURL = apiBase + "/v1/auditmanager/audits"
	}
	if auditURL == "" {
		return AuditConfig{}, fmt.Errorf("audit manager not configured: set MOSIP_ESIGNET_AUDIT_MANAGER_URL or MOSIP_API_INTERNAL_HOST")
	}

	tokenURL := os.Getenv("MOSIP_ESIGNET_AUTH_TOKEN_URL")
	if tokenURL == "" && apiBase != "" {
		tokenURL = apiBase + "/v1/authmanager/authenticate/clientidsecretkey"
	}

	return AuditConfig{
		AuditManagerURL: auditURL,
		AuthTokenURL:    tokenURL,
		ClientID:        envOrDefault("MOSIP_ESIGNET_IDA_CLIENT_ID", auditDefaultClientID),
		SecretKey:       os.Getenv("MOSIP_ESIGNET_IDA_CLIENT_SECRET"),
		AppID:           envOrDefault("MOSIP_ESIGNET_IDA_APP_ID", auditDefaultAppID),
	}, nil
}
