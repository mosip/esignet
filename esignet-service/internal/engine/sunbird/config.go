/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package sunbird provides SunbirdRC registry (KBI) authentication for the embedder.
package sunbird

import (
	"errors"
	"os"
	"strconv"
	"strings"
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

// Config holds SunbirdRC registry (KBI) integration settings.
type Config struct {
	SearchURL     string
	EntityURL     string
	IDField       string
	EntityIDField string
	FieldDetails  string
	ClaimsMapping string
	TimeoutSecs   int
}

// LoadConfig reads SunbirdRC auth settings from environment variables.
//
// SearchURL has no default and must be supplied for the sunbird provider to
// start. The remaining fields default to the released MOSIP Insurance registry
// conventions.
func LoadConfig() Config {
	timeoutSecs := defaultSunbirdTimeoutSecs
	if raw := os.Getenv(envSunbirdTimeout); raw != "" {
		if secs, err := strconv.Atoi(raw); err == nil && secs > 0 {
			timeoutSecs = secs
		}
	}

	return Config{
		SearchURL:     strings.TrimSpace(envOrDefault(envSunbirdSearchURL, "")),
		EntityURL:     trimTrailingSlash(envOrDefault(envSunbirdEntityURL, "")),
		IDField:       envOrDefault(envSunbirdIDField, defaultSunbirdIDField),
		EntityIDField: envOrDefault(envSunbirdEntityIDField, defaultSunbirdEntityIDField),
		FieldDetails:  envOrDefault(envSunbirdFieldDetails, defaultSunbirdFieldDetails),
		ClaimsMapping: envOrDefault(envSunbirdClaimsMapping, defaultSunbirdClaimsMapping),
		TimeoutSecs:   timeoutSecs,
	}
}

// Validate reports whether the SunbirdRC settings are usable by the provider.
//
// It is called at provider construction rather than in LoadSunbirdAuthn, which
// runs unconditionally for every provider; failing there would also break the
// catalog/mosip providers. Gating here means a missing SearchURL only fails when
// AUTHN_PROVIDER=sunbird.
func (c Config) Validate() error {
	if strings.TrimSpace(c.SearchURL) == "" {
		return errors.New("SUNBIRD_SEARCH_URL is required for the sunbird authn provider")
	}
	if c.IDField == "" {
		return errors.New("SUNBIRD individual ID field must not be empty")
	}
	if c.EntityIDField == "" {
		return errors.New("SUNBIRD entity ID field must not be empty")
	}
	return nil
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
