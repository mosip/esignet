/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

import (
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
)

func (ts *ErrorsTestSuite) TestServiceErrorsAreWellFormed() {
	t := ts.T()
	cases := []struct {
		name string
		err  *common.ServiceError
	}{
		{"NotImplementedError", NotImplementedError},
		{"ClientNotFoundError", ClientNotFoundError},
		{"InvalidIndividualIDError", InvalidIndividualIDError},
		{"InvalidRequestError", InvalidRequestError},
		{"AuthenticationFailedError", AuthenticationFailedError},
		{"SendOTPFailedError", SendOTPFailedError},
		{"FileNotFoundError", FileNotFoundError},
		{"FileUnmarshallError", FileUnmarshallError},
	}

	seenCodes := make(map[string]string, len(cases))
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if c.err == nil {
				t.Fatal("expected non-nil service error")
			}
			if c.err.Code == "" {
				t.Error("expected non-empty Code")
			}
			if c.err.Type != common.ClientErrorType {
				t.Errorf("Type = %q, want %q", c.err.Type, common.ClientErrorType)
			}
			if c.err.Error.Key == "" || c.err.Error.DefaultValue == "" {
				t.Errorf("Error message incomplete: %+v", c.err.Error)
			}
			if c.err.ErrorDescription.Key == "" || c.err.ErrorDescription.DefaultValue == "" {
				t.Errorf("ErrorDescription incomplete: %+v", c.err.ErrorDescription)
			}
			if existing, ok := seenCodes[c.err.Code]; ok {
				t.Errorf("duplicate error code %q shared with %s", c.err.Code, existing)
			}
			seenCodes[c.err.Code] = c.name
		})
	}
}

type ErrorsTestSuite struct {
	suite.Suite
}

func TestErrorsTestSuite(t *testing.T) {
	suite.Run(t, new(ErrorsTestSuite))
}
