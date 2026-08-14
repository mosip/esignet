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

	dsn := resolveDBDSN()

	require.Equal(t, "postgres://esignet:secret@dbhost:5432/mosip_esignet?sslmode=disable", dsn)
}

func TestResolveDBDSN_IndividualVars(t *testing.T) {
	t.Setenv("DATABASE_HOST", "dbhost")
	t.Setenv("DATABASE_PORT", "5555")
	t.Setenv("DATABASE_NAME", "mydb")
	t.Setenv("DATABASE_USERNAME", "myuser")
	t.Setenv("DATABASE_PASSWORD", "mypass")

	dsn := resolveDBDSN()

	require.Equal(t, "host=dbhost port=5555 dbname=mydb user=myuser password=mypass sslmode=disable", dsn)
}

func TestResolveDBDSN_DBUserPasswordFallback(t *testing.T) {
	t.Setenv("DATABASE_HOST", "dbhost")
	t.Setenv("DATABASE_PASSWORD", "")
	t.Setenv("DB_DBUSER_PASSWORD", "fallback-pass")

	dsn := resolveDBDSN()

	require.Contains(t, dsn, "password=fallback-pass")
}

func TestResolveDBDSN_DatabasePasswordTakesPrecedence(t *testing.T) {
	t.Setenv("DATABASE_PASSWORD", "primary-pass")
	t.Setenv("DB_DBUSER_PASSWORD", "fallback-pass")

	dsn := resolveDBDSN()

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

	dsn := resolveDBDSN()

	require.NotContains(t, dsn, "password=")
	require.Contains(t, dsn, "host="+defaultDBHost)
	require.Contains(t, dsn, "port="+defaultDBPort)
	require.Contains(t, dsn, "dbname="+defaultDBName)
	require.Contains(t, dsn, "user="+defaultDBUser)
}

func TestLoadDB_Defaults(t *testing.T) {
	db := loadDB()

	require.Equal(t, defaultDBMaxOpenConns, db.Pool.MaxOpenConns)
	require.Equal(t, defaultDBMaxIdleConns, db.Pool.MaxIdleConns)
	require.Equal(t, defaultDBConnMaxLifetimeSecs, int(db.Pool.ConnMaxLifetime.Seconds()))
	require.Equal(t, defaultDBConnMaxIdleTimeSecs, int(db.Pool.ConnMaxIdleTime.Seconds()))
}

func TestLoadDB_PoolTuningOverrides(t *testing.T) {
	t.Setenv("DB_MAX_OPEN_CONNS", "50")
	t.Setenv("DB_MAX_IDLE_CONNS", "10")
	t.Setenv("DB_CONN_MAX_LIFETIME_SECS", "600")
	t.Setenv("DB_CONN_MAX_IDLE_TIME_SECS", "120")

	db := loadDB()

	require.Equal(t, 50, db.Pool.MaxOpenConns)
	require.Equal(t, 10, db.Pool.MaxIdleConns)
	require.Equal(t, 600, int(db.Pool.ConnMaxLifetime.Seconds()))
	require.Equal(t, 120, int(db.Pool.ConnMaxIdleTime.Seconds()))
}

func TestLoadDB_ZeroLifetimeOptOut(t *testing.T) {
	t.Setenv("DB_CONN_MAX_LIFETIME_SECS", "0")
	db := loadDB()
	require.Equal(t, time.Duration(0), db.Pool.ConnMaxLifetime)
}

func TestLoadDB_NonPositivePoolValuesFallBackToDefaults(t *testing.T) {
	t.Setenv("DB_MAX_OPEN_CONNS", "0")
	t.Setenv("DB_MAX_IDLE_CONNS", "-1")

	db := loadDB()

	require.Equal(t, defaultDBMaxOpenConns, db.Pool.MaxOpenConns)
	require.Equal(t, defaultDBMaxIdleConns, db.Pool.MaxIdleConns)
}
