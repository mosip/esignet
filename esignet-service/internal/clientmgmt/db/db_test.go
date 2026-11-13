/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package db

import (
	"reflect"
	"strings"
	"testing"

	"github.com/stretchr/testify/suite"
)

// DBTestSuite exercises New's query building. New only builds query strings (it
// never touches conn), so a nil TX is safe here and lets these tests inspect the
// rebound SQL without a database.
type DBTestSuite struct {
	suite.Suite
}

func TestDBTestSuite(t *testing.T) {
	suite.Run(t, new(DBTestSuite))
}

func (ts *DBTestSuite) TestNewRebindsPlaceholdersPerDriver() {
	pg := New(nil, "postgres")
	ts.Assert().Contains(pg.getClient, "$1", "postgres: expected $N placeholders, got %q", pg.getClient)
	ts.Assert().NotContains(pg.getClient, "?", "postgres: expected no ? placeholders, got %q", pg.getClient)

	my := New(nil, "mysql")
	ts.Assert().Contains(my.getClient, "?", "mysql: expected ? placeholders, got %q", my.getClient)
	ts.Assert().NotContains(my.getClient, "$1", "mysql: expected no $N placeholders, got %q", my.getClient)
}

func (ts *DBTestSuite) TestNewCreateClientPlaceholderCount() {
	my := New(nil, "mysql")
	ts.Assert().Equal(17, strings.Count(my.createClient, "?"),
		"createClient: expected 17 placeholders in %q", my.createClient)
}

func (ts *DBTestSuite) TestNewUpdateClientIDLast() {
	my := New(nil, "mysql")
	ts.Assert().Equal(11, strings.Count(my.updateClient, "?"),
		"updateClient: expected 11 placeholders in %q", my.updateClient)
	// The WHERE id must be the final placeholder — UpdateClient binds id last.
	ts.Assert().Contains(my.updateClient, "WHERE id = ?",
		"updateClient: expected 'WHERE id = ?' last, got %q", my.updateClient)
}

// TestNewAppendsReturningForPostgresOnly checks the write dialect: PostgreSQL
// appends RETURNING to the write statements, MySQL does not.
func (ts *DBTestSuite) TestNewAppendsReturningForPostgresOnly() {
	pg := New(nil, "postgres")
	ts.Assert().Contains(pg.createClient, "RETURNING", "postgres createClient should use RETURNING")
	ts.Assert().Contains(pg.updateClient, "RETURNING", "postgres updateClient should use RETURNING")

	my := New(nil, "mysql")
	ts.Assert().NotContains(my.createClient, "RETURNING", "mysql createClient must not use RETURNING")
	ts.Assert().NotContains(my.updateClient, "RETURNING", "mysql updateClient must not use RETURNING")
}

// TestClientColumnsMatchClientDetail guards the hand-maintained clientColumns
// list against drifting from the ClientDetail db tags: a missing column would be
// silently never read, an extra column would make sqlx fail with "missing
// destination name". sqlc used to regenerate both from schema.sql; this replaces
// that check now that the layer is hand-written.
func (ts *DBTestSuite) TestClientColumnsMatchClientDetail() {
	want := map[string]bool{}
	t := reflect.TypeOf(ClientDetail{})
	for i := range t.NumField() {
		if tag := t.Field(i).Tag.Get("db"); tag != "" && tag != "-" {
			want[tag] = true
		}
	}

	got := map[string]bool{}
	for _, c := range strings.Split(clientColumns, ",") {
		if col := strings.TrimSpace(c); col != "" {
			got[col] = true
		}
	}

	ts.Assert().Equal(want, got, "clientColumns must list exactly the ClientDetail db tags")
}
