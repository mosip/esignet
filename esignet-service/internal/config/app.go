/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package config loads application, database, and Redis settings from the environment.
package config

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	engineconfig "github.com/thunder-id/thunderid/pkg/thunderidengine/config"
	"gopkg.in/yaml.v3"

	applog "github.com/mosip/esignet/internal/log"
)

const (
	defaultPort                  = 8080
	defaultDataDir               = "./data"
	appConfigFileName            = "deployment.yaml"
	defaultGatePort              = 3000
	defaultGateScheme            = "http"
	defaultGateHostname          = "127.0.0.1"
	defaultGateLoginPath         = "/signin"
	defaultGateErrorPath         = "/error"
	defaultRequestTimeLeewaySecs = 300
	defaultClientCacheTTLSecs    = 3600
	defaultDesignCacheTTLSecs    = 86400
	defaultFlowCacheTTLSecs      = 86400

	defaultMetricsPort = 9090

	defaultHTTPClientTimeoutSecs         = 30
	defaultHTTPDialTimeoutSecs           = 5
	defaultHTTPDialKeepAliveSecs         = 30
	defaultHTTPTLSHandshakeTimeoutSecs   = 10
	defaultHTTPResponseHeaderTimeoutSecs = 10
	defaultHTTPIdleConnTimeoutSecs       = 90
	defaultHTTPMaxConnsPerHost           = 500
	defaultHTTPMaxIdleConns              = 500
	defaultHTTPMaxIdleConnsPerHost       = 200

	defaultInboundServerReadHeaderTimeoutSecs = 10
	defaultInboundServerReadTimeoutSecs       = 30
	// defaultInboundServerWriteTimeoutSecs must exceed the outbound HTTP
	// clients' combined TimeoutSecs (see HTTPClientConfig /
	// OutboundIDSystemHTTPClient / OutboundHTTPClient), since handling a
	// request can chain both outbound calls (e.g. captcha then an ID-system
	// call); otherwise the server would abort responses still legitimately in
	// flight. Derived from the two outbound defaults (30s each) plus a 30s
	// margin so the shipped defaults satisfy this invariant by construction
	// instead of tripping warnIfWriteTimeoutTooLow on every stock startup —
	// a custom OutboundIDSystemHTTPClient/OutboundHTTPClient timeout still
	// needs write_timeout_secs raised to match, which that warning catches.
	defaultInboundServerWriteTimeoutSecs = 2*defaultHTTPClientTimeoutSecs + 30
	defaultInboundServerIdleTimeoutSecs  = 120

	defaultCaptchaModuleName  = "esignet"
	defaultCaptchaTimeoutSecs = 10
)

// OIDCServiceAppID is esignet's own ApplicationID in the keymanager's
// key_policy_def table (see db_scripts/mosip_esignet/dml/esignet-key_policy_def.sql),
// under which its Component Master Key, EC sign key, and Component
// Encryption Keys are provisioned/resolved.
const OIDCServiceAppID = "OIDC_SERVICE"

// OIDCPartnerAppID is the ApplicationID under which esignet's MOSIP partner
// (RSA) signing key is provisioned/resolved, used to sign outgoing requests
// to MOSIP's authentication APIs (see internal/engine/mosip/authenticator.go).
const OIDCPartnerAppID = "OIDC_PARTNER"

// AppConfig holds core HTTP and infrastructure settings for the service.
type AppConfig struct {
	Identifier                 string                           `yaml:"identifier"`
	Port                       int                              `yaml:"port"`
	MetricsPort                int                              `yaml:"metrics_port"`
	Issuer                     string                           `yaml:"issuer"`
	DataDir                    string                           `yaml:"data_dir"`
	RuntimeDBType              string                           `yaml:"runtime_db_type"`
	DB                         DB                               `yaml:"db"`
	Redis                      Redis                            `yaml:"redis"`
	ScopeClaims                map[string][]string              `yaml:"scope_claims"`
	ResourceServers            []ResourceServerConfig           `yaml:"resource_servers"`
	Provider                   string                           `yaml:"provider"`
	AuthFlowID                 string                           `yaml:"auth_flow_id"`
	ThemeID                    string                           `yaml:"theme_id"`
	LayoutID                   string                           `yaml:"layout_id"`
	Server                     engineconfig.ServerConfig        `yaml:"server"`
	OAuth                      engineconfig.OAuthConfig         `yaml:"oauth"`
	JWT                        engineconfig.JWTConfig           `yaml:"jwt"`
	Flow                       engineconfig.FlowConfig          `yaml:"flow"`
	Observability              engineconfig.ObservabilityConfig `yaml:"observability"`
	GateClient                 engineconfig.GateClientConfig    `yaml:"gate_client"`
	SecurityConfig             SecurityConfig                   `yaml:"security_config"`
	ClientCacheTTLSecs         int64                            `yaml:"client_cache_ttl_secs"`
	DesignCacheTTLSecs         int64                            `yaml:"design_cache_ttl_secs"`
	FlowCacheTTLSecs           int64                            `yaml:"flow_cache_ttl_secs"`
	CaptchaConfig              CaptchaConfig                    `yaml:"captcha_config"`
	OutboundIDSystemHTTPClient HTTPClientConfig                 `yaml:"outbound_idsystem_http_client"`
	OutboundHTTPClient         HTTPClientConfig                 `yaml:"outbound_http_client"`
	InboundHTTPServer          InboundHTTPServerConfig          `yaml:"inbound_http_server"`
	SupportedSigningAlgorithms []string                         `yaml:"supported_signing_algorithms"`
	SupportedEncAlgorithms     []string                         `yaml:"supported_enc_algorithms"`
	AllowedOriginRegex         string                           `yaml:"allowed_origin_regex"`
	PProfConfig                PProfConfig                      `yaml:"-"`
}

