/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package security for application security
package security

import (
	"crypto"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"math/big"
	"net/http"
	"sync"
	"time"

	applog "github.com/mosip/esignet/internal/log"
)

var logger = applog.GetLogger().Named("security")

type jwkKey struct {
	Kty string `json:"kty"`
	Kid string `json:"kid"`
	Use string `json:"use"`
	Alg string `json:"alg"`
	// RSA fields
	N string `json:"n"`
	E string `json:"e"`
	// EC fields
	Crv string `json:"crv"`
	X   string `json:"x"`
	Y   string `json:"y"`
}

type jwksDoc struct {
	Keys []jwkKey `json:"keys"`
}

// JWKSCache fetches and caches public keys from a JWKS endpoint.
type JWKSCache struct {
	mu        sync.RWMutex
	keys      map[string]crypto.PublicKey // kid → public key
	fetchedAt time.Time
	ttl       time.Duration
	url       string
	client    *http.Client
}

// NewJWKSCache creates a JWKSCache that fetches keys from url and caches them for ttl.
func NewJWKSCache(url string, ttl time.Duration) *JWKSCache {
	return &JWKSCache{
		url: url,
		ttl: ttl,
		client: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// GetKey returns the public key for kid, refreshing the cache if stale or missing.
func (c *JWKSCache) GetKey(kid string) (crypto.PublicKey, error) {
	c.mu.RLock()
	if time.Since(c.fetchedAt) < c.ttl {
		key, ok := c.keys[kid]
		c.mu.RUnlock()
		if ok {
			return key, nil
		}
		// kid miss may be a key rotation; force one refresh even though TTL is fresh.
		logger.Debug("kid not in cache, forcing JWKS refresh", applog.String("kid", kid))
		if err := c.forceRefresh(); err != nil {
			return nil, err
		}
		c.mu.RLock()
		key, ok = c.keys[kid]
		c.mu.RUnlock()
		if !ok {
			return nil, fmt.Errorf("key %q not found in JWKS", kid)
		}
		return key, nil
	}
	c.mu.RUnlock()

	// Cache is stale — refresh then look up
	if err := c.refresh(); err != nil {
		return nil, err
	}

	c.mu.RLock()
	defer c.mu.RUnlock()
	key, ok := c.keys[kid]
	if !ok {
		return nil, fmt.Errorf("key %q not found in JWKS", kid)
	}
	return key, nil
}

func (c *JWKSCache) forceRefresh() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.fetchLocked()
}

func (c *JWKSCache) refresh() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	// Double-checked locking: another goroutine may have refreshed while we waited.
	if time.Since(c.fetchedAt) < c.ttl {
		return nil
	}
	return c.fetchLocked()
}

func (c *JWKSCache) fetchLocked() error {
	logger.Debug("refreshing JWKS cache", applog.String("url", c.url))

	resp, err := c.client.Get(c.url)
	if err != nil {
		logger.Warn("JWKS fetch failed", applog.String("url", c.url), applog.Error(err))
		return fmt.Errorf("fetch JWKS from %s: %w", c.url, err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		logger.Warn("JWKS endpoint returned non-200 status",
			applog.String("url", c.url), applog.Int("status", resp.StatusCode))
		return fmt.Errorf("JWKS endpoint returned %d", resp.StatusCode)
	}

	var doc jwksDoc
	if err := json.NewDecoder(resp.Body).Decode(&doc); err != nil {
		logger.Warn("failed to decode JWKS response", applog.String("url", c.url), applog.Error(err))
		return fmt.Errorf("decode JWKS: %w", err)
	}

	keys := make(map[string]crypto.PublicKey, len(doc.Keys))
	for _, k := range doc.Keys {
		if k.Use != "" && k.Use != "sig" {
			continue // skip encryption keys
		}
		pub, err := parseJWK(k)
		if err != nil {
			// skip keys we can't parse rather than failing entirely; kid/kty
			// identify the key without exposing any key material.
			logger.Warn("skipping unparseable JWKS key",
				applog.String("kid", k.Kid), applog.String("kty", k.Kty), applog.Error(err))
			continue
		}
		keys[k.Kid] = pub
	}

	c.keys = keys
	c.fetchedAt = time.Now()
	logger.Debug("JWKS cache refreshed", applog.String("url", c.url), applog.Int("keyCount", len(keys)))
	return nil
}

func parseJWK(k jwkKey) (crypto.PublicKey, error) {
	switch k.Kty {
	case "RSA":
		return parseRSAKey(k)
	case "EC":
		return parseECKey(k)
	default:
		return nil, fmt.Errorf("unsupported key type %q", k.Kty)
	}
}

func parseRSAKey(k jwkKey) (*rsa.PublicKey, error) {
	nBytes, err := base64.RawURLEncoding.DecodeString(k.N)
	if err != nil {
		return nil, fmt.Errorf("decode RSA n: %w", err)
	}
	eBytes, err := base64.RawURLEncoding.DecodeString(k.E)
	if err != nil {
		return nil, fmt.Errorf("decode RSA e: %w", err)
	}
	return &rsa.PublicKey{
		N: new(big.Int).SetBytes(nBytes),
		E: int(new(big.Int).SetBytes(eBytes).Int64()),
	}, nil
}

func parseECKey(k jwkKey) (*ecdsa.PublicKey, error) {
	var curve elliptic.Curve
	switch k.Crv {
	case "P-256":
		curve = elliptic.P256()
	case "P-384":
		curve = elliptic.P384()
	case "P-521":
		curve = elliptic.P521()
	default:
		return nil, fmt.Errorf("unsupported EC curve %q", k.Crv)
	}
	xBytes, err := base64.RawURLEncoding.DecodeString(k.X)
	if err != nil {
		return nil, fmt.Errorf("decode EC x: %w", err)
	}
	yBytes, err := base64.RawURLEncoding.DecodeString(k.Y)
	if err != nil {
		return nil, fmt.Errorf("decode EC y: %w", err)
	}
	return &ecdsa.PublicKey{
		Curve: curve,
		X:     new(big.Int).SetBytes(xBytes),
		Y:     new(big.Int).SetBytes(yBytes),
	}, nil
}
