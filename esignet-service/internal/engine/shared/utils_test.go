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
	runtimeMetadata := map[string][]string{transactionIDKey: {"1234567890"}}

	id, err := GenerateTransactionID(runtimeMetadata)
	if err != nil {
		t.Fatalf("GenerateTransactionID() error = %v", err)
	}
	if id != "1234567890" {
		t.Errorf("GenerateTransactionID() = %q, want %q", id, "1234567890")
	}
}

func (ts *UtilsTestSuite) TestGenerateTransactionIDGeneratesNewNumericID() {
	t := ts.T()

	t.Run("nil runtime metadata", func(t *testing.T) {
		id, err := GenerateTransactionID(nil)
		if err != nil {
			t.Fatalf("GenerateTransactionID() error = %v", err)
		}
		assertTenDigitNumeric(t, id)
	})

	t.Run("runtime metadata without existing transaction id", func(t *testing.T) {
		id, err := GenerateTransactionID(map[string][]string{})
		if err != nil {
			t.Fatalf("GenerateTransactionID() error = %v", err)
		}
		assertTenDigitNumeric(t, id)
	})

	t.Run("runtime metadata with empty (non-nil) transaction id slice", func(t *testing.T) {
		id, err := GenerateTransactionID(map[string][]string{transactionIDKey: {}})
		if err != nil {
			t.Fatalf("GenerateTransactionID() error = %v", err)
		}
		assertTenDigitNumeric(t, id)
	})
}

func (ts *UtilsTestSuite) TestGenerateTransactionIDIsRandomAcrossCalls() {
	t := ts.T()
	first, err := GenerateTransactionID(nil)
	if err != nil {
		t.Fatalf("GenerateTransactionID() error = %v", err)
	}
	second, err := GenerateTransactionID(nil)
	if err != nil {
		t.Fatalf("GenerateTransactionID() error = %v", err)
	}
	// Not a hard guarantee, but with 10^10 possibilities collision is negligible
	// and repeated equality would indicate a broken random source.
	if first == second {
		t.Errorf("expected distinct transaction ids across calls, got %q twice", first)
	}
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
