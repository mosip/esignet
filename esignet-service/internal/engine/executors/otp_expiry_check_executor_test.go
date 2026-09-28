/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
)

func newExpiryCheckNodeContext(runtimeData map[string]string, properties map[string]interface{}) *providers.NodeContext {
	if runtimeData == nil {
		runtimeData = map[string]string{}
	}
	return &providers.NodeContext{
		Context:        context.Background(),
		RuntimeData:    runtimeData,
		NodeProperties: properties,
	}
}

func (ts *OtpExpiryCheckExecutorTestSuite) TestNameAndType() {
	t := ts.T()
	e := NewOtpExpiryCheckExecutor()

	if e.GetName() != ExecutorNameEsignetOTPExpiryCheck {
		t.Errorf("GetName() = %q, want %q", e.GetName(), ExecutorNameEsignetOTPExpiryCheck)
	}
	if e.GetType() != providers.ExecutorTypeUtility {
		t.Errorf("GetType() = %q, want %q", e.GetType(), providers.ExecutorTypeUtility)
	}
}

func (ts *OtpExpiryCheckExecutorTestSuite) TestGetMetaDeclaresValidityProperty() {
	t := ts.T()
	e := NewOtpExpiryCheckExecutor()

	meta := e.GetMeta()
	if len(meta.SupportedProperties) != 1 || meta.SupportedProperties[0].Property != propertyKeyOTPValiditySeconds {
		t.Errorf("GetMeta().SupportedProperties = %v, want single %q property",
			meta.SupportedProperties, propertyKeyOTPValiditySeconds)
	}
}

func (ts *OtpExpiryCheckExecutorTestSuite) TestMissingIssuedAtReturnsFailure() {
	t := ts.T()
	e := NewOtpExpiryCheckExecutor()
	ctx := newExpiryCheckNodeContext(nil, nil)

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecFailure {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecFailure)
	}
	if resp.Error != shared.InvalidOTPError {
		t.Errorf("Error = %v, want InvalidOTPError", resp.Error)
	}
}

func (ts *OtpExpiryCheckExecutorTestSuite) TestUnparsableIssuedAtReturnsFailure() {
	t := ts.T()
	e := NewOtpExpiryCheckExecutor()
	ctx := newExpiryCheckNodeContext(map[string]string{otpIssuedAtKey: "not-a-timestamp"}, nil)

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecFailure {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecFailure)
	}
	if resp.Error != shared.InvalidOTPError {
		t.Errorf("Error = %v, want InvalidOTPError", resp.Error)
	}
}

func (ts *OtpExpiryCheckExecutorTestSuite) TestFreshOTPIsAccepted() {
	t := ts.T()
	e := NewOtpExpiryCheckExecutor()
	issuedAt := time.Now().UTC().Add(-30 * time.Second).Format(time.RFC3339)
	ctx := newExpiryCheckNodeContext(map[string]string{otpIssuedAtKey: issuedAt}, nil)

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecComplete {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecComplete)
	}
}

func (ts *OtpExpiryCheckExecutorTestSuite) TestExpiredOTPIsRejected() {
	t := ts.T()
	e := NewOtpExpiryCheckExecutor()
	// OTP issued 10 minutes ago, default validity is 5 minutes.
	issuedAt := time.Now().UTC().Add(-10 * time.Minute).Format(time.RFC3339)
	ctx := newExpiryCheckNodeContext(map[string]string{otpIssuedAtKey: issuedAt}, nil)

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecFailure {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecFailure)
	}
	if resp.Error != shared.InvalidOTPError {
		t.Errorf("Error = %v, want InvalidOTPError", resp.Error)
	}
}

func (ts *OtpExpiryCheckExecutorTestSuite) TestCustomValidityWindowRespected() {
	t := ts.T()
	e := NewOtpExpiryCheckExecutor()

	t.Run("inside custom window is accepted", func(t *testing.T) {
		issuedAt := time.Now().UTC().Add(-50 * time.Second).Format(time.RFC3339)
		ctx := newExpiryCheckNodeContext(
			map[string]string{otpIssuedAtKey: issuedAt},
			map[string]interface{}{propertyKeyOTPValiditySeconds: "60"},
		)
		resp, err := e.Execute(ctx)
		if err != nil {
			t.Fatalf("Execute() error = %v", err)
		}
		if resp.Status != providers.ExecComplete {
			t.Errorf("Status = %q, want %q", resp.Status, providers.ExecComplete)
		}
	})

	t.Run("outside custom window is rejected", func(t *testing.T) {
		issuedAt := time.Now().UTC().Add(-90 * time.Second).Format(time.RFC3339)
		ctx := newExpiryCheckNodeContext(
			map[string]string{otpIssuedAtKey: issuedAt},
			map[string]interface{}{propertyKeyOTPValiditySeconds: "60"},
		)
		resp, err := e.Execute(ctx)
		if err != nil {
			t.Fatalf("Execute() error = %v", err)
		}
		if resp.Status != providers.ExecFailure {
			t.Errorf("Status = %q, want %q", resp.Status, providers.ExecFailure)
		}
		if resp.Error != shared.InvalidOTPError {
			t.Errorf("Error = %v, want InvalidOTPError", resp.Error)
		}
	})
}

func (ts *OtpExpiryCheckExecutorTestSuite) TestOtpValiditySecondsFromContext() {
	t := ts.T()

	cases := []struct {
		name       string
		properties map[string]interface{}
		want       int
	}{
		{"absent falls back to default", nil, defaultOTPValiditySeconds},
		{"string value", map[string]interface{}{propertyKeyOTPValiditySeconds: "120"}, 120},
		{"int value", map[string]interface{}{propertyKeyOTPValiditySeconds: 120}, 120},
		{"float64 value", map[string]interface{}{propertyKeyOTPValiditySeconds: float64(120)}, 120},
		{"invalid string falls back to default", map[string]interface{}{propertyKeyOTPValiditySeconds: "not-a-number"}, defaultOTPValiditySeconds},
		{"zero falls back to default", map[string]interface{}{propertyKeyOTPValiditySeconds: 0}, defaultOTPValiditySeconds},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			ctx := &providers.NodeContext{NodeProperties: c.properties}
			if got := otpValiditySecondsFromContext(ctx); got != c.want {
				t.Errorf("otpValiditySecondsFromContext() = %d, want %d", got, c.want)
			}
		})
	}
}

type OtpExpiryCheckExecutorTestSuite struct {
	suite.Suite
}

func TestOtpExpiryCheckExecutorTestSuite(t *testing.T) {
	suite.Run(t, new(OtpExpiryCheckExecutorTestSuite))
}
