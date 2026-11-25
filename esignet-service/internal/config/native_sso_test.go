package config

import (
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

func TestApplyEnvOverrides_DeviceSecretValidityBounds(t *testing.T) {
	cfg := &AppConfig{}
	t.Setenv("MOSIP_ESIGNET_DEVICE_SECRET_VALIDITY_SECONDS", "86400")
	require.NoError(t, ApplyEnvOverrides(cfg))
	require.Equal(t, 24*time.Hour, cfg.NativeSSO.DeviceSecretValidity())

	t.Setenv("MOSIP_ESIGNET_DEVICE_SECRET_VALIDITY_SECONDS", "0")
	cfg.NativeSSO.DeviceSecretValiditySeconds = 3600
	require.NoError(t, ApplyEnvOverrides(cfg))
	require.Equal(t, time.Hour, cfg.NativeSSO.DeviceSecretValidity(), "an environment value of 0 leaves the YAML cap unchanged")

	t.Setenv("MOSIP_ESIGNET_DEVICE_SECRET_VALIDITY_SECONDS", "9223372036854775807")
	require.ErrorContains(t, ApplyEnvOverrides(cfg), "exceeds the maximum")

	t.Setenv("MOSIP_ESIGNET_DEVICE_SECRET_VALIDITY_SECONDS", "")
	cfg.NativeSSO.DeviceSecretValiditySeconds = maxDeviceSecretValiditySeconds + 1
	require.ErrorContains(t, ApplyEnvOverrides(cfg), "exceeds the maximum")
}
