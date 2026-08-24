/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package metrics registers Prometheus collectors for the esignet service and
// exposes the HTTP handler used by the private metrics listener.
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

// RegisterDBStats registers Prometheus collectors for the sql.DB pool.
// OpenConnections, InUse, and Idle are gauges (point-in-time pool state).
// WaitCount and WaitDuration are counters (monotonically increasing totals).
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

		prometheus.NewCounterFunc(prometheus.CounterOpts{
			Name: "esignet_db_wait_count_total",
			Help: "Total number of times a caller waited for a database connection.",
		}, func() float64 { return float64(db.Stats().WaitCount) }),

		prometheus.NewCounterFunc(prometheus.CounterOpts{
			Name: "esignet_db_wait_duration_seconds_total",
			Help: "Total time blocked waiting for a new database connection, in seconds.",
		}, func() float64 { return db.Stats().WaitDuration.Seconds() }),
	)
}

// RegisterRedisStats registers Prometheus collectors for the redis.Client pool.
// TotalConns and IdleConns are gauges (point-in-time pool state).
// Timeouts and StaleConns are counters (monotonically increasing totals).
// Call once after redisClient is open (only when RuntimeDBType == "redis").
func RegisterRedisStats(c *redis.Client) {
	prometheus.MustRegister(
		prometheus.NewGaugeFunc(prometheus.GaugeOpts{
			Name: "esignet_redis_total_conns",
			Help: "Current total number of connections in the Redis pool.",
		}, func() float64 { return float64(c.PoolStats().TotalConns) }),

		prometheus.NewGaugeFunc(prometheus.GaugeOpts{
			Name: "esignet_redis_idle_conns",
			Help: "Current number of idle connections in the Redis pool.",
		}, func() float64 { return float64(c.PoolStats().IdleConns) }),

		prometheus.NewCounterFunc(prometheus.CounterOpts{
			Name: "esignet_redis_pool_timeouts_total",
			Help: "Total number of times a wait timeout occurred in the Redis pool.",
		}, func() float64 { return float64(c.PoolStats().Timeouts) }),

		prometheus.NewCounterFunc(prometheus.CounterOpts{
			Name: "esignet_redis_stale_conns_total",
			Help: "Total number of stale connections removed from the Redis pool.",
		}, func() float64 { return float64(c.PoolStats().StaleConns) }),
	)
}
