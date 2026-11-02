/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package config

import (
	"context"
	"math"
	"strings"
	"time"

	applog "github.com/mosip/esignet/internal/log"
	"github.com/mosip/esignet/internal/persistence"
)

const (
	defaultDBMaxOpenConns = 50
	// defaultDBMaxIdleConns is intentionally well below defaultDBMaxOpenConns.
	// Unlike database/sql's MaxIdleConns (a passive ceiling), pgxpool.Config's
	// MinConns — which this feeds, see persistence.buildPoolConfig — is an eagerly
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

// loadDB resolves the database connection config and pool settings using env var
// > yamlDB (already parsed from deployment.yaml) > compiled-in default. The
// driver-specific DSN is built by persistence.ResolveDSN (which reads
// DATABASE_URL / DATABASE_* and the yaml DSN); the pool tuning is resolved here.
//
// Pool tuning (all optional):
//
//	DB_MAX_OPEN_CONNS         — default 50
//	DB_MAX_IDLE_CONNS         — default 25
//	DB_CONN_MAX_LIFETIME_SECS — default 1800 (0 = no limit, explicit env-var-only opt-out)
//	DB_CONN_MAX_IDLE_TIME_SECS — default 300
func loadDB(yamlDB persistence.DB) persistence.DB {
	// Resolve the driver first (env > yaml > default), then build the matching
	// DSN. An unrecognized driver falls through to the Postgres DSN here and is
	// rejected with a clear error in persistence.Open.
	driver := strings.ToLower(strings.TrimSpace(envOrDefault("DB_DRIVER", yamlDB.Driver)))
	if driver == "" {
		driver = persistence.DefaultDBDriver
	}
	dsn := persistence.ResolveDSN(driver, yamlDB.DSN)

	maxOpenRaw, maxOpenSrc := envIntOrConfigOrDefaultSourced("DB_MAX_OPEN_CONNS", yamlDB.Pool.MaxOpenConns, defaultDBMaxOpenConns)
	maxOpen := clampPositiveInt32("DB_MAX_OPEN_CONNS", maxOpenRaw)
	maxIdleRaw, maxIdleSrc := envIntOrConfigOrDefaultSourced("DB_MAX_IDLE_CONNS", yamlDB.Pool.MaxIdleConns, defaultDBMaxIdleConns)
	maxIdle := clampPositiveInt32("DB_MAX_IDLE_CONNS", maxIdleRaw)
	// An explicit env var of "0" means "no limit" — same convention as
	// database/sql and Redis. See persistence.effectiveMaxConnLifetime for why
	// pgxpool needs this translated rather than passed through. A yaml value
	// of 0 (or an omitted field, which decodes to the same zero value) can't
	// be distinguished from "not configured", so only the env var can opt out.
	lifetimeRaw, lifetimeSrc := envIntOrConfigOrDefaultAllowEnvZeroSourced("DB_CONN_MAX_LIFETIME_SECS", yamlDB.Pool.ConnMaxLifetimeSecs, defaultDBConnMaxLifetimeSecs)
	lifetimeSecs := clampDurationSecs("DB_CONN_MAX_LIFETIME_SECS", lifetimeRaw)
	idleRaw, idleSrc := envIntOrConfigOrDefaultSourced("DB_CONN_MAX_IDLE_TIME_SECS", yamlDB.Pool.ConnMaxIdleTimeSecs, defaultDBConnMaxIdleTimeSecs)
	idleSecs := clampDurationSecs("DB_CONN_MAX_IDLE_TIME_SECS", idleRaw)

	// Report the effective value *and* which tier supplied it. Logging only
	// the values leaves an operator unable to tell an env var that took effect
	// from one that was ignored in favour of deployment.yaml.
	logResolvedSettings("db pool config resolved",
		resolvedSetting{"maxOpenConns", maxOpen, maxOpenSrc},
		resolvedSetting{"maxIdleConns", maxIdle, maxIdleSrc},
		resolvedSetting{"connMaxLifetimeSecs", lifetimeSecs, lifetimeSrc},
		resolvedSetting{"connMaxIdleTimeSecs", idleSecs, idleSrc})

	return persistence.DB{
		Driver: driver,
		DSN:    dsn,
		Pool: persistence.DBPool{
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
// persistence.buildPoolConfig, and pgxpool would reject the pool as too small.
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
// `time.Duration(v) * time.Second` conversion (in persistence.buildPoolConfig) cannot
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