// PProfConfig defines application pprof configuration
type PProfConfig struct {
	Enabled bool `yaml:"enabled"`
	Port    int  `yaml:"port"`
}

// ResourceServerConfig declares one resource server this deployment issues access tokens for.
// Identifier is the RFC 8707 `resource` request-parameter value and the access token's `aud`
// claim; it must be an absolute URI. Scopes maps each permission scope name this resource server
// defines to a human-readable description.
//
// The whole resource_servers list can be overridden per-environment (without rebuilding the
// image) via the MOSIP_ESIGNET_RESOURCE_SERVERS_JSON env var, set to a JSON array matching this
// shape, e.g.:
//
//	[{"id":"esignet-default","identifier":"https://esignet.example.com/resource","default":true,"scopes":{"payment:pay":"Make payments"}}]
//
// When set, it fully replaces (not merges with) the deployment.yaml value and is subject to the
// same validateResourceServers checks (at most one default, no duplicate id/identifier).
type ResourceServerConfig struct {
	ID         string            `yaml:"id"         json:"id"`
	Identifier string            `yaml:"identifier" json:"identifier"`
	Default    bool              `yaml:"default"     json:"default"`
	Scopes     map[string]string `yaml:"scopes"      json:"scopes"`
}

// ResourceServerByIdentifier returns the resource server registered under identifier. An empty
// identifier resolves to the resource server marked Default, or nil if none is configured.
func (c *AppConfig) ResourceServerByIdentifier(identifier string) *ResourceServerConfig {
	for i := range c.ResourceServers {
		rs := &c.ResourceServers[i]
		if identifier == "" {
			if rs.Default {
				return rs
			}
			continue
		}
		if rs.Identifier == identifier {
			return rs
		}
	}
	return nil
}

// ResourceServerByID returns the resource server with the given internal ID, or nil if none matches.
func (c *AppConfig) ResourceServerByID(id string) *ResourceServerConfig {
	for i := range c.ResourceServers {
		if c.ResourceServers[i].ID == id {
			return &c.ResourceServers[i]
		}
	}
	return nil
}

