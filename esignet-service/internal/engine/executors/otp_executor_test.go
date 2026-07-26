/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"context"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
)

// fakeAuthnProvider is a hand-written stub of shared.ConsolidatedAuthnProvider used to drive
// otpExecutor without any real MOSIP/mock-identity-system dependency.
type fakeAuthnProvider struct {
	sendOTPResult   *shared.SendOTPResult
	sendOTPErr      *common.ServiceError
	lastIdentifiers map[string]interface{}
	lastMetadata    *providers.AuthnMetadata
}

func (f *fakeAuthnProvider) InitiateAuthentication(_ context.Context, _ string, _ any,
	_ *providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProvider) Authenticate(_ context.Context, _, _ map[string]interface{},
	_ *providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProvider) GetEntityReference(_ context.Context, _ any) (*providers.EntityReference,
	*common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProvider) GetAttributes(_ context.Context, _ any, _ *providers.RequestedAttributes,
	_ *providers.GetAttributesMetadata) (*providers.AttributesResponse, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProvider) InitiateEnrollment(_ context.Context, _ string, _ any,
	_ *providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProvider) Enroll(_ context.Context, _, _ map[string]interface{},
	_ *providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProvider) SendOTP(_ context.Context, identifiers map[string]interface{},
	metadata *providers.AuthnMetadata) (*shared.SendOTPResult, *common.ServiceError) {
	f.lastIdentifiers = identifiers
	f.lastMetadata = metadata
	return f.sendOTPResult, f.sendOTPErr
}

func newOtpNodeContext(userInputs, runtimeData map[string]string) *providers.NodeContext {
	if runtimeData == nil {
		runtimeData = map[string]string{}
	}
	return &providers.NodeContext{
		Context:     context.Background(),
		UserInputs:  userInputs,
		RuntimeData: runtimeData,
	}
}

func (ts *OtpExecutorTestSuite) TestNameTypeAndPrerequisites() {
	t := ts.T()
	e := NewOtpExecutor(&fakeAuthnProvider{})

	if e.GetName() != ExecutorNameEsignetOTP {
		t.Errorf("GetName() = %q, want %q", e.GetName(), ExecutorNameEsignetOTP)
	}
	if e.GetType() != providers.ExecutorTypeAuthentication {
		t.Errorf("GetType() = %q, want %q", e.GetType(), providers.ExecutorTypeAuthentication)
	}
	prereqs := e.GetPrerequisites()
	if len(prereqs) != 1 || prereqs[0].Identifier != usernameAttr {
		t.Errorf("GetPrerequisites() = %v, want single %q input", prereqs, usernameAttr)
	}
}

func (ts *OtpExecutorTestSuite) TestGetRequiredInputs() {
	t := ts.T()
	e := NewOtpExecutor(&fakeAuthnProvider{})

	t.Run("with configured node inputs", func(t *testing.T) {
		ctx := &providers.NodeContext{NodeInputs: []providers.Input{{Identifier: "custom"}}}
		got := e.GetRequiredInputs(ctx)
		if len(got) != 1 || got[0].Identifier != "custom" {
			t.Errorf("GetRequiredInputs() = %v, want configured node inputs", got)
		}
	})

	t.Run("without configured node inputs falls back to defaults", func(t *testing.T) {
		ctx := &providers.NodeContext{}
		got := e.GetRequiredInputs(ctx)
		if got != nil {
			t.Errorf("GetRequiredInputs() = %v, want nil default", got)
		}
	})
}

func (ts *OtpExecutorTestSuite) TestValidatePrerequisites() {
	t := ts.T()
	e := NewOtpExecutor(&fakeAuthnProvider{})

	t.Run("username in user inputs", func(t *testing.T) {
		ctx := newOtpNodeContext(map[string]string{usernameAttr: "user1"}, nil)
		if !e.ValidatePrerequisites(ctx, &providers.ExecutorResponse{}, nil) {
			t.Error("expected ValidatePrerequisites to be true")
		}
	})

	t.Run("username missing", func(t *testing.T) {
		ctx := newOtpNodeContext(map[string]string{}, nil)
		if e.ValidatePrerequisites(ctx, &providers.ExecutorResponse{}, nil) {
			t.Error("expected ValidatePrerequisites to be false")
		}
	})
}

