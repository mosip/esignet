/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package persistence holds esignet's shared database plumbing: it owns the DB
// connection config types, resolves the driver-specific DSN (ResolveDSN), opens
// pooled connections (Postgres via pgx, MySQL via go-sql-driver) from a resolved
// DB (Open), rebinds "?" placeholders to the active driver's style (Rebind), and
// classifies driver-specific errors into eSignet error codes (GetErrorCode). It
// is kept separate from the config package so config
// stays a pure configuration layer whose many importers do not transitively
// depend on the database drivers.
package persistence

import (
	"context"
	"database/sql"
	"fmt"
	"net"
	"os"
	"strings"
	"time"

	_ "github.com/go-sql-driver/mysql" // registers the "mysql" database/sql driver used by openMySQL
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/stdlib"

	applog "github.com/mosip/esignet/internal/log"
)

// -----------------------------------------------------------------------------
// Connection config types
// -----------------------------------------------------------------------------

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

// DB holds database connection settings. Driver selects the backend
// ("postgres" or "mysql"); DSN and Pool are interpreted per driver. The config
// package resolves these from env/yaml (building DSN via ResolveDSN); Open then
// establishes the live connection from the resolved DB.
type DB struct {
	Driver string `yaml:"driver"`
	DSN    string `yaml:"dsn"`
	Pool   DBPool `yaml:"pool"`
}

const (
	// DriverPostgres is the DB_DRIVER value selecting the PostgreSQL backend.
	DriverPostgres = "postgres"
	// DriverMySQL is the DB_DRIVER value selecting the MySQL backend.
	DriverMySQL = "mysql"
	// DefaultDBDriver is the driver used when DB_DRIVER is unset, preserving the
	// Postgres-only behavior this service shipped with.
	DefaultDBDriver = DriverPostgres
)

const (
	defaultDBHost = "localhost"
	defaultDBPort = "5455"
	defaultDBName = "mosip_esignet"
	defaultDBUser = "postgres"

	// MySQL connection defaults. The Postgres equivalents are
	// defaultDBPort/defaultDBUser above; defaultDBName is shared by both.
	defaultMySQLPort = "3306"
	defaultMySQLUser = "root"
)

// -----------------------------------------------------------------------------
// DSN construction (the "how to address this DB system" responsibility)
// -----------------------------------------------------------------------------

// SupportsReturning reports whether driver supports the SQL RETURNING clause:
// PostgreSQL does (so a write can read its row back in the same statement),
// MySQL does not (so the write and read-back are separate statements that need a
// transaction to stay consistent). The clientmgmt write path uses this to choose
// between the two forms.
func SupportsReturning(driver string) bool {
	return driver == DriverPostgres
}

// ResolveDSN builds the driver-specific connection DSN from the deployment.yaml
// value (yamlDSN) and the DATABASE_* / DATABASE_URL environment, with precedence
// env var (DATABASE_URL) > yamlDSN > compiled-in default construction. driver
// selects the dialect form (Postgres key=value vs MySQL user:pass@tcp(...)).
func ResolveDSN(driver, yamlDSN string) string {
	if driver == DriverMySQL {
		return resolveMySQLDSN(yamlDSN)
	}
	return resolveDBDSN(yamlDSN)
}

// envOrDefault returns the environment value for key, or fallback when unset.
// Kept local so this package stays independent of the config package.
func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
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

// resolveMySQLDSN resolves the MySQL DSN using the same precedence as
// resolveDBDSN: env var (DATABASE_URL) > yamlDSN (already parsed from
// deployment.yaml) > compiled-in default construction from the individual
// DATABASE_* vars. The built DSN uses go-sql-driver's form
// (user:pass@tcp(host:port)/dbname?...); ensureMySQLParams adds the parameters
// the query layer requires (parseTime/loc and clientFoundRows).
func resolveMySQLDSN(yamlDSN string) string {
	if dsn := os.Getenv("DATABASE_URL"); dsn != "" {
		return ensureMySQLParams(dsn)
	}
	if !hasDBEnvConfig() && yamlDSN != "" {
		return ensureMySQLParams(yamlDSN)
	}

	host := envOrDefault("DATABASE_HOST", defaultDBHost)
	port := envOrDefault("DATABASE_PORT", defaultMySQLPort)
	dbname := envOrDefault("DATABASE_NAME", defaultDBName)
	user := envOrDefault("DATABASE_USERNAME", defaultMySQLUser)
	password := os.Getenv("DATABASE_PASSWORD")
	if password == "" {
		password = os.Getenv("DB_DBUSER_PASSWORD")
	}

	cred := user
	if password != "" {
		cred = user + ":" + password
	}
	// net.JoinHostPort brackets IPv6 literals (e.g. [::1]:3306) as go-sql-driver
	// requires; it is a no-op for hostnames/IPv4.
	addr := net.JoinHostPort(host, port)
	return ensureMySQLParams(fmt.Sprintf("%s@tcp(%s)/%s", cred, addr, dbname))
}