// IsAuthorizationScope reports whether name is a permission scope defined on any configured
// resource server.
func (c *AppConfig) IsAuthorizationScope(name string) bool {
	for _, rs := range c.ResourceServers {
		if _, ok := rs.Scopes[name]; ok {
			return true
		}
	}
	return false
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

// CaptchaConfig Captcha validation configuration
type CaptchaConfig struct {
	ValidatorURL string `yaml:"validator_url"`
	ModuleName   string `yaml:"module_name"`
	TimeoutSecs  int    `yaml:"timeout_secs"`
}

// HTTPClientConfig tunes an outbound HTTP client's timeouts and connection pooling.
type HTTPClientConfig struct {
	TimeoutSecs               int `yaml:"timeout_secs"`
	DialTimeoutSecs           int `yaml:"dial_timeout_secs"`
	DialKeepAliveSecs         int `yaml:"dial_keep_alive_secs"`
	TLSHandshakeTimeoutSecs   int `yaml:"tls_handshake_timeout_secs"`
	ResponseHeaderTimeoutSecs int `yaml:"response_header_timeout_secs"`
	IdleConnTimeoutSecs       int `yaml:"idle_conn_timeout_secs"`
	MaxConnsPerHost           int `yaml:"max_conns_per_host"`
	MaxIdleConns              int `yaml:"max_idle_conns"`
	MaxIdleConnsPerHost       int `yaml:"max_idle_conns_per_host"`
}

// InboundHTTPServerConfig tunes the inbound http.Server's timeouts, guarding
// against slow-client and stalled-backend goroutine/connection leaks.
type InboundHTTPServerConfig struct {
	ReadHeaderTimeoutSecs int `yaml:"read_header_timeout_secs"`
	ReadTimeoutSecs       int `yaml:"read_timeout_secs"`
	WriteTimeoutSecs      int `yaml:"write_timeout_secs"`
	IdleTimeoutSecs       int `yaml:"idle_timeout_secs"`
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
	if err := validateResourceServers(cfg.ResourceServers); err != nil {
		return nil, fmt.Errorf("resource_servers: %w", err)
	}
	return &cfg, nil
}

// validateResourceServers rejects an ambiguous resource_servers configuration: at most one entry
// may be marked default (an empty `resource` request parameter cannot otherwise resolve which one
// to bind to), no two entries may share an id or identifier, and every identifier must be an
// absolute URI (it becomes the access token's `aud` claim).
func validateResourceServers(resourceServers []ResourceServerConfig) error {
	seenIDs := make(map[string]bool, len(resourceServers))
	seenIdentifiers := make(map[string]bool, len(resourceServers))
	defaultID := ""
	for _, rs := range resourceServers {
		if seenIDs[rs.ID] {
			return fmt.Errorf("duplicate resource server id %q", rs.ID)
		}
		seenIDs[rs.ID] = true

		if seenIdentifiers[rs.Identifier] {
			return fmt.Errorf("duplicate resource server identifier %q", rs.Identifier)
		}
		seenIdentifiers[rs.Identifier] = true

		if !isAbsoluteURI(rs.Identifier) {
			return fmt.Errorf("resource server %q: identifier %q must be an absolute URI", rs.ID, rs.Identifier)
		}

		if rs.Default {
			if defaultID != "" {
				return fmt.Errorf("multiple resource servers marked default: %q and %q", defaultID, rs.ID)
			}
			defaultID = rs.ID
		}
	}
	return nil
}

// isAbsoluteURI reports whether identifier is an absolute URI, i.e. it has both a scheme and a
// host (e.g. "https://esignet.example.com/resource"). A relative path such as
// "/v1/esignet/vci/credential" — which is what an unset host env var expands to — is rejected.
func isAbsoluteURI(identifier string) bool {
	u, err := url.Parse(identifier)
	if err != nil {
		return false
	}
	return u.Scheme != "" && u.Host != ""
}

func boolPtr(b bool) *bool { return &b }

func applyDefaults(cfg *AppConfig) {
	cfg.Identifier = envOrConfigOrDefault("NAMESPACE", cfg.Identifier, "esignet")
	cfg.Port = envIntOrConfigOrDefault("PORT", cfg.Port, defaultPort)
	cfg.Issuer = envOrConfigOrDefault("MOSIP_ESIGNET_HOST", cfg.Issuer, fmt.Sprintf("http://localhost:%d", cfg.Port))
	cfg.DataDir = envOrConfigOrDefault("DATA_DIR", cfg.DataDir, defaultDataDir)
	// Threads cfg.MetricsPort (the decoded `metrics_port:` value) through like
	// cfg.Port above. Previously this read only the env var and the compiled
	// default, so a metrics_port set in deployment.yaml parsed cleanly and was
	// then silently discarded — the mirror image of the yaml-shadows-env trap
	// in issue #2498, and the same "dead config" surprise for an operator.
	cfg.MetricsPort = envIntOrConfigOrDefault("METRICS_PORT", cfg.MetricsPort, defaultMetricsPort)
	cfg.DB = loadDB(cfg.DB)

	cfg.PProfConfig = PProfConfig{
		Enabled: envBool("MOSIP_ESIGNET_PPROF_ENABLED"),
		Port:    envIntOrDefault("MOSIP_ESIGNET_PPROF_PORT", 6060),
	}

	cacheType := envOrConfigOrDefault("MOSIP_ESIGNET_CACHE_TYPE", cfg.RuntimeDBType, "inmemory")
	if cacheType != "redis" {
		cacheType = "inmemory"
	}
	cfg.RuntimeDBType = cacheType
	if cacheType == "redis" {
		cfg.Redis = loadRedis(cfg.Redis)
	}

	cfg.Provider = envOrConfigOrDefault("MOSIP_ESIGNET_AUTHN_PROVIDER", cfg.Provider, "mock")
	cfg.LayoutID = envOrConfigOrDefault("MOSIP_ESIGNET_LAYOUT_ID", cfg.LayoutID, "layout-esignet")
	cfg.ThemeID = envOrConfigOrDefault("MOSIP_ESIGNET_THEME_ID", cfg.ThemeID, "theme-esignet")
	cfg.AuthFlowID = envOrConfigOrDefault("MOSIP_ESIGNET_AUTH_FLOW_ID", cfg.AuthFlowID, "flow-esignet")
	if len(cfg.SupportedSigningAlgorithms) == 0 {
		cfg.SupportedSigningAlgorithms = []string{"PS256", "ES256", "ES256K", "EdDSA"}
	}
	if len(cfg.SupportedEncAlgorithms) == 0 {
		cfg.SupportedEncAlgorithms = []string{"RSA-OAEP", "RSA-OAEP-256"}
	}
	cfg.AllowedOriginRegex = envOrConfigOrDefault("MOSIP_ESIGNET_CORS_ALLOWED_ORIGIN_REGEX", cfg.AllowedOriginRegex, "")

	cfg.Server.Port = cfg.Port
	cfg.Server.Identifier = cfg.Identifier
	cfg.Server.PublicURL = envOrConfigOrDefault("MOSIP_ESIGNET_BASE_URL", cfg.Server.PublicURL, cfg.Issuer)
	cfg.Server.HTTPOnly = strings.HasPrefix(cfg.Server.PublicURL, "http://")

	cfg.JWT.Issuer = cfg.Issuer
	cfg.JWT.Audience = cfg.Issuer
	cfg.JWT.PreferredKeyID = envOrConfigOrDefault("MOSIP_ESIGNET_SIGNING_KEY_REF_ID", cfg.JWT.PreferredKeyID, "RSA_2048")
	cfg.JWT.ValidityPeriod = int64(envIntOrConfigOrDefault("MOSIP_ESIGNET_JWT_VALIDITY_PERIOD", int(cfg.JWT.ValidityPeriod), 120))
	cfg.JWT.Leeway = int64(envIntOrConfigOrDefault("MOSIP_ESIGNET_JWT_LEEWAY", int(cfg.JWT.Leeway), 10))

	cfg.Observability.Enabled = true

	// GateClient env overrides (MOSIP_ESIGNET_OIDC_UI_*) are applied afterwards
	// by ApplyEnvOverrides, so only the yaml-value/compiled-default fallback is
	// needed here.
	if cfg.GateClient.Scheme == "" {
		cfg.GateClient.Scheme = defaultGateScheme
	}
	if cfg.GateClient.Hostname == "" {
		cfg.GateClient.Hostname = defaultGateHostname
	}
	if cfg.GateClient.Port == 0 {
		cfg.GateClient.Port = defaultGatePort
	}
	if cfg.GateClient.LoginPath == "" {
		cfg.GateClient.LoginPath = defaultGateLoginPath
	}
	if cfg.GateClient.ErrorPath == "" {
		cfg.GateClient.ErrorPath = defaultGateErrorPath
	}

	// cfg.Flow.DefaultAuthFlowHandle = "default"
	// cfg.Flow.UserOnboardingFlowHandle = "user-onboarding"
	cfg.Flow.MaxVersionHistory = 1
	cfg.Flow.AutoInferRegistration = false
	cfg.Flow.Store = cacheType
	cfg.Flow.Executors = []string{"CredentialsAuthExecutor", "AuthAssertExecutor", "ConsentExecutor", "AuthorizationExecutor"}
	cfg.Flow.Interceptors = []string{}

	cfg.OAuth.RefreshToken.RenewOnGrant = false
	if cfg.OAuth.AuthorizationCode.ValidityPeriod <= 0 {
		// Matches data/deployment.yaml's documented default (60s) — an
		// authorization code should be short-lived and single-use.
		cfg.OAuth.AuthorizationCode.ValidityPeriod = 60
	}
	if cfg.OAuth.PAR.ExpiresIn <= 0 {
		cfg.OAuth.PAR.ExpiresIn = 3600
	}
	cfg.OAuth.AuthClass.AcrAMR = map[string][]string{
		"mosip:idp:acr:generated-code": {},
		"mosip:idp:acr:biometrics":     {},
		"mosip:idp:acr:knowledge":      {},
		"mosip:idp:acr:password":       {},
	}
	cfg.OAuth.AuthClass.Amrs = []string{}
	cfg.OAuth.AllowWildcardRedirectURI = true
	cfg.OAuth.TokenRevocation.Enabled = boolPtr(false)
	cfg.OAuth.Logout.Enabled = boolPtr(false)
	cfg.OAuth.DPoP.Leeway = envIntOrConfigOrDefault("MOSIP_ESIGNET_DPOP_LEEWAY", cfg.OAuth.DPoP.Leeway, 10)

	if cfg.SecurityConfig.RequestTimeLeewaySecs <= 0 {
		cfg.SecurityConfig.RequestTimeLeewaySecs = defaultRequestTimeLeewaySecs
	}

	cfg.CaptchaConfig.ValidatorURL = envOrDefault("MOSIP_ESIGNET_CAPTCHA_VALIDATOR_URL", cfg.CaptchaConfig.ValidatorURL)
	cfg.CaptchaConfig.ModuleName = envOrConfigOrDefault("MOSIP_ESIGNET_CAPTCHA_MODULE_NAME", cfg.CaptchaConfig.ModuleName, defaultCaptchaModuleName)
	cfg.CaptchaConfig.TimeoutSecs = envIntOrConfigOrDefault("MOSIP_ESIGNET_CAPTCHA_TIMEOUT_SECS", cfg.CaptchaConfig.TimeoutSecs, defaultCaptchaTimeoutSecs)

	if cfg.ClientCacheTTLSecs <= 0 {
		cfg.ClientCacheTTLSecs = defaultClientCacheTTLSecs
	}

	if cfg.DesignCacheTTLSecs <= 0 {
		cfg.DesignCacheTTLSecs = defaultDesignCacheTTLSecs
	}

	if cfg.FlowCacheTTLSecs <= 0 {
		cfg.FlowCacheTTLSecs = defaultFlowCacheTTLSecs
	}

	applyHTTPClientEnvOverrides("OUTBOUND_IDSYSTEM_HTTP_CLIENT", &cfg.OutboundIDSystemHTTPClient)
	applyHTTPClientDefaults(&cfg.OutboundIDSystemHTTPClient)
	applyHTTPClientEnvOverrides("OUTBOUND_HTTP_CLIENT", &cfg.OutboundHTTPClient)
	applyHTTPClientDefaults(&cfg.OutboundHTTPClient)

	applyInboundServerEnvOverrides(&cfg.InboundHTTPServer)
	if cfg.InboundHTTPServer.ReadHeaderTimeoutSecs <= 0 {
		cfg.InboundHTTPServer.ReadHeaderTimeoutSecs = defaultInboundServerReadHeaderTimeoutSecs
	}
	if cfg.InboundHTTPServer.ReadTimeoutSecs <= 0 {
		cfg.InboundHTTPServer.ReadTimeoutSecs = defaultInboundServerReadTimeoutSecs
	}
	if cfg.InboundHTTPServer.WriteTimeoutSecs <= 0 {
		cfg.InboundHTTPServer.WriteTimeoutSecs = defaultInboundServerWriteTimeoutSecs
	}
	if cfg.InboundHTTPServer.IdleTimeoutSecs <= 0 {
		cfg.InboundHTTPServer.IdleTimeoutSecs = defaultInboundServerIdleTimeoutSecs
	}

	warnIfWriteTimeoutTooLow(cfg)
}

// applyHTTPClientEnvOverrides applies "<prefix>_*_SECS"/"<prefix>_MAX_*" env
// vars onto cfg, taking precedence over any value already set from YAML.
// Deliberately reads os.Getenv directly (like loadDB/loadRedis) rather than
// relying on deployment.yaml's "${VAR:-default}" substitution — os.ExpandEnv
// doesn't support that bash syntax, so a literal env var name never matches
// and every such expression in the file silently resolves to "".
func applyHTTPClientEnvOverrides(prefix string, cfg *HTTPClientConfig) {
	if v, ok := envPositiveInt(prefix + "_TIMEOUT_SECS"); ok {
		cfg.TimeoutSecs = v
	}
	if v, ok := envPositiveInt(prefix + "_DIAL_TIMEOUT_SECS"); ok {
		cfg.DialTimeoutSecs = v
	}
	if v, ok := envPositiveInt(prefix + "_DIAL_KEEP_ALIVE_SECS"); ok {
		cfg.DialKeepAliveSecs = v
	}
	if v, ok := envPositiveInt(prefix + "_TLS_HANDSHAKE_TIMEOUT_SECS"); ok {
		cfg.TLSHandshakeTimeoutSecs = v
	}
	if v, ok := envPositiveInt(prefix + "_RESPONSE_HEADER_TIMEOUT_SECS"); ok {
		cfg.ResponseHeaderTimeoutSecs = v
	}
	if v, ok := envPositiveInt(prefix + "_IDLE_CONN_TIMEOUT_SECS"); ok {
		cfg.IdleConnTimeoutSecs = v
	}
	if v, ok := envPositiveInt(prefix + "_MAX_CONNS_PER_HOST"); ok {
		cfg.MaxConnsPerHost = v
	}
	if v, ok := envPositiveInt(prefix + "_MAX_IDLE_CONNS"); ok {
		cfg.MaxIdleConns = v
	}
	if v, ok := envPositiveInt(prefix + "_MAX_IDLE_CONNS_PER_HOST"); ok {
		cfg.MaxIdleConnsPerHost = v
	}
}

// applyInboundServerEnvOverrides applies "INBOUND_HTTP_SERVER_*_SECS" env
// vars onto cfg, taking precedence over any value already set from YAML. See
// applyHTTPClientEnvOverrides for why this reads os.Getenv directly instead
// of relying on deployment.yaml's (non-functional) "${VAR:-default}" syntax.
func applyInboundServerEnvOverrides(cfg *InboundHTTPServerConfig) {
	if v, ok := envPositiveInt("INBOUND_HTTP_SERVER_READ_HEADER_TIMEOUT_SECS"); ok {
		cfg.ReadHeaderTimeoutSecs = v
	}
	if v, ok := envPositiveInt("INBOUND_HTTP_SERVER_READ_TIMEOUT_SECS"); ok {
		cfg.ReadTimeoutSecs = v
	}
	if v, ok := envPositiveInt("INBOUND_HTTP_SERVER_WRITE_TIMEOUT_SECS"); ok {
		cfg.WriteTimeoutSecs = v
	}
	if v, ok := envPositiveInt("INBOUND_HTTP_SERVER_IDLE_TIMEOUT_SECS"); ok {
		cfg.IdleTimeoutSecs = v
	}
}

// envPositiveInt returns the parsed value when key is set to a positive
// integer. A key that is set but not a positive integer (a typo, a negative
// value) is logged and ignored rather than silently discarded — without
// this, ApplyEnvOverrides's own validation for other settings would
// disagree with these HTTP-tuning env vars, which previously failed silent.
func envPositiveInt(key string) (int, bool) {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return 0, false
	}
	n, err := strconv.Atoi(raw)
	if err != nil || n <= 0 {
		applog.GetLogger().Warn(context.Background(),
			"ignoring invalid http tuning env var",
			applog.String("key", key), applog.String("value", raw))
		return 0, false
	}
	return n, true
}

