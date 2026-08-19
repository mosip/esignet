// Package httpx holds the HTTP plumbing shared by the conformance client and the eSignet driver.
package httpx

import (
	"crypto/tls"
	"net/http"
	"time"
)

// NewClient builds an *http.Client with the harness's shared TLS-verify policy.
func NewClient(tlsVerify bool, timeout time.Duration) *http.Client {
	return &http.Client{
		Transport: &http.Transport{
			// Replacing http.DefaultTransport drops its proxy support with it, and
			// a CI network that only reaches the deployment through a proxy would
			// then fail every call with a bare connection error.
			Proxy: http.ProxyFromEnvironment,
			TLSClientConfig: &tls.Config{
				MinVersion: tls.VersionTLS12,
				//nolint:gosec // operator-controlled: only the suite's self-signed localhost cert is exempted via tlsVerify.
				InsecureSkipVerify: !tlsVerify,
			},
		},
		Timeout: timeout,
	}
}

// CloneHeader deep-copies a header map for safe capture into a call trace.
func CloneHeader(h http.Header) map[string][]string {
	out := make(map[string][]string, len(h))
	for k, v := range h {
		out[k] = append([]string(nil), v...)
	}
	return out
}
