package config

import (
	"strconv"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

func TestLoadRuntimeStore_defaults(t *testing.T) {
	t.Setenv("RUNTIME_STORE", "")
	t.Setenv("REDIS_ADDRESS", "")
	t.Setenv("REDIS_USERNAME", "")
	t.Setenv("REDIS_PASSWORD", "")
	t.Setenv("REDIS_DB", "")
	t.Setenv("REDIS_DEPLOYMENT_ID", "")
	t.Setenv("REDIS_MAX_RETRIES", "")
	t.Setenv("REDIS_MIN_RETRY_BACKOFF_MS", "")
	t.Setenv("REDIS_MAX_RETRY_BACKOFF_MS", "")
	t.Setenv("REDIS_DIAL_TIMEOUT_MS", "")
	t.Setenv("REDIS_READ_TIMEOUT_MS", "")
	t.Setenv("REDIS_WRITE_TIMEOUT_MS", "")

	cfg := LoadRuntimeStore()
	require.Equal(t, RuntimeStoreMemory, cfg.Backend)
	require.Equal(t, defaultRedisAddress, cfg.Redis.Address)
	require.Empty(t, cfg.Redis.DeploymentID)
	require.Equal(t, 0, cfg.Redis.DB)
	require.Equal(t, defaultRedisDialTimeout, cfg.Redis.DialTimeout)
}

func TestLoadRuntimeStore_redis(t *testing.T) {
	t.Setenv("RUNTIME_STORE", RuntimeStoreRedis)
	t.Setenv("REDIS_ADDRESS", "redis:6380")
	t.Setenv("REDIS_USERNAME", "user")
	t.Setenv("REDIS_PASSWORD", "secret")
	t.Setenv("REDIS_DB", "3")
	t.Setenv("REDIS_DEPLOYMENT_ID", "prod-1")
	t.Setenv("REDIS_DIAL_TIMEOUT_MS", "1500")

	cfg := LoadRuntimeStore()
	require.Equal(t, RuntimeStoreRedis, cfg.Backend)
	require.Equal(t, "redis:6380", cfg.Redis.Address)
	require.Equal(t, "user", cfg.Redis.Username)
	require.Equal(t, "secret", cfg.Redis.Password)
	require.Equal(t, 3, cfg.Redis.DB)
	require.Equal(t, "prod-1", cfg.Redis.DeploymentID)
	require.Equal(t, 1500*time.Millisecond, cfg.Redis.DialTimeout)
}

func TestLoadRuntimeStore_invalidIntFallsBack(t *testing.T) {
	t.Setenv("REDIS_DB", "not-a-number")
	require.Equal(t, 0, LoadRuntimeStore().Redis.DB)
}

func TestLoadRuntimeStore_invalidDurationFallsBack(t *testing.T) {
	t.Setenv("REDIS_DIAL_TIMEOUT_MS", "oops")
	require.Equal(t, defaultRedisDialTimeout, LoadRuntimeStore().Redis.DialTimeout)
}

func TestLoadRuntimeStore_nonPositiveDurationFallsBack(t *testing.T) {
	t.Setenv("REDIS_DIAL_TIMEOUT_MS", "0")
	require.Equal(t, defaultRedisDialTimeout, LoadRuntimeStore().Redis.DialTimeout)
}

func TestLoadRuntimeStore_overflowDurationFallsBack(t *testing.T) {
	t.Setenv("REDIS_DIAL_TIMEOUT_MS", strconv.Itoa(maxDurationMS+1))
	require.Equal(t, defaultRedisDialTimeout, LoadRuntimeStore().Redis.DialTimeout)
}