// warnIfWriteTimeoutTooLow logs if InboundHTTPServer.WriteTimeoutSecs doesn't
// exceed the combined worst-case outbound wait — a single request can invoke
// both OutboundIDSystemHTTPClient and OutboundHTTPClient sequentially (e.g.
// captcha validation followed by an ID-system call), so the inbound write
// timeout must cover their sum, not just the larger of the two individually.
func warnIfWriteTimeoutTooLow(cfg *AppConfig) {
	combinedOutboundSecs := cfg.OutboundIDSystemHTTPClient.TimeoutSecs + cfg.OutboundHTTPClient.TimeoutSecs
	if cfg.InboundHTTPServer.WriteTimeoutSecs <= combinedOutboundSecs {
		applog.GetLogger().Warn(context.Background(),
			"inbound_http_server.write_timeout_secs does not exceed the combined outbound client timeouts; "+
				"a request chaining both outbound calls can be aborted mid-flight",
			applog.Int("writeTimeoutSecs", cfg.InboundHTTPServer.WriteTimeoutSecs),
			applog.Int("outboundIDSystemTimeoutSecs", cfg.OutboundIDSystemHTTPClient.TimeoutSecs),
			applog.Int("outboundHTTPTimeoutSecs", cfg.OutboundHTTPClient.TimeoutSecs),
		)
	}
}

