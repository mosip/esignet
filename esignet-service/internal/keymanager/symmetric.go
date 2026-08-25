package keymanager

import (
	"context"
	"fmt"
	"time"
)

// resolvedSymmetricKey is ResolveCurrentSymmetricKey's cached value — the
// three values it returns, bundled so ttlCache[V] has a single V to store.
type resolvedSymmetricKey struct {
	alias    string
	keyBytes []byte
	uniIdent string
}

// ResolveCurrentSymmetricKey returns the current (non-expired) symmetric
// key's alias id, raw key bytes, and unique identifier for (appID, refID) —
// the AES-key equivalent of ResolveCurrentKey, but never auto-generates:
// AES keys must already exist (via GenerateSymmetricKey) before an
// encrypt-side caller (e.g. cryptomanager.Service.EncryptAES) can use one.
// Returns a wrapped ErrKeyNotFound if none exists — never silently creates
// one, unlike the asymmetric ensureCurrentKey path.
//
// Cached above resolveCurrentSymmetricKeyUncached (see
// Service.symmetricKeyCache) — this is the resolution behind every runtime
// AES-GCM encrypt/decrypt the engine performs against its own CACHE_ENCRYPT
// key (see keymanager.RefIDCacheEncrypt), so an uncached call here would
// otherwise add the same DB-query cost every such operation pays.
// Config.KeyCacheExpiry <= 0 disables caching, resolving fresh every call as
// before.
func (s *Service) ResolveCurrentSymmetricKey(ctx context.Context, appID, refID string) (alias string, keyBytes []byte, uniIdent string, err error) {
	if s.cfg.KeyCacheExpiry <= 0 {
		alias, keyBytes, uniIdent, _, err = s.resolveCurrentSymmetricKeyUncached(ctx, appID, refID)
		return alias, keyBytes, uniIdent, err
	}
	rsk, err := s.symmetricKeyCache.getOrLoad(cacheKey(appID, refID), func() (resolvedSymmetricKey, time.Time, error) {
		a, kb, ui, expiry, lerr := s.resolveCurrentSymmetricKeyUncached(ctx, appID, refID)
		if lerr != nil {
			return resolvedSymmetricKey{}, time.Time{}, lerr
		}
		return resolvedSymmetricKey{alias: a, keyBytes: kb, uniIdent: ui}, s.cacheExpiry(expiry), nil
	})
	if err != nil {
		return "", nil, "", err
	}
	return rsk.alias, rsk.keyBytes, rsk.uniIdent, nil
}

// resolveCurrentSymmetricKeyUncached is ResolveCurrentSymmetricKey's
// uncached implementation, additionally returning the resolved alias's own
// expiry — needed only to compute the cache entry's TTL (see
// Service.cacheExpiry); the public method's signature doesn't expose it
// since no existing caller needs it.
func (s *Service) resolveCurrentSymmetricKeyUncached(ctx context.Context, appID, refID string) (alias string, keyBytes []byte, uniIdent string, expiry time.Time, err error) {
	a, err := s.currentAlias(ctx, appID, refID)
	if err != nil {
		return "", nil, "", time.Time{}, err
	}
	if a == nil {
		return "", nil, "", time.Time{}, fmt.Errorf("%w: no current symmetric key for application %q, reference %q", ErrKeyNotFound, appID, refID)
	}
	kb, err := s.ks.GetSymmetricKey(a.ID)
	if err != nil {
		return "", nil, "", time.Time{}, fmt.Errorf("get symmetric key %q: %w", a.ID, err)
	}
	if a.UniIdent != nil {
		uniIdent = *a.UniIdent
	}
	if a.KeyExpireDtimes != nil {
		expiry = *a.KeyExpireDtimes
	}
	return a.ID, kb, uniIdent, expiry, nil
}

// GetSymmetricKey returns the raw key bytes for an already-known symmetric
// key alias — exported for cryptomanager.Service.DecryptAES, which resolves
// the alias itself (by unique identifier embedded in the ciphertext
// envelope, not via currentAlias/ensureCurrentKey) and only needs this
// package for the key material once it has that alias.
func (s *Service) GetSymmetricKey(_ context.Context, alias string) ([]byte, error) {
	return s.ks.GetSymmetricKey(alias)
}

// ValidateSymmetricKeyRefID is the exported form of the existing
// validateSymmetricKeyRefID, for cryptomanager.Service.EncryptAES/DecryptAES,
// which must enforce the exact same Config.SymmetricKeyAllowedRefIDs
// allow-list GenerateSymmetricKey already does — reused directly rather
// than duplicated, so both surfaces stay in sync by construction.
func (s *Service) ValidateSymmetricKeyRefID(refID string) error {
	return s.validateSymmetricKeyRefID(refID)
}
