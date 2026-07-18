/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package config loads application, database, and Redis settings from the environment.
package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	engineconfig "github.com/thunder-id/thunderid/pkg/thunderidengine/config"
	"gopkg.in/yaml.v3"
)

const (
	defaultPort                  = 8088
	defaultDataDir               = "./data"
	appConfigFileName            = "deployment.yaml"
	defaultGatePort              = 3000
	defaultSigningKeyPath        = "keys/signing.key"
	defaultSigningCertPath       = "keys/signing.crt"
	defaultGateScheme            = "http"
	defaultGateHostname          = "127.0.0.1"
	defaultGateLoginPath         = "/signin"
	defaultGateErrorPath         = "/error"
	defaultRequestTimeLeewaySecs = 300
	defaultClientCacheTTLSecs    = 3600
)

// AppConfig holds core HTTP and infrastructure settings for the service.
type AppConfig struct {
	Identifier          string                           `yaml:"identifier"`
	Port                int                              `yaml:"port"`
	Issuer              string                           `yaml:"issuer"`
	DataDir             string                           `yaml:"data_dir"`
	RuntimeDBType       string                           `yaml:"runtime_db_type"`
	DB                  DB                               `yaml:"db"`
	Redis               Redis                            `yaml:"redis"`
	ScopeClaims         map[string][]string              `yaml:"scope_claims"`
	AuthorizationScopes map[string]string                `yaml:"authorization_scopes"`
	Provider            string                           `yaml:"provider"`
	AuthFlowID          string                           `yaml:"auth_flow_id"`
	ThemeID             string                           `yaml:"theme_id"`
	LayoutID            string                           `yaml:"layout_id"`
	Server              engineconfig.ServerConfig        `yaml:"server"`
	Cache               engineconfig.CacheConfig         `yaml:"cache"`
	OAuth               engineconfig.OAuthConfig         `yaml:"oauth"`
	JWT                 engineconfig.JWTConfig           `yaml:"jwt"`
	Flow                engineconfig.FlowConfig          `yaml:"flow"`
	Observability       engineconfig.ObservabilityConfig `yaml:"observability"`
	GateClient          engineconfig.GateClientConfig    `yaml:"gate_client"`
	EncryptionConfig    engineconfig.EncryptionConfig    `yaml:"crypto"`
	KeyConfig           engineconfig.KeyConfig           `yaml:"key"`
	Consent             engineconfig.ConsentConfig       `yaml:"consent"`
	SecurityConfig      SecurityConfig                   `yaml:"security_config"`
	ClientCacheTTLSecs  int64                            `yaml:"client_cache_ttl_secs"`
}

// SecurityConfig defines application security configuration
type SecurityConfig struct {
	IssuerURL             string                `yaml:"issuer_url"`
	JwksURL               string                `yaml:"jwks_url"`
	JwksCacheTTL          int64                 `yaml:"jwks_cache_ttl"`
	RequestTimeLeewaySecs int                   `yaml:"request_time_leeway_secs"`
	ScopeMapping          []AuthorizationConfig `yaml:"scope_mapping,omitempty"`
}

// AuthorizationConfig scope - endpoint mapping
type AuthorizationConfig struct {
	Endpoint string `yaml:"endpoint,omitempty"`
	Method   string `yaml:"method,omitempty"`
	Scope    string `yaml:"scope,omitempty"`
}

// LoadAppConfig loads the application configuration from the default data directory.
func LoadAppConfig() (*AppConfig, error) {
	path := filepath.Join(defaultDataDir, appConfigFileName)
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read %s: %w", path, err)
	}

	expanded := os.ExpandEnv(string(data))
	var cfg AppConfig
	decoder := yaml.NewDecoder(strings.NewReader(expanded))
	decoder.KnownFields(true)
	if err := decoder.Decode(&cfg); err != nil {
		return nil, fmt.Errorf("parse %s: %w", path, err)
	}

	applyDefaults(&cfg)
	return &cfg, nil
}

