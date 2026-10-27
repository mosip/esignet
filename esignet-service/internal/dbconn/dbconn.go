/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package dbconn opens pooled database connections (Postgres via pgx, MySQL via
// go-sql-driver) from a resolved config.DB. It is deliberately separate from the
// config package so config stays a pure configuration layer: config resolves the
// DSN and pool settings (no driver imports), and this package consumes them to
// establish the live connection. Keeping the drivers here means the many
// importers of config do not transitively depend on pgx or the MySQL driver.
package dbconn

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	_ "github.com/go-sql-driver/mysql" // registers the "mysql" database/sql driver used by openMySQL
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/stdlib"

	"github.com/mosip/esignet/internal/config"
)

// dbPingTimeout bounds the startup connectivity check performed for both drivers.
const dbPingTimeout = 5 * time.Second

// dbUnlimitedConnLifetime approximates "no limit" for pgxpool.Config.MaxConnLifetime.
// Unlike database/sql (SetConnMaxLifetime) and go-redis (ConnMaxLifetime), pgxpool
// treats a zero-or-negative MaxConnLifetime as "already expired" — every pooled
// connection would compute maxAgeTime = time.Now() at creation and get destroyed
// and replaced continuously by the health check instead of being kept forever, the
// opposite of the "no limit" opt-out DB_CONN_MAX_LIFETIME_SECS=0 is meant to give.
const dbUnlimitedConnLifetime = 100 * 365 * 24 * time.Hour

// effectiveMaxConnLifetime translates the operator-facing "0/negative = no
// limit" convention into a value pgxpool actually treats as unbounded.
func effectiveMaxConnLifetime(configured time.Duration) time.Duration {
	if configured <= 0 {
		return dbUnlimitedConnLifetime
	}
	return configured
}

// Open opens a connection to the configured database (Postgres or MySQL, per
// cfg.Driver), pings it, and returns the resolved driver name alongside the
// *sql.DB and a closeFn. The returned driver name is suitable for
// sqlx.BindType / dbutil.Rebind. closeFn must be called (e.g. via defer) once
// the connection is no longer needed.
func Open(cfg config.DB) (conn *sql.DB, driverName string, closeFn func() error, err error) {
	driverName = cfg.Driver
	if driverName == "" {
		driverName = config.DefaultDBDriver
	}
	switch driverName {
	case config.DriverPostgres:
		conn, closeFn, err = openPostgres(cfg)
	case config.DriverMySQL:
		conn, closeFn, err = openMySQL(cfg)
	default:
		return nil, "", nil, fmt.Errorf("unsupported database driver %q: must be %q or %q", driverName, config.DriverPostgres, config.DriverMySQL)
	}
	if err != nil {
		return nil, "", nil, err
	}
	return conn, driverName, closeFn, nil
}

// buildPoolConfig translates dsn/pool into a pgxpool.Config, applying the
// same sizing pgxpool needs but *sql.DB doesn't: MinConns is the closest
// pgxpool analog to database/sql's MaxIdleConns ("keep this many warm"), but
// unlike MaxIdleConns it's an eagerly-maintained floor pgxpool's background
// health check enforces — so it's clamped to MaxConns here, since pgxpool
// was never designed to have MinConns exceed MaxConns.
func buildPoolConfig(dsn string, pool config.DBPool) (*pgxpool.Config, error) {
	poolCfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, fmt.Errorf("parse postgres dsn: %w", err)
	}
	poolCfg.MaxConns = int32(pool.MaxOpenConns)
	poolCfg.MinConns = int32(pool.MaxIdleConns)
	if poolCfg.MinConns > poolCfg.MaxConns {
		poolCfg.MinConns = poolCfg.MaxConns
	}
	poolCfg.MaxConnLifetime = effectiveMaxConnLifetime(time.Duration(pool.ConnMaxLifetimeSecs) * time.Second)
	poolCfg.MaxConnIdleTime = time.Duration(pool.ConnMaxIdleTimeSecs) * time.Second
	return poolCfg, nil
}

// openPostgres opens a pgx connection pool, configures its sizing, pings it,
// and returns a *sql.DB (via the pgx stdlib adapter) so existing database/sql
// and sqlx call sites are unaffected. The returned closeFn releases both the
// *sql.DB and the underlying pgxpool.Pool — sql.DB.Close alone does not close
// the pool — and must be called once the connection is no longer needed.
func openPostgres(cfg config.DB) (conn *sql.DB, closeFn func() error, err error) {
	poolCfg, err := buildPoolConfig(cfg.DSN, cfg.Pool)
	if err != nil {
		return nil, nil, err
	}

	ctx, cancel := context.WithTimeout(context.Background(), dbPingTimeout)
	defer cancel()

	pool, err := pgxpool.NewWithConfig(ctx, poolCfg)
	if err != nil {
		return nil, nil, fmt.Errorf("open postgres: %w", err)
	}

	// Pool sizing is governed entirely by poolCfg above, not by *sql.DB's own
	// SetMaxOpenConns/SetConnMaxLifetime/etc — OpenDBFromPool forces the
	// returned *sql.DB's MaxIdleConns to 0 (verified in pgx v5's stdlib
	// package) for exactly this reason, so setting those on conn would fight
	// the pool instead of tuning it.
	conn = stdlib.OpenDBFromPool(pool)
	if err := conn.PingContext(ctx); err != nil {
		_ = conn.Close()
		pool.Close()
		return nil, nil, fmt.Errorf("ping postgres: %w", err)
	}

	closeFn = func() error {
		err := conn.Close()
		pool.Close()
		return err
	}
	return conn, closeFn, nil
}

// openMySQL opens a database/sql connection to MySQL, applies the pool
// settings, and pings it. Unlike openPostgres, pool sizing uses database/sql's
// own setters: SetMaxIdleConns here is a passive ceiling (idle connections are
// kept up to it, not opened eagerly), not pgxpool's eagerly-maintained MinConns
// floor. SetConnMaxLifetime uses the raw duration because database/sql already
// treats 0 as "no limit" — the opposite of pgxpool, which is why
// effectiveMaxConnLifetime exists only on the Postgres path.
func openMySQL(cfg config.DB) (conn *sql.DB, closeFn func() error, err error) {
	conn, err = sql.Open("mysql", cfg.DSN)
	if err != nil {
		return nil, nil, fmt.Errorf("open mysql: %w", err)
	}
	conn.SetMaxOpenConns(cfg.Pool.MaxOpenConns)
	conn.SetMaxIdleConns(cfg.Pool.MaxIdleConns)
	conn.SetConnMaxLifetime(time.Duration(cfg.Pool.ConnMaxLifetimeSecs) * time.Second)
	conn.SetConnMaxIdleTime(time.Duration(cfg.Pool.ConnMaxIdleTimeSecs) * time.Second)

	ctx, cancel := context.WithTimeout(context.Background(), dbPingTimeout)
	defer cancel()
	if err := conn.PingContext(ctx); err != nil {
		_ = conn.Close()
		return nil, nil, fmt.Errorf("ping mysql: %w", err)
	}

	closeFn = func() error { return conn.Close() }
	return conn, closeFn, nil
}
