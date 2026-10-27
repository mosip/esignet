/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package config

import (
	"context"
	"fmt"
	"math"
	"net"
	"os"
	"strings"
	"time"

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
	// MinConns — which this feeds, see dbconn.buildPoolConfig — is an eagerly
	// maintained floor: pgxpool's background health check opens connections
	// up to it immediately at startup. Setting it equal to MaxOpenConns would
	// make every instance eagerly open its full connection allotment before
	// any traffic arrives, multiplying baseline Postgres load by replica
	// count; keeping it low preserves warm-connection reuse under steady
	// traffic while letting the pool grow lazily under load.
	defaultDBMaxIdleConns        = 25
	defaultDBConnMaxLifetimeSecs = 1800
	defaultDBConnMaxIdleTimeSecs = 300
)

const (
	// DriverPostgres and DriverMySQL are the supported DB_DRIVER values.
	// DefaultDBDriver applies when DB_DRIVER is unset, preserving the
	// Postgres-only behavior this service shipped with. They are exported so the
	// connection layer (internal/dbconn) can dispatch on the resolved driver
	// without duplicating the string literals.
	DriverPostgres  = "postgres"
	DriverMySQL     = "mysql"
	DefaultDBDriver = DriverPostgres

	// MySQL connection defaults. The Postgres equivalents are
	// defaultDBPort/defaultDBUser above; defaultDBName is shared by both.
	defaultMySQLPort = "3306"
	defaultMySQLUser = "root"
)

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
// ("postgres" or "mysql"); DSN and Pool are interpreted per driver. The live
// connection is opened from this resolved config by internal/dbconn.Open.
type DB struct {
	Driver string `yaml:"driver"`
	DSN    string `yaml:"dsn"`
	Pool   DBPool `yaml:"pool"`
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
// (user:pass@tcp(host:port)/dbname?...); parseTime=true&loc=UTC are required so
// DATE/DATETIME/TIMESTAMP columns scan into time.Time as UTC.
func resolveMySQLDSN(yamlDSN string) string {
	if dsn := os.Getenv("DATABASE_URL"); dsn != "" {
		return ensureMySQLParseTime(dsn)
	}
	if !hasDBEnvConfig() && yamlDSN != "" {
		return ensureMySQLParseTime(yamlDSN)
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
	return fmt.Sprintf("%s@tcp(%s)/%s?parseTime=true&loc=UTC", cred, addr, dbname)
}

// ensureMySQLParseTime appends parseTime=true&loc=UTC to a caller-supplied DSN
// (DATABASE_URL or yaml) when parseTime is absent, mirroring
// ensurePostgresSSLMode: without parseTime, go-sql-driver scans temporal
// columns into []byte rather than time.Time, breaking every time.Time /
// sql.NullTime destination in the query layer.
func ensureMySQLParseTime(dsn string) string {
	if strings.Contains(strings.ToLower(dsn), "parsetime=") {
		return dsn
	}
	sep := "?"
	if strings.Contains(dsn, "?") {
		sep = "&"
	}
	return dsn + sep + "parseTime=true&loc=UTC"
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
	// Resolve the driver first (env > yaml > default), then build the DSN with
	// the matching resolver. An unrecognized driver falls through to the
	// Postgres DSN here and is rejected with a clear error in dbconn.Open.
	driver := strings.ToLower(strings.TrimSpace(envOrDefault("DB_DRIVER", yamlDB.Driver)))
	if driver == "" {
		driver = DefaultDBDriver
	}

	var dsn string
	if driver == DriverMySQL {
		dsn = resolveMySQLDSN(yamlDB.DSN)
	} else {
		dsn = resolveDBDSN(yamlDB.DSN)
	}

	maxOpenRaw, maxOpenSrc := envIntOrConfigOrDefaultSourced("DB_MAX_OPEN_CONNS", yamlDB.Pool.MaxOpenConns, defaultDBMaxOpenConns)
	maxOpen := clampPositiveInt32("DB_MAX_OPEN_CONNS", maxOpenRaw)
	maxIdleRaw, maxIdleSrc := envIntOrConfigOrDefaultSourced("DB_MAX_IDLE_CONNS", yamlDB.Pool.MaxIdleConns, defaultDBMaxIdleConns)
	maxIdle := clampPositiveInt32("DB_MAX_IDLE_CONNS", maxIdleRaw)
	// An explicit env var of "0" means "no limit" — same convention as
	// database/sql and Redis. See dbconn.effectiveMaxConnLifetime for why
	// pgxpool needs this translated rather than passed through. A yaml value
	// of 0 (or an omitted field, which decodes to the same zero value) can't
	// be distinguished from "not configured", so only the env var can opt out.
	lifetimeRaw, lifetimeSrc := envIntOrConfigOrDefaultAllowEnvZeroSourced("DB_CONN_MAX_LIFETIME_SECS", yamlDB.Pool.ConnMaxLifetimeSecs, defaultDBConnMaxLifetimeSecs)
	lifetimeSecs := clampDurationSecs("DB_CONN_MAX_LIFETIME_SECS", lifetimeRaw)
	idleRaw, idleSrc := envIntOrConfigOrDefaultSourced("DB_CONN_MAX_IDLE_TIME_SECS", yamlDB.Pool.ConnMaxIdleTimeSecs, defaultDBConnMaxIdleTimeSecs)
	idleSecs := clampDurationSecs("DB_CONN_MAX_IDLE_TIME_SECS", idleRaw)

	// Report the effective value *and* which tier supplied it. Logging only
	// the values (as the "postgres connected" line in cmd/esignet/main.go
	// does) leaves an operator unable to tell an env var that took effect
	// from one that was ignored in favour of deployment.yaml.
	logResolvedSettings("db pool config resolved",
		resolvedSetting{"maxOpenConns", maxOpen, maxOpenSrc},
		resolvedSetting{"maxIdleConns", maxIdle, maxIdleSrc},
		resolvedSetting{"connMaxLifetimeSecs", lifetimeSecs, lifetimeSrc},
		resolvedSetting{"connMaxIdleTimeSecs", idleSecs, idleSrc})

	return DB{
		Driver: driver,
		DSN:    dsn,
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
// dbconn.buildPoolConfig, and pgxpool would reject the pool as too small.
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
// `time.Duration(v) * time.Second` conversion (in dbconn.buildPoolConfig) cannot
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
