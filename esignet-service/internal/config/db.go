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
	defaultDBMaxOpenConns    = 25
	defaultDBMaxIdleConns    = 5
	defaultDBConnMaxLifetime = 5 * time.Minute
	defaultDBConnMaxIdleTime = 1 * time.Minute
	dbPingTimeout            = 5 * time.Second
)

// DBPool holds connection pool tuning parameters.
type DBPool struct {
	MaxOpenConns    int
	MaxIdleConns    int
	ConnMaxLifetime time.Duration
	ConnMaxIdleTime time.Duration
}

// DB holds Postgres connection settings.
type DB struct {
	DSN  string
	Pool DBPool
}

// LoadDB reads Postgres connection config and pool settings from the environment.
// Accepts either POSTGRES_URL (full DSN) or individual vars:
// POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD.
//
// Pool tuning (all optional):
//
//	DB_MAX_OPEN_CONNS         — default 25
//	DB_MAX_IDLE_CONNS         — default 5
//	DB_CONN_MAX_LIFETIME_SECS — default 300
//	DB_CONN_MAX_IDLE_TIME_SECS — default 60
func LoadDB() DB {
	dsn := os.Getenv("POSTGRES_URL")
	if dsn == "" {
		host := envOrDefault("POSTGRES_HOST", "localhost")
		port := envOrDefault("POSTGRES_PORT", "5432")
		dbname := envOrDefault("POSTGRES_DB", "esignet")
		user := envOrDefault("POSTGRES_USER", "postgres")
		password := os.Getenv("POSTGRES_PASSWORD")
		if password != "" {
			dsn = fmt.Sprintf(
				"host=%s port=%s dbname=%s user=%s password=%s sslmode=disable",
				host, port, dbname, user, password,
			)
		} else {
			// Omit password= when unset — lib/pq mis-parses "password= sslmode=..."
			// and falls back to SSL, which fails against local Docker Postgres.
			dsn = fmt.Sprintf(
				"host=%s port=%s dbname=%s user=%s sslmode=disable",
				host, port, dbname, user,
			)
		}
	}

	maxOpen := envInt("DB_MAX_OPEN_CONNS")
	if maxOpen <= 0 {
		maxOpen = defaultDBMaxOpenConns
	}
	maxIdle := envInt("DB_MAX_IDLE_CONNS")
	if maxIdle <= 0 {
		maxIdle = defaultDBMaxIdleConns
	}
	lifetimeSecs := envInt("DB_CONN_MAX_LIFETIME_SECS")
	var lifetime time.Duration
	if lifetimeSecs > 0 {
		lifetime = time.Duration(lifetimeSecs) * time.Second
	} else {
		lifetime = defaultDBConnMaxLifetime
	}
	idleSecs := envInt("DB_CONN_MAX_IDLE_TIME_SECS")
	var idleTime time.Duration
	if idleSecs > 0 {
		idleTime = time.Duration(idleSecs) * time.Second
	} else {
		idleTime = defaultDBConnMaxIdleTime
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

// ensurePostgresSSLMode appends sslmode=disable to postgres:// URLs when absent.
// lib/pq defaults URL connections to SSL, which fails against local Docker Postgres.
func ensurePostgresSSLMode(dsn string) string {
	if strings.Contains(dsn, "sslmode=") {
		return dsn
	}
	if strings.HasPrefix(dsn, "postgres://") || strings.HasPrefix(dsn, "postgresql://") {
		if strings.Contains(dsn, "?") {
			return dsn + "&sslmode=disable"
		}
		return dsn + "?sslmode=disable"
	}
	return dsn
}
