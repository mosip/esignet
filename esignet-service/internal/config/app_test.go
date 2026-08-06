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
	engineconfig "github.com/thunder-id/thunderid/pkg/thunderidengine/config"
)

func chdirTemp(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	orig, err := os.Getwd()
	require.NoError(t, err)
	require.NoError(t, os.Chdir(dir))
	t.Cleanup(func() { require.NoError(t, os.Chdir(orig)) })
	return dir
}

func writeDeploymentYAML(t *testing.T, dir, contents string) {
	t.Helper()
	require.NoError(t, os.MkdirAll(filepath.Join(dir, "data"), 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(dir, "data", "deployment.yaml"), []byte(contents), 0o644))
}

const minimalDeploymentYAML = `
identifier: esignet
port: 8088
issuer: http://127.0.0.1:8088
crypto:
  key: "test-key"
`

func TestLoadAppConfig_MissingFile(t *testing.T) {
	chdirTemp(t)
	t.Setenv("CRYPTO_ENCRYPTION_KEY", "test-key")

	_, err := LoadAppConfig()
	require.Error(t, err)
}

func TestLoadAppConfig_MalformedYAML(t *testing.T) {
	dir := chdirTemp(t)
	writeDeploymentYAML(t, dir, "not: [valid: yaml")
	t.Setenv("CRYPTO_ENCRYPTION_KEY", "test-key")

	_, err := LoadAppConfig()
	require.Error(t, err)
}

func TestLoadAppConfig_UnknownFieldRejected(t *testing.T) {
	dir := chdirTemp(t)
	writeDeploymentYAML(t, dir, minimalDeploymentYAML+"\nnot_a_real_field: true\n")
	t.Setenv("CRYPTO_ENCRYPTION_KEY", "test-key")

	_, err := LoadAppConfig()
	require.Error(t, err)
}

func TestLoadAppConfig_ExpandsEnvAndAppliesDefaults(t *testing.T) {
	dir := chdirTemp(t)
	writeDeploymentYAML(t, dir, `
identifier: "${NAMESPACE}"
port: 8088
issuer: "${MOSIP_ESIGNET_HOST:-http://localhost:8088}"
crypto:
  key: "${CRYPTO_ENCRYPTION_KEY}"
oauth:
  authorization_code:
    validity_period: 120
`)
	t.Setenv("CRYPTO_ENCRYPTION_KEY", "abc123")
	t.Setenv("NAMESPACE", "my-namespace")

	cfg, err := LoadAppConfig()
	require.NoError(t, err)
	require.Equal(t, "my-namespace", cfg.Identifier)
	require.Equal(t, "abc123", cfg.EncryptionConfig.Key)
	require.EqualValues(t, 120, cfg.OAuth.AuthorizationCode.ValidityPeriod)
}

func TestApplyDefaults_AuthorizationCodeValidityPeriod(t *testing.T) {
	t.Run("respects yaml-supplied value", func(t *testing.T) {
		cfg := &AppConfig{OAuth: engineconfig.OAuthConfig{}}
		cfg.OAuth.AuthorizationCode.ValidityPeriod = 120
		t.Setenv("CRYPTO_ENCRYPTION_KEY", "k")

		applyDefaults(cfg)

		require.EqualValues(t, 120, cfg.OAuth.AuthorizationCode.ValidityPeriod)
	})

	t.Run("defaults to 3600 when unset", func(t *testing.T) {
		cfg := &AppConfig{}
		t.Setenv("CRYPTO_ENCRYPTION_KEY", "k")

		applyDefaults(cfg)

		require.EqualValues(t, 3600, cfg.OAuth.AuthorizationCode.ValidityPeriod)
	})
}

func TestApplyDefaults_PARExpiresIn(t *testing.T) {
	t.Run("respects yaml-supplied value", func(t *testing.T) {
		cfg := &AppConfig{}
		cfg.OAuth.PAR.ExpiresIn = 900
		t.Setenv("CRYPTO_ENCRYPTION_KEY", "k")

		applyDefaults(cfg)

		require.EqualValues(t, 900, cfg.OAuth.PAR.ExpiresIn)
	})

	t.Run("defaults to 3600 when unset", func(t *testing.T) {
		cfg := &AppConfig{}
		t.Setenv("CRYPTO_ENCRYPTION_KEY", "k")

		applyDefaults(cfg)

		require.EqualValues(t, 3600, cfg.OAuth.PAR.ExpiresIn)
	})
}

