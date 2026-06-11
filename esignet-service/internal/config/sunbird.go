package config

import (
	"os"
	"strconv"
)

// SunbirdRC env var names.
const (
	envSunbirdIDField       = "MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_INDIVIDUAL_ID_FIELD"
	envSunbirdFieldDetails  = "MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_FIELD_DETAILS"
	envSunbirdSearchURL     = "MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_REGISTRY_SEARCH_URL"
	envSunbirdEntityIDField = "MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_KBI_ENTITY_ID_FIELD"
	envSunbirdClaimsMapping = "MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_IDENTITY_OPENID_CLAIMS_MAPPING"
	envSunbirdEntityURL     = "MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REGISTRY_GET_URL"
	envSunbirdTimeout       = "MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REQUEST_TIMEOUT_SECS"
)

const (
	defaultSunbirdIDField       = "policyNumber"
	defaultSunbirdEntityIDField = "osid"
	defaultSunbirdTimeoutSecs   = 10

	defaultSunbirdFieldDetails = `[{"id":"policyNumber","type":"text","format":""},` +
		`{"id":"fullName","type":"text","format":""},` +
		`{"id":"dob","type":"date","format":"dd/mm/yyyy"}]`

	defaultSunbirdClaimsMapping = `{"name":"fullName","email":"email",` +
		`"phone_number":"mobile","gender":"gender","birthdate":"dob"}`
)

// SunbirdAuthn holds SunbirdRC registry (KBI) integration settings.
type SunbirdAuthn struct {
	SearchURL     string
	EntityURL     string
	IDField       string
	EntityIDField string
	FieldDetails  string
	ClaimsMapping string
	TimeoutSecs   int
}

// LoadSunbirdAuthn reads SunbirdRC auth settings from environment variables.
//
// SearchURL has no default and must be supplied for the sunbird provider to
// start. The remaining fields default to the released MOSIP Insurance registry
// conventions.
func LoadSunbirdAuthn() SunbirdAuthn {
	timeoutSecs := defaultSunbirdTimeoutSecs
	if raw := os.Getenv(envSunbirdTimeout); raw != "" {
		if secs, err := strconv.Atoi(raw); err == nil && secs > 0 {
			timeoutSecs = secs
		}
	}

	return SunbirdAuthn{
		SearchURL:     envOrDefault(envSunbirdSearchURL, ""),
		EntityURL:     trimTrailingSlash(envOrDefault(envSunbirdEntityURL, "")),
		IDField:       envOrDefault(envSunbirdIDField, defaultSunbirdIDField),
		EntityIDField: envOrDefault(envSunbirdEntityIDField, defaultSunbirdEntityIDField),
		FieldDetails:  envOrDefault(envSunbirdFieldDetails, defaultSunbirdFieldDetails),
		ClaimsMapping: envOrDefault(envSunbirdClaimsMapping, defaultSunbirdClaimsMapping),
		TimeoutSecs:   timeoutSecs,
	}
}
