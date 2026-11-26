/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package db

import (
	"strings"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/jmoiron/sqlx"
	"github.com/stretchr/testify/suite"
)

// DBTestSuite groups the query-building tests of this package.
type DBTestSuite struct {
	suite.Suite
}

func TestDBTestSuite(t *testing.T) {
	suite.Run(t, new(DBTestSuite))
}

// New only builds query strings (it never touches conn), so a nil TX is safe
// here and lets these tests inspect the rebound SQL without a database.

func (ts *DBTestSuite) TestNew_RebindsPlaceholdersPerDriver() {
	pg := New(nil, "postgres")
	ts.Contains(pg.getConsent, "$1")
	ts.NotContains(pg.getConsent, "?")
	ts.Contains(pg.upsertConsent, "$10")
	ts.Contains(pg.insertConsentHistory, "$10")
	ts.NotContains(pg.upsertConsent, "?")
	ts.Contains(pg.getConsentForUpdate, "$2")
	ts.Contains(pg.insertConsentIfAbsent, "$10")
	my := New(nil, "mysql")
	ts.Contains(my.getConsent, "?")
	ts.NotContains(my.getConsent, "$1")
}

func (ts *DBTestSuite) TestNew_PlaceholderCounts() {
	my := New(nil, "mysql")
	cases := []struct {
		name  string
		query string
		want  int
	}{
		{"getConsent", my.getConsent, 2},
		{"deleteConsent", my.deleteConsent, 2},
		{"insertConsentHistory", my.insertConsentHistory, 10},
		{"upsertConsent", my.upsertConsent, 10},
		{"getConsentForUpdate", my.getConsentForUpdate, 2},
		{"insertConsentIfAbsent", my.insertConsentIfAbsent, 10},
	}
	for _, c := range cases {
		ts.Equal(c.want, strings.Count(c.query, "?"), "%s: %q", c.name, c.query)
	}
}

func (ts *DBTestSuite) TestNew_UpsertConsentIsDialectSpecific() {
	pg := New(nil, "postgres")
	ts.Contains(pg.upsertConsent, "ON CONFLICT (client_id, psu_token) DO UPDATE")
	ts.NotContains(pg.upsertConsent, "ON DUPLICATE KEY")
	my := New(nil, "mysql")
	ts.Contains(my.upsertConsent, "ON DUPLICATE KEY UPDATE")
	ts.NotContains(my.upsertConsent, "ON CONFLICT")
	for name, q := range map[string]string{"postgres": pg.upsertConsent, "mysql": my.upsertConsent} {
		ts.NotContains(q, "id = ", "%s: the upsert must keep the existing row id", name)
	}
}

func (ts *DBTestSuite) TestNew_GetConsentForUpdateLocksThePlainRead() {
	for _, driver := range []string{"postgres", "mysql"} {
		q := New(nil, driver)
		ts.Equal(q.getConsent+" FOR UPDATE", q.getConsentForUpdate, driver)
	}
}

func (ts *DBTestSuite) TestNew_InsertConsentIfAbsentIsDialectSpecific() {
	pg := New(nil, "postgres")
	ts.Contains(pg.insertConsentIfAbsent, "ON CONFLICT (client_id, psu_token) DO NOTHING")
	ts.NotContains(pg.insertConsentIfAbsent, "DO UPDATE")
	my := New(nil, "mysql")
	ts.True(strings.HasPrefix(my.insertConsentIfAbsent, "INSERT IGNORE INTO consent_detail"), my.insertConsentIfAbsent)
	ts.NotContains(my.insertConsentIfAbsent, "ON DUPLICATE KEY")
}

func (ts *DBTestSuite) TestWithTx_ReusesQueriesAndSwapsConnection() {
	mockDB, mock, err := sqlmock.New()
	ts.Require().NoError(err)
	ts.T().Cleanup(func() { _ = mockDB.Close() })
	conn := sqlx.NewDb(mockDB, "postgres")
	mock.ExpectBegin()
	tx, err := conn.Beginx()
	ts.Require().NoError(err)

	base := New(conn, "postgres")
	bound := base.WithTx(tx)
	ts.NotSame(base, bound, "WithTx must return a new Queries")
	ts.Same(tx, bound.db, "the copy must run on the transaction")
	ts.Same(conn, base.db, "the original must keep its connection")
	ts.Equal(base.upsertConsent, bound.upsertConsent)
	ts.Equal(base.getConsent, bound.getConsent)
}
