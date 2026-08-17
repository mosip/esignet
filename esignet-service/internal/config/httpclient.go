/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package config

import (
	"context"
	"net"
	"net/http"
	"time"

	applog "github.com/mosip/esignet/internal/log"
)

// NewHTTPClient returns a tuned HTTP client for outbound calls, configured
// from cfg. Each HTTPClientConfig block gets its own client/connection pool:
// OutboundIDSystemHTTPClient is built once for ID-system calls, and
// OutboundHTTPClient is built once and shared by both captcha validation and
// JWKS fetching (see commonHTTPClient in cmd/esignet/main.go) — those two
// consumers do share a pool with each other, so tuning OutboundHTTPClient
// affects both.
func NewHTTPClient(cfg HTTPClientConfig) *http.Client {
	applog.GetLogger().Debug(context.Background(), "building http client",
		applog.Int("timeoutSecs", cfg.TimeoutSecs),
		applog.Int("dialTimeoutSecs", cfg.DialTimeoutSecs),
		applog.Int("dialKeepAliveSecs", cfg.DialKeepAliveSecs),
		applog.Int("tlsHandshakeTimeoutSecs", cfg.TLSHandshakeTimeoutSecs),
		applog.Int("responseHeaderTimeoutSecs", cfg.ResponseHeaderTimeoutSecs),
		applog.Int("idleConnTimeoutSecs", cfg.IdleConnTimeoutSecs),
		applog.Int("MaxConnsPerHost", cfg.MaxConnsPerHost),
		applog.Int("MaxIdleConnsPerHost", cfg.MaxIdleConnsPerHost),
		applog.Int("MaxIdleConns", cfg.MaxIdleConns),
	)

	return &http.Client{
		Timeout: time.Duration(cfg.TimeoutSecs) * time.Second,
		Transport: &http.Transport{
			DialContext: (&net.Dialer{
				Timeout:   time.Duration(cfg.DialTimeoutSecs) * time.Second,
				KeepAlive: time.Duration(cfg.DialKeepAliveSecs) * time.Second,
			}).DialContext,
			TLSHandshakeTimeout:   time.Duration(cfg.TLSHandshakeTimeoutSecs) * time.Second,
			ResponseHeaderTimeout: time.Duration(cfg.ResponseHeaderTimeoutSecs) * time.Second,
			IdleConnTimeout:       time.Duration(cfg.IdleConnTimeoutSecs) * time.Second,
			MaxConnsPerHost:       cfg.MaxConnsPerHost,
			MaxIdleConns:          cfg.MaxIdleConns,
			MaxIdleConnsPerHost:   cfg.MaxIdleConnsPerHost,
		},
	}
}
