/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/suite"
)

func (ts *ModelTestSuite) TestSendOTPResultMarshalsMaskedFields() {
	t := ts.T()
	result := SendOTPResult{
		TransactionID: "txn-123",
		MaskedEmail:   "j***@example.com",
		MaskedMobile:  "9*****789",
	}

	data, err := json.Marshal(result)
	if err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}

	var decoded map[string]interface{}
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal() error = %v", err)
	}

	if decoded["maskedEmail"] != result.MaskedEmail {
		t.Errorf("maskedEmail = %v, want %v", decoded["maskedEmail"], result.MaskedEmail)
	}
	if decoded["maskedMobile"] != result.MaskedMobile {
		t.Errorf("maskedMobile = %v, want %v", decoded["maskedMobile"], result.MaskedMobile)
	}
	// TransactionID has no json tag, so it is exported under its Go field name.
	if decoded["TransactionID"] != result.TransactionID {
		t.Errorf("TransactionID = %v, want %v", decoded["TransactionID"], result.TransactionID)
	}
}

func (ts *ModelTestSuite) TestSendOTPResultOmitsEmptyMaskedFields() {
	t := ts.T()
	result := SendOTPResult{TransactionID: "txn-456"}

	data, err := json.Marshal(result)
	if err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}

	var decoded map[string]interface{}
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal() error = %v", err)
	}
	if _, ok := decoded["maskedEmail"]; ok {
		t.Error("expected maskedEmail to be omitted when empty")
	}
	if _, ok := decoded["maskedMobile"]; ok {
		t.Error("expected maskedMobile to be omitted when empty")
	}
}

type ModelTestSuite struct {
	suite.Suite
}

func TestModelTestSuite(t *testing.T) {
	suite.Run(t, new(ModelTestSuite))
}
