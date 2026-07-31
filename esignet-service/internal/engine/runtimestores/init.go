/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package runtimestores selects and initializes the runtime store backend for the engine.
package runtimestores

import (
	"context"

	"github.com/redis/go-redis/v9"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/runtimestores/inmemory"
	"github.com/mosip/esignet/internal/engine/runtimestores/redisstore"
	applog "github.com/mosip/esignet/internal/log"
)

// Initialize selects and initializes the runtime store backend based on the configured RuntimeDBType.
func Initialize(appCfg *config.AppConfig, redisClient *redis.Client) providers.RuntimeStoreProvider {
	logger := applog.GetLogger()
	if appCfg.RuntimeDBType == "redis" {
		store, err := redisstore.Initialize(appCfg.Identifier, appCfg.Redis.KeyPrefix, redisClient)
		if err != nil {
			logger.Fatal("Failed to initialize redis store", applog.Error(err))
		}
		logger.Info(context.Background(), "runtime store initialized", applog.String("type", "redis"))
		return store
	}

	logger.Warn(context.Background(), "runtime store initialized", applog.String("type", "in-memory"),
		applog.String("note", "not shared across replicas; use redis for multi-instance deployments"))
	return inmemory.Initialize(appCfg.Identifier)
}
