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
	"time"

	"github.com/golang-jwt/jwt/v5"

	applog "github.com/mosip/esignet/internal/log"
)

// authHeaderName is the response header carrying the token from authmanager.
const authHeaderName = "authorization"

// tokenProvider fetches and caches an authmanager token in memory. The token
// is reused across audit calls and refetched after Purge (called on 401/403).
type tokenProvider struct {
	cfg    Config
	client *http.Client

	mu     sync.RWMutex
	cached string
	expiry time.Time
}

func newTokenProvider(cfg Config, client *http.Client) *tokenProvider {
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

	token, expiry, err := t.fetch(ctx)
	if err != nil {
		return "", err
	}

	t.mu.Lock()
	t.cached = token
	t.expiry = expiry
	t.mu.Unlock()
	return token, nil
}

// Purge clears the cached token so the next GetAuthToken call refetches it.
func (t *tokenProvider) Purge() {
	t.mu.Lock()
	t.cached = ""
	t.expiry = time.Time{}
	t.mu.Unlock()
}

// TokenExpiry returns the expiry of the currently cached auth token and
// whether it's known — i.e. the token parses as a JWT with an exp claim.
// Callers that cache data fetched using this token (e.g. signing
// certificates) can use this to avoid caching that data past the point
// where the token itself needs to be refreshed anyway.
func (t *tokenProvider) TokenExpiry() (time.Time, bool) {
	t.mu.RLock()
	defer t.mu.RUnlock()
	return t.expiry, !t.expiry.IsZero()
}

// fetch requests a fresh authmanager token and returns it alongside its
// expiry, parsed from the token's own exp claim (zero time if the token
// isn't a JWT or carries no exp claim — callers treat that as "unknown").
func (t *tokenProvider) fetch(ctx context.Context) (string, time.Time, error) {
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
		return "", time.Time{}, fmt.Errorf("marshal auth token request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, t.cfg.AuthTokenURL, bytes.NewReader(body))
	if err != nil {
		return "", time.Time{}, fmt.Errorf("create auth token request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := t.client.Do(req)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("auth token request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	_, _ = io.Copy(io.Discard, resp.Body)

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", time.Time{}, fmt.Errorf("unexpected auth token status: %d", resp.StatusCode)
	}

	token := resp.Header.Get(authHeaderName)
	if token == "" {
		applog.GetLogger().Warn(ctx, "audit: authmanager returned empty authorization header")
		return "", time.Time{}, fmt.Errorf("empty authorization header from authmanager")
	}
	return token, tokenExpiry(token), nil
}

// tokenExpiry parses the exp claim from a JWT without verifying its
// signature — authmanager issues the token, esignet only needs to know when
// it stops being usable, not to authenticate it.
func tokenExpiry(token string) time.Time {
	claims := jwt.MapClaims{}
	if _, _, err := jwt.NewParser().ParseUnverified(token, claims); err != nil {
		return time.Time{}
	}
	expiresAt, err := claims.GetExpirationTime()
	if err != nil || expiresAt == nil {
		return time.Time{}
	}
	return expiresAt.Time
}
