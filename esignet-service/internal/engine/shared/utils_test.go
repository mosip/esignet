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
