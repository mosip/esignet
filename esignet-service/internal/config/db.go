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
	"math"
	"os"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/stdlib"

	applog "github.com/mosip/esignet/internal/log"
)

const (
	defaultDBHost         = "localhost"
	defaultDBPort         = "5455"
	defaultDBName         = "mosip_esignet"
	defaultDBUser         = "postgres"
	defaultDBMaxOpenConns = 50
	// defaultDBMaxIdleConns is intentionally well below defaultDBMaxOpenConns.
	// Unlike database/sql's MaxIdleConns (a passive ceiling), pgxpool.Config's
	// MinConns — which this feeds, see buildPoolConfig — is an eagerly
	// maintained floor: pgxpool's background health check opens connections
	// up to it immediately at startup. Setting it equal to MaxOpenConns would
	// make every instance eagerly open its full connection allotment before
	// any traffic arrives, multiplying baseline Postgres load by replica
	// count; keeping it low preserves warm-connection reuse under steady
	// traffic while letting the pool grow lazily under load.
	defaultDBMaxIdleConns        = 25
	defaultDBConnMaxLifetimeSecs = 1800
	defaultDBConnMaxIdleTimeSecs = 300
	dbPingTimeout                = 5 * time.Second
)

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

// DBPool holds connection pool tuning parameters. Seconds fields (not
// time.Duration) so a plain integer in deployment.yaml decodes correctly —
// yaml.v3 errors decoding an int directly into time.Duration rather than
// treating it as seconds or nanoseconds.
type DBPool struct {
	MaxOpenConns        int `yaml:"max_open_conns"`
	MaxIdleConns        int `yaml:"max_idle_conns"`
	ConnMaxLifetimeSecs int `yaml:"conn_max_lifetime_secs"`
	ConnMaxIdleTimeSecs int `yaml:"conn_max_idle_time_secs"`
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

// resolveDBDSN resolves the Postgres DSN using env var > yamlDSN (already
// parsed from deployment.yaml) > compiled-in default construction.
func resolveDBDSN(yamlDSN string) string {
	if dsn := os.Getenv("DATABASE_URL"); dsn != "" {
		return ensurePostgresSSLMode(dsn)
	}
	if !hasDBEnvConfig() && yamlDSN != "" {
		return yamlDSN
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
	// Omit password= when unset — an empty "password= sslmode=..." value is
	// ambiguous to parse and can cause SSL negotiation to be attempted
	// against a local Docker Postgres that doesn't support it.
	return fmt.Sprintf(
		"host=%s port=%s dbname=%s user=%s sslmode=disable",
		host, port, dbname, user,
	)
}

// loadDB resolves Postgres connection config and pool settings using env var
// > yamlDB (already parsed from deployment.yaml) > compiled-in default.
// Accepts POSTGRES_URL or DATABASE_URL (full DSN), or individual vars:
// DATABASE_HOST, DATABASE_PORT, DATABASE_NAME, DATABASE_USERNAME, DATABASE_PASSWORD.
//
// Pool tuning (all optional):
//
//	DB_MAX_OPEN_CONNS         — default 50
//	DB_MAX_IDLE_CONNS         — default 25
//	DB_CONN_MAX_LIFETIME_SECS — default 1800 (0 = no limit, explicit env-var-only opt-out)
//	DB_CONN_MAX_IDLE_TIME_SECS — default 300
func loadDB(yamlDB DB) DB {
	dsn := resolveDBDSN(yamlDB.DSN)

	maxOpen := clampPositiveInt32("DB_MAX_OPEN_CONNS", envIntOrConfigOrDefault("DB_MAX_OPEN_CONNS", yamlDB.Pool.MaxOpenConns, defaultDBMaxOpenConns))
	maxIdle := clampPositiveInt32("DB_MAX_IDLE_CONNS", envIntOrConfigOrDefault("DB_MAX_IDLE_CONNS", yamlDB.Pool.MaxIdleConns, defaultDBMaxIdleConns))
	// An explicit env var of "0" means "no limit" — same convention as
	// database/sql and Redis. See effectiveMaxConnLifetime in Open() for why
	// pgxpool needs this translated rather than passed through. A yaml value
	// of 0 (or an omitted field, which decodes to the same zero value) can't
	// be distinguished from "not configured", so only the env var can opt out.
	lifetimeSecs := clampDurationSecs("DB_CONN_MAX_LIFETIME_SECS", envIntOrConfigOrDefaultAllowEnvZero("DB_CONN_MAX_LIFETIME_SECS", yamlDB.Pool.ConnMaxLifetimeSecs, defaultDBConnMaxLifetimeSecs))
	idleSecs := clampDurationSecs("DB_CONN_MAX_IDLE_TIME_SECS", envIntOrConfigOrDefault("DB_CONN_MAX_IDLE_TIME_SECS", yamlDB.Pool.ConnMaxIdleTimeSecs, defaultDBConnMaxIdleTimeSecs))

	return DB{
		DSN: dsn,
		Pool: DBPool{
			MaxOpenConns:        maxOpen,
			MaxIdleConns:        maxIdle,
			ConnMaxLifetimeSecs: lifetimeSecs,
			ConnMaxIdleTimeSecs: idleSecs,
		},
	}
}

// clampPositiveInt32 clamps a resolved connection-count setting to the range
// pgxpool.Config's MaxConns/MinConns (both int32) can hold. Without this, a
// value like 2147483648 would wrap negative on conversion in
// buildPoolConfig, and pgxpool would reject the pool as too small.
func clampPositiveInt32(key string, v int) int {
	if v > math.MaxInt32 {
		applog.GetLogger().Warn(context.Background(),
			"clamping out-of-range pool size env var",
			applog.String("key", key), applog.Int("configured", v), applog.Int("clampedTo", math.MaxInt32))
		return math.MaxInt32
	}
	if v < 0 {
		return 0
	}
	return v
}

// maxDurationSecs is the largest number of seconds representable as a
// time.Duration (int64 nanoseconds) without overflowing on conversion.
const maxDurationSecs = int(math.MaxInt64 / int64(time.Second))

// clampDurationSecs clamps a resolved seconds setting so its later
// `time.Duration(v) * time.Second` conversion (in buildPoolConfig) cannot
// overflow and wrap into a small or negative duration.
func clampDurationSecs(key string, v int) int {
	if v > maxDurationSecs {
		applog.GetLogger().Warn(context.Background(),
			"clamping out-of-range duration env var",
			applog.String("key", key), applog.Int("configuredSecs", v), applog.Int("clampedToSecs", maxDurationSecs))
		return maxDurationSecs
	}
	return v
}

// buildPoolConfig translates dsn/pool into a pgxpool.Config, applying the
// same sizing pgxpool needs but *sql.DB doesn't: MinConns is the closest
// pgxpool analog to database/sql's MaxIdleConns ("keep this many warm"), but
// unlike MaxIdleConns it's an eagerly-maintained floor pgxpool's background
// health check enforces — so it's clamped to MaxConns here, since pgxpool
// was never designed to have MinConns exceed MaxConns.
func buildPoolConfig(dsn string, pool DBPool) (*pgxpool.Config, error) {
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

// Open opens a pgx connection pool, configures its sizing, pings it, and
// returns a *sql.DB (via the pgx stdlib adapter) so existing database/sql and
// sqlx call sites are unaffected. The returned closeFn releases both the
// *sql.DB and the underlying pgxpool.Pool — sql.DB.Close alone does not close
// the pool — and must be called (e.g. via defer) once the connection is no
// longer needed.
func (d DB) Open() (conn *sql.DB, closeFn func() error, err error) {
	poolCfg, err := buildPoolConfig(d.DSN, d.Pool)
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
