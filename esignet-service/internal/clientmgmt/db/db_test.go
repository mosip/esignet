package db

import (
	"strings"
	"testing"
)

// New only builds query strings (it never touches conn), so a nil TX is safe
// here and lets these tests inspect the rebound SQL without a database.

func TestNew_RebindsPlaceholdersPerDriver(t *testing.T) {
	pg := New(nil, "postgres")
	if !strings.Contains(pg.getClient, "$1") || strings.Contains(pg.getClient, "?") {
		t.Errorf("postgres: expected $N placeholders, got %q", pg.getClient)
	}
	my := New(nil, "mysql")
	if !strings.Contains(my.getClient, "?") || strings.Contains(my.getClient, "$1") {
		t.Errorf("mysql: expected ? placeholders, got %q", my.getClient)
	}
}

func TestNew_CreateClientPlaceholderCount(t *testing.T) {
	my := New(nil, "mysql")
	if got := strings.Count(my.createClient, "?"); got != 17 {
		t.Errorf("createClient: expected 17 placeholders, got %d in %q", got, my.createClient)
	}
}

func TestNew_UpdateClientIDLast(t *testing.T) {
	my := New(nil, "mysql")
	if got := strings.Count(my.updateClient, "?"); got != 11 {
		t.Errorf("updateClient: expected 11 placeholders, got %d in %q", got, my.updateClient)
	}
	// The WHERE id must be the final placeholder — UpdateClient binds id last.
	if !strings.Contains(my.updateClient, "WHERE id = ?") {
		t.Errorf("updateClient: expected 'WHERE id = ?' last, got %q", my.updateClient)
	}
}

func TestNew_PatchClientVariants(t *testing.T) {
	my := New(nil, "mysql")

	// Expected-value variant: 13 SET + WHERE id + optimistic-lock guard = 15.
	if got := strings.Count(my.patchClientExpected, "?"); got != 15 {
		t.Errorf("patchClientExpected: expected 15 placeholders, got %d in %q", got, my.patchClientExpected)
	}
	if !strings.HasSuffix(strings.TrimSpace(my.patchClientExpected), "AND upd_dtimes = ?") {
		t.Errorf("patchClientExpected: expected trailing 'AND upd_dtimes = ?', got %q", my.patchClientExpected)
	}

	// NULL variant: 13 SET + WHERE id = 14, guard is a literal IS NULL (no bind).
	if got := strings.Count(my.patchClientExpectedNull, "?"); got != 14 {
		t.Errorf("patchClientExpectedNull: expected 14 placeholders, got %d in %q", got, my.patchClientExpectedNull)
	}
	if !strings.HasSuffix(strings.TrimSpace(my.patchClientExpectedNull), "AND upd_dtimes IS NULL") {
		t.Errorf("patchClientExpectedNull: expected trailing 'AND upd_dtimes IS NULL', got %q", my.patchClientExpectedNull)
	}
}
