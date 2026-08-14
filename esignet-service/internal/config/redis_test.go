/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package config

import (
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/require"
)

func TestLoadRedis_Defaults(t *testing.T) {
	for _, envVar := range []string{
		"REDIS_URL", "REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD", "REDIS_DB", "REDIS_TLS_ENABLED",
		"REDIS_POOL_SIZE", "REDIS_MIN_IDLE_CONNS", "REDIS_KEY_PREFIX",
		"REDIS_SENTINEL_MASTER", "REDIS_SENTINEL_ADDRS",
		"REDIS_DIAL_TIMEOUT_SECS", "REDIS_READ_TIMEOUT_SECS", "REDIS_WRITE_TIMEOUT_SECS",
		"REDIS_CONN_MAX_IDLE_TIME_SECS", "REDIS_CONN_MAX_LIFETIME_SECS",
	} {
		t.Setenv(envVar, "")
	}

	r := loadRedis()

	require.Equal(t, defaultRedisHost, r.Host)
	require.Equal(t, defaultRedisPort, r.Port)
	require.Equal(t, defaultRedisDB, r.DB)
	require.False(t, r.TLS)
	require.Equal(t, defaultRedisPoolSize, r.PoolSize)
	require.Equal(t, defaultRedisMinIdleConns, r.MinIdleConns)
	require.Equal(t, defaultRedisKeyPrefix, r.KeyPrefix)
	require.Empty(t, r.SentinelMaster)
	require.Empty(t, r.SentinelAddrs)
	require.Equal(t, time.Duration(defaultRedisDialTimeoutSecs)*time.Second, r.DialTimeout)
	require.Equal(t, time.Duration(defaultRedisReadTimeoutSecs)*time.Second, r.ReadTimeout)
	require.Equal(t, time.Duration(defaultRedisWriteTimeoutSecs)*time.Second, r.WriteTimeout)
	require.Equal(t, time.Duration(defaultRedisConnMaxIdleTime)*time.Second, r.ConnMaxIdleTime)
	require.Equal(t, time.Duration(defaultRedisConnMaxLifetimeSecs)*time.Second, r.ConnMaxLifetime)
}

func TestLoadRedis_EnvOverrides(t *testing.T) {
	t.Setenv("REDIS_URL", "redis://:pw@myhost:1234/2")
	t.Setenv("REDIS_HOST", "otherhost")
	t.Setenv("REDIS_PORT", "1111")
	t.Setenv("REDIS_PASSWORD", "secret")
	t.Setenv("REDIS_DB", "5")
	t.Setenv("REDIS_TLS_ENABLED", "true")
	t.Setenv("REDIS_POOL_SIZE", "20")
	t.Setenv("REDIS_MIN_IDLE_CONNS", "4")
	t.Setenv("REDIS_KEY_PREFIX", "custom:")
	t.Setenv("REDIS_DIAL_TIMEOUT_SECS", "1")
	t.Setenv("REDIS_READ_TIMEOUT_SECS", "2")
	t.Setenv("REDIS_WRITE_TIMEOUT_SECS", "3")
	t.Setenv("REDIS_CONN_MAX_IDLE_TIME_SECS", "111")
	t.Setenv("REDIS_CONN_MAX_LIFETIME_SECS", "222")
	t.Setenv("REDIS_SENTINEL_MASTER", "mymaster")
	t.Setenv("REDIS_SENTINEL_ADDRS", "a:1,b:2, ,c:3")

	r := loadRedis()

	require.Equal(t, "redis://:pw@myhost:1234/2", r.URL)
	require.Equal(t, "otherhost", r.Host)
	require.Equal(t, "1111", r.Port)
	require.Equal(t, "secret", r.Password)
	require.Equal(t, 5, r.DB)
	require.True(t, r.TLS)
	require.Equal(t, 20, r.PoolSize)
	require.Equal(t, 4, r.MinIdleConns)
	require.Equal(t, "custom:", r.KeyPrefix)
	require.Equal(t, time.Second, r.DialTimeout)
	require.Equal(t, 2*time.Second, r.ReadTimeout)
	require.Equal(t, 3*time.Second, r.WriteTimeout)
	require.Equal(t, 111*time.Second, r.ConnMaxIdleTime)
	require.Equal(t, 222*time.Second, r.ConnMaxLifetime)
	require.Equal(t, "mymaster", r.SentinelMaster)
	require.Equal(t, []string{"a:1", "b:2", "c:3"}, r.SentinelAddrs)
}

