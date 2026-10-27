/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dbconn

import (
	"testing"
	"time"

	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/config"
)

func TestBuildPoolConfig_MinConnsClampedToMaxConns(t *testing.T) {
	poolCfg, err := buildPoolConfig("postgres://localhost/db", config.DBPool{
		MaxOpenConns: 5,
		MaxIdleConns: 25, // e.g. left at the default while MaxOpenConns was lowered
	})

	require.NoError(t, err)
	require.EqualValues(t, 5, poolCfg.MaxConns)
	require.EqualValues(t, 5, poolCfg.MinConns, "MinConns must never exceed MaxConns")
}

func TestBuildPoolConfig_MinConnsWithinMaxConnsUnaffected(t *testing.T) {
	poolCfg, err := buildPoolConfig("postgres://localhost/db", config.DBPool{
		MaxOpenConns: 25,
		MaxIdleConns: 10,
	})

	require.NoError(t, err)
	require.EqualValues(t, 25, poolCfg.MaxConns)
	require.EqualValues(t, 10, poolCfg.MinConns)
}

func TestEffectiveMaxConnLifetime(t *testing.T) {
	require.Equal(t, dbUnlimitedConnLifetime, effectiveMaxConnLifetime(0))
	require.Equal(t, dbUnlimitedConnLifetime, effectiveMaxConnLifetime(-time.Second))
	require.Equal(t, 30*time.Minute, effectiveMaxConnLifetime(30*time.Minute))
}

func TestOpen_UnsupportedDriver(t *testing.T) {
	_, _, _, err := Open(config.DB{Driver: "oracle"})

	require.Error(t, err)
	require.Contains(t, err.Error(), "unsupported database driver")
}
