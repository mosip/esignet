// Package config loads application, database, and Redis settings from the environment.
package config

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/kelseyhightower/envconfig"
	engineconfig "github.com/thunder-id/thunderid/pkg/thunderidengine/config"
	"gopkg.in/yaml.v3"
)

const (
	defaultDataDir         = "./data"
	appConfigFileName      = "deployment.yaml"
	defaultGatePort        = 3000
	defaultSigningKeyPath  = "./keys/signing.key"
	defaultSigningCertPath = "./keys/signing.crt"
	defaultGateScheme      = "http"
	defaultGateHostname    = "127.0.0.1"
	defaultGateLoginPath   = "/signin"
	defaultGateErrorPath   = "/error"
)

// AppConfig holds core HTTP and infrastructure settings for the service.
type AppConfig struct {
	Identifier       string                           `yaml:"identifier"`
	Port             int                              `yaml:"port"`
	Issuer           string                           `yaml:"issuer"`
	DataDir          string                           `yaml:"data_dir"`
	DB               DB                               `yaml:"db"`
	Redis            Redis                            `yaml:"redis"`
	Provider         string                           `yaml:"provider"`
	AuthFlowID       string                           `yaml:"auth_flow_id"`
	ThemeID          string                           `yaml:"theme_id"`
	LayoutID         string                           `yaml:"layout_id"`
	Server           engineconfig.ServerConfig        `yaml:"server"`
	Cache            engineconfig.CacheConfig         `yaml:"cache"`
	OAuth            engineconfig.OAuthConfig         `yaml:"oauth"`
	JWT              engineconfig.JWTConfig           `yaml:"jwt"`
	Flow             engineconfig.FlowConfig          `yaml:"flow"`
	Observability    engineconfig.ObservabilityConfig `yaml:"observability"`
	GateClient       engineconfig.GateClientConfig    `yaml:"gate_client"`
	EncryptionConfig engineconfig.EncryptionConfig    `yaml:"crypto"`
	KeyConfig        engineconfig.KeyConfig           `yaml:"key"`
	Consent          engineconfig.ConsentConfig       `yaml:"consent"`
}

// appSpec is the environment-variable layout for core application settings.
// Issuer carries no default tag because its fallback is derived from the
// resolved Port at load time.
type appSpec struct {
	Identifier    string `envconfig:"NAMESPACE" default:"esignet"`
	Port          int    `envconfig:"PORT" default:"8088"`
	Issuer        string `envconfig:"MOSIP_ESIGNET_HOST"`
	DataDir       string `envconfig:"DATA_DIR" default:"./data"`
	Provider      string `envconfig:"AUTHN_PROVIDER" default:"mock"`
	AuthFlowID    string `envconfig:"AUTH_FLOW_ID" default:"flow-esignet"`
	ThemeID       string `envconfig:"THEME_ID" default:"theme-esignet"`
	LayoutID      string `envconfig:"LAYOUT_ID" default:"layout-esignet"`
	EncryptionKey string `envconfig:"CRYPTO_ENCRYPTION_KEY" required:"true"`
}

// LoadAppConfig loads the application configuration from the default data
// directory and overlays environment-derived settings. It returns an error
// (and a nil config) if the file cannot be read or any environment variable
// cannot be parsed into its target type, so an invalid configuration fails
// startup rather than being silently coerced or mistaken for a usable
// zero-value struct.
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

	if err := applyDefaults(&cfg); err != nil {
		return nil, err
	}
	return &cfg, nil
}

