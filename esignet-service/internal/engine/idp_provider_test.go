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

func (ts *IDPProviderTestSuite) TestIDPProvider_GetIdentityProvidersByProperty() {
	p := NewIDPProvider(&config.AppConfig{})
	idps, svcErr := p.GetIdentityProvidersByProperty(context.Background(), "key", "value")
	require.Nil(ts.T(), svcErr)
	require.Nil(ts.T(), idps)
}

func (ts *IDPProviderTestSuite) TestIDPProvider_GetIdentityProvider() {
	p := NewIDPProvider(&config.AppConfig{})
	idp, svcErr := p.GetIdentityProvider(context.Background(), "idp-1")
	require.Nil(ts.T(), svcErr)
	require.Nil(ts.T(), idp)
}

type IDPProviderTestSuite struct {
	suite.Suite
}

func TestIDPProviderTestSuite(t *testing.T) {
	suite.Run(t, new(IDPProviderTestSuite))
}
