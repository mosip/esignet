/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package config

import (
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/persistence"
)

func TestLoadDB_Defaults(t *testing.T) {
	db := loadDB(persistence.DB{})

	require.Equal(t, defaultDBMaxOpenConns, db.Pool.MaxOpenConns)
	require.Equal(t, defaultDBMaxIdleConns, db.Pool.MaxIdleConns)
	require.Equal(t, defaultDBConnMaxLifetimeSecs, db.Pool.ConnMaxLifetimeSecs)
	require.Equal(t, defaultDBConnMaxIdleTimeSecs, db.Pool.ConnMaxIdleTimeSecs)
}

func TestLoadDB_PoolTuningOverrides(t *testing.T) {
	t.Setenv("DB_MAX_OPEN_CONNS", "50")
	t.Setenv("DB_MAX_IDLE_CONNS", "10")
	t.Setenv("DB_CONN_MAX_LIFETIME_SECS", "600")
	t.Setenv("DB_CONN_MAX_IDLE_TIME_SECS", "120")

	db := loadDB(persistence.DB{})

	require.Equal(t, 50, db.Pool.MaxOpenConns)
	require.Equal(t, 10, db.Pool.MaxIdleConns)
	require.Equal(t, 600, db.Pool.ConnMaxLifetimeSecs)
	require.Equal(t, 120, db.Pool.ConnMaxIdleTimeSecs)
}

func TestLoadDB_NonPositivePoolValuesFallBackToDefaults(t *testing.T) {
	t.Setenv("DB_MAX_OPEN_CONNS", "0")
	t.Setenv("DB_MAX_IDLE_CONNS", "-1")

	db := loadDB(persistence.DB{})

	require.Equal(t, defaultDBMaxOpenConns, db.Pool.MaxOpenConns)
	require.Equal(t, defaultDBMaxIdleConns, db.Pool.MaxIdleConns)
}

func TestLoadDB_ExplicitZeroLifetimeIsOptOut(t *testing.T) {
	t.Setenv("DB_CONN_MAX_LIFETIME_SECS", "0")

	db := loadDB(persistence.DB{})

	require.Zero(t, db.Pool.ConnMaxLifetimeSecs)
}

func TestLoadDB_YAMLPoolValuesUsedWhenEnvUnset(t *testing.T) {
	for _, envVar := range []string{
		"DB_MAX_OPEN_CONNS", "DB_MAX_IDLE_CONNS", "DB_CONN_MAX_LIFETIME_SECS", "DB_CONN_MAX_IDLE_TIME_SECS",
	} {
		t.Setenv(envVar, "")
	}

	db := loadDB(persistence.DB{Pool: persistence.DBPool{
		MaxOpenConns:        15,
		MaxIdleConns:        5,
		ConnMaxLifetimeSecs: 900,
		ConnMaxIdleTimeSecs: 60,
	}})

	require.Equal(t, 15, db.Pool.MaxOpenConns)
	require.Equal(t, 5, db.Pool.MaxIdleConns)
	require.Equal(t, 900, db.Pool.ConnMaxLifetimeSecs)
	require.Equal(t, 60, db.Pool.ConnMaxIdleTimeSecs)
}

func TestLoadDB_EnvPoolValuesTakePrecedenceOverYAML(t *testing.T) {
	t.Setenv("DB_MAX_OPEN_CONNS", "77")

	db := loadDB(persistence.DB{Pool: persistence.DBPool{MaxOpenConns: 15}})

	require.Equal(t, 77, db.Pool.MaxOpenConns)
}

// Regression lock for issue #2498: the shipped data/deployment.yaml ships a
// populated db.pool block, so every deployment has yaml values sitting ready
// to shadow an operator's env var. They must not win.
func TestLoadDB_EnvWinsOverPopulatedYAMLPool(t *testing.T) {
	t.Setenv("DB_MAX_OPEN_CONNS", "50")
	t.Setenv("DB_MAX_IDLE_CONNS", "9")
	t.Setenv("DB_CONN_MAX_LIFETIME_SECS", "600")
	t.Setenv("DB_CONN_MAX_IDLE_TIME_SECS", "120")

	// Mirrors the values shipped in data/deployment.yaml.
	db := loadDB(persistence.DB{Pool: persistence.DBPool{
		MaxOpenConns:        25,
		MaxIdleConns:        5,
		ConnMaxLifetimeSecs: 1800,
		ConnMaxIdleTimeSecs: 300,
	}})

	require.Equal(t, 50, db.Pool.MaxOpenConns)
	require.Equal(t, 9, db.Pool.MaxIdleConns)
	require.Equal(t, 600, db.Pool.ConnMaxLifetimeSecs)
	require.Equal(t, 120, db.Pool.ConnMaxIdleTimeSecs)
}

// An env var that is set but unusable falls back to yaml rather than taking
// effect — and, unlike before, says so via a WARN (see warnIgnoredEnvVar).
func TestLoadDB_InvalidEnvFallsBackToYAML(t *testing.T) {
	t.Setenv("DB_MAX_OPEN_CONNS", "abc")
	t.Setenv("DB_CONN_MAX_LIFETIME_SECS", "not-a-number")

	db := loadDB(persistence.DB{Pool: persistence.DBPool{MaxOpenConns: 15, ConnMaxLifetimeSecs: 900}})

	require.Equal(t, 15, db.Pool.MaxOpenConns)
	require.Equal(t, 900, db.Pool.ConnMaxLifetimeSecs)
}

// A negative lifetime is not a second, undocumented route to "no limit" — only
// an explicit "0" opts out. This matches loadRedis, which already rejected
// negatives.
func TestLoadDB_NegativeLifetimeFallsBackToDefault(t *testing.T) {
	t.Setenv("DB_CONN_MAX_LIFETIME_SECS", "-5")

	db := loadDB(persistence.DB{})

	require.Equal(t, defaultDBConnMaxLifetimeSecs, db.Pool.ConnMaxLifetimeSecs)
}

func clearDBEnv(t *testing.T) {
	t.Helper()
	for _, envVar := range []string{
		"DATABASE_URL", "DATABASE_HOST", "DATABASE_PORT", "DATABASE_NAME",
		"DATABASE_USERNAME", "DATABASE_PASSWORD", "DB_DBUSER_PASSWORD",
	} {
		t.Setenv(envVar, "")
	}
}

func TestLoadDB_DriverDefaultsToPostgres(t *testing.T) {
	t.Setenv("DB_DRIVER", "")

	db := loadDB(persistence.DB{})

	require.Equal(t, persistence.DriverPostgres, db.Driver)
}

func TestLoadDB_DriverFromEnvBuildsMySQLDSN(t *testing.T) {
	clearDBEnv(t)
	t.Setenv("DB_DRIVER", "mysql")

	db := loadDB(persistence.DB{})

	require.Equal(t, persistence.DriverMySQL, db.Driver)
	require.Contains(t, db.DSN, "@tcp(")
	require.Contains(t, db.DSN, "parseTime=true")
}

func TestLoadDB_DriverNormalizedAndEnvOverridesYAML(t *testing.T) {
	t.Setenv("DB_DRIVER", "MySQL")

	db := loadDB(persistence.DB{Driver: "postgres"})

	require.Equal(t, persistence.DriverMySQL, db.Driver)
}
