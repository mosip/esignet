// Package database provides a pgxpool wrapper for Postgres.
package database

import (
	"context"
	"fmt"
	"net/url"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	applog "github.com/mosip/esignet/internal/log"
)

// Config bundles the knobs for the Postgres pool.
type Config struct {
	Host     string
	Port     string
	Name     string
	Username string
	Password string
	SSLMode  string
	Schema   string

	MaxConns        int32
	MinConns        int32
	MaxConnLifetime time.Duration
	MaxConnIdleTime time.Duration
	HealthTimeout   time.Duration
}

// ConnString assembles a libpq-style URL with URL-escaped password.
// The returned string contains the password and must not be logged.
func (c Config) ConnString() string {
	u := url.URL{
		Scheme: "postgres",
		User:   url.UserPassword(c.Username, c.Password),
		Host:   fmt.Sprintf("%s:%s", c.Host, c.Port),
		Path:   "/" + c.Name,
	}
	q := url.Values{}
	if c.SSLMode != "" {
		q.Set("sslmode", c.SSLMode)
	}
	if c.Schema != "" {
		q.Set("search_path", c.Schema)
	}
	u.RawQuery = q.Encode()
	return u.String()
}

// Redacted returns a log-safe representation with the password masked.
func (c Config) Redacted() string {
	return fmt.Sprintf("postgres://%s:****@%s:%s/%s?sslmode=%s&search_path=%s",
		c.Username, c.Host, c.Port, c.Name, c.SSLMode, c.Schema)
}

// NewPool opens a pgxpool, pings once, and returns it ready for use.
// Caller owns the lifecycle and must defer pool.Close.
func NewPool(ctx context.Context, cfg Config, log *applog.Logger) (*pgxpool.Pool, error) {
	log.Info("postgres: opening pool", applog.String("conn", cfg.Redacted()))

	pc, err := pgxpool.ParseConfig(cfg.ConnString())
	if err != nil {
		return nil, fmt.Errorf("parse db url: %w", err)
	}
	if cfg.MaxConns > 0 {
		pc.MaxConns = cfg.MaxConns
	}
	if cfg.MinConns > 0 {
		pc.MinConns = cfg.MinConns
	}
	if cfg.MaxConnLifetime > 0 {
		pc.MaxConnLifetime = cfg.MaxConnLifetime
	}
	if cfg.MaxConnIdleTime > 0 {
		pc.MaxConnIdleTime = cfg.MaxConnIdleTime
	}

	pool, err := pgxpool.NewWithConfig(ctx, pc)
	if err != nil {
		return nil, fmt.Errorf("open db pool: %w", err)
	}

	pingCtx, cancel := context.WithTimeout(ctx, cfg.HealthTimeout)
	defer cancel()
	if err := pool.Ping(pingCtx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("db ping: %w", err)
	}

	log.Info("postgres: pool ready",
		applog.Int("max_conns", int(pc.MaxConns)),
		applog.Int("min_conns", int(pc.MinConns)),
	)
	return pool, nil
}
