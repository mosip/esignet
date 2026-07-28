package keymanager

import (
	"context"
	"crypto"
	"database/sql"
	"errors"
	"fmt"
)

// publicKeysEqual compares two public keys via the standard library's
// Equal method (implemented by *rsa.PublicKey, *ecdsa.PublicKey, and
// ed25519.PublicKey), used by UploadCertificate's thumbprint-match check.
func publicKeysEqual(a, b crypto.PublicKey) bool {
	type equaler interface{ Equal(crypto.PublicKey) bool }
	if ea, ok := a.(equaler); ok {
		return ea.Equal(b)
	}
	return false
}

// validateForeignDomainAppID confirms appID is eligible for
// UploadOtherDomainCertificate: it must appear in the configured
// foreign-domain allow-list (Config.ForeignDomainAllowedAppIDs) AND must NOT
// already be a registered MOSIP application (i.e. must have no row of its
// own in key_policy_def) — a foreign-domain, cert-only entry must never be
// confusable with one of MOSIP's own key-hierarchy applications. ReferenceID
// is deliberately not restricted here: any ref id already used for
// asymmetric key generation (RSA_2048, EC_SECP256K1_SIGN, etc.) is fine to
// reuse under a foreign appID, since (appID, refID) together are what
// distinguish a key_alias row, and appID is guaranteed foreign by this check.
func (s *Service) validateForeignDomainAppID(ctx context.Context, appID string) error {
	allowed := false
	for _, a := range s.cfg.ForeignDomainAllowedAppIDs {
		if appID == a {
			allowed = true
			break
		}
	}
	if !allowed {
		return fmt.Errorf("%w: %q (allowed: %v)", ErrForeignDomainAppIDNotAllowed, appID, s.cfg.ForeignDomainAllowedAppIDs)
	}
	if _, err := s.q.GetKeyPolicy(ctx, appID); err == nil {
		return fmt.Errorf("%w: %q", ErrForeignDomainAppIDRegistered, appID)
	} else if !errors.Is(err, sql.ErrNoRows) {
		return fmt.Errorf("validate foreign domain application id %q: %w", appID, err)
	}
	return nil
}

// validateSymmetricKeyRefID confirms refID is in the configured allow-list
// (Config.SymmetricKeyAllowedRefIDs) before GenerateSymmetricKey proceeds.
// An empty/unset allow-list allows nothing — it must be configured
// explicitly, same "no silent default" stance as everything else required
// for key generation in this package.
func (s *Service) validateSymmetricKeyRefID(refID string) error {
	for _, allowed := range s.cfg.SymmetricKeyAllowedRefIDs {
		if refID == allowed {
			return nil
		}
	}
	return fmt.Errorf("%w: %q (allowed: %v)", ErrSymmetricKeyRefIDNotAllowed, refID, s.cfg.SymmetricKeyAllowedRefIDs)
}

// validateAsymmetricRefIDNotReserved rejects an asymmetric key generation
// (GenerateMasterKey/GetCertificate/GenerateCSR, any tier — ROOT, Component
// Master Key, EC sign key, or Component Encryption Key) whose refID is
// configured as a symmetric key reference id (Config.SymmetricKeyAllowedRefIDs).
// The reservation is enforced against the configured list directly — it
// applies even if no symmetric key with that reference id has actually
// been generated.
func (s *Service) validateAsymmetricRefIDNotReserved(refID string) error {
	for _, reserved := range s.cfg.SymmetricKeyAllowedRefIDs {
		if refID == reserved {
			return fmt.Errorf("%w: %q", ErrReferenceIDReservedForSymmetricKey, refID)
		}
	}
	return nil
}
