package db

import (
	"strings"
	"testing"
)

// New only builds query strings (it never touches conn), so a nil TX is safe
// here and lets these tests inspect the rebound SQL without a database.

func TestNew_RebindsPlaceholdersPerDriver(t *testing.T) {
	pg := New(nil, "keymgr", "postgres")
	if !strings.Contains(pg.selectKeyAlias, "$1") {
		t.Errorf("postgres: expected $N placeholders, got %q", pg.selectKeyAlias)
	}
	if strings.Contains(pg.selectKeyAlias, "?") {
		t.Errorf("postgres: expected no ? placeholders, got %q", pg.selectKeyAlias)
	}

	my := New(nil, "keymgr", "mysql")
	if !strings.Contains(my.selectKeyAlias, "?") {
		t.Errorf("mysql: expected ? placeholders, got %q", my.selectKeyAlias)
	}
	if strings.Contains(my.selectKeyAlias, "$1") {
		t.Errorf("mysql: expected no $N placeholders, got %q", my.selectKeyAlias)
	}
}

func TestNew_SelectKeyAliasHasThreePlaceholders(t *testing.T) {
	// selectKeyAlias binds app_id, ref_id, and the ref_id = '' NULL-guard, so it
	// must expand to exactly three positional parameters (see
	// GetKeyAliasesByAppRef, which binds refID twice).
	my := New(nil, "keymgr", "mysql")
	if got := strings.Count(my.selectKeyAlias, "?"); got != 3 {
		t.Errorf("mysql selectKeyAlias: expected 3 placeholders, got %d in %q", got, my.selectKeyAlias)
	}
	pg := New(nil, "keymgr", "postgres")
	for _, p := range []string{"$1", "$2", "$3"} {
		if !strings.Contains(pg.selectKeyAlias, p) {
			t.Errorf("postgres selectKeyAlias: expected %s in %q", p, pg.selectKeyAlias)
		}
	}
}

func TestNew_UpdateStatementsPlaceholderCounts(t *testing.T) {
	// The two UPDATEs put the WHERE id last; their arg lists are reordered to
	// match. Guard the placeholder counts so a stray column change is caught.
	q := New(nil, "keymgr", "mysql")
	if got := strings.Count(q.updateKeyAlias, "?"); got != 6 {
		t.Errorf("updateKeyAlias: expected 6 placeholders, got %d in %q", got, q.updateKeyAlias)
	}
	if got := strings.Count(q.updateKeyStore, "?"); got != 5 {
		t.Errorf("updateKeyStore: expected 5 placeholders, got %d in %q", got, q.updateKeyStore)
	}
	if !strings.Contains(q.updateKeyAlias, "WHERE id = ?") {
		t.Errorf("updateKeyAlias: expected WHERE id = ? last, got %q", q.updateKeyAlias)
	}
	if !strings.Contains(q.updateKeyStore, "WHERE id = ?") {
		t.Errorf("updateKeyStore: expected WHERE id = ? last, got %q", q.updateKeyStore)
	}
}

func TestNew_InvalidSchemaPanics(t *testing.T) {
	defer func() {
		if recover() == nil {
			t.Error("expected panic for invalid schema identifier")
		}
	}()
	New(nil, "bad-schema!", "postgres")
}
