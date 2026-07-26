/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"testing"

	"github.com/stretchr/testify/suite"
)

func (ts *InitTestSuite) TestInitializeRegistersAllExecutors() {
	t := ts.T()
	registered := Initialize(&fakeAuthnProvider{})

	if len(registered) != 2 {
		t.Fatalf("len(registered) = %d, want 2", len(registered))
	}

	clearInputs, ok := registered[ExecutorNameEsignetClearInputs]
	if !ok {
		t.Fatal("expected clear-inputs executor to be registered")
	}
	if clearInputs.GetName() != ExecutorNameEsignetClearInputs {
		t.Errorf("GetName() = %q, want %q", clearInputs.GetName(), ExecutorNameEsignetClearInputs)
	}

	otp, ok := registered[ExecutorNameEsignetOTP]
	if !ok {
		t.Fatal("expected otp executor to be registered")
	}
	if otp.GetName() != ExecutorNameEsignetOTP {
		t.Errorf("GetName() = %q, want %q", otp.GetName(), ExecutorNameEsignetOTP)
	}
}

type InitTestSuite struct {
	suite.Suite
}

func TestInitTestSuite(t *testing.T) {
	suite.Run(t, new(InitTestSuite))
}
