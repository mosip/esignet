package cryptomanager

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"fmt"
)

const (
	// verR2Header is the fixed 6-byte format marker prepended to every
	// envelope's key material — Java's VERSION_RSA_2048 constant. There is
	// only one wire format in this port (see design doc's Context section:
	// caller-supplied salt/AAD was dropped), so this is always present, not
	// a branch marker.
	verR2Header = "VER_R2"

	thumbprintLen    = 32  // SHA-256 digest size
	rsa2048CipherLen = 256 // RSA-2048-OAEP ciphertext length

	// keyMaterialLength is the total length of a well-formed envelope's key
	// material: VER_R2 header + raw thumbprint + wrapped session key.
	keyMaterialLength = len(verR2Header) + thumbprintLen + rsa2048CipherLen

	gcmAADLength   = 32 // Java's GCM_AAD_LENGTH
	gcmNonceLength = 12 // Java's GCM_NONCE_LENGTH — first 12 bytes of the AAD
)

// symmetricEncrypt is the sole encryption path this port implements —
// mirrors Java's CryptomanagerServiceImpl.generateAadAndEncryptData: 32
// random bytes serve as both the AES-GCM AAD and, via their first 12 bytes,
// the nonce; the full 32-byte AAD is prepended to the ciphertext+tag.
func symmetricEncrypt(sessionKey, plaintext []byte) ([]byte, error) {
	gcm, err := newGCM(sessionKey)
	if err != nil {
		return nil, err
	}
	aad := make([]byte, gcmAADLength)
	if _, err := rand.Read(aad); err != nil {
		return nil, fmt.Errorf("generate aad: %w", err)
	}
	ciphertext := gcm.Seal(nil, aad[:gcmNonceLength], plaintext, aad)
	return append(aad, ciphertext...), nil
}

// symmetricDecrypt reverses symmetricEncrypt: the first 32 bytes of
// ciphertext are the AAD (whose first 12 bytes double as the nonce), the
// remainder is the GCM ciphertext+tag.
func symmetricDecrypt(sessionKey, ciphertext []byte) ([]byte, error) {
	if len(ciphertext) < gcmAADLength {
		return nil, fmt.Errorf("ciphertext too short: expected at least %d bytes of AAD, got %d", gcmAADLength, len(ciphertext))
	}
	aad := ciphertext[:gcmAADLength]
	data := ciphertext[gcmAADLength:]

	gcm, err := newGCM(sessionKey)
	if err != nil {
		return nil, err
	}
	plaintext, err := gcm.Open(nil, aad[:gcmNonceLength], data, aad)
	if err != nil {
		return nil, fmt.Errorf("gcm open: %w", err)
	}
	return plaintext, nil
}

func newGCM(key []byte) (cipher.AEAD, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("new AES cipher: %w", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("new GCM: %w", err)
	}
	return gcm, nil
}

// thumbprintRaw is the raw 32-byte SHA-256 digest of a certificate's DER
// encoding — the value embedded directly in the envelope's key material
// (distinct from keymanager.ThumbprintForCert, which returns the same
// digest hex-encoded for DB storage/lookup).
func thumbprintRaw(cert *x509.Certificate) [32]byte {
	return sha256.Sum256(cert.Raw)
}

// buildEnvelope assembles the final wire format: base64url( VER_R2 header
// || thumbprint(32 raw bytes) || encryptedSessionKey || splitter ||
// encryptedData ) — key material first, then the splitter, then the
// encrypted data. This order was verified directly against
// CryptoUtil.combineByteArray's method body (not just its call site): the
// method's parameter names suggest data-then-key, but the body itself lays
// out key-then-splitter-then-data.
func buildEnvelope(splitter string, thumbprint [32]byte, encryptedSessionKey, encryptedData []byte) string {
	keyMaterial := make([]byte, 0, len(verR2Header)+len(thumbprint)+len(encryptedSessionKey))
	keyMaterial = append(keyMaterial, []byte(verR2Header)...)
	keyMaterial = append(keyMaterial, thumbprint[:]...)
	keyMaterial = append(keyMaterial, encryptedSessionKey...)

	out := make([]byte, 0, len(keyMaterial)+len(splitter)+len(encryptedData))
	out = append(out, keyMaterial...)
	out = append(out, []byte(splitter)...)
	out = append(out, encryptedData...)
	return base64.RawURLEncoding.EncodeToString(out)
}

// parseEnvelope reverses buildEnvelope. thumbprintHex is lower-case hex
// (encoding/hex's default), matching the format keymanager.ThumbprintForCert
// produces and key_alias.cert_thumbprint stores — not upper-case, unlike
// the Java reference's PrivateKeyDecryptorHelper lookups, since this must
// match what this Go port itself writes to the DB, not Java's convention.
func parseEnvelope(splitter, wireFormat string) (thumbprintHex string, encryptedSessionKey, encryptedData []byte, err error) {
	raw, err := base64.RawURLEncoding.DecodeString(wireFormat)
	if err != nil {
		return "", nil, nil, fmt.Errorf("%w: %w", ErrEnvelopeMalformed, err)
	}
	idx := bytes.Index(raw, []byte(splitter))
	if idx < 0 {
		return "", nil, nil, fmt.Errorf("%w: splitter not found", ErrEnvelopeMalformed)
	}
	keyMaterial := raw[:idx]
	data := raw[idx+len(splitter):]

	if len(keyMaterial) != keyMaterialLength || !bytes.HasPrefix(keyMaterial, []byte(verR2Header)) {
		return "", nil, nil, ErrLegacyFormatUnsupported
	}
	thumbprint := keyMaterial[len(verR2Header) : len(verR2Header)+thumbprintLen]
	encKey := keyMaterial[len(verR2Header)+thumbprintLen:]
	return hex.EncodeToString(thumbprint), encKey, data, nil
}

// decodeBase64 tries URL-safe (unpadded) base64 first, falling back to
// standard padded base64 — mirrors CryptomanagerUtils.decodeBase64Data.
func decodeBase64(data string) ([]byte, error) {
	if b, err := base64.RawURLEncoding.DecodeString(data); err == nil {
		return b, nil
	}
	b, err := base64.StdEncoding.DecodeString(data)
	if err != nil {
		return nil, fmt.Errorf("%w: %w", ErrInvalidData, err)
	}
	return b, nil
}
