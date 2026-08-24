/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package config

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	engineconfig "github.com/thunder-id/thunderid/pkg/thunderidengine/config"
)

type AppConfigTestSuite struct {
	suite.Suite
}

func TestAppConfigTestSuite(t *testing.T) {
	suite.Run(t, new(AppConfigTestSuite))
}

// SetupTest clears the HTTP tuning env vars applyDefaults reads via
// applyHTTPClientEnvOverrides/applyInboundServerEnvOverrides before every
// test, so a name exported by the CI environment or a developer shell can't
// make tests that exercise applyDefaults's yaml/compiled-default fallback
// fail for reasons unrelated to the change under test. t.Setenv restores the
// prior value after each test, and tests that need a specific value still
// set it themselves after this runs.
func (ts *AppConfigTestSuite) SetupTest() {
	t := ts.T()
	for _, prefix := range []string{"OUTBOUND_IDSYSTEM_HTTP_CLIENT", "OUTBOUND_HTTP_CLIENT"} {
		for _, suffix := range []string{
			"_TIMEOUT_SECS", "_DIAL_TIMEOUT_SECS", "_DIAL_KEEP_ALIVE_SECS",
			"_TLS_HANDSHAKE_TIMEOUT_SECS", "_RESPONSE_HEADER_TIMEOUT_SECS",
			"_IDLE_CONN_TIMEOUT_SECS", "_MAX_CONNS_PER_HOST", "_MAX_IDLE_CONNS",
			"_MAX_IDLE_CONNS_PER_HOST",
		} {
			t.Setenv(prefix+suffix, "")
		}
	}
	for _, key := range []string{
		"INBOUND_HTTP_SERVER_READ_HEADER_TIMEOUT_SECS",
		"INBOUND_HTTP_SERVER_READ_TIMEOUT_SECS",
		"INBOUND_HTTP_SERVER_WRITE_TIMEOUT_SECS",
		"INBOUND_HTTP_SERVER_IDLE_TIMEOUT_SECS",
		"NAMESPACE",
		"PORT",
		"MOSIP_ESIGNET_HOST",
		"DATA_DIR",
		"MOSIP_ESIGNET_CACHE_TYPE",
		"MOSIP_ESIGNET_AUTHN_PROVIDER",
		"MOSIP_ESIGNET_LAYOUT_ID",
		"MOSIP_ESIGNET_THEME_ID",
		"MOSIP_ESIGNET_AUTH_FLOW_ID",
		"MOSIP_ESIGNET_CORS_ALLOWED_ORIGIN_REGEX",
		"MOSIP_ESIGNET_BASE_URL",
		"MOSIP_ESIGNET_SIGNING_KEY_REF_ID",
		"MOSIP_ESIGNET_JWT_VALIDITY_PERIOD",
		"MOSIP_ESIGNET_JWT_LEEWAY",
		"MOSIP_ESIGNET_DPOP_LEEWAY",
	} {
		t.Setenv(key, "")
	}
}

func (ts *AppConfigTestSuite) chdirTemp() string {
	t := ts.T()
	t.Helper()
	dir := t.TempDir()
	orig, err := os.Getwd()
	ts.Require().NoError(err)
	ts.Require().NoError(os.Chdir(dir))
	t.Cleanup(func() { ts.Require().NoError(os.Chdir(orig)) })
	return dir
}

func (ts *AppConfigTestSuite) writeDeploymentYAML(dir, contents string) {
	ts.T().Helper()
	ts.Require().NoError(os.MkdirAll(filepath.Join(dir, "data"), 0o755))
	ts.Require().NoError(os.WriteFile(filepath.Join(dir, "data", "deployment.yaml"), []byte(contents), 0o644))
}

const minimalDeploymentYAML = `
identifier: esignet
port: 8088
issuer: http://127.0.0.1:8088
`

func (ts *AppConfigTestSuite) TestLoadAppConfigMissingFile() {
	ts.chdirTemp()

	_, err := LoadAppConfig()
	ts.Require().Error(err)
}

func (ts *AppConfigTestSuite) TestLoadAppConfigMalformedYAML() {
	dir := ts.chdirTemp()
	ts.writeDeploymentYAML(dir, "not: [valid: yaml")

	_, err := LoadAppConfig()
	ts.Require().Error(err)
}

func (ts *AppConfigTestSuite) TestLoadAppConfigUnknownFieldRejected() {
	dir := ts.chdirTemp()
	ts.writeDeploymentYAML(dir, minimalDeploymentYAML+"\nnot_a_real_field: true\n")

	_, err := LoadAppConfig()
	ts.Require().Error(err)
}

func (ts *AppConfigTestSuite) TestLoadAppConfigExpandsEnvAndAppliesDefaults() {
	t := ts.T()
	dir := ts.chdirTemp()
	ts.writeDeploymentYAML(dir, `
identifier: "${NAMESPACE}"
port: 8088
issuer: "${MOSIP_ESIGNET_HOST:-http://localhost:8088}"
oauth:
  authorization_code:
    validity_period: 120
`)
	t.Setenv("NAMESPACE", "my-namespace")

	cfg, err := LoadAppConfig()
	ts.Require().NoError(err)
	ts.Require().Equal("my-namespace", cfg.Identifier)
	ts.Require().EqualValues(120, cfg.OAuth.AuthorizationCode.ValidityPeriod)
}

