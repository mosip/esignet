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
// from cfg. Each outbound consumer (a given ID-system provider, or captcha
// validation) builds its own client from its own HTTPClientConfig block, so
// they don't share a connection pool.
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
