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

func (f *fakeAuthnProvider) GetSigningCertificates(_ context.Context) ([]shared.CertificateData, *common.ServiceError) {
	return nil, nil
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
	if len(prereqs) != 1 || prereqs[0].Identifier != "username" {
		t.Errorf("GetPrerequisites() = %v, want single %q input", prereqs, "username")
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

	t.Run("without configured node inputs falls back to the untyped identifier", func(t *testing.T) {
		ctx := &providers.NodeContext{}
		got := e.GetRequiredInputs(ctx)
		if len(got) != 1 || got[0].Identifier != shared.LoginIDUsername {
			t.Errorf("GetRequiredInputs() = %v, want the default identifier input", got)
		}
	})
}

func (ts *OtpExecutorTestSuite) TestValidatePrerequisites() {
	t := ts.T()
	e := NewOtpExecutor(&fakeAuthnProvider{})

	t.Run("username in user inputs", func(t *testing.T) {
		ctx := newOtpNodeContext(map[string]string{"username": "user1"}, nil)
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
	if len(resp.Inputs) != 1 || resp.Inputs[0].Identifier != "username" {
		t.Errorf("Inputs = %v, want single %q input", resp.Inputs, "username")
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
	ctx := newOtpNodeContext(map[string]string{"username": "user1"}, map[string]string{})

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
	if provider.lastIdentifiers["username"] != "user1" {
		t.Errorf("SendOTP identifiers[username] = %v, want user1", provider.lastIdentifiers["username"])
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteSuccessViaRuntimeData() {
	t := ts.T()
	provider := &fakeAuthnProvider{sendOTPResult: &shared.SendOTPResult{TransactionID: "txn-2"}}
	e := NewOtpExecutor(provider)
	ctx := newOtpNodeContext(map[string]string{}, map[string]string{"username": "user2"})

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecComplete {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecComplete)
	}
	if provider.lastIdentifiers["username"] != "user2" {
		t.Errorf("SendOTP identifiers[username] = %v, want user2", provider.lastIdentifiers["username"])
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteClientErrorReturnsUserInputRequiredStatus() {
	t := ts.T()
	provider := &fakeAuthnProvider{sendOTPErr: shared.SendOTPFailedError}
	e := NewOtpExecutor(provider)
	ctx := newOtpNodeContext(map[string]string{"username": "user1"}, map[string]string{})

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v, want nil (client error surfaces via response)", err)
	}
	// ExecUserInputRequired (not ExecFailure) keeps the flow session alive so the user can
	// correct the individual ID and retry, instead of terminating with flowStatus: ERROR.
	if resp.Status != providers.ExecUserInputRequired {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecUserInputRequired)
	}
	if len(resp.Inputs) != 1 || resp.Inputs[0].Identifier != "username" {
		t.Errorf("Inputs = %v, want [%s]", resp.Inputs, "username")
	}
	if resp.Error != shared.SendOTPFailedError {
		t.Errorf("Error = %v, want %v", resp.Error, shared.SendOTPFailedError)
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteClientErrorIncrementsAttemptCount() {
	t := ts.T()
	provider := &fakeAuthnProvider{sendOTPErr: shared.SendOTPFailedError}
	e := NewOtpExecutor(provider)
	ctx := newOtpNodeContext(map[string]string{"username": "user1"}, map[string]string{})

	if _, err := e.Execute(ctx); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if got := ctx.RuntimeData[otpAttemptCountKey]; got != "1" {
		t.Errorf("RuntimeData[otpAttemptCountKey] = %q, want %q", got, "1")
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteServerErrorPropagatesAsGoError() {
	t := ts.T()
	provider := &fakeAuthnProvider{sendOTPErr: &common.ServiceError{Code: "internal_error", Type: common.ServerErrorType}}
	e := NewOtpExecutor(provider)
	ctx := newOtpNodeContext(map[string]string{"username": "user1"}, map[string]string{})

	resp, err := e.Execute(ctx)
	if err == nil {
		t.Fatal("expected non-nil error for server-side failure")
	}
	if resp.Status == providers.ExecComplete {
		t.Errorf("Status = %q, want non-complete", resp.Status)
	}
}

func (ts *OtpExecutorTestSuite) TestGetMetaDeclaresMaxAttemptsProperty() {
	t := ts.T()
	e := NewOtpExecutor(&fakeAuthnProvider{})

	meta := e.GetMeta()
	if len(meta.SupportedProperties) != 1 || meta.SupportedProperties[0].Property != propertyKeyMaxAttempts {
		t.Errorf("GetMeta().SupportedProperties = %v, want single %q property", meta.SupportedProperties, propertyKeyMaxAttempts)
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteAttemptCountIncrementsOnSuccess() {
	t := ts.T()
	provider := &fakeAuthnProvider{sendOTPResult: &shared.SendOTPResult{TransactionID: "txn-1"}}
	e := NewOtpExecutor(provider)
	ctx := newOtpNodeContext(map[string]string{"username": "user1"}, map[string]string{})

	if _, err := e.Execute(ctx); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if got := ctx.RuntimeData[otpAttemptCountKey]; got != "1" {
		t.Errorf("RuntimeData[otpAttemptCountKey] = %q, want %q", got, "1")
	}

	if _, err := e.Execute(ctx); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if got := ctx.RuntimeData[otpAttemptCountKey]; got != "2" {
		t.Errorf("RuntimeData[otpAttemptCountKey] = %q, want %q", got, "2")
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteBlocksAtDefaultMaxAttempts() {
	t := ts.T()
	provider := &fakeAuthnProvider{sendOTPResult: &shared.SendOTPResult{TransactionID: "txn-1"}}
	e := NewOtpExecutor(provider)
	ctx := newOtpNodeContext(map[string]string{"username": "user1"},
		map[string]string{otpAttemptCountKey: "3"})

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecFailure {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecFailure)
	}
	if resp.Error != shared.MaxOTPAttemptsReachedError {
		t.Errorf("Error = %v, want %v", resp.Error, shared.MaxOTPAttemptsReachedError)
	}
	if provider.lastIdentifiers != nil {
		t.Errorf("SendOTP was called, want no call once max attempts reached")
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteRespectsConfiguredMaxAttempts() {
	t := ts.T()
	provider := &fakeAuthnProvider{sendOTPResult: &shared.SendOTPResult{TransactionID: "txn-1"}}
	e := NewOtpExecutor(provider)
	ctx := newOtpNodeContext(map[string]string{"username": "user1"},
		map[string]string{otpAttemptCountKey: "1"})
	ctx.NodeProperties = map[string]interface{}{propertyKeyMaxAttempts: "1"}

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecFailure {
		t.Errorf("Status = %q, want %q", resp.Status, providers.ExecFailure)
	}
	if provider.lastIdentifiers != nil {
		t.Errorf("SendOTP was called, want no call once configured max attempts reached")
	}
}

func (ts *OtpExecutorTestSuite) TestMaxOTPAttemptsFromContext() {
	t := ts.T()

	cases := []struct {
		name       string
		properties map[string]interface{}
		want       int
	}{
		{"absent falls back to default", nil, defaultMaxOTPAttempts},
		{"string value", map[string]interface{}{propertyKeyMaxAttempts: "5"}, 5},
		{"int value", map[string]interface{}{propertyKeyMaxAttempts: 5}, 5},
		{"float64 value", map[string]interface{}{propertyKeyMaxAttempts: float64(5)}, 5},
		{"invalid string falls back to default", map[string]interface{}{propertyKeyMaxAttempts: "not-a-number"}, defaultMaxOTPAttempts},
		{"zero falls back to default", map[string]interface{}{propertyKeyMaxAttempts: 0}, defaultMaxOTPAttempts},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			ctx := &providers.NodeContext{NodeProperties: c.properties}
			if got := maxOTPAttemptsFromContext(ctx); got != c.want {
				t.Errorf("maxOTPAttemptsFromContext() = %d, want %d", got, c.want)
			}
		})
	}
}

type OtpExecutorTestSuite struct {
	suite.Suite
}

func TestOtpExecutorTestSuite(t *testing.T) {
	suite.Run(t, new(OtpExecutorTestSuite))
}

func (ts *OtpExecutorTestSuite) TestExecutePassesLoginIDUnderItsOwnKey() {
	t := ts.T()
	authn := &fakeAuthnProvider{sendOTPResult: &shared.SendOTPResult{TransactionID: "txn-1"}}
	e := NewOtpExecutor(authn)

	ctx := &providers.NodeContext{
		NodeInputs:  []providers.Input{{Identifier: shared.LoginIDPhone, Type: providers.InputTypeText}},
		UserInputs:  map[string]string{shared.LoginIDPhone: "+919876543210@phone"},
		RuntimeData: map[string]string{},
	}

	resp, err := e.Execute(ctx)
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.Status != providers.ExecComplete {
		t.Fatalf("Execute() status = %v, want %v", resp.Status, providers.ExecComplete)
	}
	if got := authn.lastIdentifiers[shared.LoginIDPhone]; got != "+919876543210@phone" {
		t.Errorf("identifiers = %v, want the value under the phone key", authn.lastIdentifiers)
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteClearsAbandonedLoginIDs() {
	t := ts.T()
	e := NewOtpExecutor(&fakeAuthnProvider{sendOTPResult: &shared.SendOTPResult{TransactionID: "txn-1"}})

	// The user tried a UIN first, then switched to mobile; only the mobile ID must survive.
	ctx := &providers.NodeContext{
		NodeInputs: []providers.Input{{Identifier: shared.LoginIDPhone, Type: providers.InputTypeText}},
		UserInputs: map[string]string{
			shared.LoginIDUIN:   "4358192047",
			shared.LoginIDPhone: "+919876543210@phone",
		},
		RuntimeData: map[string]string{},
	}

	if _, err := e.Execute(ctx); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if _, stale := ctx.UserInputs[shared.LoginIDUIN]; stale {
		t.Errorf("UserInputs = %v, want the abandoned uin cleared", ctx.UserInputs)
	}
	if ctx.UserInputs[shared.LoginIDPhone] == "" {
		t.Errorf("UserInputs = %v, want the phone in use retained", ctx.UserInputs)
	}
}

func (ts *OtpExecutorTestSuite) TestExecuteFallsBackToUntypedIdentifier() {
	t := ts.T()
	authn := &fakeAuthnProvider{sendOTPResult: &shared.SendOTPResult{TransactionID: "txn-1"}}
	e := NewOtpExecutor(authn)

	// A flow that declares no inputs keeps the untyped "username" identifier.
	ctx := &providers.NodeContext{
		UserInputs:  map[string]string{shared.LoginIDUsername: "ind-1"},
		RuntimeData: map[string]string{},
	}

	if _, err := e.Execute(ctx); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if got := authn.lastIdentifiers[shared.LoginIDUsername]; got != "ind-1" {
		t.Errorf("identifiers = %v, want the value under the username key", authn.lastIdentifiers)
	}
}

// A node reachable from several identifier prompts (the resend node) declares no inputs, so
// that a failed resend does not clear the identifier the flow already holds.
func (ts *OtpExecutorTestSuite) TestExecuteResolvesLoginIDWithoutDeclaredInputs() {
	t := ts.T()
	authn := &fakeAuthnProvider{sendOTPResult: &shared.SendOTPResult{TransactionID: "txn-1"}}
	e := NewOtpExecutor(authn)

	ctx := &providers.NodeContext{
		UserInputs:  map[string]string{shared.LoginIDEmail: "alice@example.com@email"},
		RuntimeData: map[string]string{},
	}

	if _, err := e.Execute(ctx); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if got := authn.lastIdentifiers[shared.LoginIDEmail]; got != "alice@example.com@email" {
		t.Errorf("identifiers = %v, want the value under the email key", authn.lastIdentifiers)
	}
}
