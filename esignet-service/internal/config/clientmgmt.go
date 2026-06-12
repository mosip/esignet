package config

import (
	"os"
	"strings"
	"time"
)

const (
	defaultClientMgmtScope    = "client_mgmt"
	defaultJWKSCacheTTL       = 5 * time.Minute
	defaultJWKSEndpointSuffix = "/.well-known/jwks.json"
)

// ClientMgmt holds settings for the client management API scope enforcement.
type ClientMgmt struct {
	// JWKSEndpoint is the URL to fetch signing public keys from.
	// Defaults to <ISSUER_URL>/.well-known/jwks.json.
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

// LoadClientMgmt reads client management API settings from the environment.
// It requires the issuer URL (passed from the engine config) to build the
// default JWKS URL.
func LoadClientMgmt(issuer string) ClientMgmt {
	endpoint := os.Getenv("CLIENT_MGMT_JWKS_ENDPOINT")
	if endpoint == "" {
		endpoint = strings.TrimRight(issuer, "/") + defaultJWKSEndpointSuffix
	}

	scope := os.Getenv("CLIENT_MGMT_REQUIRED_SCOPE")
	if scope == "" {
		scope = defaultClientMgmtScope
	}

	ttlSecs := envInt("CLIENT_MGMT_JWKS_CACHE_TTL_SECS")
	var ttl time.Duration
	if ttlSecs > 0 {
		ttl = time.Duration(ttlSecs) * time.Second
	} else {
		ttl = defaultJWKSCacheTTL
	}

	return ClientMgmt{
		JWKSEndpoint:  endpoint,
		RequiredScope: scope,
		JWKSCacheTTL:  ttl,
		Issuer:        issuer,
	}
}
