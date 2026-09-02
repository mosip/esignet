/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

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
	defaultRedisHost                = "localhost"
	defaultRedisPort                = "6379"
	defaultRedisPoolSize            = 10
	defaultRedisMinIdleConns        = 2
	defaultRedisConnMaxIdleTime     = 300
	defaultRedisDialTimeoutSecs     = 5
	defaultRedisReadTimeoutSecs     = 3
	defaultRedisWriteTimeoutSecs    = 3
	defaultRedisPoolTimeoutSecs     = 4
	defaultRedisConnMaxLifetimeSecs = 1800
	defaultRedisKeyPrefix           = "esignet:"
	redisPingTimeout                = 5 * time.Second
	defaultRedisDB                  = 0
)

// Redis holds all settings needed to open and configure a Redis client.
//
// Precedence for each field is: env var, then the value already parsed from
// deployment.yaml's redis: block (KnownFields(true) means only the fields
// tagged below can be set there), then the compiled-in default.
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
//	REDIS_KEY_PREFIX            — default "esignet:"
//
//	REDIS_SENTINEL_MASTER       — master name for Sentinel mode
//	REDIS_SENTINEL_ADDRS        — comma-separated sentinel addresses (enables Sentinel mode)
//
// Pool/timeout tuning (all optional, env-var-driven only — like DBPool in
// db.go, deployment.yaml documents these but they are never read from YAML,
// keeping a single source of truth for pool sizing):
//
//	REDIS_POOL_SIZE             — default 10
//	REDIS_MIN_IDLE_CONNS        — default 2
//	REDIS_CONN_MAX_IDLE_TIME_SECS — default 300
//	REDIS_CONN_MAX_LIFETIME_SECS  — default 1800 (0 = no limit, explicit opt-out)
//	REDIS_DIAL_TIMEOUT_SECS     — default 5
//	REDIS_READ_TIMEOUT_SECS     — default 3
//	REDIS_WRITE_TIMEOUT_SECS    — default 3
//	REDIS_POOL_TIMEOUT_SECS     — default 4; how long a caller blocks waiting
//	                              for a pooled connection before failing with
//	                              a pool-timeout error (go-redis's default is
//	                              ReadTimeout+1s, made explicit here)
type Redis struct {
	URL      string `yaml:"url"`
	Host     string `yaml:"host"`
	Port     string `yaml:"port"`
	Password string `yaml:"password"`
	DB       int    `yaml:"db"`
	TLS      bool   `yaml:"tls"`

	PoolSize        int           `yaml:"-"`
	MinIdleConns    int           `yaml:"-"`
	ConnMaxIdleTime time.Duration `yaml:"-"`
	ConnMaxLifetime time.Duration `yaml:"-"`
	DialTimeout     time.Duration `yaml:"-"`
	ReadTimeout     time.Duration `yaml:"-"`
	WriteTimeout    time.Duration `yaml:"-"`
	PoolTimeout     time.Duration `yaml:"-"`

	KeyPrefix string `yaml:"key_prefix"`

	SentinelMaster string   `yaml:"sentinel_master"`
	SentinelAddrs  []string `yaml:"sentinel_addrs"`
}

