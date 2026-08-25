/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"crypto/sha256"
	"sync"

	jose "github.com/go-jose/go-jose/v4"
)

// maxJWKCacheEntries bounds jwkCache's size — a defensive cap, not a tuned
// value: the number of distinct encryption JWKs seen in practice is bounded
// by the number of registered relying-party clients (tens to low hundreds),
// so this should never be reached in normal operation.
const maxJWKCacheEntries = 10000

// jwkCache caches parsed/validated jose.JSONWebKeys by the SHA-256 digest of
// their raw JSON bytes, so getPublicKey doesn't re-marshal, re-parse, and
// re-validate the same client's registered encryption JWK on every Encrypt
// call (RSA-OAEP/RSA-OAEP-256, e.g. userinfo JWE for clients registered with
// an encryption key — see getPublicKey's doc comment). Keyed by content, not
// by client id: parsing a JWK's bytes is a pure function of those bytes, so
// there's no TTL or invalidation to get right — the same bytes always parse
// to the same key, and a client rotating its key simply produces a new,
// distinct cache entry rather than invalidating the old one.
type jwkCache struct {
	mu      sync.RWMutex
	entries map[[sha256.Size]byte]*jose.JSONWebKey
}

func newJWKCache() *jwkCache {
	return &jwkCache{entries: map[[sha256.Size]byte]*jose.JSONWebKey{}}
}

func (c *jwkCache) get(digest [sha256.Size]byte) (*jose.JSONWebKey, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	k, ok := c.entries[digest]
	return k, ok
}

func (c *jwkCache) set(digest [sha256.Size]byte, key *jose.JSONWebKey) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if len(c.entries) >= maxJWKCacheEntries {
		// Defensive reset rather than proper eviction — see the size-cap
		// comment above; this path should not be reached in practice.
		c.entries = map[[sha256.Size]byte]*jose.JSONWebKey{}
	}
	c.entries[digest] = key
}
