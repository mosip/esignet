package config

import (
	"fmt"
	"os"
	"strconv"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"

	applog "github.com/mosip/esignet/internal/log"
)

const (
	defaultPort           = "8080"
	defaultGatePort       = 8080
	defaultDataDir        = "./data"
	defaultSigningKeyPath = "./keys/signing.key"
	defaultGateScheme     = "http"
	defaultGateHostname   = "127.0.0.1"
	defaultGateLoginPath  = "/signin"
	defaultGateErrorPath  = "/error"
)

// Engine holds ThunderID engine bootstrap settings loaded from the environment.
type Engine struct {
	Port           string
	Issuer         string
	Audience       string
	DataDir        string
	SigningKeyPath string
	PreferredKeyID string
	JWKSCacheTTL   int64
	JWTLeeway      int64
	GateClient     EngineGateClient
	OAuth          EngineOAuth
}

// EngineGateClient holds login gate redirect settings for the authorization endpoint.
type EngineGateClient struct {
	Scheme    string
	Hostname  string
	Port      int
	LoginPath string
	ErrorPath string
}

// EngineOAuth holds optional OAuth2/OIDC lifetime and policy overrides.
// Zero values leave Thunder engine defaults in place.
type EngineOAuth struct {
	AuthorizationCodeLifetimeSeconds int
	AccessTokenLifetimeSeconds       int
	RefreshTokenLifetimeSeconds      int
	PARExpirySeconds                 int
	PARRequired                      bool
	DPoPRequired                     bool
	RefreshTokenRenewOnGrant         bool
}

// LoadEngine reads ThunderID engine settings from environment variables.
func LoadEngine() Engine {
	port := envOrDefault("PORT", defaultPort)
	gatePort := envInt("GATE_CLIENT_PORT")
	if gatePort == 0 {
		if parsedPort, err := strconv.Atoi(port); err == nil && parsedPort > 0 {
			gatePort = parsedPort
		} else {
			gatePort = defaultGatePort
		}
	}

	return Engine{
		Port:           port,
		Issuer:         envOrDefault("ISSUER_URL", fmt.Sprintf("http://127.0.0.1:%s", port)),
		Audience:       os.Getenv("JWT_AUDIENCE"),
		DataDir:        envOrDefault("DATA_DIR", defaultDataDir),
		SigningKeyPath: envOrDefault("SIGNING_KEY_PATH", defaultSigningKeyPath),
		PreferredKeyID: os.Getenv("JWT_PREFERRED_KEY_ID"),
		JWKSCacheTTL:   envInt64("JWKS_CACHE_TTL"),
		JWTLeeway:      envInt64("JWT_LEEWAY"),
		GateClient: EngineGateClient{
			Scheme:    envOrDefault("GATE_CLIENT_SCHEME", defaultGateScheme),
			Hostname:  envOrDefault("GATE_CLIENT_HOSTNAME", defaultGateHostname),
			Port:      gatePort,
			LoginPath: envOrDefault("GATE_CLIENT_LOGIN_PATH", defaultGateLoginPath),
			ErrorPath: envOrDefault("GATE_CLIENT_ERROR_PATH", defaultGateErrorPath),
		},
		OAuth: EngineOAuth{
			AuthorizationCodeLifetimeSeconds: envInt("OAUTH_AUTH_CODE_LIFETIME_SECONDS"),
			AccessTokenLifetimeSeconds:       envInt("OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS"),
			RefreshTokenLifetimeSeconds:      envInt("OAUTH_REFRESH_TOKEN_LIFETIME_SECONDS"),
			PARExpirySeconds:                 envInt("OAUTH_PAR_EXPIRY_SECONDS"),
			PARRequired:                      envBool("OAUTH_PAR_REQUIRED"),
			DPoPRequired:                     envBool("OAUTH_DPOP_REQUIRED"),
			RefreshTokenRenewOnGrant:         envBool("OAUTH_REFRESH_TOKEN_RENEW_ON_GRANT"),
		},
	}
}

// ThunderEngineConfig maps embedder settings into thunderidengine.EngineConfig.
func (e Engine) ThunderEngineConfig() thunderidengine.EngineConfig {
	return thunderidengine.EngineConfig{
		Issuer:   e.Issuer,
		Audience: e.Audience,
		System: thunderidengine.SystemConfig{
			SigningKeyPath: e.SigningKeyPath,
			PreferredKeyID: e.PreferredKeyID,
			JWKSCacheTTL:   e.JWKSCacheTTL,
		},
		JWT: thunderidengine.JWTConfig{
			Issuer:   e.Issuer,
			Audience: e.Audience,
			Leeway:   e.JWTLeeway,
		},
		OAuth: thunderidengine.OAuthConfig{
			AuthorizationCodeLifetimeSeconds: e.OAuth.AuthorizationCodeLifetimeSeconds,
			AccessTokenLifetimeSeconds:       e.OAuth.AccessTokenLifetimeSeconds,
			RefreshTokenLifetimeSeconds:      e.OAuth.RefreshTokenLifetimeSeconds,
			PARExpirySeconds:                 e.OAuth.PARExpirySeconds,
			PARRequired:                      e.OAuth.PARRequired,
			DPoPRequired:                     e.OAuth.DPoPRequired,
			RefreshTokenRenewOnGrant:         e.OAuth.RefreshTokenRenewOnGrant,
		},
		GateClient: thunderidengine.GateClientConfig{
			Scheme:    e.GateClient.Scheme,
			Hostname:  e.GateClient.Hostname,
			Port:      e.GateClient.Port,
			LoginPath: e.GateClient.LoginPath,
			ErrorPath: e.GateClient.ErrorPath,
		},
		DataDir: e.DataDir,
		FlowStore: thunderidengine.FlowProviderConfig{
			StoreMode: thunderidengine.StoreModeDeclarative,
		},
		Flow: thunderidengine.FlowConfig{
			Executors: []string{
				"BasicAuthExecutor",
				"AuthorizationExecutor",
				"AuthAssertExecutor",
				"ConsentExecutor",
			},
		},
	}
}

func envInt(key string) int {
	value := os.Getenv(key)
	if value == "" {
		return 0
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		applog.GetLogger().Warn(
			"invalid integer environment variable value",
			applog.String("key", key),
			applog.String("value", value),
		)
		return 0
	}
	return parsed
}

func envInt64(key string) int64 {
	value := os.Getenv(key)
	if value == "" {
		return 0
	}
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		applog.GetLogger().Warn(
			"invalid integer environment variable value",
			applog.String("key", key),
			applog.String("value", value),
		)
		return 0
	}
	return parsed
}

func envBool(key string) bool {
	value := os.Getenv(key)
	switch value {
	case "", "0", "false", "FALSE", "no", "NO":
		return false
	case "1", "true", "TRUE", "yes", "YES":
		return true
	default:
		applog.GetLogger().Warn(
			"invalid boolean environment variable value",
			applog.String("key", key),
			applog.String("value", value),
		)
		return false
	}
}
