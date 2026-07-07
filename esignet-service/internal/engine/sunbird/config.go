// Package sunbird provides SunbirdRC registry (KBI) authentication for the embedder.
package sunbird

import (
	"errors"
	"fmt"
	"strings"

	"github.com/kelseyhightower/envconfig"
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

// sunbirdSpec is the environment-variable layout for SunbirdRC settings.
//
// Defaults are declared in envconfig `default` tags and reflect the released
// MOSIP Insurance registry conventions. The field-details / claims-mapping
// defaults are JSON blobs, so the embedded double quotes are backslash-escaped
// to survive struct-tag parsing.
type sunbirdSpec struct {
	SearchURL     string `envconfig:"MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_REGISTRY_SEARCH_URL"`
	EntityURL     string `envconfig:"MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REGISTRY_GET_URL"`
	IDField       string `envconfig:"MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_INDIVIDUAL_ID_FIELD" default:"policyNumber"`
	EntityIDField string `envconfig:"MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_KBI_ENTITY_ID_FIELD" default:"osid"`
	FieldDetails  string `envconfig:"MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_FIELD_DETAILS" default:"[{\"id\":\"policyNumber\",\"type\":\"text\",\"format\":\"\"},{\"id\":\"fullName\",\"type\":\"text\",\"format\":\"\"},{\"id\":\"dob\",\"type\":\"date\",\"format\":\"dd/mm/yyyy\"}]"`
	ClaimsMapping string `envconfig:"MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_IDENTITY_OPENID_CLAIMS_MAPPING" default:"{\"name\":\"fullName\",\"email\":\"email\",\"phone_number\":\"mobile\",\"gender\":\"gender\",\"birthdate\":\"dob\"}"`
	Timeout       int    `envconfig:"MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REQUEST_TIMEOUT_SECS" default:"10"`
}

// LoadConfig reads SunbirdRC auth settings from environment variables. It
// returns nil on error so a failed load can never be mistaken for a usable
// zero-value config.
//
// SearchURL has no default and must be supplied for the sunbird provider to
// start (enforced by Validate). The remaining fields fall back to their
// envconfig `default` tags.
func LoadConfig() (*Config, error) {
	var s sunbirdSpec
	if err := envconfig.Process("", &s); err != nil {
		return nil, fmt.Errorf("loading sunbird config: %w", err)
	}

	if s.Timeout <= 0 {
		return nil, fmt.Errorf("loading sunbird config: MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REQUEST_TIMEOUT_SECS must be positive, got %d", s.Timeout)
	}

	return &Config{
		SearchURL:     strings.TrimSpace(s.SearchURL),
		EntityURL:     trimTrailingSlash(strings.TrimSpace(s.EntityURL)),
		IDField:       strings.TrimSpace(s.IDField),
		EntityIDField: strings.TrimSpace(s.EntityIDField),
		FieldDetails:  strings.TrimSpace(s.FieldDetails),
		ClaimsMapping: strings.TrimSpace(s.ClaimsMapping),
		TimeoutSecs:   s.Timeout,
	}, nil
}

// Validate reports whether the SunbirdRC settings are usable by the provider.
//
// It is called at provider construction rather than in LoadConfig, which runs
// unconditionally for every provider; failing there would also break the
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

func trimTrailingSlash(value string) string {
	for len(value) > 0 && value[len(value)-1] == '/' {
		value = value[:len(value)-1]
	}
	return value
}
