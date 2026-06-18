package config

import (
	"testing"

	"github.com/stretchr/testify/require"
)

func TestLoadSunbirdAuthn_defaults(t *testing.T) {
	for _, key := range []string{
		envSunbirdSearchURL, envSunbirdEntityURL, envSunbirdIDField,
		envSunbirdEntityIDField, envSunbirdFieldDetails, envSunbirdClaimsMapping,
		envSunbirdTimeout,
	} {
		t.Setenv(key, "")
	}

	cfg := LoadSunbirdAuthn()
	require.Empty(t, cfg.SearchURL)
	require.Empty(t, cfg.EntityURL)
	require.Equal(t, "policyNumber", cfg.IDField)
	require.Equal(t, "osid", cfg.EntityIDField)
	require.Equal(t, defaultSunbirdFieldDetails, cfg.FieldDetails)
	require.Equal(t, defaultSunbirdClaimsMapping, cfg.ClaimsMapping)
	require.Equal(t, 10, cfg.TimeoutSecs)
}

func TestLoadSunbirdAuthn_overrides(t *testing.T) {
	t.Setenv(envSunbirdSearchURL, "https://reg.example/api/v1/Insurance/search")
	t.Setenv(envSunbirdEntityURL, "https://reg.example/api/v1/Insurance/")
	t.Setenv(envSunbirdIDField, "memberId")
	t.Setenv(envSunbirdEntityIDField, "id")
	t.Setenv(envSunbirdFieldDetails, `[{"id":"memberId","type":"text","format":""}]`)
	t.Setenv(envSunbirdClaimsMapping, `{"name":"fullName"}`)
	t.Setenv(envSunbirdTimeout, "25")

	cfg := LoadSunbirdAuthn()
	require.Equal(t, "https://reg.example/api/v1/Insurance/search", cfg.SearchURL)
	// Trailing slash is trimmed so entity ids can be appended cleanly.
	require.Equal(t, "https://reg.example/api/v1/Insurance", cfg.EntityURL)
	require.Equal(t, "memberId", cfg.IDField)
	require.Equal(t, "id", cfg.EntityIDField)
	require.Equal(t, `[{"id":"memberId","type":"text","format":""}]`, cfg.FieldDetails)
	require.Equal(t, `{"name":"fullName"}`, cfg.ClaimsMapping)
	require.Equal(t, 25, cfg.TimeoutSecs)
}

func TestLoadSunbirdAuthn_invalidTimeoutFallsBackToDefault(t *testing.T) {
	t.Setenv(envSunbirdTimeout, "not-a-number")
	require.Equal(t, 10, LoadSunbirdAuthn().TimeoutSecs)

	t.Setenv(envSunbirdTimeout, "0")
	require.Equal(t, 10, LoadSunbirdAuthn().TimeoutSecs)

	t.Setenv(envSunbirdTimeout, "-5")
	require.Equal(t, 10, LoadSunbirdAuthn().TimeoutSecs)
}
