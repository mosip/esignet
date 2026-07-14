package keymanager_test

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/db"
)

func TestExpiryFor(t *testing.T) {
	gen := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	policy := db.KeyPolicy{KeyValidityDuration: 730}
	got := keymanager.ExpiryFor(gen, policy)
	assert.Equal(t, time.Date(2028, 1, 1, 0, 0, 0, 0, time.UTC), got)
}

func TestIsCurrent(t *testing.T) {
	gen := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	expiry := time.Date(2028, 1, 1, 0, 0, 0, 0, time.UTC) // 730 days validity
	preExpireDays := 30

	tests := []struct {
		name string
		now  time.Time
		want bool
	}{
		{"before generation", gen.Add(-time.Hour), false},
		{"just after generation", gen.Add(time.Minute), true},
		{"well within validity", expiry.AddDate(0, -6, 0), true},
		{"exactly at pre-expiry cutoff", expiry.AddDate(0, 0, -preExpireDays), false},
		{"just before pre-expiry cutoff", expiry.AddDate(0, 0, -preExpireDays).Add(-time.Second), true},
		{"past hard expiry", expiry.Add(time.Hour), false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := keymanager.IsCurrent(tt.now, gen, expiry, preExpireDays)
			assert.Equal(t, tt.want, got)
		})
	}
}

// TestUniqueIdentifier pins the hash formula against independently computed
// (via `shasum -a1`, not the code under test) SHA1 values, catching any
// drift in the date format or hex/case conversion.
func TestUniqueIdentifier(t *testing.T) {
	date := time.Date(2026, 7, 6, 0, 0, 0, 0, time.UTC)
	got := keymanager.UniqueIdentifier("ROOT", "", date)
	assert.Equal(t, "6926E251F1E7578CFFDA583E0DC7B38C1DE2C642", got)

	date2 := time.Date(2030, 1, 15, 0, 0, 0, 0, time.UTC)
	got2 := keymanager.UniqueIdentifier("ESIGNET_RSA", "RSA_2048", date2)
	assert.Equal(t, "32981FDB2FC4B3360CCC4ED7F3D6E03A4EECA371", got2)
}

func TestIsDuplicateUniIdent(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want bool
	}{
		{"nil error", nil, false},
		{"unrelated error", assertErr("insert key_alias: connection refused"), false},
		{"unique violation on a different constraint", assertErr("pq: duplicate key value violates unique constraint \"pk_keymals_id\" (SQLSTATE 23505)"), false},
		{"uni_ident conflict", assertErr("pq: duplicate key value violates unique constraint \"uni_ident_const\" (SQLSTATE 23505)"), true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.want, keymanager.IsDuplicateUniIdent(tt.err))
		})
	}
}

type simpleErr string

func (e simpleErr) Error() string { return string(e) }
func assertErr(s string) error    { return simpleErr(s) }