func applyDefaults(cfg *AppConfig) {
	cfg.Identifier = envOrDefault("NAMESPACE", "esignet")
	cfg.Port = envIntOrDefault("PORT", defaultPort)
	cfg.Issuer = envOrDefault("MOSIP_ESIGNET_HOST", fmt.Sprintf("http://127.0.0.1:%d", cfg.Port))
	cfg.DataDir = envOrDefault("DATA_DIR", defaultDataDir)
	yamlDB := cfg.DB
	cfg.DB = loadDB()
	if !hasDBEnvConfig() && yamlDB.DSN != "" {
		cfg.DB.DSN = yamlDB.DSN
	}
	if yamlDB.Pool.MaxOpenConns > 0 {
		cfg.DB.Pool = yamlDB.Pool
	}
	cfg.Redis = loadRedis()
	cfg.Provider = envOrDefault("AUTHN_PROVIDER", "mock")
	cfg.LayoutID = envOrDefault("LAYOUT_ID", "layout-esignet")
	cfg.ThemeID = envOrDefault("THEME_ID", "theme-esignet")
	cfg.AuthFlowID = envOrDefault("AUTH_FLOW_ID", "flow-esignet")

	cfg.Server.Port = cfg.Port
	cfg.Server.Identifier = cfg.Identifier
	cfg.Server.PublicURL = envOrDefault("MOSIP_ESIGNET_BASE_URL", cfg.Issuer)
	cfg.Server.HTTPOnly = strings.HasPrefix(cfg.Server.PublicURL, "http://")

	cfg.JWT.Issuer = cfg.Issuer
	cfg.JWT.Audience = cfg.Issuer
	cfg.JWT.PreferredKeyID = "default-key"
	cfg.JWT.ValidityPeriod = 3600

	cfg.EncryptionConfig.Key = envOrDefault("CRYPTO_ENCRYPTION_KEY", "")
	if cfg.EncryptionConfig.Key == "" {
		panic("CRYPTO_ENCRYPTION_KEY must be set")
	}

	cfg.Cache.Disabled = false
	cfg.Cache.Type = "redis"
	cfg.Cache.Size = 1000
	cfg.Cache.TTL = 3600
	cfg.Cache.EvictionPolicy = "LRU"
	cfg.Cache.Redis.Address = fmt.Sprintf("%s:%s", cfg.Redis.Host, cfg.Redis.Port)
	cfg.Cache.Redis.Password = cfg.Redis.Password
	cfg.Cache.Redis.DB = cfg.Redis.DB

	cfg.Observability.Enabled = true

	cfg.GateClient.Scheme = defaultGateScheme
	cfg.GateClient.Hostname = defaultGateHostname
	cfg.GateClient.Port = defaultGatePort
	cfg.GateClient.LoginPath = defaultGateLoginPath
	cfg.GateClient.ErrorPath = defaultGateErrorPath

	cfg.Flow.DefaultAuthFlowHandle = "default"
	cfg.Flow.UserOnboardingFlowHandle = "user-onboarding"
	cfg.Flow.MaxVersionHistory = 1
	cfg.Flow.AutoInferRegistration = false
	cfg.Flow.Store = "memory"
	cfg.Flow.Executors = []string{"CredentialsAuthExecutor", "AuthAssertExecutor", "ConsentExecutor", "AuthorizationExecutor"}
	cfg.Flow.Interceptors = []string{}

	cfg.Consent.Enabled = false

	cfg.OAuth.RefreshToken.RenewOnGrant = false
	cfg.OAuth.AuthorizationCode.ValidityPeriod = 3600
	cfg.OAuth.PAR.ExpiresIn = 3600
	cfg.OAuth.AuthClass.AcrAMR = map[string][]string{
		"mosip:idp:acr:generated-code": {},
		"mosip:idp:acr:biometrics":     {},
		"mosip:idp:acr:knowledge":      {},
		"mosip:idp:acr:password":       {},
	}
	cfg.OAuth.AuthClass.Amrs = []string{}
	cfg.OAuth.AllowWildcardRedirectURI = true

	cfg.KeyConfig.CertFile = defaultSigningCertPath
	cfg.KeyConfig.KeyFile = defaultSigningKeyPath
	cfg.KeyConfig.ID = "default-key"

	if cfg.SecurityConfig.RequestTimeLeewaySecs <= 0 {
		cfg.SecurityConfig.RequestTimeLeewaySecs = defaultRequestTimeLeewaySecs
	}

	if cfg.ClientCacheTTLSecs <= 0 {
		cfg.ClientCacheTTLSecs = defaultClientCacheTTLSecs
	}
}

// ApplyEnvOverrides overlays environment and application settings onto cfg.
// Env vars take precedence over values from app.yaml.
func ApplyEnvOverrides(cfg *AppConfig) error {
	if v := os.Getenv("OIDC_UI_SCHEME"); v != "" {
		cfg.GateClient.Scheme = v
	}
	if v := os.Getenv("OIDC_UI_HOSTNAME"); v != "" {
		cfg.GateClient.Hostname = v
	}
	if v := os.Getenv("OIDC_UI_PORT"); v != "" {
		port, err := strconv.Atoi(v)
		if err != nil {
			return fmt.Errorf("invalid OIDC_UI_PORT: %w", err)
		}
		if port < 1 || port > 65535 {
			return fmt.Errorf("invalid OIDC_UI_PORT: port must be between 1 and 65535")
		}
		cfg.GateClient.Port = port
	}
	if v := os.Getenv("OIDC_UI_LOGIN_PATH"); v != "" {
		cfg.GateClient.LoginPath = v
	}
	if v := os.Getenv("OIDC_UI_ERROR_PATH"); v != "" {
		cfg.GateClient.ErrorPath = v
	}

	if v := os.Getenv("OAUTH_AUTH_CODE_LIFETIME_SECONDS"); v != "" {
		secs, err := strconv.ParseInt(v, 10, 64)
		if err != nil {
			return fmt.Errorf("invalid OAUTH_AUTH_CODE_LIFETIME_SECONDS: %w", err)
		}
		if secs > 0 {
			cfg.OAuth.AuthorizationCode.ValidityPeriod = secs
		}
	}
	if v := os.Getenv("OAUTH_PAR_EXPIRY_SECONDS"); v != "" {
		secs, err := strconv.ParseInt(v, 10, 64)
		if err != nil {
			return fmt.Errorf("invalid OAUTH_PAR_EXPIRY_SECONDS: %w", err)
		}
		if secs > 0 {
			cfg.OAuth.PAR.ExpiresIn = secs
		}
	}
	if v := os.Getenv("OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS"); v != "" {
		secs, err := strconv.ParseInt(v, 10, 64)
		if err != nil {
			return fmt.Errorf("invalid OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS: %w", err)
		}
		if secs > 0 {
			cfg.JWT.ValidityPeriod = secs
		}
	}
	return nil
}

func envOrDefault(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func envIntOrDefault(key string, fallback int) int {
	raw := os.Getenv(key)
	if raw == "" {
		return fallback
	}
	n, err := strconv.Atoi(raw)
	if err != nil {
		return fallback
	}
	return n
}

func envBool(key string) bool {
	switch strings.ToLower(strings.TrimSpace(os.Getenv(key))) {
	case "1", "true", "yes", "on":
		return true
	default:
		return false
	}
}
