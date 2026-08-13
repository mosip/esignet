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

	"github.com/stretchr/testify/require"
)

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

func TestNewHTTPClient(t *testing.T) {
	cfg := testHTTPClientConfig()
	c := NewHTTPClient(cfg)
	require.Equal(t, 30*time.Second, c.Timeout)

	transport, ok := c.Transport.(*http.Transport)
	require.True(t, ok)
	require.Equal(t, 10*time.Second, transport.TLSHandshakeTimeout)
	require.Equal(t, 10*time.Second, transport.ResponseHeaderTimeout)
	require.Equal(t, 90*time.Second, transport.IdleConnTimeout)
	require.Equal(t, 500, transport.MaxConnsPerHost)
	require.Equal(t, 500, transport.MaxIdleConns)
	require.Equal(t, 200, transport.MaxIdleConnsPerHost)
}

func TestNewHTTPClientReturnsDistinctInstances(t *testing.T) {
	cfg := testHTTPClientConfig()
	require.NotSame(t, NewHTTPClient(cfg), NewHTTPClient(cfg))
}
