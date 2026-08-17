package keymanager

import (
	"sync"
	"time"

	"golang.org/x/sync/singleflight"
)

// ttlCache is a small in-process, per-key TTL cache with singleflight-backed
// loading. It backs GetSigningCertificate/ResolveCurrentKey/
// ResolveCurrentSymmetricKey's key-resolution caches (see Service's cache
// fields) — each otherwise re-runs ensureCurrentKey's DB queries (and, for
// GetSigningCertificate, a keystore/HSM call) on every single call, "by
// design, uncached" until now. See Config.KeyCacheExpiry for the TTL knob
// and cacheExpiry (service.go) for how an entry's expiry is computed.
type ttlCache[V any] struct {
	mu      sync.RWMutex
	entries map[string]ttlCacheEntry[V]
	sf      singleflight.Group
}

type ttlCacheEntry[V any] struct {
	value     V
	expiresAt time.Time
}

func newTTLCache[V any]() *ttlCache[V] {
	return &ttlCache[V]{entries: map[string]ttlCacheEntry[V]{}}
}

// get returns the cached value for key if present and not expired.
func (c *ttlCache[V]) get(key string) (V, bool) {
	c.mu.RLock()
	e, ok := c.entries[key]
	c.mu.RUnlock()
	if !ok || time.Now().UTC().After(e.expiresAt) {
		var zero V
		return zero, false
	}
	return e.value, true
}

func (c *ttlCache[V]) set(key string, value V, expiresAt time.Time) {
	c.mu.Lock()
	c.entries[key] = ttlCacheEntry[V]{value: value, expiresAt: expiresAt}
	c.mu.Unlock()
}

// invalidate removes key, if present. Called by every write path that
// changes what "current" resolves to for a given (appID, refID): key
// generation/rotation (ensureCurrentKey), RevokeKey, UploadCertificate, and
// GenerateSymmetricKey — see invalidateCurrentKeyCaches (service.go). These
// caches are in-process only, so invalidation here only ever affects the pod
// that made the write; KeyCacheExpiry is the backstop bounding how stale
// another pod's cache entry can be.
func (c *ttlCache[V]) invalidate(key string) {
	c.mu.Lock()
	delete(c.entries, key)
	c.mu.Unlock()
}

// getOrLoad returns the cached value for key, or calls load to resolve and
// cache it. Concurrent misses for the same key are collapsed into a single
// load call via singleflight, so a cold entry under concurrent load (e.g.
// right after invalidate, or at process start) doesn't fan out into
// redundant DB/keystore work. load returns the value together with its
// absolute expiry time.
func (c *ttlCache[V]) getOrLoad(key string, load func() (V, time.Time, error)) (V, error) {
	if v, ok := c.get(key); ok {
		return v, nil
	}
	res, err, _ := c.sf.Do(key, func() (interface{}, error) {
		// Re-check: a concurrent caller may have already populated the
		// entry between this goroutine's initial miss and it acquiring the
		// singleflight slot.
		if v, ok := c.get(key); ok {
			return v, nil
		}
		v, expiresAt, err := load()
		if err != nil {
			return nil, err
		}
		c.set(key, v, expiresAt)
		return v, nil
	})
	if err != nil {
		var zero V
		return zero, err
	}
	return res.(V), nil
}

// cacheKey builds the (appID, refID) key shared by every ttlCache[...] in
// Service. "\x1f" (ASCII unit separator) avoids collisions between e.g.
// appID="A", refID="B|C" and appID="A|B", refID="C" without needing to
// escape either component — appID/refID are internal identifiers, not
// user-supplied free text, so a fixed delimiter is enough.
func cacheKey(appID, refID string) string {
	return appID + "\x1f" + refID
}
