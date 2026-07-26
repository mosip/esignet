/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package runtimestores

import (
	"testing"

	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/config"
)

func (ts *InitTestSuite) TestInitializeSelectsInMemoryStoreByDefault() {
	t := ts.T()
	store := Initialize(&config.AppConfig{Identifier: "test"}, nil)
	if store == nil {
		t.Fatal("expected non-nil runtime store")
	}
}

func (ts *InitTestSuite) TestInitializeSelectsInMemoryStoreForUnknownType() {
	t := ts.T()
	store := Initialize(&config.AppConfig{Identifier: "test", RuntimeDBType: "unknown"}, nil)
	if store == nil {
		t.Fatal("expected non-nil runtime store")
	}
}

func (ts *InitTestSuite) TestInitializeSelectsRedisStore() {
	t := ts.T()
	appCfg := &config.AppConfig{Identifier: "test", RuntimeDBType: "redis"}
	appCfg.Redis.KeyPrefix = "esignet:"
	redisClient := redis.NewClient(&redis.Options{Addr: "127.0.0.1:0"})
	defer func() { _ = redisClient.Close() }()

	store := Initialize(appCfg, redisClient)
	if store == nil {
		t.Fatal("expected non-nil runtime store")
	}
}

type InitTestSuite struct {
	suite.Suite
}

func TestInitTestSuite(t *testing.T) {
	suite.Run(t, new(InitTestSuite))
}
