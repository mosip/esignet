/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
)

// fakeAuthnProvider is a minimal shared.ConsolidatedAuthnProvider stub for
// exercising mosipOtpExecutor without a real MOSIP IDA integration.
type fakeAuthnProvider struct {
	sendOTPResult  *shared.SendOTPResult
	sendOTPErr     *common.ServiceError
	gotIdentifiers map[string]interface{}
	gotMetadata    *providers.AuthnMetadata
}

func (f *fakeAuthnProvider) SendOTP(_ context.Context, identifiers map[string]interface{},
	metadata *providers.AuthnMetadata) (*shared.SendOTPResult, *common.ServiceError) {
	f.gotIdentifiers = identifiers
	f.gotMetadata = metadata
	return f.sendOTPResult, f.sendOTPErr
}

func (f *fakeAuthnProvider) AuthenticateUser(_ context.Context, _, _ map[string]interface{},
	_ *providers.RequestedAttributes, _ *providers.AuthnMetadata,
	authUser providers.AuthUser) (providers.AuthUser, providers.AuthenticatedClaims, *common.ServiceError) {
	return authUser, nil, nil
}

func (f *fakeAuthnProvider) GetEntityReference(_ context.Context, authUser providers.AuthUser) (
	providers.AuthUser, *providers.EntityReference, *common.ServiceError) {
	return authUser, nil, nil
}

func (f *fakeAuthnProvider) GetUserAvailableAttributes(_ context.Context,
	_ providers.AuthUser) (*providers.AttributesResponse, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProvider) GetUserAttributes(_ context.Context, _ *providers.RequestedAttributes,
	_ *providers.GetAttributesMetadata, authUser providers.AuthUser) (
	providers.AuthUser, *providers.AttributesResponse, *common.ServiceError) {
	return authUser, nil, nil
}

func newNodeContext(userInputs, runtimeData map[string]string) *providers.NodeContext {
	return &providers.NodeContext{
		Context:     context.Background(),
		UserInputs:  userInputs,
		RuntimeData: runtimeData,
	}
}

func (ts *ExecutorsTestSuite) TestMosipOtpExecutor_StaticMetadata() {
	t := ts.T()
	e := NewMosipOtpExecutor(&fakeAuthnProvider{})
	if e.GetName() != ExecutorNameMosipOTP {
		t.Errorf("GetName() = %q, want %q", e.GetName(), ExecutorNameMosipOTP)
	}
	if e.GetType() != providers.ExecutorTypeAuthentication {
		t.Errorf("GetType() = %v, want ExecutorTypeAuthentication", e.GetType())
	}
	if e.GetDefaultInputs() != nil {
		t.Errorf("GetDefaultInputs() = %v, want nil", e.GetDefaultInputs())
	}
	prereqs := e.GetPrerequisites()
	if len(prereqs) != 1 || prereqs[0].Identifier != usernameAttr {
		t.Errorf("GetPrerequisites() = %+v, want single username input", prereqs)
	}
	if e.GetExecutionPolicy("x") != nil {
		t.Error("GetExecutionPolicy() should be nil")
	}
	if !e.HasRequiredInputs(nil, nil) {
		t.Error("HasRequiredInputs() should always be true")
	}
	if got := e.GetUserIDFromContext(nil, nil, nil); got != "" {
		t.Errorf("GetUserIDFromContext() = %q, want empty", got)
	}
}

func (ts *ExecutorsTestSuite) TestMosipOtpExecutor_GetRequiredInputs() {
	t := ts.T()
	e := NewMosipOtpExecutor(&fakeAuthnProvider{})
	custom := []providers.Input{{Identifier: "custom", Type: providers.InputTypeText}}

	got := e.GetRequiredInputs(&providers.NodeContext{NodeInputs: custom})
	if len(got) != 1 || got[0].Identifier != "custom" {
		t.Errorf("GetRequiredInputs() with NodeInputs = %+v, want custom input", got)
	}

	got = e.GetRequiredInputs(&providers.NodeContext{})
	if got != nil {
		t.Errorf("GetRequiredInputs() without NodeInputs = %v, want nil (default inputs)", got)
	}
}

