package client

import (
	"fmt"
	"time"

	"github.com/kelseyhightower/envconfig"

	"github.com/mosip/esignet/internal/database"
)

// Config holds operator-controlled settings for the client-mgmt feature.
type Config struct {
	Postgres database.Config

	SupportedUserClaims        []string
	SupportedACRValues         []string
	SupportedGrantTypes        []string
	SupportedClientAuthMethods []string

	// Regex bounding clientId / relyingPartyId.
	SupportedIDRegex string

	// Empty uses the embedded schema; otherwise an http/https/file URL.
	AdditionalConfigSchemaURL string
}

// envConfig is the envconfig-tagged representation of Config.
type envConfig struct {
	DatabaseHost     string `envconfig:"DATABASE_HOST" default:"localhost"`
	DatabasePort     string `envconfig:"DATABASE_PORT" default:"5432"`
	DatabaseName     string `envconfig:"DATABASE_NAME" default:"mosip_esignet"`
	DatabaseUsername string `envconfig:"DATABASE_USERNAME" default:"esignetuser"`
	DBDBUserPassword string `envconfig:"DB_DBUSER_PASSWORD" required:"true"`
	DatabaseSSLMode  string `envconfig:"DATABASE_SSLMODE" default:"disable"`
	DatabaseSchema   string `envconfig:"DATABASE_SCHEMA" default:"esignet"`

	DBMaxConns        int32         `envconfig:"DB_MAX_CONNS" default:"10"`
	DBMinConns        int32         `envconfig:"DB_MIN_CONNS" default:"2"`
	DBMaxConnLifetime time.Duration `envconfig:"DB_MAX_CONN_LIFETIME" default:"1h"`
	DBMaxConnIdleTime time.Duration `envconfig:"DB_MAX_CONN_IDLE_TIME" default:"30m"`
	DBHealthTimeout   time.Duration `envconfig:"DB_HEALTH_TIMEOUT" default:"5s"`

	SupportedUserClaims        []string `envconfig:"MOSIP_ESIGNET_SUPPORTED_USER_CLAIMS" required:"true"`
	SupportedACRValues         []string `envconfig:"MOSIP_ESIGNET_SUPPORTED_ACR_VALUES" required:"true"`
	SupportedGrantTypes        []string `envconfig:"MOSIP_ESIGNET_SUPPORTED_GRANT_TYPES" required:"true"`
	SupportedClientAuthMethods []string `envconfig:"MOSIP_ESIGNET_SUPPORTED_CLIENT_AUTH_METHODS" required:"true"`

	SupportedIDRegex          string `envconfig:"MOSIP_ESIGNET_SUPPORTED_ID_REGEX" default:""`
	AdditionalConfigSchemaURL string `envconfig:"MOSIP_ESIGNET_CLIENT_ADDITIONAL_CONFIG_SCHEMA_URL" default:""`
}

// LoadConfig reads the operator's environment into a populated Config.
// Returns an error when required vars are missing or values can't parse.
func LoadConfig() (Config, error) {
	var raw envConfig
	if err := envconfig.Process("", &raw); err != nil {
		return Config{}, fmt.Errorf("load client config: %w", err)
	}
	return Config{
		Postgres: database.Config{
			Host:            raw.DatabaseHost,
			Port:            raw.DatabasePort,
			Name:            raw.DatabaseName,
			Username:        raw.DatabaseUsername,
			Password:        raw.DBDBUserPassword,
			SSLMode:         raw.DatabaseSSLMode,
			Schema:          raw.DatabaseSchema,
			MaxConns:        raw.DBMaxConns,
			MinConns:        raw.DBMinConns,
			MaxConnLifetime: raw.DBMaxConnLifetime,
			MaxConnIdleTime: raw.DBMaxConnIdleTime,
			HealthTimeout:   raw.DBHealthTimeout,
		},
		SupportedUserClaims:        raw.SupportedUserClaims,
		SupportedACRValues:         raw.SupportedACRValues,
		SupportedGrantTypes:        raw.SupportedGrantTypes,
		SupportedClientAuthMethods: raw.SupportedClientAuthMethods,
		SupportedIDRegex:           raw.SupportedIDRegex,
		AdditionalConfigSchemaURL:  raw.AdditionalConfigSchemaURL,
	}, nil
}
