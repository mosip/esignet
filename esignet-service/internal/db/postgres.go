// Package db provides a thin wrapper around a [pgxpool.Pool] with a
// production-ready connection pool and a [Ping] method for health checks.
package db

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/mosip/esignet/internal/config"
)

// pgxPool is an internal interface satisfied by *pgxpool.Pool.
// It exists solely to allow unit-test injection without requiring a real server.
type pgxPool interface {
	Ping(ctx context.Context) error
	Close()
}

// newPool is the constructor used by NewPostgres.
// Tests override it to inject a fake pool.
var newPool = func(ctx context.Context, cfg *pgxpool.Config) (pgxPool, error) {
	return pgxpool.NewWithConfig(ctx, cfg)
}

// Postgres wraps a pgxpool connection pool.
type Postgres struct {
	Pool *pgxpool.Pool // exposed for repository use; nil in unit tests
	pool pgxPool       // internal: used by Ping and Close
	log  *slog.Logger
}

// NewPostgres creates and validates a pgxpool connection pool.
// The pool is configured from cfg and a first Ping is issued before returning.
// The caller owns the returned *Postgres and must call Close when done.
func NewPostgres(ctx context.Context, cfg config.PostgresConfig, log *slog.Logger) (*Postgres, error) {
	poolCfg, err := pgxpool.ParseConfig(cfg.URL)
	if err != nil {
		return nil, fmt.Errorf("parse postgres DSN: %w", err)
	}

	// Pool sizing & lifetime knobs
	poolCfg.MaxConns = cfg.MaxConns
	poolCfg.MinConns = cfg.MinConns
	poolCfg.MaxConnLifetime = cfg.MaxConnLifetime
	poolCfg.MaxConnIdleTime = cfg.MaxConnIdleTime

	p, err := newPool(ctx, poolCfg)
	if err != nil {
		return nil, fmt.Errorf("create postgres pool: %w", err)
	}

	// Fail fast: verify connectivity before the server starts serving traffic.
	pingCtx, cancel := context.WithTimeout(ctx, cfg.HealthTimeout)
	defer cancel()

	if err := p.Ping(pingCtx); err != nil {
		p.Close()
		return nil, fmt.Errorf("initial postgres ping: %w", err)
	}

	log.Info("postgres connected",
		slog.Int("max_conns", int(cfg.MaxConns)),
		slog.Int("min_conns", int(cfg.MinConns)),
	)

	// Type-assert back to *pgxpool.Pool for the public Pool field.
	// In unit tests newPool returns a mock so this will be nil — fine, since
	// unit tests never call repository methods that use Pool directly.
	concretePool, _ := p.(*pgxpool.Pool)
	return &Postgres{Pool: concretePool, pool: p, log: log}, nil
}

// Ping sends a lightweight ping to verify the database is reachable.
// It is safe for concurrent use and intended for health-check endpoints.
func (pg *Postgres) Ping(ctx context.Context) error {
	if err := pg.pool.Ping(ctx); err != nil {
		return fmt.Errorf("postgres ping: %w", err)
	}
	return nil
}

// Close drains and closes all connections in the pool.
// It should be deferred in main() after NewPostgres succeeds.
func (pg *Postgres) Close() {
	pg.pool.Close()
	pg.log.Info("postgres pool closed")
}
