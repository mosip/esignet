/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

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
