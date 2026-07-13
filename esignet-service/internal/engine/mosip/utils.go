/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sync"

	applog "github.com/mosip/esignet/internal/log"
)

// authHeaderName is the response header carrying the token from authmanager.
const authHeaderName = "authorization"

// tokenProvider fetches and caches an authmanager token in memory. The token
// is reused across audit calls and refetched after Purge (called on 401/403).
type tokenProvider struct {
	cfg    AuditConfig
	client *http.Client

	mu     sync.RWMutex
	cached string
}

func newTokenProvider(cfg AuditConfig, client *http.Client) *tokenProvider {
	return &tokenProvider{cfg: cfg, client: client}
}

// GetAuthToken returns a cached authmanager token, fetching a new one if the
// cache is empty.
func (t *tokenProvider) GetAuthToken(ctx context.Context) (string, error) {
	t.mu.RLock()
	token := t.cached
	t.mu.RUnlock()
	if token != "" {
		return token, nil
	}

	token, err := t.fetch(ctx)
	if err != nil {
		return "", err
	}

	t.mu.Lock()
	t.cached = token
	t.mu.Unlock()
	return token, nil
}

// Purge clears the cached token so the next GetAuthToken call refetches it.
func (t *tokenProvider) Purge() {
	t.mu.Lock()
	t.cached = ""
	t.mu.Unlock()
}

func (t *tokenProvider) fetch(ctx context.Context) (string, error) {
	body, err := json.Marshal(AuditRequestWrapper[ClientIDSecretKeyRequest]{
		ID:          "ida",
		RequestTime: GetUTCDateTime(),
		Request: ClientIDSecretKeyRequest{
			ClientID:  t.cfg.ClientID,
			SecretKey: t.cfg.SecretKey,
			AppID:     t.cfg.AppID,
		},
	})
	if err != nil {
		return "", fmt.Errorf("marshal auth token request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, t.cfg.AuthTokenURL, bytes.NewReader(body))
	if err != nil {
		return "", fmt.Errorf("create auth token request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := t.client.Do(req)
	if err != nil {
		return "", fmt.Errorf("auth token request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	_, _ = io.Copy(io.Discard, resp.Body)

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", fmt.Errorf("unexpected auth token status: %d", resp.StatusCode)
	}

	token := resp.Header.Get(authHeaderName)
	if token == "" {
		applog.GetLogger().Warn("audit: authmanager returned empty authorization header")
		return "", fmt.Errorf("empty authorization header from authmanager")
	}
	return token, nil
}