func (ts *AppConfigTestSuite) TestLoadAppConfigRejectsMultipleDefaultResourceServers() {
	dir := ts.chdirTemp()
	ts.writeDeploymentYAML(dir, minimalDeploymentYAML+`
resource_servers:
  - id: rs-1
    identifier: "https://rs-1.example.com"
    default: true
  - id: rs-2
    identifier: "https://rs-2.example.com"
    default: true
`)

	_, err := LoadAppConfig()
	ts.Require().Error(err)
	ts.Require().ErrorContains(err, "multiple resource servers marked default")
}

func (ts *AppConfigTestSuite) TestLoadAppConfigAcceptsSingleDefaultResourceServer() {
	dir := ts.chdirTemp()
	ts.writeDeploymentYAML(dir, minimalDeploymentYAML+`
resource_servers:
  - id: rs-1
    identifier: "https://rs-1.example.com"
  - id: rs-2
    identifier: "https://rs-2.example.com"
    default: true
`)

	cfg, err := LoadAppConfig()
	ts.Require().NoError(err)
	ts.Require().Len(cfg.ResourceServers, 2)
}

func TestResourceServerByIdentifier(t *testing.T) {
	withDefault := []ResourceServerConfig{
		{ID: "rs-default", Identifier: "https://default.example.com", Default: true},
		{ID: "rs-other", Identifier: "https://other.example.com"},
	}
	noDefault := []ResourceServerConfig{
		{ID: "rs-1", Identifier: "https://rs-1.example.com"},
		{ID: "rs-2", Identifier: "https://rs-2.example.com"},
	}

	cases := []struct {
		name       string
		servers    []ResourceServerConfig
		identifier string
		wantID     string
	}{
		{name: "no resource servers configured, empty identifier", servers: nil, identifier: "", wantID: ""},
		{name: "no resource servers configured, non-empty identifier", servers: nil, identifier: "https://rs-1.example.com", wantID: ""},
		{name: "configured but none marked default, empty identifier", servers: noDefault, identifier: "", wantID: ""},
		{name: "configured but none marked default, known identifier", servers: noDefault, identifier: "https://rs-2.example.com", wantID: "rs-2"},
		{name: "default configured, empty identifier resolves default", servers: withDefault, identifier: "", wantID: "rs-default"},
		{name: "default configured, unknown identifier", servers: withDefault, identifier: "https://unknown.example.com", wantID: ""},
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			cfg := &AppConfig{ResourceServers: c.servers}
			rs := cfg.ResourceServerByIdentifier(c.identifier)
			if c.wantID == "" {
				require.Nil(t, rs)
				return
			}
			require.NotNil(t, rs)
			require.Equal(t, c.wantID, rs.ID)
		})
	}
}

func TestResourceServerByID(t *testing.T) {
	servers := []ResourceServerConfig{
		{ID: "rs-1", Identifier: "https://rs-1.example.com"},
		{ID: "rs-2", Identifier: "https://rs-2.example.com"},
	}

	cases := []struct {
		name    string
		servers []ResourceServerConfig
		id      string
		wantID  string
	}{
		{name: "no resource servers configured", servers: nil, id: "rs-1", wantID: ""},
		{name: "known id", servers: servers, id: "rs-2", wantID: "rs-2"},
		{name: "unknown id", servers: servers, id: "rs-unknown", wantID: ""},
		{name: "empty id with none matching", servers: servers, id: "", wantID: ""},
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			cfg := &AppConfig{ResourceServers: c.servers}
			rs := cfg.ResourceServerByID(c.id)
			if c.wantID == "" {
				require.Nil(t, rs)
				return
			}
			require.NotNil(t, rs)
			require.Equal(t, c.wantID, rs.ID)
		})
	}
}

func TestIsAuthorizationScope(t *testing.T) {
	servers := []ResourceServerConfig{
		{ID: "rs-1", Identifier: "https://rs-1.example.com", Scopes: map[string]string{"payment:pay": "Make payments"}},
		{ID: "rs-2", Identifier: "https://rs-2.example.com", Scopes: map[string]string{"profile:read": "Read profile"}},
	}

	cases := []struct {
		name    string
		servers []ResourceServerConfig
		scope   string
		want    bool
	}{
		{name: "no resource servers configured", servers: nil, scope: "payment:pay", want: false},
		{name: "scope defined on first resource server", servers: servers, scope: "payment:pay", want: true},
		{name: "scope defined on second resource server", servers: servers, scope: "profile:read", want: true},
		{name: "scope not defined on any resource server", servers: servers, scope: "unknown:scope", want: false},
		{name: "empty scope name not defined", servers: servers, scope: "", want: false},
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			cfg := &AppConfig{ResourceServers: c.servers}
			require.Equal(t, c.want, cfg.IsAuthorizationScope(c.scope))
		})
	}
}

