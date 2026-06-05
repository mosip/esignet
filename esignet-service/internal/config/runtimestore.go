package config

import (
	"math"
	"os"
	"strconv"
	"time"

	applog "github.com/mosip/esignet/internal/log"
)

// Runtime store backend identifiers.
const (
	RuntimeStoreMemory  = "memory"
	RuntimeStoreRedis   = "redis"
	defaultRuntimeStore = RuntimeStoreMemory
)

const (
	defaultRedisAddress         = "127.0.0.1:6379"
	defaultRedisDialTimeout     = 5 * time.Second
	defaultRedisReadTimeout     = 3 * time.Second
	defaultRedisWriteTimeout    = 3 * time.Second
	defaultRedisMaxRetries      = 3
	defaultRedisMinRetryBackoff = 8 * time.Millisecond
	defaultRedisMaxRetryBackoff = 512 * time.Millisecond
)

// RuntimeStore selects and configures the engine runtime store backend.
type RuntimeStore struct {
	Backend string
	Redis   RedisRuntimeStore
}

// RedisRuntimeStore holds connection settings for the Redis runtime store.
type RedisRuntimeStore struct {
	Address         string
	Username        string
	Password        string
	DB              int
	DeploymentID    string
	MaxRetries      int
	MinRetryBackoff time.Duration
	MaxRetryBackoff time.Duration
	DialTimeout     time.Duration
	ReadTimeout     time.Duration
	WriteTimeout    time.Duration
}

// LoadRuntimeStore reads runtime store settings from environment variables.
func LoadRuntimeStore() RuntimeStore {
	return RuntimeStore{
		Backend: envOrDefault("RUNTIME_STORE", defaultRuntimeStore),
		Redis: RedisRuntimeStore{
			Address:         envOrDefault("REDIS_ADDRESS", defaultRedisAddress),
			Username:        os.Getenv("REDIS_USERNAME"),
			Password:        os.Getenv("REDIS_PASSWORD"),
			DB:              envIntOrDefault("REDIS_DB", 0),
			DeploymentID:    os.Getenv("REDIS_DEPLOYMENT_ID"),
			MaxRetries:      envIntOrDefault("REDIS_MAX_RETRIES", defaultRedisMaxRetries),
			MinRetryBackoff: envDurationMSOrDefault("REDIS_MIN_RETRY_BACKOFF_MS", defaultRedisMinRetryBackoff),
			MaxRetryBackoff: envDurationMSOrDefault("REDIS_MAX_RETRY_BACKOFF_MS", defaultRedisMaxRetryBackoff),
			DialTimeout:     envDurationMSOrDefault("REDIS_DIAL_TIMEOUT_MS", defaultRedisDialTimeout),
			ReadTimeout:     envDurationMSOrDefault("REDIS_READ_TIMEOUT_MS", defaultRedisReadTimeout),
			WriteTimeout:    envDurationMSOrDefault("REDIS_WRITE_TIMEOUT_MS", defaultRedisWriteTimeout),
		},
	}
}

func envIntOrDefault(key string, fallback int) int {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		applog.GetLogger().Warn("invalid integer env var; using default",
			applog.String("key", key), applog.String("value", value), applog.Int("default", fallback))
		return fallback
	}
	return parsed
}

// maxDurationMS is the largest millisecond value that fits in time.Duration
// without overflowing once multiplied by time.Millisecond.
const maxDurationMS = int(math.MaxInt64 / int64(time.Millisecond))

func envDurationMSOrDefault(key string, fallback time.Duration) time.Duration {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		applog.GetLogger().Warn("invalid duration env var; using default",
			applog.String("key", key), applog.String("value", value), applog.String("default", fallback.String()))
		return fallback
	}
	if parsed <= 0 {
		applog.GetLogger().Warn("non-positive duration env var; using default",
			applog.String("key", key), applog.String("value", value), applog.String("default", fallback.String()))
		return fallback
	}
	if parsed > maxDurationMS {
		applog.GetLogger().Warn("duration env var overflows; using default",
			applog.String("key", key), applog.String("value", value), applog.String("default", fallback.String()))
		return fallback
	}
	return time.Duration(parsed) * time.Millisecond
}
