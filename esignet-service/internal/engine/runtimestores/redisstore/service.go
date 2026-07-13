/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package redisstore

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"

	applog "github.com/mosip/esignet/internal/log"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

// redisClient is the minimal Redis API needed by redisStore.
type redisClient interface {
	Set(ctx context.Context, key string, value any, expiration time.Duration) *redis.StatusCmd
	Get(ctx context.Context, key string) *redis.StringCmd
	SetArgs(ctx context.Context, key string, value any, a redis.SetArgs) *redis.StatusCmd
	Del(ctx context.Context, keys ...string) *redis.IntCmd
	GetDel(ctx context.Context, key string) *redis.StringCmd
	Expire(ctx context.Context, key string, expiration time.Duration) *redis.BoolCmd
}

// keyFormat is the format string used to build Redis store keys.
const keyFormat = "%s:runtime:%s:%s:%s"

// redisStore implements the RuntimeStoreProvider interface using Redis as the backend.
type redisStore struct {
	keyPrefix    string
	deploymentID string
	client       redisClient
	logger       *applog.Logger
}

func newRedisStore(deploymentID string, keyprefix string, redisClient *redis.Client) providers.RuntimeStoreProvider {
	return &redisStore{
		keyPrefix:    keyprefix,
		deploymentID: deploymentID,
		client:       redisClient,
		logger:       applog.GetLogger(),
	}
}

// Put stores a value in the Redis store with the specified TTL.
func (r *redisStore) Put(ctx context.Context, namespace providers.RuntimeStoreNamespace,
	key string, value []byte, ttlSeconds int64) error {
	ttl := time.Duration(0)
	if ttlSeconds > 0 {
		ttl = time.Duration(ttlSeconds) * time.Second
	}
	if err := r.client.Set(ctx, r.getFormattedKey(namespace, key), value, ttl).Err(); err != nil {
		return fmt.Errorf("failed to store in Redis: %w", err)
	}

	r.logger.Debug("Stored in Redis", applog.String("key", key))
	return nil
}

// Get retrieves a value from the Redis store by its key.
func (r *redisStore) Get(ctx context.Context, namespace providers.RuntimeStoreNamespace,
	key string) ([]byte, error) {
	data, err := r.client.Get(ctx, r.getFormattedKey(namespace, key)).Bytes()
	if err != nil {
		if errors.Is(err, redis.Nil) {
			return nil, nil
		}
		return nil, fmt.Errorf("failed to get data from Redis: %w", err)
	}
	return data, nil
}

// Update updates the value associated with a key in the Redis store, preserving its TTL.
func (r *redisStore) Update(ctx context.Context, namespace providers.RuntimeStoreNamespace,
	key string, value []byte) error {
	formattedKey := r.getFormattedKey(namespace, key)
	err := r.client.SetArgs(ctx, formattedKey, value, redis.SetArgs{
		Mode:    "XX",
		KeepTTL: true,
	}).Err()
	if err != nil {
		if errors.Is(err, redis.Nil) {
			return providers.ErrRuntimeStoreKeyNotFound
		}
		return fmt.Errorf("failed to update in Redis: %w", err)
	}
	return nil
}

// Delete removes a value from the Redis store by its key.
func (r *redisStore) Delete(ctx context.Context, namespace providers.RuntimeStoreNamespace,
	key string) error {
	if err := r.client.Del(ctx, r.getFormattedKey(namespace, key)).Err(); err != nil {
		return fmt.Errorf("failed to delete from Redis: %w", err)
	}
	return nil
}

// Take retrieves and removes a value from the Redis store by its key.
func (r *redisStore) Take(ctx context.Context, namespace providers.RuntimeStoreNamespace,
	key string) ([]byte, error) {
	data, err := r.client.GetDel(ctx, r.getFormattedKey(namespace, key)).Bytes()
	if err != nil {
		if errors.Is(err, redis.Nil) {
			return nil, nil
		}
		return nil, fmt.Errorf("failed to take data from Redis: %w", err)
	}

	r.logger.Debug("Taken from Redis", applog.String("key", key))
	return data, nil
}

// ExtendTTL extends the TTL of an existing entry in the Redis store.
func (r *redisStore) ExtendTTL(ctx context.Context, namespace providers.RuntimeStoreNamespace,
	key string, ttlSeconds int64) error {
	formattedKey := r.getFormattedKey(namespace, key)
	ttl := time.Duration(ttlSeconds) * time.Second
	ok, err := r.client.Expire(ctx, formattedKey, ttl).Result()
	if err != nil {
		return fmt.Errorf("failed to extend TTL in Redis: %w", err)
	}
	if !ok {
		return providers.ErrRuntimeStoreKeyNotFound
	}
	return nil
}

// getFormattedKey builds the Redis key.
func (r *redisStore) getFormattedKey(namespace providers.RuntimeStoreNamespace, key string) string {
	return fmt.Sprintf(keyFormat, r.keyPrefix, r.deploymentID, namespace, key)
}
