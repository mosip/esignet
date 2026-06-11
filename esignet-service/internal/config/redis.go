package config

import (
	"context"
	"crypto/tls"
	"fmt"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
)

const (
	defaultRedisHost            = "localhost"
	defaultRedisPort            = "6379"
	defaultRedisDB              = 0
	defaultRedisPoolSize        = 10
	defaultRedisMinIdleConns    = 2
	defaultRedisConnMaxIdleTime = 5 * time.Minute
	defaultRedisDialTimeout     = 5 * time.Second
	defaultRedisReadTimeout     = 3 * time.Second
	defaultRedisWriteTimeout    = 3 * time.Second
	defaultRedisKeyPrefix       = "esignet:"
	redisPingTimeout            = 5 * time.Second
)

// Redis holds all settings needed to open and configure a Redis client.
//
// Environment variables (all optional):
//
//	REDIS_URL                   — full redis:// or rediss:// DSN (takes precedence over individual vars)
//	REDIS_HOST                  — default "localhost"
//	REDIS_PORT                  — default "6379"
//	REDIS_PASSWORD
//	REDIS_DB                    — default 0
//	REDIS_TLS_ENABLED           — "true" to enable TLS (automatically on for rediss:// URLs)
//
//	REDIS_POOL_SIZE             — default 10
//	REDIS_MIN_IDLE_CONNS        — default 2
//	REDIS_CONN_MAX_IDLE_TIME_SECS — default 300
//	REDIS_CONN_MAX_LIFETIME_SECS  — default 0 (no limit)
//	REDIS_DIAL_TIMEOUT_SECS     — default 5
//	REDIS_READ_TIMEOUT_SECS     — default 3
//	REDIS_WRITE_TIMEOUT_SECS    — default 3
//
//	REDIS_KEY_PREFIX            — default "esignet:"
//
//	REDIS_SENTINEL_MASTER       — master name for Sentinel mode
//	REDIS_SENTINEL_ADDRS        — comma-separated sentinel addresses (enables Sentinel mode)
type Redis struct {
	URL      string // full DSN if provided
	Host     string
	Port     string
	Password string
	DB       int
	TLS      bool

	PoolSize        int
	MinIdleConns    int
	ConnMaxIdleTime time.Duration
	ConnMaxLifetime time.Duration
	DialTimeout     time.Duration
	ReadTimeout     time.Duration
	WriteTimeout    time.Duration

	KeyPrefix string

	SentinelMaster string
	SentinelAddrs  []string
}

// LoadRedis reads Redis settings from environment variables.
func LoadRedis() Redis {
	poolSize := envInt("REDIS_POOL_SIZE")
	if poolSize <= 0 {
		poolSize = defaultRedisPoolSize
	}
	minIdle := envInt("REDIS_MIN_IDLE_CONNS")
	if minIdle <= 0 {
		minIdle = defaultRedisMinIdleConns
	}

	idleSecs := envInt("REDIS_CONN_MAX_IDLE_TIME_SECS")
	var idleTime time.Duration
	if idleSecs > 0 {
		idleTime = time.Duration(idleSecs) * time.Second
	} else {
		idleTime = defaultRedisConnMaxIdleTime
	}

	lifetimeSecs := envInt("REDIS_CONN_MAX_LIFETIME_SECS")
	lifetime := time.Duration(lifetimeSecs) * time.Second // 0 = no limit

	dialSecs := envInt("REDIS_DIAL_TIMEOUT_SECS")
	var dialTimeout time.Duration
	if dialSecs > 0 {
		dialTimeout = time.Duration(dialSecs) * time.Second
	} else {
		dialTimeout = defaultRedisDialTimeout
	}

	readSecs := envInt("REDIS_READ_TIMEOUT_SECS")
	var readTimeout time.Duration
	if readSecs > 0 {
		readTimeout = time.Duration(readSecs) * time.Second
	} else {
		readTimeout = defaultRedisReadTimeout
	}

	writeSecs := envInt("REDIS_WRITE_TIMEOUT_SECS")
	var writeTimeout time.Duration
	if writeSecs > 0 {
		writeTimeout = time.Duration(writeSecs) * time.Second
	} else {
		writeTimeout = defaultRedisWriteTimeout
	}

	keyPrefix := envOrDefault("REDIS_KEY_PREFIX", defaultRedisKeyPrefix)

	sentinelAddrsRaw := envOrDefault("REDIS_SENTINEL_ADDRS", "")
	var sentinelAddrs []string
	if sentinelAddrsRaw != "" {
		for _, a := range strings.Split(sentinelAddrsRaw, ",") {
			if a = strings.TrimSpace(a); a != "" {
				sentinelAddrs = append(sentinelAddrs, a)
			}
		}
	}

	return Redis{
		URL:             envOrDefault("REDIS_URL", ""),
		Host:            envOrDefault("REDIS_HOST", defaultRedisHost),
		Port:            envOrDefault("REDIS_PORT", defaultRedisPort),
		Password:        envOrDefault("REDIS_PASSWORD", ""),
		DB:              envInt("REDIS_DB"),
		TLS:             envBool("REDIS_TLS_ENABLED"),
		PoolSize:        poolSize,
		MinIdleConns:    minIdle,
		ConnMaxIdleTime: idleTime,
		ConnMaxLifetime: lifetime,
		DialTimeout:     dialTimeout,
		ReadTimeout:     readTimeout,
		WriteTimeout:    writeTimeout,
		KeyPrefix:       keyPrefix,
		SentinelMaster:  envOrDefault("REDIS_SENTINEL_MASTER", ""),
		SentinelAddrs:   sentinelAddrs,
	}
}

