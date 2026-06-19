package config

import (
	"os"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

// redisEnvKeys is every variable loadRedis reads; tests clear them all so a
// stray value in the ambient environment cannot mask a default.
var redisEnvKeys = []string{
	"REDIS_URL", "REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD", "REDIS_DB",
	"REDIS_TLS_ENABLED", "REDIS_POOL_SIZE", "REDIS_MIN_IDLE_CONNS",
	"REDIS_CONN_MAX_IDLE_TIME_SECS", "REDIS_CONN_MAX_LIFETIME_SECS",
	"REDIS_DIAL_TIMEOUT_SECS", "REDIS_READ_TIMEOUT_SECS", "REDIS_WRITE_TIMEOUT_SECS",
	"REDIS_KEY_PREFIX", "REDIS_SENTINEL_MASTER", "REDIS_SENTINEL_ADDRS",
}

// clearRedisEnv unsets every Redis variable so envconfig's `default` tags apply.
// t.Setenv first registers restoration of any ambient value at test cleanup;
// os.Unsetenv then removes it for the duration of the test (an empty string
// would count as "set" and suppress the default).
func clearRedisEnv(t *testing.T) {
	t.Helper()
	for _, k := range redisEnvKeys {
		t.Setenv(k, "")
		require.NoError(t, os.Unsetenv(k))
	}
}

func TestLoadRedis_defaults(t *testing.T) {
	clearRedisEnv(t)

	cfg, err := loadRedis()
	require.NoError(t, err)
	require.Empty(t, cfg.URL)
	require.Equal(t, "localhost", cfg.Host)
	require.Equal(t, "6379", cfg.Port)
	require.Empty(t, cfg.Password)
	require.Equal(t, 0, cfg.DB)
	require.False(t, cfg.TLS)
	require.Equal(t, 10, cfg.PoolSize)
	require.Equal(t, 2, cfg.MinIdleConns)
	require.Equal(t, 5*time.Minute, cfg.ConnMaxIdleTime)
	require.Equal(t, time.Duration(0), cfg.ConnMaxLifetime) // 0 = no limit
	require.Equal(t, 5*time.Second, cfg.DialTimeout)
	require.Equal(t, 3*time.Second, cfg.ReadTimeout)
	require.Equal(t, 3*time.Second, cfg.WriteTimeout)
	require.Equal(t, "esignet:", cfg.KeyPrefix)
	require.Empty(t, cfg.SentinelMaster)
	require.Nil(t, cfg.SentinelAddrs)
}

func TestLoadRedis_overrides(t *testing.T) {
	clearRedisEnv(t)
	t.Setenv("REDIS_URL", "redis://:secret@cache:6380/2")
	t.Setenv("REDIS_HOST", "cache")
	t.Setenv("REDIS_PORT", "6380")
	t.Setenv("REDIS_PASSWORD", "secret")
	t.Setenv("REDIS_DB", "2")
	t.Setenv("REDIS_TLS_ENABLED", "true")
	t.Setenv("REDIS_POOL_SIZE", "20")
	t.Setenv("REDIS_MIN_IDLE_CONNS", "4")
	t.Setenv("REDIS_CONN_MAX_IDLE_TIME_SECS", "120")
	t.Setenv("REDIS_CONN_MAX_LIFETIME_SECS", "600")
	t.Setenv("REDIS_DIAL_TIMEOUT_SECS", "7")
	t.Setenv("REDIS_READ_TIMEOUT_SECS", "8")
	t.Setenv("REDIS_WRITE_TIMEOUT_SECS", "9")
	t.Setenv("REDIS_KEY_PREFIX", "test:")
	t.Setenv("REDIS_SENTINEL_MASTER", "mymaster")

	cfg, err := loadRedis()
	require.NoError(t, err)
	require.Equal(t, "redis://:secret@cache:6380/2", cfg.URL)
	require.Equal(t, "cache", cfg.Host)
	require.Equal(t, "6380", cfg.Port)
	require.Equal(t, "secret", cfg.Password)
	require.Equal(t, 2, cfg.DB)
	require.True(t, cfg.TLS)
	require.Equal(t, 20, cfg.PoolSize)
	require.Equal(t, 4, cfg.MinIdleConns)
	// Each *_SECS value lands on its own distinct field — guards against a
	// swapped read/write/dial timeout mapping.
	require.Equal(t, 120*time.Second, cfg.ConnMaxIdleTime)
	require.Equal(t, 600*time.Second, cfg.ConnMaxLifetime)
	require.Equal(t, 7*time.Second, cfg.DialTimeout)
	require.Equal(t, 8*time.Second, cfg.ReadTimeout)
	require.Equal(t, 9*time.Second, cfg.WriteTimeout)
	require.Equal(t, "test:", cfg.KeyPrefix)
	require.Equal(t, "mymaster", cfg.SentinelMaster)
}

func TestLoadRedis_sentinelAddrsTrimAndDropEmpty(t *testing.T) {
	clearRedisEnv(t)
	t.Setenv("REDIS_SENTINEL_ADDRS", "a:26379, b:26379 ,, c:26379")

	cfg, err := loadRedis()
	require.NoError(t, err)
	require.Equal(t, []string{"a:26379", "b:26379", "c:26379"}, cfg.SentinelAddrs)
}

// An unparseable numeric value now fails loudly rather than silently falling
// back to a default.
func TestLoadRedis_invalidNumberReturnsError(t *testing.T) {
	clearRedisEnv(t)
	t.Setenv("REDIS_POOL_SIZE", "not-a-number")

	_, err := loadRedis()
	require.Error(t, err)
}

// A negative max-lifetime parses as a valid integer but is not a meaningful
// duration, so loadRedis now rejects it rather than silently clamping to 0.
func TestLoadRedis_negativeLifetimeReturnsError(t *testing.T) {
	clearRedisEnv(t)
	t.Setenv("REDIS_CONN_MAX_LIFETIME_SECS", "-1")

	_, err := loadRedis()
	require.Error(t, err)
}
