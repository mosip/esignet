// Package redisstore provides a Redis-backed runtime store implementation.
package redisstore

import (
	"github.com/redis/go-redis/v9"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

// Initialize creates and returns a new RedisStore instance for the given deployment.
func Initialize(deploymentID string, keyPrefix string, redisClient *redis.Client) (providers.RuntimeStoreProvider, error) {
	return newRedisStore(deploymentID, keyPrefix, redisClient), nil
}
