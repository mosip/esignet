package cryptomanager

import (
	"bytes"
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"database/sql"
	"encoding/base64"
	"errors"
	"fmt"

	"github.com/mosip/esignet/internal/keymanager"
)

// symmetricUniIdentLength is the fixed length, in bytes, of a
// keymanager key_alias.uni_ident value embedded in an AES envelope —
// uppercase hex of a SHA1 digest (see keymanager's uniqueIdentifier),
// always exactly 40 ASCII bytes.
const symmetricUniIdentLength = 40

// Sentinel errors specific to EncryptAES/DecryptAES (branch via errors.Is),
// following the same convention as the rest of this package's error vars.
var (
	// ErrSymmetricKeyNotFound is returned by EncryptAES when no current
	// symmetric key exists for (ApplicationID, ReferenceID) — AES keys are
	// never auto-generated (see keymanager.Service.ResolveCurrentSymmetricKey)
	// — and by DecryptAES when no key_alias for (ApplicationID, ReferenceID)
	// has a unique identifier matching the one embedded in the envelope.
	ErrSymmetricKeyNotFound = errors.New("symmetric key not found")
)

// EncryptAESRequest carries the inputs for EncryptAES. Unlike Encrypt,
// Nonce and AAD are both caller-controllable: supply either, both, or
// neither. Whichever isn't supplied is generated internally (a random
// 12-byte nonce and/or a random 32-byte AAD) and embedded in the returned
// envelope so DecryptAES can recover it; whichever IS supplied is not
// embedded, since the caller already has it and is expected to supply the
// same value again on decrypt.
type EncryptAESRequest struct {
	ApplicationID string
	ReferenceID   string
	Data          string // base64url-encoded plaintext

	// Nonce is base64url-encoded, optional. If blank, a random 12-byte
	// nonce is generated and embedded in the envelope.
	//
	// WARNING: a caller-supplied Nonce is reused verbatim with the current
	// long-lived symmetric key. AES-GCM catastrophically loses
	// confidentiality — and leaks the authentication subkey, enabling
	// ciphertext forgery — if the same (key, nonce) pair is ever used
	// twice. Rejected with ErrCallerNonceNotAllowed unless ReferenceID is
	// in the configured allow-list (Config.CallerNonceAllowedRefIDs) — only
	// a documented interop wire format that itself guarantees per-key
	// nonce uniqueness should ever be listed there; otherwise leave this
	// blank and let EncryptAES generate one.
	Nonce string

	// AAD is base64url-encoded, optional. If blank, a random 32-byte AAD
	// is generated and embedded in the envelope.
	AAD string
}

// EncryptAESResponse mirrors EncryptResponse's shape.
type EncryptAESResponse struct {
	Data string // base64url wire-format envelope, see buildAESEnvelope
}

// DecryptAESRequest carries the inputs for DecryptAES. Nonce/AAD must be
// supplied here under exactly the same presence/absence pattern as the
// original EncryptAES call: give the same value again for whichever one you
// originally supplied (it was never embedded), and leave the other blank so
// it's extracted from Data (it was embedded because you didn't supply it).
type DecryptAESRequest struct {
	ApplicationID string
	ReferenceID   string
	Data          string // base64url wire-format envelope, as produced by EncryptAES

	// Nonce is base64url-encoded. Supply it only if you supplied it to the
	// original EncryptAES call; leave blank to extract it from Data instead.
	Nonce string

	// AAD is base64url-encoded. Supply it only if you supplied it to the
	// original EncryptAES call; leave blank to extract it from Data instead.
	AAD string
}

// DecryptAESResponse mirrors DecryptResponse's shape.
type DecryptAESResponse struct {
	Data string // base64url-encoded plaintext
}

