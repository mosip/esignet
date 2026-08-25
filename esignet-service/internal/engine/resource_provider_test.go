/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"

	"github.com/mosip/esignet/internal/config"
)

func testResourceServers() []config.ResourceServerConfig {
	return []config.ResourceServerConfig{
		{
			ID:         "rs-default",
			Identifier: "https://default.example.com/resource",
			Default:    true,
			Scopes:     map[string]string{"read": "Read", "write": "Write"},
		},
		{
			ID:         "rs-other",
			Identifier: "https://other.example.com/resource",
			Scopes:     map[string]string{"admin": "Admin"},
		},
	}
}

func (ts *ResourceProviderTestSuite) TestResourceProvider_GetResourceServerByIdentifier_Found() {
	p := NewResourceProvider(&config.AppConfig{ResourceServers: testResourceServers()})
	rs, svcErr := p.GetResourceServerByIdentifier(context.Background(), "https://other.example.com/resource")
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), rs)
	assert.Equal(ts.T(), "rs-other", rs.ID)
	assert.Equal(ts.T(), "https://other.example.com/resource", rs.Identifier)
}

func (ts *ResourceProviderTestSuite) TestResourceProvider_GetResourceServerByIdentifier_EmptyResolvesDefault() {
	p := NewResourceProvider(&config.AppConfig{ResourceServers: testResourceServers()})
	rs, svcErr := p.GetResourceServerByIdentifier(context.Background(), "")
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), rs)
	assert.Equal(ts.T(), "rs-default", rs.ID)
}

func (ts *ResourceProviderTestSuite) TestResourceProvider_GetResourceServerByIdentifier_UnknownIdentifier() {
	p := NewResourceProvider(&config.AppConfig{ResourceServers: testResourceServers()})
	rs, svcErr := p.GetResourceServerByIdentifier(context.Background(), "https://unknown.example.com")
	assert.Nil(ts.T(), rs)
	require.NotNil(ts.T(), svcErr)
	assert.Equal(ts.T(), common.ClientErrorType, svcErr.Type)
}

func (ts *ResourceProviderTestSuite) TestResourceProvider_GetResourceServerByIdentifier_EmptyNoDefaultConfigured() {
	p := NewResourceProvider(&config.AppConfig{})
	rs, svcErr := p.GetResourceServerByIdentifier(context.Background(), "")
	assert.Nil(ts.T(), rs)
	require.NotNil(ts.T(), svcErr)
}

// TestResourceProvider_GetResourceServerByIdentifier_ConfiguredButNoneDefault covers a deployment
// that has resource_servers configured (a valid config per TestValidateResourceServers's "no
// default is valid" case) but none marked default. An OAuth request that omits the `resource`
// parameter resolves an empty identifier here and must fail to bind to a resource server, the same
// as when no resource_servers are configured at all, rather than falling back to some entry.
func (ts *ResourceProviderTestSuite) TestResourceProvider_GetResourceServerByIdentifier_ConfiguredButNoneDefault() {
	p := NewResourceProvider(&config.AppConfig{ResourceServers: []config.ResourceServerConfig{
		{ID: "rs-1", Identifier: "https://rs-1.example.com", Scopes: map[string]string{"read": "Read"}},
		{ID: "rs-2", Identifier: "https://rs-2.example.com", Scopes: map[string]string{"write": "Write"}},
	}})
	rs, svcErr := p.GetResourceServerByIdentifier(context.Background(), "")
	assert.Nil(ts.T(), rs)
	require.NotNil(ts.T(), svcErr)
	assert.Equal(ts.T(), common.ClientErrorType, svcErr.Type)
}

func (ts *ResourceProviderTestSuite) TestResourceProvider_GetResourceServer_Found() {
	p := NewResourceProvider(&config.AppConfig{ResourceServers: testResourceServers()})
	rs, svcErr := p.GetResourceServer(context.Background(), "rs-other")
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), rs)
	assert.Equal(ts.T(), "https://other.example.com/resource", rs.Identifier)
}

func (ts *ResourceProviderTestSuite) TestResourceProvider_GetResourceServer_NotFound() {
	p := NewResourceProvider(&config.AppConfig{ResourceServers: testResourceServers()})
	rs, svcErr := p.GetResourceServer(context.Background(), "does-not-exist")
	assert.Nil(ts.T(), rs)
	require.NotNil(ts.T(), svcErr)
}

func (ts *ResourceProviderTestSuite) TestResourceProvider_ValidatePermissions_AllValid() {
	p := NewResourceProvider(&config.AppConfig{ResourceServers: testResourceServers()})
	invalid, svcErr := p.ValidatePermissions(context.Background(), "rs-default", []string{"read", "write"})
	require.Nil(ts.T(), svcErr)
	assert.Empty(ts.T(), invalid)
}

func (ts *ResourceProviderTestSuite) TestResourceProvider_ValidatePermissions_PartiallyInvalid() {
	p := NewResourceProvider(&config.AppConfig{ResourceServers: testResourceServers()})
	invalid, svcErr := p.ValidatePermissions(context.Background(), "rs-default", []string{"read", "admin"})
	require.Nil(ts.T(), svcErr)
	assert.Equal(ts.T(), []string{"admin"}, invalid)
}

func (ts *ResourceProviderTestSuite) TestResourceProvider_ValidatePermissions_UnknownResourceServer() {
	p := NewResourceProvider(&config.AppConfig{ResourceServers: testResourceServers()})
	invalid, svcErr := p.ValidatePermissions(context.Background(), "does-not-exist", []string{"read", "write"})
	require.Nil(ts.T(), svcErr)
	assert.Equal(ts.T(), []string{"read", "write"}, invalid)
}

type ResourceProviderTestSuite struct {
	suite.Suite
}

func TestResourceProviderTestSuite(t *testing.T) {
	suite.Run(t, new(ResourceProviderTestSuite))
}
