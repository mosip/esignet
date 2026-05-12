// Package db wraps pgxpool with sqlc-generated queries, transaction helpers,
// and advisory lock support.
//
//go:generate go run github.com/sqlc-dev/sqlc/cmd/sqlc@latest generate -f sqlc.yaml
package db

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/mosip/esignet/pkg/db/sqlc"
)

// DB provides a connection pool with query execution and transaction support.
type DB struct {
	pool *pgxpool.Pool
	*sqlc.Queries
}

// New creates a new database connection pool and pings to verify connectivity.
func New(ctx context.Context, url string) (*DB, error) {
	pool, err := pgxpool.New(ctx, url)
	if err != nil {
		return nil, fmt.Errorf("create pool: %w", err)
	}

	if err := pool.Ping(ctx); err != nil {
		pool.Close()

		return nil, fmt.Errorf("ping database: %w", err)
	}

	return &DB{
		pool:    pool,
		Queries: sqlc.New(pool),
	}, nil
}

// Close shuts down the connection pool.
func (d *DB) Close() {
	d.pool.Close()
}

// Ping verifies connectivity to the database.
func (d *DB) Ping(ctx context.Context) error {
	return d.pool.Ping(ctx)
}

// AcquireConn returns a dedicated connection from the pool. The caller
// must call Release on the returned *pgxpool.Conn when done. This is
// useful for session-level operations like advisory locks.
func (d *DB) AcquireConn(ctx context.Context) (*pgxpool.Conn, error) {
	return d.pool.Acquire(ctx)
}

// TryAdvisoryLock attempts to acquire a session-level Postgres advisory
// lock on the given connection. Returns true if the lock was acquired.
func TryAdvisoryLock(ctx context.Context, conn *pgxpool.Conn, key int64) (bool, error) {
	var locked bool
	err := conn.QueryRow(ctx, "SELECT pg_try_advisory_lock($1)", key).Scan(&locked)
	if err != nil {
		return false, fmt.Errorf("pg_try_advisory_lock: %w", err)
	}

	return locked, nil
}

// Begin starts a transaction and returns a Querier bound to it along with
// the underlying pgx.Tx. The caller is responsible for calling Commit or
// Rollback on the transaction.
//
// Prefer InTx for the common case. Use Begin when the transaction must span
// multiple call sites or cannot be expressed as a single callback.
func (d *DB) Begin(ctx context.Context) (sqlc.Querier, pgx.Tx, error) {
	tx, err := d.pool.Begin(ctx)
	if err != nil {
		return nil, nil, fmt.Errorf("begin transaction: %w", err)
	}

	return sqlc.New(tx), tx, nil
}

// InTx executes fn inside a database transaction. The transaction is
// automatically rolled back if fn returns an error or panics. On success
// the transaction is committed.
func (d *DB) InTx(ctx context.Context, fn func(sqlc.Querier) error) error {
	q, tx, err := d.Begin(ctx)
	if err != nil {
		return err
	}
	// Rollback is deferred to ensure the transaction is always cleaned up, even
	// on panic. After a successful Commit the connection is already closed, so
	// Rollback returns an error that is safe to ignore.
	defer func() { _ = tx.Rollback(ctx) }()

	if err := fn(q); err != nil {
		return err
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("commit transaction: %w", err)
	}

	return nil
}
