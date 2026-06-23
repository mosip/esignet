package clientmgmt

import (
	"os"
	"strconv"
	"time"
)

const (
	defaultClientMgmtScope = "client_mgmt"
	defaultJWKSCacheTTL    = 300
)

// Config holds settings for the client management API scope enforcement.
type Config struct {
	// JWKSEndpoint is the URL to fetch signing public keys from.
	// Defaults to <MOSIP_ESIGNET_HOST>/.well-known/jwks.json when unset.
	JWKSEndpoint string

	// RequiredScope is the OAuth2 scope a Bearer token must carry to access
	// the client management endpoints.
	// Env: CLIENT_MGMT_REQUIRED_SCOPE — default "client_mgmt"
	RequiredScope string

	// JWKSCacheTTL controls how long fetched keys are cached before re-fetching.
	// Env: CLIENT_MGMT_JWKS_CACHE_TTL_SECS — default 300
	JWKSCacheTTL time.Duration

	// Issuer is the expected iss claim in Bearer tokens.
	Issuer string
}

// LoadConfig reads client management API settings from the environment.
func LoadConfig() Config {
	issuer := os.Getenv("CLIENT_MGMT_ISSUER_URL")
	endpoint := os.Getenv("CLIENT_MGMT_JWKS_ENDPOINT")

	scope := os.Getenv("CLIENT_MGMT_REQUIRED_SCOPE")
	if scope == "" {
		scope = defaultClientMgmtScope
	}

	ttlSecs, err := strconv.Atoi(os.Getenv("CLIENT_MGMT_JWKS_CACHE_TTL_SECS"))
	if err != nil {
		ttlSecs = defaultJWKSCacheTTL
	}
	if ttlSecs <= 0 {
		ttlSecs = defaultJWKSCacheTTL
	}

	return Config{
		JWKSEndpoint:  endpoint,
		RequiredScope: scope,
		JWKSCacheTTL:  time.Duration(ttlSecs) * time.Second,
		Issuer:        issuer,
	}
}

// ScopeEnforcementEnabled reports whether Bearer token scope enforcement should
// be applied. Both Issuer and JWKSEndpoint must be set.
func (c Config) ScopeEnforcementEnabled() bool {
	return c.Issuer != "" && c.JWKSEndpoint != ""
}
