/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
)

func (ts *OUProviderTestSuite) TestOUProvider_GetOrganizationUnit() {
	p := NewOUProvider(&config.AppConfig{})
	ou, svcErr := p.GetOrganizationUnit(context.Background(), "ou-1")
	require.Nil(ts.T(), svcErr)
	require.Equal(ts.T(), "", ou.ID)
}

func (ts *OUProviderTestSuite) TestOUProvider_GetOrganizationUnitList() {
	p := NewOUProvider(&config.AppConfig{})
	resp, svcErr := p.GetOrganizationUnitList(context.Background(), 10, 0, nil)
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), resp)
}

func (ts *OUProviderTestSuite) TestOUProvider_CreateOrganizationUnit() {
	p := NewOUProvider(&config.AppConfig{})
	ou, svcErr := p.CreateOrganizationUnit(context.Background(), providers.OrganizationUnitRequestWithID{})
	require.Nil(ts.T(), svcErr)
	require.Equal(ts.T(), "", ou.ID)
}

func (ts *OUProviderTestSuite) TestOUProvider_IsParent() {
	p := NewOUProvider(&config.AppConfig{})
	ok, svcErr := p.IsParent(context.Background(), "parent-1", "child-1")
	require.Nil(ts.T(), svcErr)
	require.True(ts.T(), ok)
}

func (ts *OUProviderTestSuite) TestOUProvider_IsOrganizationUnitExists() {
	p := NewOUProvider(&config.AppConfig{})
	ok, svcErr := p.IsOrganizationUnitExists(context.Background(), "ou-1")
	require.Nil(ts.T(), svcErr)
	require.True(ts.T(), ok)
}

func (ts *OUProviderTestSuite) TestOUProvider_GetOrganizationUnitChildren() {
	p := NewOUProvider(&config.AppConfig{})
	resp, svcErr := p.GetOrganizationUnitChildren(context.Background(), "ou-1", 10, 0, nil)
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), resp)
}

type OUProviderTestSuite struct {
	suite.Suite
}

func TestOUProviderTestSuite(t *testing.T) {
	suite.Run(t, new(OUProviderTestSuite))
}