func TestLoadRedis_ZeroLifetimeOptOut(t *testing.T) {
	t.Setenv("REDIS_CONN_MAX_LIFETIME_SECS", "0")
	r := loadRedis()
	require.Equal(t, time.Duration(0), r.ConnMaxLifetime)
}

func TestLoadRedis_NonPositivePoolValuesFallBackToDefaults(t *testing.T) {
	t.Setenv("REDIS_POOL_SIZE", "0")
	t.Setenv("REDIS_MIN_IDLE_CONNS", "-1")

	r := loadRedis()

	require.Equal(t, defaultRedisPoolSize, r.PoolSize)
	require.Equal(t, defaultRedisMinIdleConns, r.MinIdleConns)
}

func TestRedisNewClient_FullDSN(t *testing.T) {
	r := Redis{URL: "redis://:pw@dsnhost:9999/3", PoolSize: 7, MinIdleConns: 2}

	client, err := r.newClient()
	require.NoError(t, err)
	defer func() { _ = client.Close() }()

	opts := client.Options()
	require.Equal(t, "dsnhost:9999", opts.Addr)
	require.Equal(t, "pw", opts.Password)
	require.Equal(t, 3, opts.DB)
	require.Equal(t, 7, opts.PoolSize)
}

func TestRedisNewClient_InvalidURL(t *testing.T) {
	r := Redis{URL: "not-a-valid-url://::"}

	_, err := r.newClient()
	require.Error(t, err)
}

func TestRedisNewClient_Sentinel(t *testing.T) {
	r := Redis{
		SentinelMaster: "mymaster",
		SentinelAddrs:  []string{"s1:26379", "s2:26379"},
		DB:             1,
		PoolSize:       5,
		TLS:            true,
	}

	client, err := r.newClient()
	require.NoError(t, err)
	defer func() { _ = client.Close() }()
}

func TestRedisNewClient_SingleNode(t *testing.T) {
	r := Redis{Host: "singlehost", Port: "6390", DB: 4, PoolSize: 9}

	client, err := r.newClient()
	require.NoError(t, err)
	defer func() { _ = client.Close() }()

	opts := client.Options()
	require.Equal(t, "singlehost:6390", opts.Addr)
	require.Equal(t, 4, opts.DB)
	require.Equal(t, 9, opts.PoolSize)
	require.Nil(t, opts.TLSConfig)
}

func TestRedisNewClient_SingleNodeTLS(t *testing.T) {
	r := Redis{Host: "singlehost", Port: "6390", TLS: true}

	client, err := r.newClient()
	require.NoError(t, err)
	defer func() { _ = client.Close() }()

	require.NotNil(t, client.Options().TLSConfig)
}

func TestRedisApplyPool(t *testing.T) {
	r := Redis{
		PoolSize:        11,
		MinIdleConns:    3,
		ConnMaxIdleTime: 5 * time.Second,
		ConnMaxLifetime: 6 * time.Second,
		DialTimeout:     time.Second,
		ReadTimeout:     2 * time.Second,
		WriteTimeout:    3 * time.Second,
		TLS:             true,
	}
	opts := &redis.Options{}
	r.applyPool(opts)

	require.Equal(t, 11, opts.PoolSize)
	require.Equal(t, 3, opts.MinIdleConns)
	require.NotNil(t, opts.TLSConfig)
}
