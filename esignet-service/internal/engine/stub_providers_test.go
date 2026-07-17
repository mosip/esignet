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

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
)

func (ts *StubProvidersTestSuite) TestAuthorizationProvider() {
	t := ts.T()
	p := NewAuthorizationProvider(&config.AppConfig{})

	resp, svcErr := p.EvaluateAccess(context.Background(), providers.AccessEvaluationRequest{Context: map[string]any{"k": "v"}})
	if svcErr != nil {
		t.Fatalf("EvaluateAccess: %v", svcErr)
	}
	if !resp.Decision {
		t.Error("EvaluateAccess() should always permit")
	}
	if resp.Context["k"] != "v" {
		t.Errorf("EvaluateAccess() should echo back the request context, got %v", resp.Context)
	}

	batchResp, svcErr := p.EvaluateAccessBatch(context.Background(), providers.AccessEvaluationsRequest{})
	if svcErr != nil {
		t.Fatalf("EvaluateAccessBatch: %v", svcErr)
	}
	if len(batchResp.Evaluations) != 1 || !batchResp.Evaluations[0].Decision {
		t.Errorf("EvaluateAccessBatch() = %+v, want single permissive decision", batchResp.Evaluations)
	}
}

func (ts *StubProvidersTestSuite) TestIDPProvider() {
	t := ts.T()
	p := NewIDPProvider(&config.AppConfig{})

	idps, svcErr := p.GetIdentityProvidersByProperty(context.Background(), "", "")
	if idps != nil || svcErr != nil {
		t.Errorf("GetIdentityProvidersByProperty() = (%v, %v), want (nil, nil)", idps, svcErr)
	}

	idp, svcErr := p.GetIdentityProvider(context.Background(), "")
	if idp != nil || svcErr != nil {
		t.Errorf("GetIdentityProvider() = (%v, %v), want (nil, nil)", idp, svcErr)
	}
}

func (ts *StubProvidersTestSuite) TestOUProvider() {
	t := ts.T()
	p := NewOUProvider(&config.AppConfig{})

	if _, svcErr := p.GetOrganizationUnit(context.Background(), "ou-1"); svcErr != nil {
		t.Errorf("GetOrganizationUnit: %v", svcErr)
	}
	if resp, svcErr := p.GetOrganizationUnitList(context.Background(), 0, 10, nil); svcErr != nil || resp == nil {
		t.Errorf("GetOrganizationUnitList() = (%v, %v)", resp, svcErr)
	}
	if _, svcErr := p.CreateOrganizationUnit(context.Background(), providers.OrganizationUnitRequestWithID{}); svcErr != nil {
		t.Errorf("CreateOrganizationUnit: %v", svcErr)
	}
	if isParent, svcErr := p.IsParent(context.Background(), "a", "b"); svcErr != nil || !isParent {
		t.Errorf("IsParent() = (%v, %v), want (true, nil)", isParent, svcErr)
	}
	if exists, svcErr := p.IsOrganizationUnitExists(context.Background(), "ou-1"); svcErr != nil || !exists {
		t.Errorf("IsOrganizationUnitExists() = (%v, %v), want (true, nil)", exists, svcErr)
	}
	if resp, svcErr := p.GetOrganizationUnitChildren(context.Background(), "ou-1", 0, 10, nil); svcErr != nil || resp == nil {
		t.Errorf("GetOrganizationUnitChildren() = (%v, %v)", resp, svcErr)
	}
}

func (ts *StubProvidersTestSuite) TestResourceProvider() {
	t := ts.T()
	p := NewResourceProvider(&config.AppConfig{})

	if resp, svcErr := p.GetResourceServerByIdentifier(context.Background(), "res-1"); svcErr != nil || resp == nil {
		t.Errorf("GetResourceServerByIdentifier() = (%v, %v)", resp, svcErr)
	}
	if perms, svcErr := p.ValidatePermissions(context.Background(), "res-1", []string{"read"}); svcErr != nil || perms == nil {
		t.Errorf("ValidatePermissions() = (%v, %v)", perms, svcErr)
	}
	if servers, svcErr := p.FindResourceServersByPermissions(context.Background(), []string{"read"}); svcErr != nil || servers == nil {
		t.Errorf("FindResourceServersByPermissions() = (%v, %v)", servers, svcErr)
	}
}

type StubProvidersTestSuite struct {
	suite.Suite
}

func TestStubProvidersTestSuite(t *testing.T) {
	suite.Run(t, new(StubProvidersTestSuite))
}
