/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

import (
	"context"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

// fakeConsolidatedAuthnProvider is a hand-written stub used only to prove that
// ConsolidatedAuthnProvider is satisfiable by a type implementing both
// providers.AuthnProviderInterface and the SendOTP extension.
type fakeConsolidatedAuthnProvider struct {
	sendOTPResult *SendOTPResult
}

func (f *fakeConsolidatedAuthnProvider) InitiateAuthentication(_ context.Context, _ string, _ any,
	_ *providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (f *fakeConsolidatedAuthnProvider) Authenticate(_ context.Context, _, _ map[string]interface{},
	_ *providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {
	return nil, nil
}

func (f *fakeConsolidatedAuthnProvider) GetEntityReference(_ context.Context, _ any) (*providers.EntityReference,
	*common.ServiceError) {
	return nil, nil
}

func (f *fakeConsolidatedAuthnProvider) GetAttributes(_ context.Context, _ any, _ *providers.RequestedAttributes,
	_ *providers.GetAttributesMetadata) (*providers.AttributesResponse, *common.ServiceError) {
	return nil, nil
}

func (f *fakeConsolidatedAuthnProvider) InitiateEnrollment(_ context.Context, _ string, _ any,
	_ *providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (f *fakeConsolidatedAuthnProvider) Enroll(_ context.Context, _, _ map[string]interface{},
	_ *providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {
	return nil, nil
}

func (f *fakeConsolidatedAuthnProvider) SendOTP(_ context.Context, _ map[string]interface{},
	_ *providers.AuthnMetadata) (*SendOTPResult, *common.ServiceError) {
	return f.sendOTPResult, nil
}

var _ ConsolidatedAuthnProvider = (*fakeConsolidatedAuthnProvider)(nil)

func (ts *InterfaceTestSuite) TestConsolidatedAuthnProviderExtendsAuthnProviderInterface() {
	t := ts.T()
	var provider ConsolidatedAuthnProvider = &fakeConsolidatedAuthnProvider{
		sendOTPResult: &SendOTPResult{TransactionID: "txn-1"},
	}

	// Reachable via the embedded providers.AuthnProviderInterface.
	if _, svcErr := provider.Authenticate(context.Background(), nil, nil, nil); svcErr != nil {
		t.Errorf("Authenticate() svcErr = %v, want nil", svcErr)
	}

	result, svcErr := provider.SendOTP(context.Background(), nil, nil)
	if svcErr != nil {
		t.Fatalf("SendOTP() svcErr = %v, want nil", svcErr)
	}
	if result == nil || result.TransactionID != "txn-1" {
		t.Errorf("SendOTP() result = %+v, want TransactionID txn-1", result)
	}
}

type InterfaceTestSuite struct {
	suite.Suite
}

func TestInterfaceTestSuite(t *testing.T) {
	suite.Run(t, new(InterfaceTestSuite))
}