func (ts *OtpExecutorTestSuite) TestExecuteMissingUsernameRequestsInput() {
	t := ts.T()
	e := NewOtpExecutor(&fakeAuthnProvider{})
	ctx := newOtpNodeContext(map[string]string{}, nil)

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecUserInputRequired {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecUserInputRequired)
	}
	if len(resp.Inputs) != 1 || resp.Inputs[0].Identifier != usernameAttr {
		t.Errorf("Inputs = %v, want single %q input", resp.Inputs, usernameAttr)
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteSuccessViaUserInputs() {
	t := ts.T()
	provider := &fakeAuthnProvider{
		sendOTPResult: &shared.SendOTPResult{
			TransactionID: "txn-1",
			MaskedEmail:   "j***@example.com",
			MaskedMobile:  "9***789",
		},
	}
	e := NewOtpExecutor(provider)
	ctx := newOtpNodeContext(map[string]string{usernameAttr: "user1"}, map[string]string{})

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecComplete {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecComplete)
	}
	if resp.ForwardedData[maskedEmail] != provider.sendOTPResult.MaskedEmail {
		t.Errorf("ForwardedData[maskedEmail] = %v, want %v", resp.ForwardedData[maskedEmail], provider.sendOTPResult.MaskedEmail)
	}
	if resp.ForwardedData[maskedMobile] != provider.sendOTPResult.MaskedMobile {
		t.Errorf("ForwardedData[maskedMobile] = %v, want %v", resp.ForwardedData[maskedMobile], provider.sendOTPResult.MaskedMobile)
	}
	if got := ctx.RuntimeData[providerExtendedKeyPrefix+"TransactionID"]; got != "txn-1" {
		t.Errorf("RuntimeData transaction id = %q, want txn-1", got)
	}
	if provider.lastIdentifiers[usernameAttr] != "user1" {
		t.Errorf("SendOTP identifiers[username] = %v, want user1", provider.lastIdentifiers[usernameAttr])
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteSuccessViaRuntimeData() {
	t := ts.T()
	provider := &fakeAuthnProvider{sendOTPResult: &shared.SendOTPResult{TransactionID: "txn-2"}}
	e := NewOtpExecutor(provider)
	ctx := newOtpNodeContext(map[string]string{}, map[string]string{usernameAttr: "user2"})

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecComplete {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecComplete)
	}
	if provider.lastIdentifiers[usernameAttr] != "user2" {
		t.Errorf("SendOTP identifiers[username] = %v, want user2", provider.lastIdentifiers[usernameAttr])
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteClientErrorReturnsFailureStatus() {
	t := ts.T()
	provider := &fakeAuthnProvider{sendOTPErr: shared.SendOTPFailedError}
	e := NewOtpExecutor(provider)
	ctx := newOtpNodeContext(map[string]string{usernameAttr: "user1"}, map[string]string{})

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v, want nil (client error surfaces via response)", err)
	}
	if resp.Status != providers.ExecFailure {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecFailure)
	}
	if resp.Error != shared.SendOTPFailedError {
		t.Errorf("Error = %v, want %v", resp.Error, shared.SendOTPFailedError)
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteServerErrorPropagatesAsGoError() {
	t := ts.T()
	provider := &fakeAuthnProvider{sendOTPErr: &common.ServiceError{Code: "internal_error", Type: common.ServerErrorType}}
	e := NewOtpExecutor(provider)
	ctx := newOtpNodeContext(map[string]string{usernameAttr: "user1"}, map[string]string{})

	resp, err := e.Execute(ctx)
	if err == nil {
		t.Fatal("expected non-nil error for server-side failure")
	}
	if resp.Status == providers.ExecComplete {
		t.Errorf("Status = %q, want non-complete", resp.Status)
	}
}

type OtpExecutorTestSuite struct {
	suite.Suite
}

func TestOtpExecutorTestSuite(t *testing.T) {
	suite.Run(t, new(OtpExecutorTestSuite))
}
