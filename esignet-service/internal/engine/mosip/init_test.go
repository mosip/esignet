/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/config"
)

func (ts *InitTestSuite) TestNewHTTPClientIsTuned() {
	t := ts.T()
	client := newHTTPClient()

	require.Equal(t, 30*time.Second, client.Timeout)
	require.NotNil(t, client.Transport)
}

func (ts *InitTestSuite) TestInitSucceedsWithAuditConfigured() {
	t := ts.T()
	t.Setenv("MOSIP_ESIGNET_AUDIT_MANAGER_URL", "http://audit.example.org/audits")
	t.Setenv("MOSIP_ESIGNET_AUTH_TOKEN_URL", "http://audit.example.org/token")
	t.Setenv("MOSIP_ESIGNET_IDA_CLIENT_SECRET", "secret")

	authnProvider, auditor, err := Init(&config.AppConfig{}, nil)

	require.NoError(t, err)
	require.NotNil(t, authnProvider)
	require.NotNil(t, auditor)
	require.True(t, auditor.IsEnabled())
}

func (ts *InitTestSuite) TestInitFailsWhenAuditNotConfigured() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "")
	t.Setenv("MOSIP_ESIGNET_AUDIT_MANAGER_URL", "")
	t.Setenv("MOSIP_ESIGNET_AUTH_TOKEN_URL", "")

	authnProvider, auditor, err := Init(&config.AppConfig{}, nil)

	require.Error(t, err)
	require.Nil(t, authnProvider)
	require.Nil(t, auditor)
}

type InitTestSuite struct {
	suite.Suite
}

func TestInitTestSuite(t *testing.T) {
	suite.Run(t, new(InitTestSuite))
}