func TestValidateResourceServers(t *testing.T) {
	cases := []struct {
		name    string
		servers []ResourceServerConfig
		wantErr string
	}{
		{name: "empty is valid"},
		{
			name: "single default is valid",
			servers: []ResourceServerConfig{
				{ID: "rs-1", Identifier: "https://rs-1.example.com", Default: true},
			},
		},
		{
			name: "no default is valid",
			servers: []ResourceServerConfig{
				{ID: "rs-1", Identifier: "https://rs-1.example.com"},
			},
		},
		{
			name: "duplicate default rejected",
			servers: []ResourceServerConfig{
				{ID: "rs-1", Identifier: "https://rs-1.example.com", Default: true},
				{ID: "rs-2", Identifier: "https://rs-2.example.com", Default: true},
			},
			wantErr: "multiple resource servers marked default",
		},
		{
			name: "duplicate id rejected",
			servers: []ResourceServerConfig{
				{ID: "rs-1", Identifier: "https://rs-1.example.com"},
				{ID: "rs-1", Identifier: "https://rs-2.example.com"},
			},
			wantErr: "duplicate resource server id",
		},
		{
			name: "duplicate identifier rejected",
			servers: []ResourceServerConfig{
				{ID: "rs-1", Identifier: "https://rs.example.com"},
				{ID: "rs-2", Identifier: "https://rs.example.com"},
			},
			wantErr: "duplicate resource server identifier",
		},
		{
			name: "empty identifier rejected",
			servers: []ResourceServerConfig{
				{ID: "rs-1", Identifier: ""},
			},
			wantErr: "must be an absolute URI",
		},
		{
			name: "relative identifier rejected",
			servers: []ResourceServerConfig{
				{ID: "rs-1", Identifier: "/v1/esignet/vci/credential"},
			},
			wantErr: "must be an absolute URI",
		},
		{
			name: "scheme without host rejected",
			servers: []ResourceServerConfig{
				{ID: "rs-1", Identifier: "https:///no-host-path"},
			},
			wantErr: "must be an absolute URI",
		},
		{
			name: "unparsable identifier rejected",
			servers: []ResourceServerConfig{
				{ID: "rs-1", Identifier: "https://example.com/%zz"},
			},
			wantErr: "must be an absolute URI",
		},
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			err := validateResourceServers(c.servers)
			if c.wantErr == "" {
				require.NoError(t, err)
				return
			}
			require.ErrorContains(t, err, c.wantErr)
		})
	}
}

func (ts *AppConfigTestSuite) TestApplyDefaultsAuthorizationCodeValidityPeriod() {
	t := ts.T()
	t.Run("respects yaml-supplied value", func(_ *testing.T) {
		cfg := &AppConfig{OAuth: engineconfig.OAuthConfig{}}
		cfg.OAuth.AuthorizationCode.ValidityPeriod = 120

		applyDefaults(cfg)

		ts.Require().EqualValues(120, cfg.OAuth.AuthorizationCode.ValidityPeriod)
	})

	t.Run("defaults to 60 when unset", func(_ *testing.T) {
		cfg := &AppConfig{}

		applyDefaults(cfg)

		ts.Require().EqualValues(60, cfg.OAuth.AuthorizationCode.ValidityPeriod)
	})
}

func (ts *AppConfigTestSuite) TestApplyDefaultsPARExpiresIn() {
	t := ts.T()
	t.Run("respects yaml-supplied value", func(_ *testing.T) {
		cfg := &AppConfig{}
		cfg.OAuth.PAR.ExpiresIn = 900

		applyDefaults(cfg)

		ts.Require().EqualValues(900, cfg.OAuth.PAR.ExpiresIn)
	})

	t.Run("defaults to 3600 when unset", func(_ *testing.T) {
		cfg := &AppConfig{}

		applyDefaults(cfg)

		ts.Require().EqualValues(3600, cfg.OAuth.PAR.ExpiresIn)
	})
}

func (ts *AppConfigTestSuite) TestApplyDefaultsJWTValidityAndLeeway() {
	t := ts.T()
	t.Run("defaults when unset", func(_ *testing.T) {
		cfg := &AppConfig{}

		applyDefaults(cfg)

		ts.Require().EqualValues(120, cfg.JWT.ValidityPeriod)
		ts.Require().EqualValues(10, cfg.JWT.Leeway)
		ts.Require().EqualValues(10, cfg.OAuth.DPoP.Leeway)
	})

	t.Run("respects env override", func(t *testing.T) {
		cfg := &AppConfig{}
		t.Setenv("MOSIP_ESIGNET_JWT_VALIDITY_PERIOD", "7200")
		t.Setenv("MOSIP_ESIGNET_JWT_LEEWAY", "30")
		t.Setenv("MOSIP_ESIGNET_DPOP_LEEWAY", "20")

		applyDefaults(cfg)

		ts.Require().EqualValues(7200, cfg.JWT.ValidityPeriod)
		ts.Require().EqualValues(30, cfg.JWT.Leeway)
		ts.Require().EqualValues(20, cfg.OAuth.DPoP.Leeway)
	})
}

