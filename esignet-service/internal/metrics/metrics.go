/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package metrics

import (
	"database/sql"
	"net/http"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/redis/go-redis/v9"
)

// Handler returns the HTTP handler that serves Prometheus text-format metrics.
func Handler() http.Handler {
	return promhttp.Handler()
}

// RegisterDBStats registers five GaugeFunc metrics exposing the sql.DB pool state.
// Call once after pgConn is open.
func RegisterDBStats(db *sql.DB) {
	prometheus.MustRegister(
		prometheus.NewGaugeFunc(prometheus.GaugeOpts{
			Name: "esignet_db_open_connections",
			Help: "Current number of open connections to the database.",
		}, func() float64 { return float64(db.Stats().OpenConnections) }),

		prometheus.NewGaugeFunc(prometheus.GaugeOpts{
			Name: "esignet_db_in_use",
			Help: "Current number of connections currently in use.",
		}, func() float64 { return float64(db.Stats().InUse) }),

		prometheus.NewGaugeFunc(prometheus.GaugeOpts{
			Name: "esignet_db_idle",
			Help: "Current number of idle connections.",
		}, func() float64 { return float64(db.Stats().Idle) }),

		prometheus.NewGaugeFunc(prometheus.GaugeOpts{
			Name: "esignet_db_wait_count",
			Help: "Total number of connections waited for.",
		}, func() float64 { return float64(db.Stats().WaitCount) }),

		prometheus.NewGaugeFunc(prometheus.GaugeOpts{
			Name: "esignet_db_wait_duration_seconds",
			Help: "Total time blocked waiting for a new connection, in seconds.",
		}, func() float64 { return db.Stats().WaitDuration.Seconds() }),
	)
}

// RegisterRedisStats registers four GaugeFunc metrics exposing the redis.Client pool state.
// Call once after redisClient is open (only when RuntimeDBType == "redis").
func RegisterRedisStats(c *redis.Client) {
	prometheus.MustRegister(
		prometheus.NewGaugeFunc(prometheus.GaugeOpts{
			Name: "esignet_redis_total_conns",
			Help: "Total number of connections in the Redis pool.",
		}, func() float64 { return float64(c.PoolStats().TotalConns) }),

		prometheus.NewGaugeFunc(prometheus.GaugeOpts{
			Name: "esignet_redis_idle_conns",
			Help: "Number of idle connections in the Redis pool.",
		}, func() float64 { return float64(c.PoolStats().IdleConns) }),

		prometheus.NewGaugeFunc(prometheus.GaugeOpts{
			Name: "esignet_redis_pool_timeouts",
			Help: "Number of times a wait timeout occurred in the Redis pool.",
		}, func() float64 { return float64(c.PoolStats().Timeouts) }),

		prometheus.NewGaugeFunc(prometheus.GaugeOpts{
			Name: "esignet_redis_stale_conns",
			Help: "Number of stale connections removed from the Redis pool.",
		}, func() float64 { return float64(c.PoolStats().StaleConns) }),
	)
}