func (ts *ExecutorsTestSuite) TestMosipOtpExecutor_ValidatePrerequisites() {
	t := ts.T()
	e := NewMosipOtpExecutor(&fakeAuthnProvider{})

	ctx := newNodeContext(map[string]string{"username": "user-1"}, nil)
	if !e.ValidatePrerequisites(ctx, nil, nil) {
		t.Error("expected prerequisites satisfied when username is present in UserInputs")
	}

	ctx = newNodeContext(nil, nil)
	if e.ValidatePrerequisites(ctx, nil, nil) {
		t.Error("expected prerequisites unsatisfied when username is missing")
	}
}

func (ts *ExecutorsTestSuite) TestMosipOtpExecutor_Execute() {
	t := ts.T()
	t.Run("missing username requests input", func(t *testing.T) {
		e := NewMosipOtpExecutor(&fakeAuthnProvider{})
		ctx := newNodeContext(nil, nil)

		resp, err := e.Execute(ctx)
		if err != nil {
			t.Fatalf("Execute: %v", err)
		}
		if resp.Status != providers.ExecUserInputRequired {
			t.Errorf("Status = %v, want ExecUserInputRequired", resp.Status)
		}
		if len(resp.Inputs) != 1 || resp.Inputs[0].Identifier != usernameAttr {
			t.Errorf("Inputs = %+v, want single username input", resp.Inputs)
		}
	})

	t.Run("success sends OTP and forwards masked contact info", func(t *testing.T) {
		fake := &fakeAuthnProvider{sendOTPResult: &shared.SendOTPResult{MaskedEmail: "j***@example.com", MaskedMobile: "***1234"}}
		e := NewMosipOtpExecutor(fake)
		ctx := newNodeContext(map[string]string{"username": "user-1"}, map[string]string{"clientId": "client-1"})

		resp, err := e.Execute(ctx)
		if err != nil {
			t.Fatalf("Execute: %v", err)
		}
		if resp.Status != providers.ExecComplete {
			t.Errorf("Status = %v, want ExecComplete", resp.Status)
		}
		if resp.ForwardedData["maskedEmail"] != "j***@example.com" {
			t.Errorf("maskedEmail = %v, want j***@example.com", resp.ForwardedData["maskedEmail"])
		}
		if resp.ForwardedData["maskedMobile"] != "***1234" {
			t.Errorf("maskedMobile = %v, want ***1234", resp.ForwardedData["maskedMobile"])
		}
		if fake.gotIdentifiers["username"] != "user-1" {
			t.Errorf("SendOTP identifiers = %v, want username=user-1", fake.gotIdentifiers)
		}
		if fake.gotMetadata.RuntimeMetadata["current_client_id"] != "client-1" {
			t.Errorf("current_client_id = %q, want client-1", fake.gotMetadata.RuntimeMetadata["current_client_id"])
		}
	})

	t.Run("service error propagates", func(t *testing.T) {
		fake := &fakeAuthnProvider{sendOTPErr: &common.ServiceError{Code: "boom"}}
		e := NewMosipOtpExecutor(fake)
		ctx := newNodeContext(map[string]string{"username": "user-1"}, nil)

		_, err := e.Execute(ctx)
		if err == nil {
			t.Fatal("expected error when SendOTP fails")
		}
	})
}

