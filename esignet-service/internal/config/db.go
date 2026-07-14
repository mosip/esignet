/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package config

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"strings"
	"time"

	_ "github.com/lib/pq" // PostgreSQL driver for database/sql
)

const (
	defaultDBHost                = "localhost"
	defaultDBPort                = "5455"
	defaultDBName                = "mosip_esignet"
	defaultDBUser                = "postgres"
	defaultDBMaxOpenConns        = 25
	defaultDBMaxIdleConns        = 5
	defaultDBConnMaxLifetimeSecs = 0 // no limit
	defaultDBConnMaxIdleTimeSecs = 60
	dbPingTimeout                = 5 * time.Second
)

// DBPool holds connection pool tuning parameters.
type DBPool struct {
	MaxOpenConns    int           `yaml:"max_open_conns"`
	MaxIdleConns    int           `yaml:"max_idle_conns"`
	ConnMaxLifetime time.Duration `yaml:"conn_max_lifetime"`
	ConnMaxIdleTime time.Duration `yaml:"conn_max_idle_time"`
}

// DB holds Postgres connection settings.
type DB struct {
	DSN  string `yaml:"dsn"`
	Pool DBPool `yaml:"pool"`
}

func hasDBEnvConfig() bool {
	return os.Getenv("DATABASE_URL") != "" ||
		os.Getenv("DATABASE_HOST") != "" ||
		os.Getenv("DATABASE_PORT") != "" ||
		os.Getenv("DATABASE_NAME") != "" ||
		os.Getenv("DATABASE_USERNAME") != "" ||
		os.Getenv("DATABASE_PASSWORD") != "" ||
		os.Getenv("DB_DBUSER_PASSWORD") != ""
}

func ensurePostgresSSLMode(dsn string) string {
	if (strings.HasPrefix(dsn, "postgres://") || strings.HasPrefix(dsn, "postgresql://")) &&
		!strings.Contains(strings.ToLower(dsn), "sslmode=") {
		sep := "?"
		if strings.Contains(dsn, "?") {
			sep = "&"
		}
		return dsn + sep + "sslmode=disable"
	}
	return dsn
}

func resolveDBDSN() string {
	if dsn := os.Getenv("DATABASE_URL"); dsn != "" {
		return ensurePostgresSSLMode(dsn)
	}

	host := envOrDefault("DATABASE_HOST", defaultDBHost)
	port := envOrDefault("DATABASE_PORT", defaultDBPort)
	dbname := envOrDefault("DATABASE_NAME", defaultDBName)
	user := envOrDefault("DATABASE_USERNAME", defaultDBUser)
	password := os.Getenv("DATABASE_PASSWORD")
	if password == "" {
		password = os.Getenv("DB_DBUSER_PASSWORD")
	}
	if password != "" {
		return fmt.Sprintf(
			"host=%s port=%s dbname=%s user=%s password=%s sslmode=disable",
			host, port, dbname, user, password,
		)
	}
	// Omit password= when unset — lib/pq mis-parses "password= sslmode=..."
	// and falls back to SSL, which fails against local Docker Postgres.
	return fmt.Sprintf(
		"host=%s port=%s dbname=%s user=%s sslmode=disable",
		host, port, dbname, user,
	)
}

// loadDB reads Postgres connection config and pool settings from the environment.
// Accepts POSTGRES_URL or DATABASE_URL (full DSN), or individual vars:
// DATABASE_HOST, DATABASE_PORT, DATABASE_NAME, DATABASE_USERNAME, DATABASE_PASSWORD.
//
// Pool tuning (all optional):
//
//	DB_MAX_OPEN_CONNS         — default 25
//	DB_MAX_IDLE_CONNS         — default 5
//	DB_CONN_MAX_LIFETIME_SECS — default 300
//	DB_CONN_MAX_IDLE_TIME_SECS — default 60
func loadDB() DB {
	dsn := resolveDBDSN()

	maxOpen := envIntOrDefault("DB_MAX_OPEN_CONNS", defaultDBMaxOpenConns)
	if maxOpen <= 0 {
		maxOpen = defaultDBMaxOpenConns
	}
	maxIdle := envIntOrDefault("DB_MAX_IDLE_CONNS", defaultDBMaxIdleConns)
	if maxIdle <= 0 {
		maxIdle = defaultDBMaxIdleConns
	}
	lifetimeSecs := envIntOrDefault("DB_CONN_MAX_LIFETIME_SECS", defaultDBConnMaxLifetimeSecs)
	var lifetime time.Duration
	if lifetimeSecs > 0 {
		lifetime = time.Duration(lifetimeSecs) * time.Second
	} else {
		lifetime = defaultDBConnMaxLifetimeSecs
	}
	idleSecs := envIntOrDefault("DB_CONN_MAX_IDLE_TIME_SECS", defaultDBConnMaxIdleTimeSecs)
	var idleTime time.Duration
	if idleSecs > 0 {
		idleTime = time.Duration(idleSecs) * time.Second
	} else {
		idleTime = defaultDBConnMaxIdleTimeSecs
	}

	return DB{
		DSN: dsn,
		Pool: DBPool{
			MaxOpenConns:    maxOpen,
			MaxIdleConns:    maxIdle,
			ConnMaxLifetime: lifetime,
			ConnMaxIdleTime: idleTime,
		},
	}
}

// Open opens, configures the pool, and pings the Postgres connection.
func (d DB) Open() (*sql.DB, error) {
	conn, err := sql.Open("postgres", d.DSN)
	if err != nil {
		return nil, fmt.Errorf("open postgres: %w", err)
	}

	conn.SetMaxOpenConns(d.Pool.MaxOpenConns)
	conn.SetMaxIdleConns(d.Pool.MaxIdleConns)
	conn.SetConnMaxLifetime(d.Pool.ConnMaxLifetime)
	conn.SetConnMaxIdleTime(d.Pool.ConnMaxIdleTime)

	ctx, cancel := context.WithTimeout(context.Background(), dbPingTimeout)
	defer cancel()
	if err := conn.PingContext(ctx); err != nil {
		_ = conn.Close()
		return nil, fmt.Errorf("ping postgres: %w", err)
	}
	return conn, nil
}
