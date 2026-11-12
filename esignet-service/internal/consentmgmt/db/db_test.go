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
		{"insertConsent", my.insertConsent, 10},
	}
	for _, c := range cases {
		if got := strings.Count(c.query, "?"); got != c.want {
			t.Errorf("%s: expected %d placeholders, got %d in %q", c.name, c.want, got, c.query)
		}
	}
}
