/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package persistence

import (
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

type PersistenceTestSuite struct {
	suite.Suite
}

func TestPersistenceTestSuite(t *testing.T) {
	suite.Run(t, new(PersistenceTestSuite))
}

// clearDBEnv clears every DATABASE_* / password env var for a test, so DSN
// resolution exercises the compiled-in defaults or the supplied yaml value.
func (ts *PersistenceTestSuite) clearDBEnv() {
	for _, envVar := range []string{
		"DATABASE_URL", "DATABASE_HOST", "DATABASE_PORT", "DATABASE_NAME",
		"DATABASE_USERNAME", "DATABASE_PASSWORD", "DB_DBUSER_PASSWORD",
	} {
		ts.T().Setenv(envVar, "")
	}
}

// -----------------------------------------------------------------------------
// DSN construction
// -----------------------------------------------------------------------------

func (ts *PersistenceTestSuite) TestHasDBEnvConfig() {
	cases := []string{
		"DATABASE_URL", "DATABASE_HOST", "DATABASE_PORT",
		"DATABASE_NAME", "DATABASE_USERNAME", "DATABASE_PASSWORD", "DB_DBUSER_PASSWORD",
	}
	for _, envVar := range cases {
		ts.Run(envVar, func() {
			ts.T().Setenv(envVar, "set")
			ts.Require().True(hasDBEnvConfig())
		})
	}

	ts.Run("none set", func() {
		for _, envVar := range cases {
			ts.T().Setenv(envVar, "")
		}
		ts.Require().False(hasDBEnvConfig())
	})
}

func (ts *PersistenceTestSuite) TestEnsurePostgresSSLMode() {
	cases := []struct {
		name string
		dsn  string
		want string
	}{
		{"adds sslmode with no query", "postgres://user:pass@host/db", "postgres://user:pass@host/db?sslmode=disable"},
		{"adds sslmode with existing query", "postgres://user:pass@host/db?foo=bar", "postgres://user:pass@host/db?foo=bar&sslmode=disable"},
		{"leaves existing sslmode alone", "postgres://user:pass@host/db?sslmode=require", "postgres://user:pass@host/db?sslmode=require"},
		{"leaves non-postgres DSN alone", "host=localhost dbname=x", "host=localhost dbname=x"},
		{"postgresql scheme also matched", "postgresql://user@host/db", "postgresql://user@host/db?sslmode=disable"},
	}
	for _, tc := range cases {
		ts.Run(tc.name, func() {
			ts.Require().Equal(tc.want, ensurePostgresSSLMode(tc.dsn))
		})
	}
}

func (ts *PersistenceTestSuite) TestResolveDBDSN_FullURL() {
	ts.T().Setenv("DATABASE_URL", "postgres://esignet:secret@dbhost:5432/mosip_esignet")

	dsn := resolveDBDSN("")

	ts.Require().Equal("postgres://esignet:secret@dbhost:5432/mosip_esignet?sslmode=disable", dsn)
}

func (ts *PersistenceTestSuite) TestResolveDBDSN_IndividualVars() {
	ts.clearDBEnv()
	ts.T().Setenv("DATABASE_HOST", "dbhost")
	ts.T().Setenv("DATABASE_PORT", "5555")
	ts.T().Setenv("DATABASE_NAME", "mydb")
	ts.T().Setenv("DATABASE_USERNAME", "myuser")
	ts.T().Setenv("DATABASE_PASSWORD", "mypass")

	dsn := resolveDBDSN("")

	ts.Require().Equal("host=dbhost port=5555 dbname=mydb user=myuser password=mypass sslmode=disable", dsn)
}

func (ts *PersistenceTestSuite) TestResolveDBDSN_DBUserPasswordFallback() {
	ts.clearDBEnv()
	ts.T().Setenv("DATABASE_HOST", "dbhost")
	ts.T().Setenv("DATABASE_PASSWORD", "")
	ts.T().Setenv("DB_DBUSER_PASSWORD", "fallback-pass")

	dsn := resolveDBDSN("")

	ts.Require().Contains(dsn, "password=fallback-pass")
}

func (ts *PersistenceTestSuite) TestResolveDBDSN_DatabasePasswordTakesPrecedence() {
	ts.clearDBEnv()
	ts.T().Setenv("DATABASE_PASSWORD", "primary-pass")
	ts.T().Setenv("DB_DBUSER_PASSWORD", "fallback-pass")

	dsn := resolveDBDSN("")

	ts.Require().Contains(dsn, "password=primary-pass")
	ts.Require().NotContains(dsn, "fallback-pass")
}

func (ts *PersistenceTestSuite) TestResolveDBDSN_NoPasswordOmitsField() {
	ts.clearDBEnv()

	dsn := resolveDBDSN("")

	ts.Require().NotContains(dsn, "password=")
	ts.Require().Contains(dsn, "host="+defaultDBHost)
	ts.Require().Contains(dsn, "port="+defaultDBPort)
	ts.Require().Contains(dsn, "dbname="+defaultDBName)
	ts.Require().Contains(dsn, "user="+defaultDBUser)
}

func (ts *PersistenceTestSuite) TestResolveDBDSN_YAMLUsedWhenNoEnvConfig() {
	ts.clearDBEnv()

	dsn := resolveDBDSN("host=yamlhost port=5432 dbname=yamldb user=yamluser sslmode=disable")

	ts.Require().Equal("host=yamlhost port=5432 dbname=yamldb user=yamluser sslmode=disable", dsn)
}

func (ts *PersistenceTestSuite) TestResolveDBDSN_EnvTakesPrecedenceOverYAML() {
	ts.clearDBEnv()
	ts.T().Setenv("DATABASE_HOST", "envhost")

	dsn := resolveDBDSN("host=yamlhost port=5432 dbname=yamldb user=yamluser sslmode=disable")

	ts.Require().Contains(dsn, "host=envhost")
	ts.Require().NotContains(dsn, "yamlhost")
}

func (ts *PersistenceTestSuite) TestEnsureMySQLParseTime() {
	cases := []struct {
		name string
		dsn  string
		want string
	}{
		{"appends with no query", "user:pass@tcp(h:3306)/db", "user:pass@tcp(h:3306)/db?parseTime=true&loc=UTC"},
		{"appends with existing query", "user:pass@tcp(h:3306)/db?charset=utf8mb4", "user:pass@tcp(h:3306)/db?charset=utf8mb4&parseTime=true&loc=UTC"},
		{"leaves existing parseTime alone", "user:pass@tcp(h:3306)/db?parseTime=true", "user:pass@tcp(h:3306)/db?parseTime=true"},
	}
	for _, tc := range cases {
		ts.Run(tc.name, func() {
			ts.Require().Equal(tc.want, ensureMySQLParseTime(tc.dsn))
		})
	}
}

func (ts *PersistenceTestSuite) TestResolveMySQLDSN_FullURLPassedThrough() {
	ts.T().Setenv("DATABASE_URL", "esignet:secret@tcp(dbhost:3306)/mosip_esignet?parseTime=true&loc=UTC")

	dsn := resolveMySQLDSN("")

	ts.Require().Equal("esignet:secret@tcp(dbhost:3306)/mosip_esignet?parseTime=true&loc=UTC", dsn)
}

func (ts *PersistenceTestSuite) TestResolveMySQLDSN_FullURLGetsParseTimeAppended() {
	ts.T().Setenv("DATABASE_URL", "esignet:secret@tcp(dbhost:3306)/mosip_esignet")

	dsn := resolveMySQLDSN("")

	ts.Require().Equal("esignet:secret@tcp(dbhost:3306)/mosip_esignet?parseTime=true&loc=UTC", dsn)
}

func (ts *PersistenceTestSuite) TestResolveMySQLDSN_IndividualVars() {
	ts.T().Setenv("DATABASE_URL", "")
	ts.T().Setenv("DATABASE_HOST", "dbhost")
	ts.T().Setenv("DATABASE_PORT", "3307")
	ts.T().Setenv("DATABASE_NAME", "mydb")
	ts.T().Setenv("DATABASE_USERNAME", "myuser")
	ts.T().Setenv("DATABASE_PASSWORD", "mypass")

	dsn := resolveMySQLDSN("")

	ts.Require().Equal("myuser:mypass@tcp(dbhost:3307)/mydb?parseTime=true&loc=UTC", dsn)
}

func (ts *PersistenceTestSuite) TestResolveMySQLDSN_DBUserPasswordFallback() {
	ts.T().Setenv("DATABASE_URL", "")
	ts.T().Setenv("DATABASE_HOST", "dbhost")
	ts.T().Setenv("DATABASE_PASSWORD", "")
	ts.T().Setenv("DB_DBUSER_PASSWORD", "fallback-pass")

	dsn := resolveMySQLDSN("")

	ts.Require().Contains(dsn, ":fallback-pass@tcp(")
}

func (ts *PersistenceTestSuite) TestResolveMySQLDSN_NoPasswordOmitsCredentialColon() {
	ts.clearDBEnv()

	dsn := resolveMySQLDSN("")

	ts.Require().Equal(
		defaultMySQLUser+"@tcp("+defaultDBHost+":"+defaultMySQLPort+")/"+defaultDBName+"?parseTime=true&loc=UTC",
		dsn)
}

func (ts *PersistenceTestSuite) TestResolveMySQLDSN_YAMLUsedWhenNoEnvConfig() {
	ts.clearDBEnv()

	dsn := resolveMySQLDSN("esignet:secret@tcp(yamlhost:3306)/yamldb?parseTime=true&loc=UTC")

	ts.Require().Equal("esignet:secret@tcp(yamlhost:3306)/yamldb?parseTime=true&loc=UTC", dsn)
}

// -----------------------------------------------------------------------------
// Connection opening
// -----------------------------------------------------------------------------

func (ts *PersistenceTestSuite) TestBuildPoolConfig_MinConnsClampedToMaxConns() {
	poolCfg, err := buildPoolConfig("postgres://localhost/db", DBPool{
		MaxOpenConns: 5,
		MaxIdleConns: 25, // e.g. left at the default while MaxOpenConns was lowered
	})

	ts.Require().NoError(err)
	ts.Require().EqualValues(5, poolCfg.MaxConns)
	ts.Require().EqualValues(5, poolCfg.MinConns, "MinConns must never exceed MaxConns")
}

func (ts *PersistenceTestSuite) TestBuildPoolConfig_MinConnsWithinMaxConnsUnaffected() {
	poolCfg, err := buildPoolConfig("postgres://localhost/db", DBPool{
		MaxOpenConns: 25,
		MaxIdleConns: 10,
	})

	ts.Require().NoError(err)
	ts.Require().EqualValues(25, poolCfg.MaxConns)
	ts.Require().EqualValues(10, poolCfg.MinConns)
}

func (ts *PersistenceTestSuite) TestEffectiveMaxConnLifetime() {
	ts.Require().Equal(dbUnlimitedConnLifetime, effectiveMaxConnLifetime(0))
	ts.Require().Equal(dbUnlimitedConnLifetime, effectiveMaxConnLifetime(-time.Second))
	ts.Require().Equal(30*time.Minute, effectiveMaxConnLifetime(30*time.Minute))
}

func (ts *PersistenceTestSuite) TestOpen_UnsupportedDriver() {
	_, _, _, err := Open(DB{Driver: "oracle"})

	ts.Require().Error(err)
	ts.Require().Contains(err.Error(), "unsupported database driver")
}