func (ts *ExecutorsTestSuite) TestBuildAuthnMetadata() {
	t := ts.T()
	ctx := &providers.NodeContext{
		Application: providers.Application{
			Metadata: map[string]interface{}{"foo": "bar"},
			InboundAuthConfig: []providers.InboundAuthConfigWithSecret{
				{OAuthConfig: &providers.OAuthConfigWithSecret{ClientID: "client-1"}},
				{OAuthConfig: &providers.OAuthConfigWithSecret{ClientID: ""}},
				{},
			},
		},
		RuntimeData: map[string]string{
			"clientId":               "client-1",
			"authorizationRequestId": "req-1",
			"ext_TransactionID":      "existing-txn",
			"ext_Foo":                "bar",
			"unrelated":              "should not propagate",
		},
	}

	meta := (&mosipOtpExecutor{}).BuildAuthnMetadata(ctx)
	if meta.AppMetadata["foo"] != "bar" {
		t.Errorf("AppMetadata[foo] = %v, want bar", meta.AppMetadata["foo"])
	}
	clientIDs, ok := meta.AppMetadata["client_ids"].([]string)
	if !ok || len(clientIDs) != 1 || clientIDs[0] != "client-1" {
		t.Errorf("client_ids = %v, want [client-1]", meta.AppMetadata["client_ids"])
	}
	if meta.RuntimeMetadata["authorization_request_id"] != "req-1" {
		t.Errorf("authorization_request_id = %q, want req-1", meta.RuntimeMetadata["authorization_request_id"])
	}
	if meta.RuntimeMetadata["current_client_id"] != "client-1" {
		t.Errorf("current_client_id = %q, want client-1", meta.RuntimeMetadata["current_client_id"])
	}
	if meta.RuntimeMetadata["ext_TransactionID"] != "existing-txn" {
		t.Errorf("ext_TransactionID = %q, want existing-txn (should not be regenerated)", meta.RuntimeMetadata["ext_TransactionID"])
	}
	if meta.RuntimeMetadata["ext_Foo"] != "bar" {
		t.Errorf("ext_Foo = %q, want bar", meta.RuntimeMetadata["ext_Foo"])
	}
	if _, ok := meta.RuntimeMetadata["unrelated"]; ok {
		t.Error("non ext_ runtime data should not propagate")
	}
}

func (ts *ExecutorsTestSuite) TestBuildAuthnMetadata_GeneratesTransactionIDWhenMissing() {
	t := ts.T()
	ctx := &providers.NodeContext{RuntimeData: map[string]string{}}
	meta := (&mosipOtpExecutor{}).BuildAuthnMetadata(ctx)
	if meta.RuntimeMetadata["ext_TransactionID"] == "" {
		t.Error("expected a generated ext_TransactionID")
	}
	if ctx.RuntimeData["ext_TransactionID"] == "" {
		t.Error("expected ext_TransactionID to be persisted on RuntimeData")
	}
}

func (ts *ExecutorsTestSuite) TestBuildAuthnMetadata_NilRuntimeData() {
	t := ts.T()
	ctx := &providers.NodeContext{}
	meta := (&mosipOtpExecutor{}).BuildAuthnMetadata(ctx)
	if ctx.RuntimeData == nil {
		t.Error("expected RuntimeData to be initialized")
	}
	if meta.RuntimeMetadata["ext_TransactionID"] == "" {
		t.Error("expected a generated ext_TransactionID")
	}
}

func (ts *ExecutorsTestSuite) TestUsernameFromContext() {
	t := ts.T()
	t.Run("from user inputs", func(t *testing.T) {
		got, err := usernameFromContext(newNodeContext(map[string]string{"username": "user-1"}, nil))
		if err != nil || got != "user-1" {
			t.Errorf("usernameFromContext() = (%q, %v), want (user-1, nil)", got, err)
		}
	})

	t.Run("from runtime data", func(t *testing.T) {
		got, err := usernameFromContext(newNodeContext(nil, map[string]string{"username": "user-2"}))
		if err != nil || got != "user-2" {
			t.Errorf("usernameFromContext() = (%q, %v), want (user-2, nil)", got, err)
		}
	})

	t.Run("missing", func(t *testing.T) {
		_, err := usernameFromContext(newNodeContext(nil, nil))
		if err == nil {
			t.Error("expected error when username is missing from both sources")
		}
	})
}

func (ts *ExecutorsTestSuite) TestGenerateTransactionID() {
	t := ts.T()
	id, err := generateTransactionID()
	if err != nil {
		t.Fatalf("generateTransactionID: %v", err)
	}
	if len(id) != 10 {
		t.Errorf("len(id) = %d, want 10", len(id))
	}
	for _, r := range id {
		if r < '0' || r > '9' {
			t.Errorf("id %q contains non-digit character %q", id, r)
		}
	}
}

type ExecutorsTestSuite struct {
	suite.Suite
}

func TestExecutorsTestSuite(t *testing.T) {
	suite.Run(t, new(ExecutorsTestSuite))
}
