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

func (ts *AuthzProviderTestSuite) TestNewAuthorizationProvider_ReturnsProvider() {
	p := NewAuthorizationProvider(&config.AppConfig{})
	ts.Require().NotNil(p)
}

func (ts *AuthzProviderTestSuite) TestAuthorizationProvider_EvaluateAccess() {
	p := NewAuthorizationProvider(&config.AppConfig{})

	req := providers.AccessEvaluationRequest{
		Subject:        providers.Subject{ID: "user-1"},
		ResourceServer: providers.AccessEvaluationResourceServer{ID: "rs-1"},
		Permission:     providers.Permission{Name: "read"},
		Context:        map[string]interface{}{"foo": "bar"},
	}

	resp, svcErr := p.EvaluateAccess(context.Background(), req)
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), resp)
	require.True(ts.T(), resp.Decision)
	require.Equal(ts.T(), req.Context, resp.Context)
}

func (ts *AuthzProviderTestSuite) TestAuthorizationProvider_EvaluateAccessBatch() {
	p := NewAuthorizationProvider(&config.AppConfig{})

	req := providers.AccessEvaluationsRequest{
		Evaluations: []providers.AccessEvaluationRequest{
			{Subject: providers.Subject{ID: "user-1"}},
			{Subject: providers.Subject{ID: "user-2"}},
		},
	}

	resp, svcErr := p.EvaluateAccessBatch(context.Background(), req)
	require.Nil(ts.T(), svcErr)
	require.NotNil(ts.T(), resp)
	require.Len(ts.T(), resp.Evaluations, 1)
	require.True(ts.T(), resp.Evaluations[0].Decision)
}

type AuthzProviderTestSuite struct {
	suite.Suite
}

func TestAuthzProviderTestSuite(t *testing.T) {
	suite.Run(t, new(AuthzProviderTestSuite))
}
