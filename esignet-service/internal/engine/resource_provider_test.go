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

	"github.com/mosip/esignet/internal/config"
)

func (ts *ResourceProviderTestSuite) TestResourceProvider_GetResourceServerByIdentifier() {
	p := NewResourceProvider(&config.AppConfig{})
	rs, svcErr := p.GetResourceServerByIdentifier(context.Background(), "identifier-1")
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), rs)
}

func (ts *ResourceProviderTestSuite) TestResourceProvider_ValidatePermissions() {
	p := NewResourceProvider(&config.AppConfig{})
	perms, svcErr := p.ValidatePermissions(context.Background(), "rs-1", []string{"read", "write"})
	require.Nil(ts.T(), svcErr)
	require.Empty(ts.T(), perms)
}

func (ts *ResourceProviderTestSuite) TestResourceProvider_GetResourceServer() {
	p := NewResourceProvider(&config.AppConfig{})
	rs, svcErr := p.GetResourceServer(context.Background(), "rs-1")
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), rs)
}

type ResourceProviderTestSuite struct {
	suite.Suite
}

func TestResourceProviderTestSuite(t *testing.T) {
	suite.Run(t, new(ResourceProviderTestSuite))
}
