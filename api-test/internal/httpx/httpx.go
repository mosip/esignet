// Package httpx holds the tiny bits of HTTP plumbing shared by the conformance
// client and the eSignet driver, so the InsecureSkipVerify policy and the
// header-cloning used to capture the debug call trace each live in one place.
package httpx

import (
	"crypto/tls"
	"net/http"
	"time"
)

// NewClient builds an *http.Client with the harness's shared TLS-verify policy.
// Callers that need a cookie jar or a CheckRedirect override set those fields
// on the returned client (or a shallow copy of it) afterward.
func NewClient(tlsVerify bool, timeout time.Duration) *http.Client {
	return &http.Client{
		Transport: &http.Transport{TLSClientConfig: &tls.Config{
			MinVersion:         tls.VersionTLS12,
			InsecureSkipVerify: !tlsVerify,
		}},
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