// applyHTTPClientDefaults fills unset (<= 0) fields of an outbound
// HTTPClientConfig block with the package defaults. Applied independently
// to each of OutboundIDSystemHTTPClient/OutboundHTTPClient so each can be
// tuned separately via its own yaml block.
func applyHTTPClientDefaults(cfg *HTTPClientConfig) {
	if cfg.TimeoutSecs <= 0 {
		cfg.TimeoutSecs = defaultHTTPClientTimeoutSecs
	}
	if cfg.DialTimeoutSecs <= 0 {
		cfg.DialTimeoutSecs = defaultHTTPDialTimeoutSecs
	}
	if cfg.DialKeepAliveSecs <= 0 {
		cfg.DialKeepAliveSecs = defaultHTTPDialKeepAliveSecs
	}
	if cfg.TLSHandshakeTimeoutSecs <= 0 {
		cfg.TLSHandshakeTimeoutSecs = defaultHTTPTLSHandshakeTimeoutSecs
	}
	if cfg.ResponseHeaderTimeoutSecs <= 0 {
		cfg.ResponseHeaderTimeoutSecs = defaultHTTPResponseHeaderTimeoutSecs
	}
	if cfg.IdleConnTimeoutSecs <= 0 {
		cfg.IdleConnTimeoutSecs = defaultHTTPIdleConnTimeoutSecs
	}
	if cfg.MaxConnsPerHost <= 0 {
		cfg.MaxConnsPerHost = defaultHTTPMaxConnsPerHost
	}
	if cfg.MaxIdleConns <= 0 {
		cfg.MaxIdleConns = defaultHTTPMaxIdleConns
	}
	if cfg.MaxIdleConnsPerHost <= 0 {
		cfg.MaxIdleConnsPerHost = defaultHTTPMaxIdleConnsPerHost
	}
}