// EncryptAES AES-GCM-encrypts Data under the application's existing current
// symmetric key (never auto-generated — see
// keymanager.Service.ResolveCurrentSymmetricKey). Unlike Encrypt's hybrid
// envelope, there is no certificate/thumbprint involved; the key's own
// key_alias.uni_ident identifies which key to use on decrypt instead.
//
// Validation order matches the rest of this package: ApplicationID, then
// ReferenceID (both blank-checked, then ReferenceID checked against the
// Config.SymmetricKeyAllowedRefIDs allow-list), then Data — all before the
// one DB/keystore-touching call (ResolveCurrentSymmetricKey).
func (s *Service) EncryptAES(ctx context.Context, req EncryptAESRequest) (EncryptAESResponse, error) {
	if !isDataValid(req.ApplicationID) {
		return EncryptAESResponse{}, ErrBlankApplicationID
	}
	if !isDataValid(req.ReferenceID) {
		return EncryptAESResponse{}, ErrBlankReferenceID
	}
	if err := s.km.ValidateSymmetricKeyRefID(req.ReferenceID); err != nil {
		return EncryptAESResponse{}, err
	}
	if isDataValid(req.Nonce) && !s.callerNonceAllowed(req.ReferenceID) {
		return EncryptAESResponse{}, ErrCallerNonceNotAllowed
	}
	if !isDataValid(req.Data) {
		return EncryptAESResponse{}, ErrInvalidRequest
	}
	plaintext, err := decodeBase64(req.Data)
	if err != nil {
		return EncryptAESResponse{}, err
	}
	if len(bytes.TrimSpace(plaintext)) == 0 {
		return EncryptAESResponse{}, ErrInvalidRequest
	}

	nonce, nonceGenerated, err := resolveOrGenerate(req.Nonce, gcmNonceLength)
	if err != nil {
		return EncryptAESResponse{}, err
	}
	if len(nonce) != gcmNonceLength {
		return EncryptAESResponse{}, fmt.Errorf("%w: nonce must be %d bytes", ErrInvalidRequest, gcmNonceLength)
	}

	aad, aadGenerated, err := resolveOrGenerate(req.AAD, gcmAADLength)
	if err != nil {
		return EncryptAESResponse{}, err
	}

	// Input validation is complete — only now do we touch the DB/keystore.
	_, keyBytes, uniIdent, err := s.km.ResolveCurrentSymmetricKey(ctx, req.ApplicationID, req.ReferenceID)
	if err != nil {
		if errors.Is(err, keymanager.ErrKeyNotFound) {
			return EncryptAESResponse{}, ErrSymmetricKeyNotFound
		}
		return EncryptAESResponse{}, fmt.Errorf("resolve symmetric key: %w", err)
	}

	ciphertext, err := aesGCMSeal(keyBytes, nonce, plaintext, aad)
	if err != nil {
		return EncryptAESResponse{}, fmt.Errorf("aes-gcm encrypt: %w", err)
	}

	envelope := buildAESEnvelope(uniIdent, nonce, aad, nonceGenerated, aadGenerated, ciphertext)
	return EncryptAESResponse{Data: envelope}, nil
}

