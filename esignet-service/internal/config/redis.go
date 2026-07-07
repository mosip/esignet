package config

import (
	"context"
	"crypto/tls"
	"fmt"
	"strings"
	"time"

	"github.com/kelseyhightower/envconfig"
	"github.com/redis/go-redis/v9"
)

const redisPingTimeout = 5 * time.Second

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

// redisSpec is the environment-variable layout for Redis settings.
// SentinelAddrs is parsed as a raw string so the historical trim/drop-empty
// splitting is preserved in loadRedis.
type redisSpec struct {
	URL      string `envconfig:"REDIS_URL"`
	Host     string `envconfig:"REDIS_HOST" default:"localhost"`
	Port     string `envconfig:"REDIS_PORT" default:"6379"`
	Password string `envconfig:"REDIS_PASSWORD"`
	DB       int    `envconfig:"REDIS_DB"`
	TLS      bool   `envconfig:"REDIS_TLS_ENABLED"`

	PoolSize        int `envconfig:"REDIS_POOL_SIZE" default:"10"`
	MinIdleConns    int `envconfig:"REDIS_MIN_IDLE_CONNS" default:"2"`
	ConnMaxIdleTime int `envconfig:"REDIS_CONN_MAX_IDLE_TIME_SECS" default:"300"`
	ConnMaxLifetime int `envconfig:"REDIS_CONN_MAX_LIFETIME_SECS"` // 0 = no limit
	DialTimeout     int `envconfig:"REDIS_DIAL_TIMEOUT_SECS" default:"5"`
	ReadTimeout     int `envconfig:"REDIS_READ_TIMEOUT_SECS" default:"3"`
	WriteTimeout    int `envconfig:"REDIS_WRITE_TIMEOUT_SECS" default:"3"`

	KeyPrefix string `envconfig:"REDIS_KEY_PREFIX" default:"esignet:"`

	SentinelMaster string `envconfig:"REDIS_SENTINEL_MASTER"`
	SentinelAddrs  string `envconfig:"REDIS_SENTINEL_ADDRS"`
}

// loadRedis reads Redis settings from environment variables. It returns nil on
// error so a failed load can never be mistaken for a usable zero-value config.
func loadRedis() (*Redis, error) {
	var s redisSpec
	if err := envconfig.Process("", &s); err != nil {
		return nil, fmt.Errorf("loading redis config: %w", err)
	}

	// A non-negative lifetime is required; 0 means "no limit". A negative value
	// is rejected rather than silently clamped so a misconfiguration fails loudly.
	if s.ConnMaxLifetime < 0 {
		return nil, fmt.Errorf("loading redis config: REDIS_CONN_MAX_LIFETIME_SECS must not be negative, got %d", s.ConnMaxLifetime)
	}
	lifetime := time.Duration(s.ConnMaxLifetime) * time.Second // 0 = no limit

	var sentinelAddrs []string
	if s.SentinelAddrs != "" {
		for _, a := range strings.Split(s.SentinelAddrs, ",") {
			if a = strings.TrimSpace(a); a != "" {
				sentinelAddrs = append(sentinelAddrs, a)
			}
		}
	}

	return &Redis{
		URL:             s.URL,
		Host:            s.Host,
		Port:            s.Port,
		Password:        s.Password,
		DB:              s.DB,
		TLS:             s.TLS,
		PoolSize:        s.PoolSize,
		MinIdleConns:    s.MinIdleConns,
		ConnMaxIdleTime: time.Duration(s.ConnMaxIdleTime) * time.Second,
		ConnMaxLifetime: lifetime,
		DialTimeout:     time.Duration(s.DialTimeout) * time.Second,
		ReadTimeout:     time.Duration(s.ReadTimeout) * time.Second,
		WriteTimeout:    time.Duration(s.WriteTimeout) * time.Second,
		KeyPrefix:       s.KeyPrefix,
		SentinelMaster:  s.SentinelMaster,
		SentinelAddrs:   sentinelAddrs,
	}, nil
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
