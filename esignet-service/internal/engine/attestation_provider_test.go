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

func (ts *AttestationProviderTestSuite) TestNewAttestationProvider_ReturnsProvider() {
	p := NewAttestationProvider(&config.AppConfig{})
	ts.Require().NotNil(p)
}

func (ts *AttestationProviderTestSuite) TestAttestationProvider_Verify() {
	p := NewAttestationProvider(&config.AppConfig{})

	ok, svcErr := p.Verify(context.Background(), &providers.AttestationConfig{}, "some-token")
	require.Nil(ts.T(), svcErr)
	require.True(ts.T(), ok)

	ok, svcErr = p.Verify(context.Background(), nil, "")
	require.Nil(ts.T(), svcErr)
	require.True(ts.T(), ok)
}

type AttestationProviderTestSuite struct {
	suite.Suite
}

func TestAttestationProviderTestSuite(t *testing.T) {
	suite.Run(t, new(AttestationProviderTestSuite))
}
