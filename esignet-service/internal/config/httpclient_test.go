/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package config

import (
	"net/http"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

type HTTPClientTestSuite struct {
	suite.Suite
}

func TestHTTPClientTestSuite(t *testing.T) {
	suite.Run(t, new(HTTPClientTestSuite))
}

func testHTTPClientConfig() HTTPClientConfig {
	return HTTPClientConfig{
		TimeoutSecs:               30,
		DialTimeoutSecs:           5,
		DialKeepAliveSecs:         30,
		TLSHandshakeTimeoutSecs:   10,
		ResponseHeaderTimeoutSecs: 10,
		IdleConnTimeoutSecs:       90,
		MaxConnsPerHost:           500,
		MaxIdleConns:              500,
		MaxIdleConnsPerHost:       200,
	}
}

func (ts *HTTPClientTestSuite) TestNewHTTPClient() {
	cfg := testHTTPClientConfig()
	c := NewHTTPClient(cfg)
	ts.Require().Equal(30*time.Second, c.Timeout)

	transport, ok := c.Transport.(*http.Transport)
	ts.Require().True(ok)
	ts.Require().Equal(10*time.Second, transport.TLSHandshakeTimeout)
	ts.Require().Equal(10*time.Second, transport.ResponseHeaderTimeout)
	ts.Require().Equal(90*time.Second, transport.IdleConnTimeout)
	ts.Require().Equal(500, transport.MaxConnsPerHost)
	ts.Require().Equal(500, transport.MaxIdleConns)
	ts.Require().Equal(200, transport.MaxIdleConnsPerHost)
	ts.Require().True(transport.ForceAttemptHTTP2)
}

func (ts *HTTPClientTestSuite) TestNewHTTPClientReturnsDistinctInstances() {
	cfg := testHTTPClientConfig()
	ts.Require().NotSame(NewHTTPClient(cfg), NewHTTPClient(cfg))
}