// ensureMySQLParams appends the connection parameters the query layer requires
// to a MySQL DSN (constructed or caller-supplied via DATABASE_URL/yaml) when
// each is absent, mirroring ensurePostgresSSLMode:
//   - parseTime=true&loc=UTC: without parseTime, go-sql-driver scans temporal
//     columns into []byte rather than time.Time, breaking every time.Time /
//     sql.NullTime destination in the query layer.
//   - clientFoundRows=true: makes UPDATE report matched rows rather than only
//     changed rows, so PatchClient's RowsAffected()==0 means the row is absent
//     even when a PATCH writes the same values already stored.
func ensureMySQLParams(dsn string) string {
	if !mysqlHasParam(dsn, "parseTime") {
		dsn += mysqlParamSep(dsn) + "parseTime=true&loc=UTC"
	}
	if !mysqlHasParam(dsn, "clientFoundRows") {
		dsn += mysqlParamSep(dsn) + "clientFoundRows=true"
	}
	return dsn
}

// mysqlQuery returns the query-string portion of a MySQL DSN — the text after the
// first "?" that follows the final "/". go-sql-driver locates parameters the same
// way, so detection must scan only here: credentials and the address can contain
// "?" and "/" (e.g. a password "p?ss"), which must not be mistaken for parameters.
// Returns "" when the DSN has no query string.
func mysqlQuery(dsn string) string {
	afterDB := dsn[strings.LastIndex(dsn, "/")+1:]
	_, query, found := strings.Cut(afterDB, "?")
	if !found {
		return ""
	}
	return query
}

// mysqlHasParam reports whether the DSN's query string already sets name
// (case-insensitively), so a value the caller supplied is never overridden.
func mysqlHasParam(dsn, name string) bool {
	for _, param := range strings.Split(mysqlQuery(dsn), "&") {
		if key, _, found := strings.Cut(param, "="); found && strings.EqualFold(key, name) {
			return true
		}
	}
	return false
}

// mysqlParamSep returns "?" if dsn has no query string yet, else "&". It inspects
// only the portion after the final "/", so a "?" in the password or address does
// not make it pick "&" (which would fold the appended parameters into the DB name).
func mysqlParamSep(dsn string) string {
	if strings.Contains(dsn[strings.LastIndex(dsn, "/")+1:], "?") {
		return "&"
	}
	return "?"
}

// -----------------------------------------------------------------------------
// Connection opening
// -----------------------------------------------------------------------------

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
// sqlx.BindType / Rebind. closeFn must be called (e.g. via defer) once the
// connection is no longer needed.
func Open(cfg DB) (conn *sql.DB, driverName string, closeFn func() error, err error) {
	driverName = cfg.Driver
	if driverName == "" {
		driverName = DefaultDBDriver
	}
	switch driverName {
	case DriverPostgres:
		conn, closeFn, err = openPostgres(cfg)
	case DriverMySQL:
		conn, closeFn, err = openMySQL(cfg)
	default:
		return nil, "", nil, fmt.Errorf("unsupported database driver %q: must be %q or %q", driverName, DriverPostgres, DriverMySQL)
	}
	if err != nil {
		return nil, "", nil, err
	}

	applog.GetLogger().Debug(context.Background(), "database connected",
		applog.String("driver", driverName),
		applog.Int("maxOpenConns", cfg.Pool.MaxOpenConns),
		applog.Int("maxIdleConns", cfg.Pool.MaxIdleConns),
		applog.Int("connMaxLifetimeSecs", cfg.Pool.ConnMaxLifetimeSecs),
		applog.Int("connMaxIdleTimeSecs", cfg.Pool.ConnMaxIdleTimeSecs))
	return conn, driverName, closeFn, nil
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

// openPostgres opens a pgx connection pool, configures its sizing, pings it,
// and returns a *sql.DB (via the pgx stdlib adapter) so existing database/sql
// and sqlx call sites are unaffected. The returned closeFn releases both the
// *sql.DB and the underlying pgxpool.Pool — sql.DB.Close alone does not close
// the pool — and must be called once the connection is no longer needed.
func openPostgres(cfg DB) (conn *sql.DB, closeFn func() error, err error) {
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
func openMySQL(cfg DB) (conn *sql.DB, closeFn func() error, err error) {
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
