package cryptomanager

import (
	"context"
	"crypto"
	"database/sql"
	"errors"
	"fmt"

	"github.com/mosip/esignet/internal/keymanager"
)

// resolveDecryptionKey resolves a certificate-thumbprint-hex (embedded in
// an Encrypt envelope's key material, or a JWTEncrypt token's kid header)
// into the private key needed to decrypt it — mirrors Java's
// PrivateKeyDecryptorHelper.getDBKeyStoreData + getKeyObjects, used
// identically by both Decrypt and JWTDecrypt. Unlike Java, there is no
// fetchMasterKey-style flag: a keystore-resident result (ROOT, a Component
// Master Key, or an EC sign key) is always rejected, since Encrypt already
// refuses to ever target one — see the design doc's Context section.
func (s *Service) resolveDecryptionKey(ctx context.Context, requestAppID, requestRefID, thumbprintHex string) (priv crypto.PrivateKey, resolvedAlias string, err error) {
	row, err := s.q.GetKeyAliasByCertThumbprint(ctx, thumbprintHex)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, "", ErrKeyNotFoundForThumbprint
		}
		return nil, "", fmt.Errorf("get key alias by cert thumbprint: %w", err)
	}

	rowRefID := ""
	if row.RefID != nil {
		rowRefID = *row.RefID
	}
	// Deliberately does not echo the resolved (row.AppID, rowRefID) back to
	// the caller — that would disclose which application/reference id the
	// thumbprint actually belongs to. A plain mismatch message is enough.
	if row.AppID != requestAppID || rowRefID != requestRefID {
		return nil, "", ErrKeyIdentifierMismatch
	}

	// Defensive check, not conditional on a flag: Encrypt's guard already
	// refuses to ever target a keystore-resident key, so a genuinely
	// Encrypt-produced envelope should never resolve to one here. Checked
	// unconditionally anyway, for both Decrypt and JWTDecrypt.
	if keymanager.IsKeystoreResident(row.AppID, rowRefID) {
		return nil, "", ErrDecryptionNotAllowed
	}

	rec, err := s.q.GetKeyStoreRecord(ctx, row.ID)
	if err != nil {
		return nil, "", fmt.Errorf("get key_store record %q: %w", row.ID, err)
	}
	if rec.PrivateKey == keymanager.ForeignDomainPrivateKeyMarker {
		return nil, "", ErrForeignDomainKeyNotDecryptable
	}

	_, priv, err = s.km.ResolveKeyMaterial(ctx, row.ID, false)
	if err != nil {
		return nil, "", fmt.Errorf("resolve key material for alias %q: %w", row.ID, err)
	}
	return priv, row.ID, nil
}