func (ts *AppConfigTestSuite) TestApplyDefaultsYAMLValuesUsedWhenEnvUnset() {
	cfg := &AppConfig{
		Identifier: "yaml-namespace",
		Issuer:     "https://yaml-issuer.example",
		Provider:   "yaml-provider",
		LayoutID:   "yaml-layout",
		ThemeID:    "yaml-theme",
		AuthFlowID: "yaml-flow",
	}

	applyDefaults(cfg)

	ts.Require().Equal("yaml-namespace", cfg.Identifier)
	ts.Require().Equal("https://yaml-issuer.example", cfg.Issuer)
	ts.Require().Equal("yaml-provider", cfg.Provider)
	ts.Require().Equal("yaml-layout", cfg.LayoutID)
	ts.Require().Equal("yaml-theme", cfg.ThemeID)
	ts.Require().Equal("yaml-flow", cfg.AuthFlowID)
}

func (ts *AppConfigTestSuite) TestApplyDefaultsEnvTakesPrecedenceOverYAML() {
	t := ts.T()
	t.Setenv("NAMESPACE", "env-namespace")

	cfg := &AppConfig{Identifier: "yaml-namespace"}
	applyDefaults(cfg)

	ts.Require().Equal("env-namespace", cfg.Identifier)
}

func (ts *AppConfigTestSuite) TestApplyDefaultsGateClientRespectsYAML() {
	cfg := &AppConfig{}
	cfg.GateClient.Scheme = "https"
	cfg.GateClient.Hostname = "yaml-gate.example"
	cfg.GateClient.Port = 4000
	cfg.GateClient.LoginPath = "/yaml-signin"
	cfg.GateClient.ErrorPath = "/yaml-error"

	applyDefaults(cfg)

	ts.Require().Equal("https", cfg.GateClient.Scheme)
	ts.Require().Equal("yaml-gate.example", cfg.GateClient.Hostname)
	ts.Require().Equal(4000, cfg.GateClient.Port)
	ts.Require().Equal("/yaml-signin", cfg.GateClient.LoginPath)
	ts.Require().Equal("/yaml-error", cfg.GateClient.ErrorPath)
}

func (ts *AppConfigTestSuite) TestApplyDefaultsGateClientFallsBackWhenUnset() {
	cfg := &AppConfig{}

	applyDefaults(cfg)

	ts.Require().Equal(defaultGateScheme, cfg.GateClient.Scheme)
	ts.Require().Equal(defaultGateHostname, cfg.GateClient.Hostname)
	ts.Require().Equal(defaultGatePort, cfg.GateClient.Port)
	ts.Require().Equal(defaultGateLoginPath, cfg.GateClient.LoginPath)
	ts.Require().Equal(defaultGateErrorPath, cfg.GateClient.ErrorPath)
}

func (ts *AppConfigTestSuite) TestApplyDefaultsJWTPreferredKeyIDRespectsYAML() {
	cfg := &AppConfig{}
	cfg.JWT.PreferredKeyID = "yaml-key-ref"

	applyDefaults(cfg)

	ts.Require().Equal("yaml-key-ref", cfg.JWT.PreferredKeyID)
}

func (ts *AppConfigTestSuite) TestApplyDefaultsCoreFields() {
	t := ts.T()
	t.Setenv("PORT", "9999")
	t.Setenv("MOSIP_ESIGNET_HOST", "https://issuer.example")
	t.Setenv("MOSIP_ESIGNET_AUTHN_PROVIDER", "mosip")

	cfg := &AppConfig{}
	applyDefaults(cfg)

	ts.Require().Equal(9999, cfg.Port)
	ts.Require().Equal("https://issuer.example", cfg.Issuer)
	ts.Require().Equal("mosip", cfg.Provider)
	ts.Require().Equal("https://issuer.example", cfg.JWT.Issuer)
	ts.Require().Equal("https://issuer.example", cfg.JWT.Audience)
	ts.Require().False(cfg.Server.HTTPOnly, "https:// public URL should not be flagged http-only")
}

func (ts *AppConfigTestSuite) TestApplyDefaultsSupportedAlgorithms() {
	cfg := &AppConfig{}
	applyDefaults(cfg)

	ts.Require().Equal([]string{"PS256", "ES256", "ES256K", "EdDSA"}, cfg.SupportedSigningAlgorithms)
	ts.Require().Equal([]string{"RSA-OAEP", "RSA-OAEP-256"}, cfg.SupportedEncAlgorithms)
}

