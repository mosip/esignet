package keymanager

import (
	"context"
	"fmt"
)

// ResolveCurrentSymmetricKey returns the current (non-expired) symmetric
// key's alias id, raw key bytes, and unique identifier for (appID, refID) —
// the AES-key equivalent of ResolveCurrentKey, but never auto-generates:
// AES keys must already exist (via GenerateSymmetricKey) before an
// encrypt-side caller (e.g. cryptomanager.Service.EncryptAES) can use one.
// Returns a wrapped ErrKeyNotFound if none exists — never silently creates
// one, unlike the asymmetric ensureCurrentKey path.
func (s *Service) ResolveCurrentSymmetricKey(ctx context.Context, appID, refID string) (alias string, keyBytes []byte, uniIdent string, err error) {
	a, err := s.currentAlias(ctx, appID, refID)
	if err != nil {
		return "", nil, "", err
	}
	if a == nil {
		return "", nil, "", fmt.Errorf("%w: no current symmetric key for application %q, reference %q", ErrKeyNotFound, appID, refID)
	}
	kb, err := s.ks.GetSymmetricKey(a.ID)
	if err != nil {
		return "", nil, "", fmt.Errorf("get symmetric key %q: %w", a.ID, err)
	}
	if a.UniIdent != nil {
		uniIdent = *a.UniIdent
	}
	return a.ID, kb, uniIdent, nil
}

// GetSymmetricKey returns the raw key bytes for an already-known symmetric
// key alias — exported for cryptomanager.Service.DecryptAES, which resolves
// the alias itself (by unique identifier embedded in the ciphertext
// envelope, not via currentAlias/ensureCurrentKey) and only needs this
// package for the key material once it has that alias.
func (s *Service) GetSymmetricKey(ctx context.Context, alias string) ([]byte, error) {
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