// Open creates a Redis client, applies pool/timeout settings, and pings the server.
// Supports three modes (in priority order):
//  1. Full DSN via REDIS_URL
//  2. Sentinel via REDIS_SENTINEL_MASTER + REDIS_SENTINEL_ADDRS
//  3. Single-node via REDIS_HOST / REDIS_PORT
func (r Redis) Open() (*redis.Client, error) {
	client, err := r.newClient()
	if err != nil {
		return nil, err
	}

	ctx, cancel := context.WithTimeout(context.Background(), redisPingTimeout)
	defer cancel()
	if err := client.Ping(ctx).Err(); err != nil {
		_ = client.Close()
		return nil, fmt.Errorf("ping redis: %w", err)
	}
	return client, nil
}

func (r Redis) newClient() (*redis.Client, error) {
	// --- Mode 1: full DSN ---
	if r.URL != "" {
		opts, err := redis.ParseURL(r.URL)
		if err != nil {
			return nil, fmt.Errorf("parse REDIS_URL: %w", err)
		}
		r.applyPool(opts)
		return redis.NewClient(opts), nil
	}

	// --- Mode 2: Sentinel ---
	if r.SentinelMaster != "" && len(r.SentinelAddrs) > 0 {
		opts := &redis.FailoverOptions{
			MasterName:      r.SentinelMaster,
			SentinelAddrs:   r.SentinelAddrs,
			Password:        r.Password,
			DB:              r.DB,
			PoolSize:        r.PoolSize,
			MinIdleConns:    r.MinIdleConns,
			ConnMaxIdleTime: r.ConnMaxIdleTime,
			ConnMaxLifetime: r.ConnMaxLifetime,
			DialTimeout:     r.DialTimeout,
			ReadTimeout:     r.ReadTimeout,
			WriteTimeout:    r.WriteTimeout,
		}
		if r.TLS {
			opts.TLSConfig = &tls.Config{MinVersion: tls.VersionTLS12}
		}
		return redis.NewFailoverClient(opts), nil
	}

	// --- Mode 3: single node ---
	opts := &redis.Options{
		Addr:            fmt.Sprintf("%s:%s", r.Host, r.Port),
		Password:        r.Password,
		DB:              r.DB,
		PoolSize:        r.PoolSize,
		MinIdleConns:    r.MinIdleConns,
		ConnMaxIdleTime: r.ConnMaxIdleTime,
		ConnMaxLifetime: r.ConnMaxLifetime,
		DialTimeout:     r.DialTimeout,
		ReadTimeout:     r.ReadTimeout,
		WriteTimeout:    r.WriteTimeout,
	}
	if r.TLS {
		opts.TLSConfig = &tls.Config{MinVersion: tls.VersionTLS12}
	}
	return redis.NewClient(opts), nil
}

// applyPool copies pool/timeout settings onto options parsed from a DSN.
// The DSN may not encode pool parameters so we always override with explicit config.
func (r Redis) applyPool(opts *redis.Options) {
	opts.PoolSize = r.PoolSize
	opts.MinIdleConns = r.MinIdleConns
	opts.ConnMaxIdleTime = r.ConnMaxIdleTime
	opts.ConnMaxLifetime = r.ConnMaxLifetime
	opts.DialTimeout = r.DialTimeout
	opts.ReadTimeout = r.ReadTimeout
	opts.WriteTimeout = r.WriteTimeout
	if r.TLS && opts.TLSConfig == nil {
		opts.TLSConfig = &tls.Config{MinVersion: tls.VersionTLS12}
	}
}