func TestApplyDefaults_JWTValidityAndLeeway(t *testing.T) {
	t.Run("defaults when unset", func(t *testing.T) {
		cfg := &AppConfig{}
		t.Setenv("CRYPTO_ENCRYPTION_KEY", "k")

		applyDefaults(cfg)

		require.EqualValues(t, 120, cfg.JWT.ValidityPeriod)
		require.EqualValues(t, 10, cfg.JWT.Leeway)
		require.EqualValues(t, 10, cfg.OAuth.DPoP.Leeway)
	})

	t.Run("respects env override", func(t *testing.T) {
		cfg := &AppConfig{}
		t.Setenv("CRYPTO_ENCRYPTION_KEY", "k")
		t.Setenv("MOSIP_ESIGNET_JWT_VALIDITY_PERIOD", "7200")
		t.Setenv("MOSIP_ESIGNET_JWT_LEEWAY", "30")
		t.Setenv("MOSIP_ESIGNET_DPOP_LEEWAY", "20")

		applyDefaults(cfg)

		require.EqualValues(t, 7200, cfg.JWT.ValidityPeriod)
		require.EqualValues(t, 30, cfg.JWT.Leeway)
		require.EqualValues(t, 20, cfg.OAuth.DPoP.Leeway)
	})
}

func TestApplyDefaults_CoreFields(t *testing.T) {
	t.Setenv("CRYPTO_ENCRYPTION_KEY", "k")
	t.Setenv("PORT", "9999")
	t.Setenv("MOSIP_ESIGNET_HOST", "https://issuer.example")
	t.Setenv("AUTHN_PROVIDER", "mosip")

	cfg := &AppConfig{}
	applyDefaults(cfg)

	require.Equal(t, 9999, cfg.Port)
	require.Equal(t, "https://issuer.example", cfg.Issuer)
	require.Equal(t, "mosip", cfg.Provider)
	require.Equal(t, "https://issuer.example", cfg.JWT.Issuer)
	require.Equal(t, "https://issuer.example", cfg.JWT.Audience)
	require.False(t, cfg.Server.HTTPOnly, "https:// public URL should not be flagged http-only")
}

func TestApplyDefaults_HTTPOnlyDetection(t *testing.T) {
	t.Setenv("CRYPTO_ENCRYPTION_KEY", "k")
	t.Setenv("MOSIP_ESIGNET_HOST", "http://127.0.0.1:8088")

	cfg := &AppConfig{}
	applyDefaults(cfg)

	require.True(t, cfg.Server.HTTPOnly)
}

func TestApplyDefaults_TTLGuards(t *testing.T) {
	t.Setenv("CRYPTO_ENCRYPTION_KEY", "k")

	cfg := &AppConfig{
		ClientCacheTTLSecs: 42,
		DesignCacheTTLSecs: -1,
		FlowCacheTTLSecs:   0,
	}
	applyDefaults(cfg)

	require.EqualValues(t, 42, cfg.ClientCacheTTLSecs, "positive yaml value preserved")
	require.EqualValues(t, defaultDesignCacheTTLSecs, cfg.DesignCacheTTLSecs)
	require.EqualValues(t, defaultFlowCacheTTLSecs, cfg.FlowCacheTTLSecs)
}

func TestApplyDefaults_OutboundHTTPClient(t *testing.T) {
	t.Setenv("CRYPTO_ENCRYPTION_KEY", "k")

	cfg := &AppConfig{
		OutboundHTTPClient: HTTPClientConfig{
			TimeoutSecs:     15,
			MaxConnsPerHost: -1,
		},
	}
	applyDefaults(cfg)

	require.EqualValues(t, 15, cfg.OutboundHTTPClient.TimeoutSecs, "positive yaml value preserved")
	require.EqualValues(t, defaultHTTPDialTimeoutSecs, cfg.OutboundHTTPClient.DialTimeoutSecs)
	require.EqualValues(t, defaultHTTPDialKeepAliveSecs, cfg.OutboundHTTPClient.DialKeepAliveSecs)
	require.EqualValues(t, defaultHTTPTLSHandshakeTimeoutSecs, cfg.OutboundHTTPClient.TLSHandshakeTimeoutSecs)
	require.EqualValues(t, defaultHTTPResponseHeaderTimeoutSecs, cfg.OutboundHTTPClient.ResponseHeaderTimeoutSecs)
	require.EqualValues(t, defaultHTTPIdleConnTimeoutSecs, cfg.OutboundHTTPClient.IdleConnTimeoutSecs)
	require.EqualValues(t, defaultHTTPMaxConnsPerHost, cfg.OutboundHTTPClient.MaxConnsPerHost)
}

func TestApplyEnvOverrides_GateClient(t *testing.T) {
	cfg := &AppConfig{}
	t.Setenv("OIDC_UI_SCHEME", "https")
	t.Setenv("OIDC_UI_HOSTNAME", "gate.example")
	t.Setenv("OIDC_UI_PORT", "4443")
	t.Setenv("OIDC_UI_LOGIN_PATH", "/login")
	t.Setenv("OIDC_UI_ERROR_PATH", "/oops")

	require.NoError(t, ApplyEnvOverrides(cfg))

	require.Equal(t, "https", cfg.GateClient.Scheme)
	require.Equal(t, "gate.example", cfg.GateClient.Hostname)
	require.Equal(t, 4443, cfg.GateClient.Port)
	require.Equal(t, "/login", cfg.GateClient.LoginPath)
	require.Equal(t, "/oops", cfg.GateClient.ErrorPath)
}