func (ts *AppConfigTestSuite) TestApplyDefaultsAllowedOriginRegex() {
	t := ts.T()

	t.Run("env var sets regex", func(t *testing.T) {
		t.Setenv("MOSIP_ESIGNET_CORS_ALLOWED_ORIGIN_REGEX", `^https://.*\.example\.com$`)
		cfg := &AppConfig{}
		applyDefaults(cfg)
		require.Equal(t, `^https://.*\.example\.com$`, cfg.AllowedOriginRegex)
	})

	t.Run("yaml value used when env unset", func(t *testing.T) {
		t.Setenv("MOSIP_ESIGNET_CORS_ALLOWED_ORIGIN_REGEX", "")
		cfg := &AppConfig{AllowedOriginRegex: `^https://app\.example\.com$`}
		applyDefaults(cfg)
		require.Equal(t, `^https://app\.example\.com$`, cfg.AllowedOriginRegex)
	})

	t.Run("empty when neither env nor yaml set", func(t *testing.T) {
		t.Setenv("MOSIP_ESIGNET_CORS_ALLOWED_ORIGIN_REGEX", "")
		cfg := &AppConfig{}
		applyDefaults(cfg)
		require.Empty(t, cfg.AllowedOriginRegex)
	})

	t.Run("env var overrides yaml value", func(t *testing.T) {
		t.Setenv("MOSIP_ESIGNET_CORS_ALLOWED_ORIGIN_REGEX", `^https://env\.example\.com$`)
		cfg := &AppConfig{AllowedOriginRegex: `^https://yaml\.example\.com$`}
		applyDefaults(cfg)
		require.Equal(t, `^https://env\.example\.com$`, cfg.AllowedOriginRegex)
	})
}

func (ts *AppConfigTestSuite) TestApplyDefaultsCacheType() {
	t := ts.T()
	t.Run("defaults to inmemory", func(_ *testing.T) {
		cfg := &AppConfig{}

		applyDefaults(cfg)

		ts.Require().Equal("inmemory", cfg.Flow.Store)
	})

	t.Run("non-redis cache type skips loading redis config", func(t *testing.T) {
		t.Setenv("MOSIP_ESIGNET_CACHE_TYPE", "memory")
		cfg := &AppConfig{}

		applyDefaults(cfg)

		ts.Require().Equal("inmemory", cfg.Flow.Store)
		ts.Require().Zero(cfg.Redis, "redis config should not be loaded for a non-redis cache type")
	})
}

func (ts *AppConfigTestSuite) TestApplyDefaultsHTTPOnlyDetection() {
	t := ts.T()
	t.Setenv("MOSIP_ESIGNET_HOST", "http://127.0.0.1:8088")

	cfg := &AppConfig{}
	applyDefaults(cfg)

	ts.Require().True(cfg.Server.HTTPOnly)
}

func (ts *AppConfigTestSuite) TestApplyDefaultsTTLGuards() {

	cfg := &AppConfig{
		ClientCacheTTLSecs: 42,
		DesignCacheTTLSecs: -1,
		FlowCacheTTLSecs:   0,
	}
	applyDefaults(cfg)

	ts.Require().EqualValues(42, cfg.ClientCacheTTLSecs, "positive yaml value preserved")
	ts.Require().EqualValues(defaultDesignCacheTTLSecs, cfg.DesignCacheTTLSecs)
	ts.Require().EqualValues(defaultFlowCacheTTLSecs, cfg.FlowCacheTTLSecs)
}

func (ts *AppConfigTestSuite) TestApplyHTTPClientDefaults() {
	cfg := &HTTPClientConfig{
		TimeoutSecs:     15,
		MaxConnsPerHost: -1,
	}
	applyHTTPClientDefaults(cfg)

	ts.Require().EqualValues(15, cfg.TimeoutSecs, "positive value preserved")
	ts.Require().EqualValues(defaultHTTPDialTimeoutSecs, cfg.DialTimeoutSecs)
	ts.Require().EqualValues(defaultHTTPDialKeepAliveSecs, cfg.DialKeepAliveSecs)
	ts.Require().EqualValues(defaultHTTPTLSHandshakeTimeoutSecs, cfg.TLSHandshakeTimeoutSecs)
	ts.Require().EqualValues(defaultHTTPResponseHeaderTimeoutSecs, cfg.ResponseHeaderTimeoutSecs)
	ts.Require().EqualValues(defaultHTTPIdleConnTimeoutSecs, cfg.IdleConnTimeoutSecs)
	ts.Require().EqualValues(defaultHTTPMaxConnsPerHost, cfg.MaxConnsPerHost)
	ts.Require().EqualValues(defaultHTTPMaxIdleConns, cfg.MaxIdleConns)
	ts.Require().EqualValues(defaultHTTPMaxIdleConnsPerHost, cfg.MaxIdleConnsPerHost)
}

func (ts *AppConfigTestSuite) TestApplyDefaultsCoversAllOutboundHTTPClientBlocks() {
	cfg := &AppConfig{
		OutboundIDSystemHTTPClient: HTTPClientConfig{TimeoutSecs: 11},
		OutboundHTTPClient:         HTTPClientConfig{TimeoutSecs: 12},
	}
	applyDefaults(cfg)

	ts.Require().EqualValues(11, cfg.OutboundIDSystemHTTPClient.TimeoutSecs, "positive yaml value preserved")
	ts.Require().EqualValues(12, cfg.OutboundHTTPClient.TimeoutSecs, "positive yaml value preserved")

	for name, c := range map[string]HTTPClientConfig{
		"idsystem": cfg.OutboundIDSystemHTTPClient,
		"outbound": cfg.OutboundHTTPClient,
	} {
		ts.Require().EqualValues(defaultHTTPMaxConnsPerHost, c.MaxConnsPerHost, name)
		ts.Require().EqualValues(defaultHTTPMaxIdleConns, c.MaxIdleConns, name)
		ts.Require().EqualValues(defaultHTTPMaxIdleConnsPerHost, c.MaxIdleConnsPerHost, name)
	}
}

