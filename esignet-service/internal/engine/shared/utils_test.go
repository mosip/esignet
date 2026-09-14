/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

import (
	"testing"

	"github.com/stretchr/testify/suite"
)

func (ts *UtilsTestSuite) TestGenerateTransactionIDReusesExistingID() {
	t := ts.T()
	runtimeMetadata := map[string][]string{TransactionIDKey: {"1234567890"}}

	id, err := GenerateTransactionID(runtimeMetadata, defaultAuthTransactionIDLength)
	if err != nil {
		t.Fatalf("GenerateTransactionID() error = %v", err)
	}
	if want := "1234567890"; id != want {
		t.Errorf("GenerateTransactionID() = %q, want %q", id, want)
	}
}

func (ts *UtilsTestSuite) TestGenerateTransactionIDGeneratesNewNumericID() {
	t := ts.T()

	t.Run("nil runtime metadata", func(t *testing.T) {
		id, err := GenerateTransactionID(nil, defaultAuthTransactionIDLength)
		if err != nil {
			t.Fatalf("GenerateTransactionID() error = %v", err)
		}
		assertTenDigitNumeric(t, id)
	})

	t.Run("runtime metadata without existing transaction id", func(t *testing.T) {
		id, err := GenerateTransactionID(map[string][]string{}, defaultAuthTransactionIDLength)
		if err != nil {
			t.Fatalf("GenerateTransactionID() error = %v", err)
		}
		assertTenDigitNumeric(t, id)
	})

	t.Run("runtime metadata with empty (non-nil) transaction id slice", func(t *testing.T) {
		id, err := GenerateTransactionID(map[string][]string{TransactionIDKey: {}}, defaultAuthTransactionIDLength)
		if err != nil {
			t.Fatalf("GenerateTransactionID() error = %v", err)
		}
		assertTenDigitNumeric(t, id)
	})

	t.Run("non-positive length falls back to default", func(t *testing.T) {
		id, err := GenerateTransactionID(nil, 0)
		if err != nil {
			t.Fatalf("GenerateTransactionID() error = %v", err)
		}
		assertTenDigitNumeric(t, id)
	})

	t.Run("custom positive length is honored", func(t *testing.T) {
		id, err := GenerateTransactionID(nil, 6)
		if err != nil {
			t.Fatalf("GenerateTransactionID() error = %v", err)
		}
		if len(id) != 6 {
			t.Fatalf("GenerateTransactionID() len = %d, want 6 (id=%q)", len(id), id)
		}
	})
}

func (ts *UtilsTestSuite) TestGenerateTransactionIDIsRandomAcrossCalls() {
	t := ts.T()
	first, err := GenerateTransactionID(nil, defaultAuthTransactionIDLength)
	if err != nil {
		t.Fatalf("GenerateTransactionID() error = %v", err)
	}
	second, err := GenerateTransactionID(nil, defaultAuthTransactionIDLength)
	if err != nil {
		t.Fatalf("GenerateTransactionID() error = %v", err)
	}
	// Not a hard guarantee, but with 10^10 possibilities collision is negligible
	// and repeated equality would indicate a broken random source.
	if first == second {
		t.Errorf("expected distinct transaction ids across calls, got %q twice", first)
	}
}

func (ts *UtilsTestSuite) TestDeriveAuthTransactionID() {
	t := ts.T()

	tests := []struct {
		name              string
		oidcTransactionID string
		want              string
	}{
		{
			name:              "10-char input is read back to front",
			oidcTransactionID: "1234567890",
			want:              "0987654321",
		},
		{
			name:              "hyphens and underscores are stripped before deriving",
			oidcTransactionID: "0199183a-8f2e-7abc_def0-1234-56789abcdef0",
			want:              mustDeriveAuthTransactionID(t, "0199183a8f2e7abcdef0123456789abcdef0"),
		},
		{
			name:              "input shorter than target length wraps cyclically",
			oidcTransactionID: "abc",
			want:              "cbacbacbac",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := DeriveAuthTransactionID(tt.oidcTransactionID, defaultAuthTransactionIDLength)
			if err != nil {
				t.Fatalf("DeriveAuthTransactionID(%q) error = %v", tt.oidcTransactionID, err)
			}
			if got != tt.want {
				t.Fatalf("DeriveAuthTransactionID(%q) = %q, want %q", tt.oidcTransactionID, got, tt.want)
			}
			if len(got) != 10 {
				t.Fatalf("DeriveAuthTransactionID(%q) length = %d, want 10", tt.oidcTransactionID, len(got))
			}
		})
	}
}

func (ts *UtilsTestSuite) TestDeriveAuthTransactionIDLength() {
	t := ts.T()

	t.Run("non-positive length falls back to default", func(t *testing.T) {
		got, err := DeriveAuthTransactionID("1234567890", 0)
		if err != nil {
			t.Fatalf("DeriveAuthTransactionID() error = %v", err)
		}
		if len(got) != defaultAuthTransactionIDLength {
			t.Fatalf("DeriveAuthTransactionID() len = %d, want %d", len(got), defaultAuthTransactionIDLength)
		}
	})

	t.Run("custom positive length is honored", func(t *testing.T) {
		got, err := DeriveAuthTransactionID("1234567890", 6)
		if err != nil {
			t.Fatalf("DeriveAuthTransactionID() error = %v", err)
		}
		if want := "098765"; got != want {
			t.Fatalf("DeriveAuthTransactionID() = %q, want %q", got, want)
		}
	})
}

