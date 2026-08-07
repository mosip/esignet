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

	"github.com/stretchr/testify/suite"
	engineconfig "github.com/thunder-id/thunderid/pkg/thunderidengine/config"
)

type AppConfigTestSuite struct {
	suite.Suite
}

func TestAppConfigTestSuite(t *testing.T) {
	suite.Run(t, new(AppConfigTestSuite))
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

func (ts *AppConfigTestSuite) TestApplyDefaultsAuthorizationCodeValidityPeriod() {
	t := ts.T()
	t.Run("respects yaml-supplied value", func(_ *testing.T) {
		cfg := &AppConfig{OAuth: engineconfig.OAuthConfig{}}
		cfg.OAuth.AuthorizationCode.ValidityPeriod = 120

		applyDefaults(cfg)

		ts.Require().EqualValues(120, cfg.OAuth.AuthorizationCode.ValidityPeriod)
	})

	t.Run("defaults to 3600 when unset", func(_ *testing.T) {
		cfg := &AppConfig{}

		applyDefaults(cfg)

		ts.Require().EqualValues(3600, cfg.OAuth.AuthorizationCode.ValidityPeriod)
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

func (ts *AppConfigTestSuite) TestApplyDefaultsCacheType() {
	t := ts.T()
	t.Run("defaults to redis and loads redis config", func(_ *testing.T) {
		cfg := &AppConfig{}

		applyDefaults(cfg)

		ts.Require().Equal("redis", cfg.Flow.Store)
		ts.Require().NotEmpty(cfg.Redis.Host, "redis config should be loaded when cache type is redis")
	})

	t.Run("non-redis cache type skips loading redis config", func(t *testing.T) {
		t.Setenv("MOSIP_ESIGNET_CACHE_TYPE", "memory")
		cfg := &AppConfig{}

		applyDefaults(cfg)

		ts.Require().Equal("memory", cfg.Flow.Store)
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

func (ts *AppConfigTestSuite) TestApplyDefaultsOutboundHTTPClient() {

	cfg := &AppConfig{
		OutboundHTTPClient: HTTPClientConfig{
			TimeoutSecs:     15,
			MaxConnsPerHost: -1,
		},
	}
	applyDefaults(cfg)

	ts.Require().EqualValues(15, cfg.OutboundHTTPClient.TimeoutSecs, "positive yaml value preserved")
	ts.Require().EqualValues(defaultHTTPDialTimeoutSecs, cfg.OutboundHTTPClient.DialTimeoutSecs)
	ts.Require().EqualValues(defaultHTTPDialKeepAliveSecs, cfg.OutboundHTTPClient.DialKeepAliveSecs)
	ts.Require().EqualValues(defaultHTTPTLSHandshakeTimeoutSecs, cfg.OutboundHTTPClient.TLSHandshakeTimeoutSecs)
	ts.Require().EqualValues(defaultHTTPResponseHeaderTimeoutSecs, cfg.OutboundHTTPClient.ResponseHeaderTimeoutSecs)
	ts.Require().EqualValues(defaultHTTPIdleConnTimeoutSecs, cfg.OutboundHTTPClient.IdleConnTimeoutSecs)
	ts.Require().EqualValues(defaultHTTPMaxConnsPerHost, cfg.OutboundHTTPClient.MaxConnsPerHost)
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
