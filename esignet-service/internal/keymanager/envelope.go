package keymanager

import (
	"crypto"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"fmt"
)

// envelopeEncrypt wraps plaintext (a Component Encryption Key's PKCS8
// private key, in practice) for storage in keymgr.key_store: a random
// AES-256-GCM data-encryption-key encrypts plaintext, and masterPub
// (RSA-OAEP) encrypts only that 32-byte DEK.
//
// RSA-OAEP cannot encrypt plaintext larger than (keySizeBytes - 2*hashSize -
// 2) directly — for a 2048-bit key with SHA-256 that's ~190 bytes, far
// smaller than a PKCS8-encoded private key — so wrapping the key material
// directly with RSA-OAEP (as an earlier version of this function did) fails
// with "message too long for RSA key size". Envelope encryption is the
// standard fix: RSA only ever wraps a small symmetric key.
func envelopeEncrypt(masterPub *rsa.PublicKey, plaintext []byte) (string, error) {
	dek := make([]byte, 32)
	if _, err := rand.Read(dek); err != nil {
		return "", fmt.Errorf("generate DEK: %w", err)
	}
	block, err := aes.NewCipher(dek)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", fmt.Errorf("generate nonce: %w", err)
	}
	ciphertext := gcm.Seal(nonce, nonce, plaintext, nil) // nonce || ciphertext

	wrappedDEK, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, masterPub, dek, nil)
	if err != nil {
		return "", fmt.Errorf("wrap DEK: %w", err)
	}

	out := make([]byte, 2+len(wrappedDEK)+len(ciphertext))
	binary.BigEndian.PutUint16(out[:2], uint16(len(wrappedDEK)))
	copy(out[2:], wrappedDEK)
	copy(out[2+len(wrappedDEK):], ciphertext)
	return base64.StdEncoding.EncodeToString(out), nil
}

// envelopeDecrypt reverses envelopeEncrypt, using masterDecrypter (the
// Component Master Key's private key) to unwrap the DEK.
func envelopeDecrypt(masterDecrypter crypto.Decrypter, encoded string) ([]byte, error) {
	raw, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil {
		return nil, fmt.Errorf("decode: %w", err)
	}
	if len(raw) < 2 {
		return nil, fmt.Errorf("envelope too short")
	}
	wrappedLen := int(binary.BigEndian.Uint16(raw[:2]))
	if len(raw) < 2+wrappedLen {
		return nil, fmt.Errorf("envelope truncated")
	}
	wrappedDEK := raw[2 : 2+wrappedLen]
	ciphertext := raw[2+wrappedLen:]

	dek, err := masterDecrypter.Decrypt(rand.Reader, wrappedDEK, &rsa.OAEPOptions{Hash: crypto.SHA256})
	if err != nil {
		return nil, fmt.Errorf("unwrap DEK: %w", err)
	}
	block, err := aes.NewCipher(dek)
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	if len(ciphertext) < gcm.NonceSize() {
		return nil, fmt.Errorf("ciphertext too short")
	}
	nonce, ct := ciphertext[:gcm.NonceSize()], ciphertext[gcm.NonceSize():]
	return gcm.Open(nil, nonce, ct, nil)
}