func (ts *AppConfigTestSuite) TestApplyDefaultsInboundHTTPServer() {
	cfg := &AppConfig{
		InboundHTTPServer: InboundHTTPServerConfig{
			ReadTimeoutSecs: 15,
		},
	}
	applyDefaults(cfg)

	ts.Require().EqualValues(15, cfg.InboundHTTPServer.ReadTimeoutSecs, "positive yaml value preserved")
	ts.Require().EqualValues(defaultInboundServerReadHeaderTimeoutSecs, cfg.InboundHTTPServer.ReadHeaderTimeoutSecs)
	ts.Require().EqualValues(defaultInboundServerWriteTimeoutSecs, cfg.InboundHTTPServer.WriteTimeoutSecs)
	ts.Require().EqualValues(defaultInboundServerIdleTimeoutSecs, cfg.InboundHTTPServer.IdleTimeoutSecs)
}

func (ts *AppConfigTestSuite) TestApplyHTTPClientEnvOverrides() {
	t := ts.T()
	t.Setenv("OUTBOUND_IDSYSTEM_HTTP_CLIENT_TIMEOUT_SECS", "45")
	t.Setenv("OUTBOUND_IDSYSTEM_HTTP_CLIENT_MAX_IDLE_CONNS", "999")

	cfg := &HTTPClientConfig{TimeoutSecs: 11, MaxConnsPerHost: 22}
	applyHTTPClientEnvOverrides("OUTBOUND_IDSYSTEM_HTTP_CLIENT", cfg)

	ts.Require().EqualValues(45, cfg.TimeoutSecs, "env var overrides yaml value")
	ts.Require().EqualValues(999, cfg.MaxIdleConns, "env var sets unset field")
	ts.Require().EqualValues(22, cfg.MaxConnsPerHost, "field without a matching env var is left untouched")
}

func (ts *AppConfigTestSuite) TestApplyInboundServerEnvOverrides() {
	t := ts.T()
	t.Setenv("INBOUND_HTTP_SERVER_WRITE_TIMEOUT_SECS", "90")

	cfg := &InboundHTTPServerConfig{ReadTimeoutSecs: 15}
	applyInboundServerEnvOverrides(cfg)

	ts.Require().EqualValues(90, cfg.WriteTimeoutSecs, "env var overrides default")
	ts.Require().EqualValues(15, cfg.ReadTimeoutSecs, "field without a matching env var is left untouched")
}

