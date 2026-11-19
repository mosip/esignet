/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package db

import (
	"strings"
	"testing"
)

// New only builds query strings (it never touches conn), so a nil TX is safe
// here and lets these tests inspect the rebound SQL without a database.

func TestNew_RebindsPlaceholdersPerDriver(t *testing.T) {
	pg := New(nil, "postgres")
	if !strings.Contains(pg.getConsent, "$1") || strings.Contains(pg.getConsent, "?") {
		t.Errorf("postgres: expected $N placeholders, got %q", pg.getConsent)
	}
	my := New(nil, "mysql")
	if !strings.Contains(my.getConsent, "?") || strings.Contains(my.getConsent, "$1") {
		t.Errorf("mysql: expected ? placeholders, got %q", my.getConsent)
	}
}

func TestNew_PlaceholderCounts(t *testing.T) {
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
	}
	for _, c := range cases {
		if got := strings.Count(c.query, "?"); got != c.want {
			t.Errorf("%s: expected %d placeholders, got %d in %q", c.name, c.want, got, c.query)
		}
	}
}

func TestNew_UpsertConsentIsDialectSpecific(t *testing.T) {
	pg := New(nil, "postgres")
	if !strings.Contains(pg.upsertConsent, "ON CONFLICT (client_id, psu_token) DO UPDATE") ||
		strings.Contains(pg.upsertConsent, "ON DUPLICATE KEY") {
		t.Errorf("postgres: expected an ON CONFLICT upsert, got %q", pg.upsertConsent)
	}
	my := New(nil, "mysql")
	if !strings.Contains(my.upsertConsent, "ON DUPLICATE KEY UPDATE") ||
		strings.Contains(my.upsertConsent, "ON CONFLICT") {
		t.Errorf("mysql: expected an ON DUPLICATE KEY upsert, got %q", my.upsertConsent)
	}
	for name, q := range map[string]string{"postgres": pg.upsertConsent, "mysql": my.upsertConsent} {
		if strings.Contains(q, "id = ") {
			t.Errorf("%s: the upsert must keep the existing row id, got %q", name, q)
		}
	}
}

func TestWithTx_ReusesQueriesAndSwapsConnection(t *testing.T) {
	base := New(nil, "postgres")
	tx := base.WithTx(nil)
	if tx == base {
		t.Fatal("WithTx must return a new Queries")
	}
	if tx.upsertConsent != base.upsertConsent || tx.getConsent != base.getConsent {
		t.Error("WithTx must reuse the already-built query strings")
	}
}
