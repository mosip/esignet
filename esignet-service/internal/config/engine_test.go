package config

import (
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/thunder-id/thunderid/pkg/thunderidengine"
)

func TestLoadEngine_defaults(t *testing.T) {
	t.Setenv("PORT", "")
	t.Setenv("ISSUER_URL", "")
	t.Setenv("DATA_DIR", "")
	t.Setenv("SIGNING_KEY_PATH", "")
	t.Setenv("GATE_CLIENT_SCHEME", "")
	t.Setenv("GATE_CLIENT_HOSTNAME", "")
	t.Setenv("GATE_CLIENT_PORT", "")
	t.Setenv("GATE_CLIENT_LOGIN_PATH", "")
	t.Setenv("GATE_CLIENT_ERROR_PATH", "")

	cfg := LoadEngine()
	require.Equal(t, defaultPort, cfg.Port)
	require.Equal(t, "http://127.0.0.1:8080", cfg.Issuer)
	require.Equal(t, defaultDataDir, cfg.DataDir)
	require.Equal(t, defaultSigningKeyPath, cfg.SigningKeyPath)
	require.Equal(t, defaultGateScheme, cfg.GateClient.Scheme)
	require.Equal(t, defaultGateHostname, cfg.GateClient.Hostname)
	require.Equal(t, 8080, cfg.GateClient.Port)
	require.Equal(t, defaultGateLoginPath, cfg.GateClient.LoginPath)
	require.Equal(t, defaultGateErrorPath, cfg.GateClient.ErrorPath)
}

func TestLoadEngine_invalidPortFallsBackToDefaultGatePort(t *testing.T) {
	t.Setenv("PORT", "abc")
	t.Setenv("GATE_CLIENT_PORT", "")

	cfg := LoadEngine()
	require.Equal(t, defaultGatePort, cfg.GateClient.Port)
}

func TestLoadEngine_gateClientFromEnv(t *testing.T) {
	t.Setenv("GATE_CLIENT_SCHEME", "https")
	t.Setenv("GATE_CLIENT_HOSTNAME", "gate.local")
	t.Setenv("GATE_CLIENT_PORT", "9443")
	t.Setenv("GATE_CLIENT_LOGIN_PATH", "/app/signin")
	t.Setenv("GATE_CLIENT_ERROR_PATH", "/app/error")

	cfg := LoadEngine()
	require.Equal(t, "https", cfg.GateClient.Scheme)
	require.Equal(t, "gate.local", cfg.GateClient.Hostname)
	require.Equal(t, 9443, cfg.GateClient.Port)
	require.Equal(t, "/app/signin", cfg.GateClient.LoginPath)
	require.Equal(t, "/app/error", cfg.GateClient.ErrorPath)
}

func TestThunderEngineConfig_usesSystemAndJWT(t *testing.T) {
	cfg := Engine{
		Issuer:         "https://as.example.com",
		Audience:       "my-aud",
		DataDir:        "/data",
		SigningKeyPath: "/keys/signing.key",
		PreferredKeyID: "key-1",
		JWKSCacheTTL:   300,
		JWTLeeway:      10,
		GateClient: EngineGateClient{
			Scheme:    "https",
			Hostname:  "gate.example.com",
			Port:      9443,
			LoginPath: "/login",
			ErrorPath: "/error",
		},
		OAuth: EngineOAuth{
			AccessTokenLifetimeSeconds: 7200,
			PARRequired:                true,
		},
	}

	engineCfg := cfg.ThunderEngineConfig()
	require.Equal(t, "https://as.example.com", engineCfg.Issuer)
	require.Equal(t, "my-aud", engineCfg.Audience)
	require.Equal(t, "/keys/signing.key", engineCfg.System.SigningKeyPath)
	require.Equal(t, "key-1", engineCfg.System.PreferredKeyID)
	require.Equal(t, int64(300), engineCfg.System.JWKSCacheTTL)
	require.Equal(t, "https://as.example.com", engineCfg.JWT.Issuer)
	require.Equal(t, "my-aud", engineCfg.JWT.Audience)
	require.Equal(t, int64(10), engineCfg.JWT.Leeway)
	require.Equal(t, 7200, engineCfg.OAuth.AccessTokenLifetimeSeconds)
	require.True(t, engineCfg.OAuth.PARRequired)
	require.Equal(t, "gate.example.com", engineCfg.GateClient.Hostname)
	require.Equal(t, 9443, engineCfg.GateClient.Port)
	require.Equal(t, "/login", engineCfg.GateClient.LoginPath)
	require.Equal(t, thunderidengine.StoreModeDeclarative, engineCfg.FlowStore.StoreMode)
	require.Len(t, engineCfg.Flow.Executors, 4)
}
