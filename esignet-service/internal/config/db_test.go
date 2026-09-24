/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package config

import (
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

func TestHasDBEnvConfig(t *testing.T) {
	cases := []string{
		"DATABASE_URL", "DATABASE_HOST", "DATABASE_PORT",
		"DATABASE_NAME", "DATABASE_USERNAME", "DATABASE_PASSWORD", "DB_DBUSER_PASSWORD",
	}
	for _, envVar := range cases {
		t.Run(envVar, func(t *testing.T) {
			t.Setenv(envVar, "set")
			require.True(t, hasDBEnvConfig())
		})
	}

	t.Run("none set", func(t *testing.T) {
		for _, envVar := range cases {
			t.Setenv(envVar, "")
		}
		require.False(t, hasDBEnvConfig())
	})
}

func TestEnsurePostgresSSLMode(t *testing.T) {
	cases := []struct {
		name string
		dsn  string
		want string
	}{
		{"adds sslmode with no query", "postgres://user:pass@host/db", "postgres://user:pass@host/db?sslmode=disable"},
		{"adds sslmode with existing query", "postgres://user:pass@host/db?foo=bar", "postgres://user:pass@host/db?foo=bar&sslmode=disable"},
		{"leaves existing sslmode alone", "postgres://user:pass@host/db?sslmode=require", "postgres://user:pass@host/db?sslmode=require"},
		{"leaves non-postgres DSN alone", "host=localhost dbname=x", "host=localhost dbname=x"},
		{"postgresql scheme also matched", "postgresql://user@host/db", "postgresql://user@host/db?sslmode=disable"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			require.Equal(t, tc.want, ensurePostgresSSLMode(tc.dsn))
		})
	}
}

func TestResolveDBDSN_FullURL(t *testing.T) {
	t.Setenv("DATABASE_URL", "postgres://esignet:secret@dbhost:5432/mosip_esignet")

	dsn := resolveDBDSN("")

	require.Equal(t, "postgres://esignet:secret@dbhost:5432/mosip_esignet?sslmode=disable", dsn)
}

func TestResolveDBDSN_IndividualVars(t *testing.T) {
	t.Setenv("DATABASE_HOST", "dbhost")
	t.Setenv("DATABASE_PORT", "5555")
	t.Setenv("DATABASE_NAME", "mydb")
	t.Setenv("DATABASE_USERNAME", "myuser")
	t.Setenv("DATABASE_PASSWORD", "mypass")

	dsn := resolveDBDSN("")

	require.Equal(t, "host=dbhost port=5555 dbname=mydb user=myuser password=mypass sslmode=disable", dsn)
}

func TestResolveDBDSN_DBUserPasswordFallback(t *testing.T) {
	t.Setenv("DATABASE_HOST", "dbhost")
	t.Setenv("DATABASE_PASSWORD", "")
	t.Setenv("DB_DBUSER_PASSWORD", "fallback-pass")

	dsn := resolveDBDSN("")

	require.Contains(t, dsn, "password=fallback-pass")
}

func TestResolveDBDSN_DatabasePasswordTakesPrecedence(t *testing.T) {
	t.Setenv("DATABASE_PASSWORD", "primary-pass")
	t.Setenv("DB_DBUSER_PASSWORD", "fallback-pass")

	dsn := resolveDBDSN("")

	require.Contains(t, dsn, "password=primary-pass")
	require.NotContains(t, dsn, "fallback-pass")
}

func TestResolveDBDSN_NoPasswordOmitsField(t *testing.T) {
	for _, envVar := range []string{
		"DATABASE_URL", "DATABASE_HOST", "DATABASE_PORT", "DATABASE_NAME",
		"DATABASE_USERNAME", "DATABASE_PASSWORD", "DB_DBUSER_PASSWORD",
	} {
		t.Setenv(envVar, "")
	}

	dsn := resolveDBDSN("")

	require.NotContains(t, dsn, "password=")
	require.Contains(t, dsn, "host="+defaultDBHost)
	require.Contains(t, dsn, "port="+defaultDBPort)
	require.Contains(t, dsn, "dbname="+defaultDBName)
	require.Contains(t, dsn, "user="+defaultDBUser)
}

func TestResolveDBDSN_YAMLUsedWhenNoEnvConfig(t *testing.T) {
	for _, envVar := range []string{
		"DATABASE_URL", "DATABASE_HOST", "DATABASE_PORT", "DATABASE_NAME",
		"DATABASE_USERNAME", "DATABASE_PASSWORD", "DB_DBUSER_PASSWORD",
	} {
		t.Setenv(envVar, "")
	}

	dsn := resolveDBDSN("host=yamlhost port=5432 dbname=yamldb user=yamluser sslmode=disable")

	require.Equal(t, "host=yamlhost port=5432 dbname=yamldb user=yamluser sslmode=disable", dsn)
}