// DecryptAES reverses EncryptAES. Mirrors the rest of this package's
// validation-before-resolution ordering: ApplicationID, then ReferenceID
// (blank-checked, then allow-list-checked), then Data, then Nonce/AAD
// decoding — all before the one DB/keystore-touching resolution step.
func (s *Service) DecryptAES(ctx context.Context, req DecryptAESRequest) (DecryptAESResponse, error) {
	if !isDataValid(req.ApplicationID) {
		return DecryptAESResponse{}, ErrBlankApplicationID
	}
	if !isDataValid(req.ReferenceID) {
		return DecryptAESResponse{}, ErrBlankReferenceID
	}
	if err := s.km.ValidateSymmetricKeyRefID(req.ReferenceID); err != nil {
		return DecryptAESResponse{}, err
	}
	if !isDataValid(req.Data) {
		return DecryptAESResponse{}, ErrInvalidRequest
	}

	callerSuppliedNonce := isDataValid(req.Nonce)
	callerSuppliedAAD := isDataValid(req.AAD)

	var callerNonce, callerAAD []byte
	var err error
	if callerSuppliedNonce {
		if callerNonce, err = decodeBase64(req.Nonce); err != nil {
			return DecryptAESResponse{}, err
		}
		if len(callerNonce) != gcmNonceLength {
			return DecryptAESResponse{}, fmt.Errorf("%w: nonce must be %d bytes", ErrInvalidRequest, gcmNonceLength)
		}
	}
	if callerSuppliedAAD {
		if callerAAD, err = decodeBase64(req.AAD); err != nil {
			return DecryptAESResponse{}, err
		}
	}

	uniIdent, envNonce, envAAD, ciphertext, err := parseAESEnvelope(req.Data, !callerSuppliedNonce, !callerSuppliedAAD)
	if err != nil {
		return DecryptAESResponse{}, err
	}
	nonce, aad := envNonce, envAAD
	if callerSuppliedNonce {
		nonce = callerNonce
	}
	if callerSuppliedAAD {
		aad = callerAAD
	}

	// Input validation (including envelope parsing — structural, no I/O)
	// is complete — only now do we touch the DB/keystore. Resolved
	// globally by unique identifier first (mirroring the asymmetric
	// Decrypt/JWTDecrypt path's resolveDecryptionKey), then explicitly
	// checked against (ApplicationID, ReferenceID) — not resolved by
	// scanning only within that scope — so that "the unique identifier
	// doesn't exist anywhere" (ErrSymmetricKeyNotFound) and "it exists,
	// but under a different application/reference id"
	// (ErrKeyIdentifierMismatch) are distinguishable, exactly as
	// required: "validate that the key's unique identifier matches the
	// supplied appId and refId."
	row, err := s.q.GetKeyAliasByUniIdent(ctx, uniIdent)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return DecryptAESResponse{}, ErrSymmetricKeyNotFound
		}
		return DecryptAESResponse{}, fmt.Errorf("get key alias by uni_ident: %w", err)
	}
	rowRefID := ""
	if row.RefID != nil {
		rowRefID = *row.RefID
	}
	// Deliberately does not echo the resolved (row.AppID, rowRefID) back
	// to the caller — same principle as the asymmetric path's
	// ErrKeyIdentifierMismatch (see keyresolve.go).
	if row.AppID != req.ApplicationID || rowRefID != req.ReferenceID {
		return DecryptAESResponse{}, ErrKeyIdentifierMismatch
	}

	keyBytes, err := s.km.GetSymmetricKey(ctx, row.ID)
	if err != nil {
		return DecryptAESResponse{}, fmt.Errorf("get symmetric key: %w", err)
	}

	plaintext, err := aesGCMOpen(keyBytes, nonce, ciphertext, aad)
	if err != nil {
		return DecryptAESResponse{}, fmt.Errorf("aes-gcm decrypt: %w", err)
	}
	return DecryptAESResponse{Data: base64.RawURLEncoding.EncodeToString(plaintext)}, nil
}

// callerNonceAllowed reports whether refID is in the configured allow-list
// for a caller-supplied EncryptAES Nonce (Config.CallerNonceAllowedRefIDs).
func (s *Service) callerNonceAllowed(refID string) bool {
	for _, allowed := range s.cfg.CallerNonceAllowedRefIDs {
		if refID == allowed {
			return true
		}
	}
	return false
}

// resolveOrGenerate decodes value (base64) if non-blank, else generates
// size random bytes — reporting whether generation happened, since only
// generated values get embedded in the envelope (see buildAESEnvelope).
func resolveOrGenerate(value string, size int) (b []byte, generated bool, err error) {
	if isDataValid(value) {
		b, err = decodeBase64(value)
		return b, false, err
	}
	b = make([]byte, size)
	if _, err := rand.Read(b); err != nil {
		return nil, false, fmt.Errorf("generate random bytes: %w", err)
	}
	return b, true, nil
}

