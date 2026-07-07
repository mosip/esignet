package config

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/kelseyhightower/envconfig"
	_ "github.com/lib/pq" // PostgreSQL driver for database/sql
)

const dbPingTimeout = 5 * time.Second

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

// dbSpec is the environment-variable layout for Postgres settings. The
// individual connection params are only used to build the DSN when DATABASE_URL
// is empty, but always carry their defaults.
type dbSpec struct {
	URL      string `envconfig:"DATABASE_URL"`
	Host     string `envconfig:"DATABASE_HOST" default:"localhost"`
	Port     string `envconfig:"DATABASE_PORT" default:"5455"`
	Name     string `envconfig:"DATABASE_NAME" default:"mosip_esignet"`
	User     string `envconfig:"DATABASE_USERNAME" default:"postgres"`
	Password string `envconfig:"DATABASE_PASSWORD"`
	// PasswordAlt is the MOSIP deployment's name for the DB password,
	// used when DATABASE_PASSWORD is unset.
	PasswordAlt string `envconfig:"DB_DBUSER_PASSWORD"`

	MaxOpenConns    int `envconfig:"DB_MAX_OPEN_CONNS" default:"25"`
	MaxIdleConns    int `envconfig:"DB_MAX_IDLE_CONNS" default:"5"`
	ConnMaxLifetime int `envconfig:"DB_CONN_MAX_LIFETIME_SECS"` // 0 = no limit
	ConnMaxIdleTime int `envconfig:"DB_CONN_MAX_IDLE_TIME_SECS" default:"60"`
}

// loadDB reads Postgres connection config and pool settings from the environment.
// Accepts either DATABASE_URL (full DSN) or individual vars:
// DATABASE_HOST, DATABASE_PORT, DATABASE_NAME, DATABASE_USERNAME, DATABASE_PASSWORD.
//
// Pool tuning (all optional):
//
//	DB_MAX_OPEN_CONNS          — default 25
//	DB_MAX_IDLE_CONNS          — default 5
//	DB_CONN_MAX_LIFETIME_SECS  — default 0 (no limit)
//	DB_CONN_MAX_IDLE_TIME_SECS — default 60
func loadDB() (*DB, error) {
	var s dbSpec
	if err := envconfig.Process("", &s); err != nil {
		return nil, fmt.Errorf("loading database config: %w", err)
	}
	if s.Password == "" {
		s.Password = s.PasswordAlt
	}

	dsn := s.URL
	if dsn != "" &&
		(strings.HasPrefix(dsn, "postgres://") || strings.HasPrefix(dsn, "postgresql://")) &&
		!strings.Contains(strings.ToLower(dsn), "sslmode=") {
		sep := "?"
		if strings.Contains(dsn, "?") {
			sep = "&"
		}
		dsn += sep + "sslmode=disable"
	}
	if dsn == "" {
		if s.Password != "" {
			dsn = fmt.Sprintf(
				"host=%s port=%s dbname=%s user=%s password=%s sslmode=disable",
				s.Host, s.Port, s.Name, s.User, s.Password,
			)
		} else {
			// Omit password= when unset — lib/pq mis-parses "password= sslmode=..."
			// and falls back to SSL, which fails against local Docker Postgres.
			dsn = fmt.Sprintf(
				"host=%s port=%s dbname=%s user=%s sslmode=disable",
				s.Host, s.Port, s.Name, s.User,
			)
		}
	}

	return &DB{
		DSN: dsn,
		Pool: DBPool{
			MaxOpenConns:    s.MaxOpenConns,
			MaxIdleConns:    s.MaxIdleConns,
			ConnMaxLifetime: time.Duration(s.ConnMaxLifetime) * time.Second,
			ConnMaxIdleTime: time.Duration(s.ConnMaxIdleTime) * time.Second,
		},
	}, nil
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
