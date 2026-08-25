/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"net/http"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/config"
)

func (ts *InitTestSuite) TestInitSucceedsWithAuditConfigured() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "http://internal.example.org")
	t.Setenv("MOSIP_ESIGNET_MISP_KEY", "misp-1")
	t.Setenv("MOSIP_IDA_CLIENT_SECRET", "secret")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUDIT_MANAGER_URL", "http://audit.example.org/audits")
	t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUTH_TOKEN_URL", "http://audit.example.org/token")

	km, sigSvc := newTestKeyManagerServices(t, false)
	authnProvider, auditor, err := Init(&config.AppConfig{}, nil, &http.Client{Timeout: 30 * time.Second}, km, sigSvc)

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

	km, sigSvc := newTestKeyManagerServices(t, false)
	authnProvider, auditor, err := Init(&config.AppConfig{}, nil, &http.Client{Timeout: 30 * time.Second}, km, sigSvc)

	require.Error(t, err)
	require.Nil(t, authnProvider)
	require.Nil(t, auditor)
}

func (ts *InitTestSuite) TestInitFailsWithNilKeymanagerService() {
	t := ts.T()
	_, sigSvc := newTestKeyManagerServices(t, false)

	authnProvider, auditor, err := Init(&config.AppConfig{}, nil, &http.Client{Timeout: 30 * time.Second}, nil, sigSvc)

	require.ErrorIs(t, err, ErrNilCryptoService)
	require.Nil(t, authnProvider)
	require.Nil(t, auditor)
}

func (ts *InitTestSuite) TestInitFailsWithNilSignatureService() {
	t := ts.T()
	km, _ := newTestKeyManagerServices(t, false)

	authnProvider, auditor, err := Init(&config.AppConfig{}, nil, &http.Client{Timeout: 30 * time.Second}, km, nil)

	require.ErrorIs(t, err, ErrNilCryptoService)
	require.Nil(t, authnProvider)
	require.Nil(t, auditor)
}

type InitTestSuite struct {
	suite.Suite
}

func TestInitTestSuite(t *testing.T) {
	suite.Run(t, new(InitTestSuite))
}