// ApplyEnvOverrides overlays environment and application settings onto cfg.
// Env vars take precedence over values from app.yaml.
func ApplyEnvOverrides(cfg *AppConfig) error {
	if v := os.Getenv("MOSIP_ESIGNET_OIDC_UI_SCHEME"); v != "" {
		cfg.GateClient.Scheme = v
	}
	if v := os.Getenv("MOSIP_ESIGNET_OIDC_UI_HOSTNAME"); v != "" {
		cfg.GateClient.Hostname = v
	}
	if v := os.Getenv("MOSIP_ESIGNET_OIDC_UI_PORT"); v != "" {
		port, err := strconv.Atoi(v)
		if err != nil {
			return fmt.Errorf("invalid MOSIP_ESIGNET_OIDC_UI_PORT: %w", err)
		}
		if port < 1 || port > 65535 {
			return fmt.Errorf("invalid MOSIP_ESIGNET_OIDC_UI_PORT: port must be between 1 and 65535")
		}
		cfg.GateClient.Port = port
	}
	if v := os.Getenv("MOSIP_ESIGNET_OIDC_UI_LOGIN_PATH"); v != "" {
		cfg.GateClient.LoginPath = v
	}
	if v := os.Getenv("MOSIP_ESIGNET_OIDC_UI_ERROR_PATH"); v != "" {
		cfg.GateClient.ErrorPath = v
	}

	if v := os.Getenv("MOSIP_ESIGNET_OAUTH_AUTH_CODE_LIFETIME_SECONDS"); v != "" {
		secs, err := strconv.ParseInt(v, 10, 64)
		if err != nil {
			return fmt.Errorf("invalid MOSIP_ESIGNET_OAUTH_AUTH_CODE_LIFETIME_SECONDS: %w", err)
		}
		if secs > 0 {
			cfg.OAuth.AuthorizationCode.ValidityPeriod = secs
		}
	}
	if v := os.Getenv("MOSIP_ESIGNET_OAUTH_PAR_EXPIRY_SECONDS"); v != "" {
		secs, err := strconv.ParseInt(v, 10, 64)
		if err != nil {
			return fmt.Errorf("invalid MOSIP_ESIGNET_OAUTH_PAR_EXPIRY_SECONDS: %w", err)
		}
		if secs > 0 {
			cfg.OAuth.PAR.ExpiresIn = secs
		}
	}
	if v := os.Getenv("MOSIP_ESIGNET_OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS"); v != "" {
		secs, err := strconv.ParseInt(v, 10, 64)
		if err != nil {
			return fmt.Errorf("invalid MOSIP_ESIGNET_OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS: %w", err)
		}
		if secs > 0 {
			cfg.JWT.ValidityPeriod = secs
		}
	}
	if v := os.Getenv("MOSIP_ESIGNET_OAUTH_SUPPORTED_SIGNING_ALGORITHMS"); v != "" {
		algorithms := strings.Split(v, ",")
		for i := range algorithms {
			algorithms[i] = strings.TrimSpace(algorithms[i])
		}
		cfg.SupportedSigningAlgorithms = algorithms
	}
	if v := os.Getenv("MOSIP_ESIGNET_OAUTH_SUPPORTED_ENCRYPTION_ALGORITHMS"); v != "" {
		algorithms := strings.Split(v, ",")
		for i := range algorithms {
			algorithms[i] = strings.TrimSpace(algorithms[i])
		}
		cfg.SupportedEncAlgorithms = algorithms
	}
	if v := os.Getenv("MOSIP_ESIGNET_SECURITY_ISSUER_URL"); v != "" {
		cfg.SecurityConfig.IssuerURL = v
	}
	if v := os.Getenv("MOSIP_ESIGNET_SECURITY_JWKS_URL"); v != "" {
		cfg.SecurityConfig.JwksURL = v
	}
	if v := os.Getenv("MOSIP_ESIGNET_CLIENT_CACHE_TTL_SECS"); v != "" {
		secs, err := strconv.ParseInt(v, 10, 64)
		if err != nil {
			return fmt.Errorf("invalid MOSIP_ESIGNET_CLIENT_CACHE_TTL_SECS: %w", err)
		}
		if secs > 0 {
			cfg.ClientCacheTTLSecs = secs
		}
	}
	if v := os.Getenv("MOSIP_ESIGNET_RESOURCE_SERVERS_JSON"); v != "" {
		var resourceServers []ResourceServerConfig
		if err := json.Unmarshal([]byte(v), &resourceServers); err != nil {
			return fmt.Errorf("invalid MOSIP_ESIGNET_RESOURCE_SERVERS_JSON: %w", err)
		}
		if err := validateResourceServers(resourceServers); err != nil {
			return fmt.Errorf("invalid MOSIP_ESIGNET_RESOURCE_SERVERS_JSON: %w", err)
		}
		cfg.ResourceServers = resourceServers
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

// envOrConfigOrDefault resolves a string setting in the documented precedence
// order: env var, then the value already parsed from deployment.yaml
// (fromYAML), then the compiled-in fallback.
func envOrConfigOrDefault(key, fromYAML, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	if fromYAML != "" {
		return fromYAML
	}
	return fallback
}

// configSource identifies which tier of the env > yaml > default precedence
// chain supplied a resolved setting's effective value. Reported alongside the
// value at startup so an operator can tell "my env var took effect" apart
// from "my env var was ignored and yaml won" without reading source.
type configSource string

const (
	sourceEnv     configSource = "env"
	sourceYAML    configSource = "yaml"
	sourceDefault configSource = "default"
)

// warnIgnoredEnvVar reports an env var that was set but could not be used,
// naming the rejected value alongside the value and source actually taking
// effect — so "why didn't my env var change anything" is answered by the log
// rather than by reading source. Mirrors envPositiveInt's existing warning.
func warnIgnoredEnvVar(key, raw string, using int, src configSource) {
	applog.GetLogger().Warn(context.Background(),
		"ignoring invalid config env var; falling back to lower-precedence value",
		applog.String("key", key),
		applog.String("value", raw),
		applog.Int("usingValue", using),
		applog.String("usingSource", string(src)))
}

// resolvedSetting pairs a setting's effective value with the tier that
// supplied it, for reporting via logResolvedSettings.
type resolvedSetting struct {
	name  string
	value int
	src   configSource
}

// logResolvedSettings emits one INFO line reporting each setting's effective
// value alongside a "<name>Source" field naming the tier it came from. The
// suffix convention lives here rather than at each call site so a dozen
// settings across db.go and redis.go can't drift apart.
func logResolvedSettings(msg string, settings ...resolvedSetting) {
	fields := make([]applog.Field, 0, len(settings)*2)
	for _, s := range settings {
		fields = append(fields,
			applog.Int(s.name, s.value),
			applog.String(s.name+"Source", string(s.src)))
	}
	applog.GetLogger().Info(context.Background(), msg, fields...)
}

// configOrDefaultSourced is the shared yaml/fallback tail for the resolvers
// below: fromYAML wins if positive, otherwise fallback. A yaml value of 0 (or
// an omitted field, which decodes to the same zero value) can't be
// distinguished from "not configured", so no variant can honor an explicit
// yaml 0 the way they can an explicit env var 0.
func configOrDefaultSourced(fromYAML, fallback int) (int, configSource) {
	if fromYAML > 0 {
		return fromYAML, sourceYAML
	}
	return fallback, sourceDefault
}

// configOrDefault is configOrDefaultSourced without the source.
func configOrDefault(fromYAML, fallback int) int {
	v, _ := configOrDefaultSourced(fromYAML, fallback)
	return v
}

// envIntOrConfigOrDefaultSourced is envOrConfigOrDefault for int settings,
// additionally reporting which tier supplied the value. A zero/negative value
// at any tier is treated as "not set" and falls through to the next tier —
// this deliberately can't express an env-var-only "0 = no limit" opt-out; use
// envIntOrConfigOrDefaultAllowEnvZeroSourced for those.
//
// An env var that is set but unusable (unparseable, or non-positive where
// positive is required) is warned about rather than silently discarded: that
// silence is the "dead config" trap this variant exists to close.
func envIntOrConfigOrDefaultSourced(key string, fromYAML, fallback int) (int, configSource) {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return configOrDefaultSourced(fromYAML, fallback)
	}
	if n, err := strconv.Atoi(raw); err == nil && n > 0 {
		return n, sourceEnv
	}
	v, src := configOrDefaultSourced(fromYAML, fallback)
	warnIgnoredEnvVar(key, raw, v, src)
	return v, src
}

// envIntOrConfigOrDefault is envIntOrConfigOrDefaultSourced without the source.
func envIntOrConfigOrDefault(key string, fromYAML, fallback int) int {
	v, _ := envIntOrConfigOrDefaultSourced(key, fromYAML, fallback)
	return v
}

// envIntOrConfigOrDefaultAllowEnvZeroSourced is like
// envIntOrConfigOrDefaultSourced, but an explicitly-set env var of "0" is
// honored as-is rather than treated as "not set". Used for settings where 0
// is a documented env-var-only opt-out (e.g. "0 = no limit" for a connection
// lifetime). Negative values remain invalid and are warned about.
func envIntOrConfigOrDefaultAllowEnvZeroSourced(key string, fromYAML, fallback int) (int, configSource) {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return configOrDefaultSourced(fromYAML, fallback)
	}
	if n, err := strconv.Atoi(raw); err == nil && n >= 0 {
		return n, sourceEnv
	}
	v, src := configOrDefaultSourced(fromYAML, fallback)
	warnIgnoredEnvVar(key, raw, v, src)
	return v, src
}