func applyDefaults(cfg *AppConfig) error {
	var s appSpec
	if err := envconfig.Process("", &s); err != nil {
		return fmt.Errorf("loading app config: %w", err)
	}

	cfg.Identifier = s.Identifier
	cfg.Port = s.Port
	cfg.Issuer = s.Issuer
	if cfg.Issuer == "" {
		cfg.Issuer = fmt.Sprintf("http://127.0.0.1:%d", cfg.Port)
	}
	cfg.DataDir = s.DataDir
	yamlDB := cfg.DB
	db, err := loadDB()
	if err != nil {
		return err
	}
	cfg.DB = *db
	if !hasDBEnvConfig() && yamlDB.DSN != "" {
		cfg.DB.DSN = yamlDB.DSN
	}
	if yamlDB.Pool.MaxOpenConns > 0 {
		cfg.DB.Pool = yamlDB.Pool
	}
	redisCfg, err := loadRedis()
	if err != nil {
		return err
	}
	cfg.Redis = *redisCfg
	cfg.Provider = s.Provider
	cfg.LayoutID = s.LayoutID
	cfg.ThemeID = s.ThemeID
	cfg.AuthFlowID = s.AuthFlowID

	cfg.Server.Port = cfg.Port
	cfg.Server.Identifier = cfg.Identifier
	cfg.Server.PublicURL = cfg.Issuer
	cfg.Server.HTTPOnly = strings.HasPrefix(cfg.Issuer, "http://")

	cfg.JWT.Issuer = cfg.Issuer
	cfg.JWT.Audience = cfg.Issuer
	cfg.JWT.PreferredKeyID = "default-key"
	cfg.JWT.ValidityPeriod = 3600

	cfg.EncryptionConfig.Key = s.EncryptionKey

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
	cfg.Flow.Executors = []string{"CredentialsAuthExecutor", "AuthAssertExecutor", "ConsentExecutor"}
	cfg.Flow.Interceptors = []string{}

	cfg.Consent.Enabled = false

	cfg.OAuth.RefreshToken.RenewOnGrant = false
	cfg.OAuth.AuthorizationCode.ValidityPeriod = 3600
	cfg.OAuth.PAR.ExpiresIn = 3600
	cfg.OAuth.AuthClass.AcrAMR = map[string][]string{}
	cfg.OAuth.AuthClass.Amrs = []string{}
	cfg.OAuth.AllowWildcardRedirectURI = true

	cfg.KeyConfig.CertFile = defaultSigningCertPath
	cfg.KeyConfig.KeyFile = defaultSigningKeyPath
	cfg.KeyConfig.ID = "default-key"

	return nil
}

// overrideSpec is the environment-variable layout for optional overrides of
// gate-client and OAuth lifetime settings. Zero values mean "not set" and
// leave the corresponding default in place.
type overrideSpec struct {
	UIScheme    string `envconfig:"OIDC_UI_SCHEME"`
	UIHostname  string `envconfig:"OIDC_UI_HOSTNAME"`
	UIPort      int    `envconfig:"OIDC_UI_PORT"`
	UILoginPath string `envconfig:"OIDC_UI_LOGIN_PATH"`
	UIErrorPath string `envconfig:"OIDC_UI_ERROR_PATH"`

	AuthCodeLifetimeSecs    int64 `envconfig:"OAUTH_AUTH_CODE_LIFETIME_SECONDS"`
	PARExpirySecs           int64 `envconfig:"OAUTH_PAR_EXPIRY_SECONDS"`
	AccessTokenLifetimeSecs int64 `envconfig:"OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS"`
}

// ApplyEnvOverrides overlays environment and application settings onto cfg.
// Env vars take precedence over values from app.yaml.
func ApplyEnvOverrides(cfg *AppConfig) error {
	var s overrideSpec
	if err := envconfig.Process("", &s); err != nil {
		return fmt.Errorf("loading env overrides: %w", err)
	}

	if s.UIScheme != "" {
		cfg.GateClient.Scheme = s.UIScheme
	}
	if s.UIHostname != "" {
		cfg.GateClient.Hostname = s.UIHostname
	}
	if s.UIPort != 0 {
		if s.UIPort < 1 || s.UIPort > 65535 {
			return errors.New("invalid OIDC_UI_PORT: port must be between 1 and 65535")
		}
		cfg.GateClient.Port = s.UIPort
	}
	if s.UILoginPath != "" {
		cfg.GateClient.LoginPath = s.UILoginPath
	}
	if s.UIErrorPath != "" {
		cfg.GateClient.ErrorPath = s.UIErrorPath
	}

	if s.AuthCodeLifetimeSecs > 0 {
		cfg.OAuth.AuthorizationCode.ValidityPeriod = s.AuthCodeLifetimeSecs
	}
	if s.PARExpirySecs > 0 {
		cfg.OAuth.PAR.ExpiresIn = s.PARExpirySecs
	}
	if s.AccessTokenLifetimeSecs > 0 {
		cfg.JWT.ValidityPeriod = s.AccessTokenLifetimeSecs
	}
	return nil
}