// buildAESEnvelope assembles: uniIdent (40 ASCII bytes) || nonce (12 bytes,
// only if nonceGenerated) || aad (32 bytes, only if aadGenerated) ||
// ciphertext+tag — matching the order specified for this feature: Key's
// Unique Identifier + Nonce (generated internally) + AAD (generated
// internally) + Encrypted data.
func buildAESEnvelope(uniIdent string, nonce, aad []byte, nonceGenerated, aadGenerated bool, ciphertext []byte) string {
	out := make([]byte, 0, symmetricUniIdentLength+len(nonce)+len(aad)+len(ciphertext))
	out = append(out, []byte(uniIdent)...)
	if nonceGenerated {
		out = append(out, nonce...)
	}
	if aadGenerated {
		out = append(out, aad...)
	}
	out = append(out, ciphertext...)
	return base64.RawURLEncoding.EncodeToString(out)
}

// parseAESEnvelope reverses buildAESEnvelope. extractNonce/extractAAD tell
// it whether the caller's DecryptAES request omitted Nonce/AAD (so they
// must be read from the envelope) — mirroring exactly which of them
// buildAESEnvelope would have embedded for the matching EncryptAES call.
func parseAESEnvelope(wireFormat string, extractNonce, extractAAD bool) (uniIdent string, nonce, aad, ciphertext []byte, err error) {
	raw, err := base64.RawURLEncoding.DecodeString(wireFormat)
	if err != nil {
		return "", nil, nil, nil, fmt.Errorf("%w: %w", ErrEnvelopeMalformed, err)
	}
	if len(raw) < symmetricUniIdentLength {
		return "", nil, nil, nil, fmt.Errorf("%w: shorter than the unique identifier field", ErrEnvelopeMalformed)
	}
	uniIdent = string(raw[:symmetricUniIdentLength])
	rest := raw[symmetricUniIdentLength:]

	if extractNonce {
		if len(rest) < gcmNonceLength {
			return "", nil, nil, nil, fmt.Errorf("%w: shorter than the embedded nonce field", ErrEnvelopeMalformed)
		}
		nonce = rest[:gcmNonceLength]
		rest = rest[gcmNonceLength:]
	}
	if extractAAD {
		if len(rest) < gcmAADLength {
			return "", nil, nil, nil, fmt.Errorf("%w: shorter than the embedded AAD field", ErrEnvelopeMalformed)
		}
		aad = rest[:gcmAADLength]
		rest = rest[gcmAADLength:]
	}
	ciphertext = rest
	return uniIdent, nonce, aad, ciphertext, nil
}

// aesGCMSeal / aesGCMOpen are the general AES-GCM primitives EncryptAES/
// DecryptAES use directly with caller-visible nonce/AAD — distinct from
// envelope.go's symmetricEncrypt/symmetricDecrypt, which always generate
// their own 32-byte AAD internally (the VER_R2 format) and never accept an
// explicit nonce. GCM (unlike CBC) has no block-padding step, so "NoPadding"
// (as in Java's "AES/GCM/NoPadding") is automatic here — Go's cipher.AEAD
// never pads.
func aesGCMSeal(key, nonce, plaintext, aad []byte) ([]byte, error) {
	gcm, err := aesGCMWithNonceSize(key, len(nonce))
	if err != nil {
		return nil, err
	}
	return gcm.Seal(nil, nonce, plaintext, aad), nil
}

func aesGCMOpen(key, nonce, ciphertext, aad []byte) ([]byte, error) {
	gcm, err := aesGCMWithNonceSize(key, len(nonce))
	if err != nil {
		return nil, err
	}
	plaintext, err := gcm.Open(nil, nonce, ciphertext, aad)
	if err != nil {
		return nil, fmt.Errorf("gcm open: %w", err)
	}
	return plaintext, nil
}

func aesGCMWithNonceSize(key []byte, nonceSize int) (cipher.AEAD, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("new AES cipher: %w", err)
	}
	gcm, err := cipher.NewGCMWithNonceSize(block, nonceSize)
	if err != nil {
		return nil, fmt.Errorf("new GCM: %w", err)
	}
	return gcm, nil
}