// envIntOrConfigOrDefaultAllowEnvZero is
// envIntOrConfigOrDefaultAllowEnvZeroSourced without the source.
func envIntOrConfigOrDefaultAllowEnvZero(key string, fromYAML, fallback int) int {
	v, _ := envIntOrConfigOrDefaultAllowEnvZeroSourced(key, fromYAML, fallback)
	return v
}

func envBool(key string) bool {
	switch strings.ToLower(strings.TrimSpace(os.Getenv(key))) {
	case "1", "true", "yes", "on":
		return true
	default:
		return false
	}
}

// envBoolOrConfig returns the env var's boolean value if it is explicitly
// set to a recognized form (case-insensitive 1/true/yes/on, or
// 0/false/no/off), else fromYAML. An unrecognized non-empty value is logged
// and ignored rather than silently treated as false — for a setting like
// REDIS_TLS_ENABLED, silently converting a typo (e.g. "garbage") to false
// would disable TLS even though YAML has it enabled.
func envBoolOrConfig(key string, fromYAML bool) bool {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return fromYAML
	}
	switch strings.ToLower(raw) {
	case "1", "true", "yes", "on":
		return true
	case "0", "false", "no", "off":
		return false
	default:
		applog.GetLogger().Warn(context.Background(),
			"ignoring unrecognized boolean env var; keeping configured value",
			applog.String("key", key), applog.String("value", raw))
		return fromYAML
	}
}