func (ts *AppConfigTestSuite) TestApplyDefaultsHTTPClientEnvOverrideTakesPrecedenceOverYAML() {
	t := ts.T()
	t.Setenv("OUTBOUND_HTTP_CLIENT_TIMEOUT_SECS", "77")

	cfg := &AppConfig{OutboundHTTPClient: HTTPClientConfig{TimeoutSecs: 12}}
	applyDefaults(cfg)

	ts.Require().EqualValues(77, cfg.OutboundHTTPClient.TimeoutSecs, "env var takes precedence over yaml-set value")
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesGateClient() {
	t := ts.T()
	cfg := &AppConfig{}
	t.Setenv("MOSIP_ESIGNET_OIDC_UI_SCHEME", "https")
	t.Setenv("MOSIP_ESIGNET_OIDC_UI_HOSTNAME", "gate.example")
	t.Setenv("MOSIP_ESIGNET_OIDC_UI_PORT", "4443")
	t.Setenv("MOSIP_ESIGNET_OIDC_UI_LOGIN_PATH", "/login")
	t.Setenv("MOSIP_ESIGNET_OIDC_UI_ERROR_PATH", "/oops")

	ts.Require().NoError(ApplyEnvOverrides(cfg))

	ts.Require().Equal("https", cfg.GateClient.Scheme)
	ts.Require().Equal("gate.example", cfg.GateClient.Hostname)
	ts.Require().Equal(4443, cfg.GateClient.Port)
	ts.Require().Equal("/login", cfg.GateClient.LoginPath)
	ts.Require().Equal("/oops", cfg.GateClient.ErrorPath)
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesInvalidPort() {
	cfg := &AppConfig{}
	ts.T().Setenv("MOSIP_ESIGNET_OIDC_UI_PORT", "not-a-number")

	ts.Require().Error(ApplyEnvOverrides(cfg))
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesPortOutOfRange() {
	cfg := &AppConfig{}
	ts.T().Setenv("MOSIP_ESIGNET_OIDC_UI_PORT", "70000")

	ts.Require().Error(ApplyEnvOverrides(cfg))
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesOAuthLifetimes() {
	t := ts.T()
	cfg := &AppConfig{}
	t.Setenv("MOSIP_ESIGNET_OAUTH_AUTH_CODE_LIFETIME_SECONDS", "60")
	t.Setenv("MOSIP_ESIGNET_OAUTH_PAR_EXPIRY_SECONDS", "120")
	t.Setenv("MOSIP_ESIGNET_OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS", "7200")

	ts.Require().NoError(ApplyEnvOverrides(cfg))

	ts.Require().EqualValues(60, cfg.OAuth.AuthorizationCode.ValidityPeriod)
	ts.Require().EqualValues(120, cfg.OAuth.PAR.ExpiresIn)
	ts.Require().EqualValues(7200, cfg.JWT.ValidityPeriod)
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesNonPositiveLifetimesIgnored() {
	cfg := &AppConfig{}
	cfg.OAuth.AuthorizationCode.ValidityPeriod = 120
	ts.T().Setenv("MOSIP_ESIGNET_OAUTH_AUTH_CODE_LIFETIME_SECONDS", "0")

	ts.Require().NoError(ApplyEnvOverrides(cfg))

	ts.Require().EqualValues(120, cfg.OAuth.AuthorizationCode.ValidityPeriod, "0 or negative override should be ignored")
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesInvalidOAuthLifetime() {
	cfg := &AppConfig{}
	ts.T().Setenv("MOSIP_ESIGNET_OAUTH_AUTH_CODE_LIFETIME_SECONDS", "abc")

	ts.Require().Error(ApplyEnvOverrides(cfg))
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesInvalidPARLifetime() {
	cfg := &AppConfig{}
	ts.T().Setenv("MOSIP_ESIGNET_OAUTH_PAR_EXPIRY_SECONDS", "abc")

	ts.Require().Error(ApplyEnvOverrides(cfg))
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesInvalidAccessTokenLifetime() {
	cfg := &AppConfig{}
	ts.T().Setenv("MOSIP_ESIGNET_OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS", "abc")

	ts.Require().Error(ApplyEnvOverrides(cfg))
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesNoEnvSetLeavesDefaults() {
	t := ts.T()
	for _, envVar := range []string{
		"MOSIP_ESIGNET_OIDC_UI_SCHEME", "MOSIP_ESIGNET_OIDC_UI_HOSTNAME", "MOSIP_ESIGNET_OIDC_UI_PORT", "MOSIP_ESIGNET_OIDC_UI_LOGIN_PATH", "MOSIP_ESIGNET_OIDC_UI_ERROR_PATH",
		"MOSIP_ESIGNET_OAUTH_AUTH_CODE_LIFETIME_SECONDS", "MOSIP_ESIGNET_OAUTH_PAR_EXPIRY_SECONDS", "MOSIP_ESIGNET_OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS",
		"MOSIP_ESIGNET_OAUTH_SUPPORTED_SIGNING_ALGORITHMS", "MOSIP_ESIGNET_OAUTH_SUPPORTED_ENCRYPTION_ALGORITHMS",
	} {
		t.Setenv(envVar, "")
	}

	cfg := &AppConfig{}
	ts.Require().NoError(ApplyEnvOverrides(cfg))
	ts.Require().Zero(cfg.GateClient.Scheme)
	ts.Require().Nil(cfg.SupportedSigningAlgorithms)
	ts.Require().Nil(cfg.SupportedEncAlgorithms)
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesSupportedAlgorithms() {
	t := ts.T()
	cfg := &AppConfig{}
	t.Setenv("MOSIP_ESIGNET_OAUTH_SUPPORTED_SIGNING_ALGORITHMS", "PS256, ES256 ,EdDSA")
	t.Setenv("MOSIP_ESIGNET_OAUTH_SUPPORTED_ENCRYPTION_ALGORITHMS", "AES-GCM, RSA-OAEP-256")

	ts.Require().NoError(ApplyEnvOverrides(cfg))

	ts.Require().Equal([]string{"PS256", "ES256", "EdDSA"}, cfg.SupportedSigningAlgorithms)
	ts.Require().Equal([]string{"AES-GCM", "RSA-OAEP-256"}, cfg.SupportedEncAlgorithms)
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesResourceServersJSON() {
	t := ts.T()
	cfg := &AppConfig{ResourceServers: []ResourceServerConfig{
		{ID: "from-yaml", Identifier: "https://from-yaml.example.com"},
	}}
	t.Setenv("MOSIP_ESIGNET_RESOURCE_SERVERS_JSON",
		`[{"id":"rs-1","identifier":"https://rs-1.example.com","default":true,"scopes":{"read":"Read"}}]`)

	ts.Require().NoError(ApplyEnvOverrides(cfg))

	ts.Require().Equal([]ResourceServerConfig{
		{ID: "rs-1", Identifier: "https://rs-1.example.com", Default: true, Scopes: map[string]string{"read": "Read"}},
	}, cfg.ResourceServers)
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesResourceServersJSONInvalidJSON() {
	t := ts.T()
	cfg := &AppConfig{}
	t.Setenv("MOSIP_ESIGNET_RESOURCE_SERVERS_JSON", "not json")

	ts.Require().Error(ApplyEnvOverrides(cfg))
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesResourceServersJSONAmbiguousDefaultRejected() {
	t := ts.T()
	cfg := &AppConfig{}
	t.Setenv("MOSIP_ESIGNET_RESOURCE_SERVERS_JSON",
		`[{"id":"rs-1","identifier":"https://rs-1.example.com","default":true},`+
			`{"id":"rs-2","identifier":"https://rs-2.example.com","default":true}]`)

	err := ApplyEnvOverrides(cfg)
	ts.Require().Error(err)
	ts.Require().ErrorContains(err, "multiple resource servers marked default")
}

func (ts *AppConfigTestSuite) TestApplyEnvOverridesResourceServersJSONUnsetLeavesYAMLValue() {
	t := ts.T()
	cfg := &AppConfig{ResourceServers: []ResourceServerConfig{
		{ID: "from-yaml", Identifier: "https://from-yaml.example.com"},
	}}
	t.Setenv("MOSIP_ESIGNET_RESOURCE_SERVERS_JSON", "")

	ts.Require().NoError(ApplyEnvOverrides(cfg))
	ts.Require().Equal("from-yaml", cfg.ResourceServers[0].ID)
}

func (ts *AppConfigTestSuite) TestEnvOrDefault() {
	ts.T().Setenv("ESIGNET_TEST_ENV_OR_DEFAULT", "value")
	ts.Require().Equal("value", envOrDefault("ESIGNET_TEST_ENV_OR_DEFAULT", "fallback"))
	ts.Require().Equal("fallback", envOrDefault("ESIGNET_TEST_ENV_OR_DEFAULT_UNSET", "fallback"))
}

func (ts *AppConfigTestSuite) TestEnvIntOrDefault() {
	t := ts.T()
	t.Setenv("ESIGNET_TEST_ENV_INT", "123")
	ts.Require().Equal(123, envIntOrDefault("ESIGNET_TEST_ENV_INT", 999))
	ts.Require().Equal(999, envIntOrDefault("ESIGNET_TEST_ENV_INT_UNSET", 999))

	t.Setenv("ESIGNET_TEST_ENV_INT_BAD", "not-a-number")
	ts.Require().Equal(999, envIntOrDefault("ESIGNET_TEST_ENV_INT_BAD", 999))
}

func (ts *AppConfigTestSuite) TestEnvOrConfigOrDefault() {
	t := ts.T()
	t.Setenv("ESIGNET_TEST_ENV_OR_CONFIG", "from-env")
	ts.Require().Equal("from-env", envOrConfigOrDefault("ESIGNET_TEST_ENV_OR_CONFIG", "from-yaml", "fallback"), "env wins over yaml")

	t.Setenv("ESIGNET_TEST_ENV_OR_CONFIG_UNSET", "")
	ts.Require().Equal("from-yaml", envOrConfigOrDefault("ESIGNET_TEST_ENV_OR_CONFIG_UNSET", "from-yaml", "fallback"), "yaml wins when env unset")
	ts.Require().Equal("fallback", envOrConfigOrDefault("ESIGNET_TEST_ENV_OR_CONFIG_UNSET", "", "fallback"), "fallback used when both env and yaml unset")
}

func (ts *AppConfigTestSuite) TestEnvIntOrConfigOrDefault() {
	t := ts.T()
	t.Setenv("ESIGNET_TEST_ENV_INT_OR_CONFIG", "111")
	ts.Require().Equal(111, envIntOrConfigOrDefault("ESIGNET_TEST_ENV_INT_OR_CONFIG", 222, 333), "env wins over yaml")

	t.Setenv("ESIGNET_TEST_ENV_INT_OR_CONFIG_UNSET", "")
	ts.Require().Equal(222, envIntOrConfigOrDefault("ESIGNET_TEST_ENV_INT_OR_CONFIG_UNSET", 222, 333), "yaml wins when env unset")
	ts.Require().Equal(333, envIntOrConfigOrDefault("ESIGNET_TEST_ENV_INT_OR_CONFIG_UNSET", 0, 333), "fallback used when both env and yaml unset")
}

func (ts *AppConfigTestSuite) TestEnvIntOrConfigOrDefaultAllowEnvZero() {
	t := ts.T()
	t.Setenv("ESIGNET_TEST_ALLOW_ZERO", "0")
	ts.Require().Equal(0, envIntOrConfigOrDefaultAllowEnvZero("ESIGNET_TEST_ALLOW_ZERO", 222, 333), "explicit env zero honored")

	t.Setenv("ESIGNET_TEST_ALLOW_ZERO", "not-a-number")
	ts.Require().Equal(222, envIntOrConfigOrDefaultAllowEnvZero("ESIGNET_TEST_ALLOW_ZERO", 222, 333), "unparsable env falls through to yaml")

	t.Setenv("ESIGNET_TEST_ALLOW_ZERO", "")
	ts.Require().Equal(333, envIntOrConfigOrDefaultAllowEnvZero("ESIGNET_TEST_ALLOW_ZERO", 0, 333), "fallback when env and yaml unset")
}

func (ts *AppConfigTestSuite) TestEnvBool() {
	cases := map[string]bool{
		"1": true, "true": true, "TRUE": true, "yes": true, "on": true,
		"0": false, "false": false, "no": false, "": false, "garbage": false,
	}
	for raw, want := range cases {
		ts.T().Run(raw, func(t *testing.T) {
			t.Setenv("ESIGNET_TEST_ENV_BOOL", raw)
			require := ts.Require()
			t.Helper()
			require.Equal(want, envBool("ESIGNET_TEST_ENV_BOOL"))
		})
	}
}