func (ts *UtilsTestSuite) TestDeriveAuthTransactionIDErrorsOnEmptyInput() {
	t := ts.T()

	tests := []string{"", "-", "_", "-_-_"}
	for _, in := range tests {
		t.Run(in, func(t *testing.T) {
			if _, err := DeriveAuthTransactionID(in, defaultAuthTransactionIDLength); err == nil {
				t.Fatalf("DeriveAuthTransactionID(%q) error = nil, want error", in)
			}
		})
	}
}

func mustDeriveAuthTransactionID(t *testing.T, oidcTransactionID string) string {
	t.Helper()
	got, err := DeriveAuthTransactionID(oidcTransactionID, defaultAuthTransactionIDLength)
	if err != nil {
		t.Fatalf("DeriveAuthTransactionID(%q) error = %v", oidcTransactionID, err)
	}
	return got
}

func (ts *UtilsTestSuite) TestNormalizeClaimLocales() {
	t := ts.T()

	tests := []struct {
		name string
		raw  string
		want []string
	}{
		{name: "empty input yields empty (non-nil) slice", raw: "", want: []string{}},
		{name: "blank input yields empty (non-nil) slice", raw: "   ", want: []string{}},
		{name: "single 2-letter code converts to ISO 639-2/T", raw: "en", want: []string{"eng"}},
		{name: "already 3-letter code passes through unchanged", raw: "eng", want: []string{"eng"}},
		{name: "space-separated multi-locale converts each token", raw: "en fr", want: []string{"eng", "fra"}},
		{name: "extra/mixed whitespace between tokens is ignored", raw: "  en   hi ", want: []string{"eng", "hin"}},
		{name: "unknown code that fails conversion is dropped", raw: "xx", want: []string{}},
		{name: "unknown code among valid ones is dropped, valid ones kept", raw: "en xx fr", want: []string{"eng", "fra"}},
		{name: "already-3-letter unknown code passes through unvalidated", raw: "abc", want: []string{"abc"}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := NormalizeClaimLocales(tt.raw)
			if got == nil {
				t.Fatalf("NormalizeClaimLocales(%q) returned nil, want non-nil slice", tt.raw)
			}
			if len(got) != len(tt.want) {
				t.Fatalf("NormalizeClaimLocales(%q) = %v, want %v", tt.raw, got, tt.want)
			}
			for i := range got {
				if got[i] != tt.want[i] {
					t.Fatalf("NormalizeClaimLocales(%q) = %v, want %v", tt.raw, got, tt.want)
				}
			}
		})
	}
}

func (ts *UtilsTestSuite) TestAllowedAuthorizationScopes() {
	t := ts.T()

	tests := []struct {
		name             string
		additionalConfig map[string]any
		want             []string
	}{
		{name: "nil additional_config yields nil", additionalConfig: nil, want: nil},
		{name: "key absent yields nil", additionalConfig: map[string]any{}, want: nil},
		{name: "value is not a []any yields nil", additionalConfig: map[string]any{AllowedAuthorizationScopesKey: "payment:pay"}, want: nil},
		{name: "empty []any yields empty (non-nil) slice", additionalConfig: map[string]any{AllowedAuthorizationScopesKey: []any{}}, want: []string{}},
		{
			name:             "[]any of strings extracted in order",
			additionalConfig: map[string]any{AllowedAuthorizationScopesKey: []any{"payment:pay", "profile:read"}},
			want:             []string{"payment:pay", "profile:read"},
		},
		{
			name:             "non-string items in []any are dropped",
			additionalConfig: map[string]any{AllowedAuthorizationScopesKey: []any{"payment:pay", 42, "profile:read"}},
			want:             []string{"payment:pay", "profile:read"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := AllowedAuthorizationScopes(tt.additionalConfig)
			if tt.want == nil {
				if got != nil {
					t.Fatalf("AllowedAuthorizationScopes(%v) = %v, want nil", tt.additionalConfig, got)
				}
				return
			}
			if got == nil {
				t.Fatalf("AllowedAuthorizationScopes(%v) returned nil, want non-nil slice", tt.additionalConfig)
			}
			if len(got) != len(tt.want) {
				t.Fatalf("AllowedAuthorizationScopes(%v) = %v, want %v", tt.additionalConfig, got, tt.want)
			}
			for i := range got {
				if got[i] != tt.want[i] {
					t.Fatalf("AllowedAuthorizationScopes(%v) = %v, want %v", tt.additionalConfig, got, tt.want)
				}
			}
		})
	}
}

func assertTenDigitNumeric(t *testing.T, id string) {
	t.Helper()
	if len(id) != 10 {
		t.Fatalf("len(id) = %d, want 10 (id=%q)", len(id), id)
	}
	for _, r := range id {
		if r < '0' || r > '9' {
			t.Fatalf("id %q contains non-digit character %q", id, r)
		}
	}
}

type UtilsTestSuite struct {
	suite.Suite
}

func TestUtilsTestSuite(t *testing.T) {
	suite.Run(t, new(UtilsTestSuite))
}
