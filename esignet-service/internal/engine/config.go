package engine

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"

	"github.com/mosip/esignet/internal/config"
)

// Config holds host-layer settings for auth providers and declarative defaults.
type Config struct {
	Provider           string
	AuthFlowID         string
	RegistrationFlowID string
	RecoveryFlowID     string
	ThemeID            string
	LayoutID           string
}

// LoadConfig reads host-layer settings from the environment.
func LoadConfig() Config {
	return Config{
		Provider:           os.Getenv("AUTHN_PROVIDER"),
		ThemeID:            os.Getenv("DEFAULT_THEME_ID"),
		LayoutID:           os.Getenv("DEFAULT_LAYOUT_ID"),
		AuthFlowID:         os.Getenv("DEFAULT_AUTH_FLOW_ID"),
		RegistrationFlowID: os.Getenv("DEFAULT_REGISTRATION_FLOW_ID"),
		RecoveryFlowID:     os.Getenv("DEFAULT_RECOVERY_FLOW_ID"),
	}
}

const (
	defaultGatePort        = "3000"
	defaultSigningKeyPath  = "./keys/signing.key"
	defaultSigningCertPath = "./keys/signing.crt"
	defaultGateScheme      = "http"
	defaultGateHostname    = "127.0.0.1"
	defaultGateLoginPath   = "/signin"
	defaultGateErrorPath   = "/error"
)

var enabledExecutors = []string{
	"CredentialsAuthExecutor",
	"AuthorizationExecutor",
	"AuthAssertExecutor",
	"ConsentExecutor",
}

// BuildThunderConfig merges application env settings into the Thunder engine config.
func BuildThunderConfig(appCfg config.AppConfig) (*thunderidengine.Config, []string, error) {
	dataDir := appCfg.DataDir
	cfg, err := thunderidengine.LoadEngineConfig(dataDir)
	if err != nil {
		return nil, nil, err
	}

	if encKey := os.Getenv("CRYPTO_ENCRYPTION_KEY"); encKey != "" {
		cfg.Crypto.Encryption.Key = encKey
	} else {
		cfg.Crypto.Encryption.Key = os.ExpandEnv(cfg.Crypto.Encryption.Key)
	}
	if cfg.Crypto.Encryption.Key == "" {
		return nil, nil, fmt.Errorf("CRYPTO_ENCRYPTION_KEY must be set")
	}

	cfg.OAuth.RefreshToken.RenewOnGrant = false

	cfg.DeclarativeResources.Enabled = true
	cfg.Flow.Store = "declarative"
	cfg.Resource.Store = "declarative"
	cfg.OrganizationUnit.Store = "declarative"
	cfg.IdentityProvider.Store = "declarative"
	cfg.Role.Store = "declarative"
	cfg.Theme.Store = "declarative"
	cfg.Layout.Store = "declarative"
	cfg.Translation.Store = "declarative"

	issuer := appCfg.Issuer
	audience := envOrDefault("JWT_AUDIENCE", "application")
	preferredKeyID := envOrDefault("JWT_PREFERRED_KEY_ID", "default-key")
	gateClientScheme := envOrDefault("OIDC_UI_SCHEME", defaultGateScheme)
	gateClientHostname := envOrDefault("OIDC_UI_HOSTNAME", defaultGateHostname)
	gateClientPort := envOrDefault("OIDC_UI_PORT", defaultGatePort)
	gateClientLoginPath := envOrDefault("OIDC_UI_LOGIN_PATH", defaultGateLoginPath)
	gateClientErrorPath := envOrDefault("OIDC_UI_ERROR_PATH", defaultGateErrorPath)

	cfg.JWT.Issuer = issuer
	cfg.JWT.Audience = audience
	cfg.JWT.PreferredKeyID = preferredKeyID
	cfg.Server.PublicURL = strings.TrimRight(issuer, "/")
	cfg.Server.HTTPOnly = strings.HasPrefix(issuer, "http://")
	cfg.GateClient.Scheme = gateClientScheme
	cfg.GateClient.Hostname = gateClientHostname
	gateClientPortInt, err := strconv.Atoi(gateClientPort)
	if err != nil {
		return nil, nil, fmt.Errorf("invalid OIDC_UI_PORT: %w", err)
	}
	if gateClientPortInt < 1 || gateClientPortInt > 65535 {
		return nil, nil, fmt.Errorf("invalid OIDC_UI_PORT: port must be between 1 and 65535")
	}
	cfg.GateClient.Port = gateClientPortInt
	cfg.GateClient.LoginPath = gateClientLoginPath
	cfg.GateClient.ErrorPath = gateClientErrorPath

	authorizationCodeLifetimeSeconds := envOrDefault("OAUTH_AUTH_CODE_LIFETIME_SECONDS", "120")
	authorizationCodeLifetimeSecondsInt, err := strconv.Atoi(authorizationCodeLifetimeSeconds)
	if err != nil {
		return nil, nil, fmt.Errorf("invalid OAUTH_AUTH_CODE_LIFETIME_SECONDS: %w", err)
	}
	if authorizationCodeLifetimeSecondsInt > 0 {
		cfg.OAuth.AuthorizationCode.ValidityPeriod = int64(authorizationCodeLifetimeSecondsInt)
	}

	parexpirySeconds := envOrDefault("OAUTH_PAR_EXPIRY_SECONDS", "3600")
	parexpirySecondsInt, err := strconv.Atoi(parexpirySeconds)
	if err != nil {
		return nil, nil, fmt.Errorf("invalid OAUTH_PAR_EXPIRY_SECONDS: %w", err)
	}
	if parexpirySecondsInt > 0 {
		cfg.OAuth.PAR.ExpiresIn = int64(parexpirySecondsInt)
	}

	accessTokenLifetimeSeconds := envOrDefault("OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS", "3600")
	accessTokenLifetimeSecondsInt, err := strconv.Atoi(accessTokenLifetimeSeconds)
	if err != nil {
		return nil, nil, fmt.Errorf("invalid OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS: %w", err)
	}
	if accessTokenLifetimeSecondsInt > 0 {
		cfg.JWT.ValidityPeriod = int64(accessTokenLifetimeSecondsInt)
	}

	return cfg, enabledExecutors, nil
}

// PKIPaths returns signing cert and key paths relative to the data directory.
func PKIPaths(dataDir string) (certFile, keyFile string, err error) {
	dataDirAbs, err := filepath.Abs(dataDir)
	if err != nil {
		return "", "", fmt.Errorf("data dir: %w", err)
	}
	certAbs, err := filepath.Abs(defaultSigningCertPath)
	if err != nil {
		return "", "", fmt.Errorf("signing cert path: %w", err)
	}
	keyAbs, err := filepath.Abs(defaultSigningKeyPath)
	if err != nil {
		return "", "", fmt.Errorf("signing key path: %w", err)
	}
	certRel, err := filepath.Rel(dataDirAbs, certAbs)
	if err != nil {
		return "", "", fmt.Errorf("signing cert path: %w", err)
	}
	keyRel, err := filepath.Rel(dataDirAbs, keyAbs)
	if err != nil {
		return "", "", fmt.Errorf("signing key path: %w", err)
	}
	return certRel, keyRel, nil
}

func envOrDefault(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