func TestApplyEnvOverrides_InvalidPort(t *testing.T) {
	cfg := &AppConfig{}
	t.Setenv("OIDC_UI_PORT", "not-a-number")

	require.Error(t, ApplyEnvOverrides(cfg))
}

func TestApplyEnvOverrides_PortOutOfRange(t *testing.T) {
	cfg := &AppConfig{}
	t.Setenv("OIDC_UI_PORT", "70000")

	require.Error(t, ApplyEnvOverrides(cfg))
}

func TestApplyEnvOverrides_OAuthLifetimes(t *testing.T) {
	cfg := &AppConfig{}
	t.Setenv("OAUTH_AUTH_CODE_LIFETIME_SECONDS", "60")
	t.Setenv("OAUTH_PAR_EXPIRY_SECONDS", "120")
	t.Setenv("OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS", "7200")

	require.NoError(t, ApplyEnvOverrides(cfg))

	require.EqualValues(t, 60, cfg.OAuth.AuthorizationCode.ValidityPeriod)
	require.EqualValues(t, 120, cfg.OAuth.PAR.ExpiresIn)
	require.EqualValues(t, 7200, cfg.JWT.ValidityPeriod)
}

func TestApplyEnvOverrides_NonPositiveLifetimesIgnored(t *testing.T) {
	cfg := &AppConfig{}
	cfg.OAuth.AuthorizationCode.ValidityPeriod = 120
	t.Setenv("OAUTH_AUTH_CODE_LIFETIME_SECONDS", "0")

	require.NoError(t, ApplyEnvOverrides(cfg))

	require.EqualValues(t, 120, cfg.OAuth.AuthorizationCode.ValidityPeriod, "0 or negative override should be ignored")
}

func TestApplyEnvOverrides_InvalidOAuthLifetime(t *testing.T) {
	cfg := &AppConfig{}
	t.Setenv("OAUTH_AUTH_CODE_LIFETIME_SECONDS", "abc")

	require.Error(t, ApplyEnvOverrides(cfg))
}

func TestApplyEnvOverrides_InvalidPARLifetime(t *testing.T) {
	cfg := &AppConfig{}
	t.Setenv("OAUTH_PAR_EXPIRY_SECONDS", "abc")

	require.Error(t, ApplyEnvOverrides(cfg))
}

func TestApplyEnvOverrides_InvalidAccessTokenLifetime(t *testing.T) {
	cfg := &AppConfig{}
	t.Setenv("OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS", "abc")

	require.Error(t, ApplyEnvOverrides(cfg))
}

func TestApplyEnvOverrides_NoEnvSetLeavesDefaults(t *testing.T) {
	for _, envVar := range []string{
		"OIDC_UI_SCHEME", "OIDC_UI_HOSTNAME", "OIDC_UI_PORT", "OIDC_UI_LOGIN_PATH", "OIDC_UI_ERROR_PATH",
		"OAUTH_AUTH_CODE_LIFETIME_SECONDS", "OAUTH_PAR_EXPIRY_SECONDS", "OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS",
	} {
		t.Setenv(envVar, "")
	}

	cfg := &AppConfig{}
	require.NoError(t, ApplyEnvOverrides(cfg))
	require.Zero(t, cfg.GateClient.Scheme)
}

func TestEnvOrDefault(t *testing.T) {
	t.Setenv("ESIGNET_TEST_ENV_OR_DEFAULT", "value")
	require.Equal(t, "value", envOrDefault("ESIGNET_TEST_ENV_OR_DEFAULT", "fallback"))
	require.Equal(t, "fallback", envOrDefault("ESIGNET_TEST_ENV_OR_DEFAULT_UNSET", "fallback"))
}

func TestEnvIntOrDefault(t *testing.T) {
	t.Setenv("ESIGNET_TEST_ENV_INT", "123")
	require.Equal(t, 123, envIntOrDefault("ESIGNET_TEST_ENV_INT", 999))
	require.Equal(t, 999, envIntOrDefault("ESIGNET_TEST_ENV_INT_UNSET", 999))

	t.Setenv("ESIGNET_TEST_ENV_INT_BAD", "not-a-number")
	require.Equal(t, 999, envIntOrDefault("ESIGNET_TEST_ENV_INT_BAD", 999))
}

func TestEnvBool(t *testing.T) {
	cases := map[string]bool{
		"1": true, "true": true, "TRUE": true, "yes": true, "on": true,
		"0": false, "false": false, "no": false, "": false, "garbage": false,
	}
	for raw, want := range cases {
		t.Run(raw, func(t *testing.T) {
			t.Setenv("ESIGNET_TEST_ENV_BOOL", raw)
			require.Equal(t, want, envBool("ESIGNET_TEST_ENV_BOOL"))
		})
	}
}