func TestResolveDBDSN_EnvTakesPrecedenceOverYAML(t *testing.T) {
	t.Setenv("DATABASE_HOST", "envhost")

	dsn := resolveDBDSN("host=yamlhost port=5432 dbname=yamldb user=yamluser sslmode=disable")

	require.Contains(t, dsn, "host=envhost")
	require.NotContains(t, dsn, "yamlhost")
}

func TestLoadDB_Defaults(t *testing.T) {
	db := loadDB(DB{})

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

	db := loadDB(DB{})

	require.Equal(t, 50, db.Pool.MaxOpenConns)
	require.Equal(t, 10, db.Pool.MaxIdleConns)
	require.Equal(t, 600, db.Pool.ConnMaxLifetimeSecs)
	require.Equal(t, 120, db.Pool.ConnMaxIdleTimeSecs)
}

func TestLoadDB_NonPositivePoolValuesFallBackToDefaults(t *testing.T) {
	t.Setenv("DB_MAX_OPEN_CONNS", "0")
	t.Setenv("DB_MAX_IDLE_CONNS", "-1")

	db := loadDB(DB{})

	require.Equal(t, defaultDBMaxOpenConns, db.Pool.MaxOpenConns)
	require.Equal(t, defaultDBMaxIdleConns, db.Pool.MaxIdleConns)
}

func TestLoadDB_ExplicitZeroLifetimeIsOptOut(t *testing.T) {
	t.Setenv("DB_CONN_MAX_LIFETIME_SECS", "0")

	db := loadDB(DB{})

	require.Zero(t, db.Pool.ConnMaxLifetimeSecs)
}

func TestLoadDB_YAMLPoolValuesUsedWhenEnvUnset(t *testing.T) {
	for _, envVar := range []string{
		"DB_MAX_OPEN_CONNS", "DB_MAX_IDLE_CONNS", "DB_CONN_MAX_LIFETIME_SECS", "DB_CONN_MAX_IDLE_TIME_SECS",
	} {
		t.Setenv(envVar, "")
	}

	db := loadDB(DB{Pool: DBPool{
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

	db := loadDB(DB{Pool: DBPool{MaxOpenConns: 15}})

	require.Equal(t, 77, db.Pool.MaxOpenConns)
}

func TestBuildPoolConfig_MinConnsClampedToMaxConns(t *testing.T) {
	poolCfg, err := buildPoolConfig("postgres://localhost/db", DBPool{
		MaxOpenConns: 5,
		MaxIdleConns: 25, // e.g. left at the default while MaxOpenConns was lowered
	})

	require.NoError(t, err)
	require.EqualValues(t, 5, poolCfg.MaxConns)
	require.EqualValues(t, 5, poolCfg.MinConns, "MinConns must never exceed MaxConns")
}

func TestBuildPoolConfig_MinConnsWithinMaxConnsUnaffected(t *testing.T) {
	poolCfg, err := buildPoolConfig("postgres://localhost/db", DBPool{
		MaxOpenConns: 25,
		MaxIdleConns: 10,
	})

	require.NoError(t, err)
	require.EqualValues(t, 25, poolCfg.MaxConns)
	require.EqualValues(t, 10, poolCfg.MinConns)
}

func TestEffectiveMaxConnLifetime(t *testing.T) {
	require.Equal(t, dbUnlimitedConnLifetime, effectiveMaxConnLifetime(0))
	require.Equal(t, dbUnlimitedConnLifetime, effectiveMaxConnLifetime(-time.Second))
	require.Equal(t, 30*time.Minute, effectiveMaxConnLifetime(30*time.Minute))
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
	db := loadDB(DB{Pool: DBPool{
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

	db := loadDB(DB{Pool: DBPool{MaxOpenConns: 15, ConnMaxLifetimeSecs: 900}})

	require.Equal(t, 15, db.Pool.MaxOpenConns)
	require.Equal(t, 900, db.Pool.ConnMaxLifetimeSecs)
}

// A negative lifetime is not a second, undocumented route to "no limit" — only
// an explicit "0" opts out. This matches loadRedis, which already rejected
// negatives.
func TestLoadDB_NegativeLifetimeFallsBackToDefault(t *testing.T) {
	t.Setenv("DB_CONN_MAX_LIFETIME_SECS", "-5")

	db := loadDB(DB{})

	require.Equal(t, defaultDBConnMaxLifetimeSecs, db.Pool.ConnMaxLifetimeSecs)
}