// loadRedis resolves Redis settings using env var > yamlRedis (already
// parsed from deployment.yaml) > compiled-in default, except for the
// pool/timeout fields which are env-var-driven only (see the Redis doc
// comment above).
func loadRedis(yamlRedis Redis) Redis {
	// These fields are never read from yaml (yaml:"-", see the Redis doc
	// comment above), so fromYAML is always 0 — envIntOrConfigOrDefaultSourced
	// collapses to "env wins if positive, else the compiled default", and the
	// reported source is therefore only ever env or default, never yaml.
	poolSize, poolSizeSrc := envIntOrConfigOrDefaultSourced("REDIS_POOL_SIZE", 0, defaultRedisPoolSize)
	minIdle, minIdleSrc := envIntOrConfigOrDefaultSourced("REDIS_MIN_IDLE_CONNS", 0, defaultRedisMinIdleConns)
	idleTimeSecs, idleTimeSrc := envIntOrConfigOrDefaultSourced("REDIS_CONN_MAX_IDLE_TIME_SECS", 0, defaultRedisConnMaxIdleTime)
	idleTime := time.Duration(idleTimeSecs) * time.Second

	// Unlike the fields above, lifetimeSecs has a "0 = no limit" opt-out, so
	// it needs the AllowEnvZero variant (the plain one treats <=0 at every
	// tier as "not set") — an explicit env var of "0" must be honored as-is,
	// while a negative value stays invalid and falls back to the default.
	lifetimeSecs, lifetimeSrc := envIntOrConfigOrDefaultAllowEnvZeroSourced("REDIS_CONN_MAX_LIFETIME_SECS", 0, defaultRedisConnMaxLifetimeSecs)
	lifetime := time.Duration(lifetimeSecs) * time.Second // 0 = no limit

	dialTimeoutSecs, dialTimeoutSrc := envIntOrConfigOrDefaultSourced("REDIS_DIAL_TIMEOUT_SECS", 0, defaultRedisDialTimeoutSecs)
	readTimeoutSecs, readTimeoutSrc := envIntOrConfigOrDefaultSourced("REDIS_READ_TIMEOUT_SECS", 0, defaultRedisReadTimeoutSecs)
	writeTimeoutSecs, writeTimeoutSrc := envIntOrConfigOrDefaultSourced("REDIS_WRITE_TIMEOUT_SECS", 0, defaultRedisWriteTimeoutSecs)
	poolTimeoutSecs, poolTimeoutSrc := envIntOrConfigOrDefaultSourced("REDIS_POOL_TIMEOUT_SECS", 0, defaultRedisPoolTimeoutSecs)
	dialTimeout := time.Duration(dialTimeoutSecs) * time.Second
	readTimeout := time.Duration(readTimeoutSecs) * time.Second
	writeTimeout := time.Duration(writeTimeoutSecs) * time.Second
	poolTimeout := time.Duration(poolTimeoutSecs) * time.Second

	// See the matching "db pool config resolved" line in db.go: report which
	// tier supplied each value, not just the value.
	logResolvedSettings("redis pool config resolved",
		resolvedSetting{"poolSize", poolSize, poolSizeSrc},
		resolvedSetting{"minIdleConns", minIdle, minIdleSrc},
		resolvedSetting{"connMaxIdleTimeSecs", idleTimeSecs, idleTimeSrc},
		resolvedSetting{"connMaxLifetimeSecs", lifetimeSecs, lifetimeSrc},
		resolvedSetting{"dialTimeoutSecs", dialTimeoutSecs, dialTimeoutSrc},
		resolvedSetting{"readTimeoutSecs", readTimeoutSecs, readTimeoutSrc},
		resolvedSetting{"writeTimeoutSecs", writeTimeoutSecs, writeTimeoutSrc},
		resolvedSetting{"poolTimeoutSecs", poolTimeoutSecs, poolTimeoutSrc})

	keyPrefix := envOrConfigOrDefault("REDIS_KEY_PREFIX", yamlRedis.KeyPrefix, defaultRedisKeyPrefix)

	sentinelAddrsRaw := envOrDefault("REDIS_SENTINEL_ADDRS", "")
	var sentinelAddrs []string
	if sentinelAddrsRaw != "" {
		for _, a := range strings.Split(sentinelAddrsRaw, ",") {
			if a = strings.TrimSpace(a); a != "" {
				sentinelAddrs = append(sentinelAddrs, a)
			}
		}
	}
	if len(sentinelAddrs) == 0 {
		sentinelAddrs = yamlRedis.SentinelAddrs
	}

	return Redis{
		URL:             envOrConfigOrDefault("REDIS_URL", yamlRedis.URL, ""),
		Host:            envOrConfigOrDefault("REDIS_HOST", yamlRedis.Host, defaultRedisHost),
		Port:            envOrConfigOrDefault("REDIS_PORT", yamlRedis.Port, defaultRedisPort),
		Password:        envOrConfigOrDefault("REDIS_PASSWORD", yamlRedis.Password, ""),
		DB:              envIntOrConfigOrDefault("REDIS_DB", yamlRedis.DB, defaultRedisDB),
		TLS:             envBoolOrConfig("REDIS_TLS_ENABLED", yamlRedis.TLS),
		PoolSize:        poolSize,
		MinIdleConns:    minIdle,
		ConnMaxIdleTime: idleTime,
		ConnMaxLifetime: lifetime,
		DialTimeout:     dialTimeout,
		ReadTimeout:     readTimeout,
		WriteTimeout:    writeTimeout,
		PoolTimeout:     poolTimeout,
		KeyPrefix:       keyPrefix,
		SentinelMaster:  envOrConfigOrDefault("REDIS_SENTINEL_MASTER", yamlRedis.SentinelMaster, ""),
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
			PoolTimeout:     r.PoolTimeout,
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
		PoolTimeout:     r.PoolTimeout,
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
	opts.PoolTimeout = r.PoolTimeout
	if r.TLS && opts.TLSConfig == nil {
		opts.TLSConfig = &tls.Config{MinVersion: tls.VersionTLS12}
	}
}
