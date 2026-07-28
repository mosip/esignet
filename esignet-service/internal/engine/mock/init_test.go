/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mock

import (
	"net/http"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/config"
)

func (ts *InitTestSuite) TestInitReturnsAuthnProviderAndNoopAuditor() {
	t := ts.T()
	authnProvider, observability, err := Init(&config.AppConfig{}, newTestClientSvc(), &http.Client{Timeout: 30 * time.Second})

	require.NoError(t, err)
	require.NotNil(t, authnProvider)
	require.NotNil(t, observability)
	require.True(t, observability.IsEnabled())
}

type InitTestSuite struct {
	suite.Suite
}

func TestInitTestSuite(t *testing.T) {
	suite.Run(t, new(InitTestSuite))
}
