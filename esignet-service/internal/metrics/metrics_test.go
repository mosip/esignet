/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package metrics

import (
	"database/sql"
	"database/sql/driver"
	"errors"
	"io"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// fakeDriver is a minimal database/sql driver that never actually connects.
// sql.Open() only registers a name/dsn pair lazily, and *sql.DB.Stats() reads
// pool counters without requiring a live connection, so this is sufficient
// to exercise RegisterDBStats without a real database.
type fakeDriver struct{}

func (fakeDriver) Open(_ string) (driver.Conn, error) {
	return nil, errors.New("fakeDriver: connections are not supported")
}

func init() {
	sql.Register("esignet-metrics-fake-driver", fakeDriver{})
}

func scrape(t *testing.T) string {
	t.Helper()

	req := httptest.NewRequest("GET", "/metrics", nil)
	rr := httptest.NewRecorder()
	Handler().ServeHTTP(rr, req)

	require.Equal(t, 200, rr.Code)

	body, err := io.ReadAll(rr.Result().Body)
	require.NoError(t, err)

	return string(body)
}

func TestHandler_ServesPrometheusTextFormat(t *testing.T) {
	body := scrape(t)

	assert.Contains(t, body, "go_goroutines")
}

func TestRegisterDBStats_ExposesPoolGauges(t *testing.T) {
	db, err := sql.Open("esignet-metrics-fake-driver", "unused-dsn")
	require.NoError(t, err)
	t.Cleanup(func() { _ = db.Close() })

	RegisterDBStats(db)

	body := scrape(t)

	for _, metric := range []string{
		"esignet_db_open_connections",
		"esignet_db_in_use",
		"esignet_db_idle",
		"esignet_db_wait_count_total",
		"esignet_db_wait_duration_seconds_total",
	} {
		assert.Contains(t, body, metric, "expected %s to be registered", metric)
	}

	assert.True(t, strings.Contains(body, "esignet_db_open_connections 0"))
}

func TestRegisterRedisStats_ExposesPoolGauges(t *testing.T) {
	client := redis.NewClient(&redis.Options{Addr: "127.0.0.1:0"})
	t.Cleanup(func() { _ = client.Close() })

	RegisterRedisStats(client)

	body := scrape(t)

	for _, metric := range []string{
		"esignet_redis_total_conns",
		"esignet_redis_idle_conns",
		"esignet_redis_pool_timeouts_total",
		"esignet_redis_stale_conns_total",
	} {
		assert.Contains(t, body, metric, "expected %s to be registered", metric)
	}
}
