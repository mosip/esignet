/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package sig verifies raw signatures for the JOSE algorithms used by Linked
// Data proofs. ECDSA signatures are the raw 64-byte r||s
// form, as in JWS.
package sig

import (
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/asn1"
	"errors"
	"fmt"
	"math/big"

	"github.com/btcsuite/btcd/btcec/v2"
	btcecdsa "github.com/btcsuite/btcd/btcec/v2/ecdsa"
)

// JOSE algorithm identifiers.
const (
	EdDSA  = "EdDSA"
	ES256  = "ES256"
	ES256K = "ES256K"
	RS256  = "RS256"
	PS256  = "PS256"
)

const (
	ecdsaSigSize  = 64
	minRSAKeyBits = 2048
	pssSaltLength = 32 // fixed: the SHA-256 digest size
)

var (
	// ErrUnsupportedAlg reports an algorithm outside the list above.
	ErrUnsupportedAlg = errors.New("unsupported signature algorithm")
	// ErrKeyMismatch reports a key whose type or curve doesn't fit the algorithm.
	ErrKeyMismatch = errors.New("public key does not match the signature algorithm")
	// ErrInvalidSignature reports a signature that doesn't verify.
	ErrInvalidSignature = errors.New("signature verification failed")
)

// CheckKey reports whether pub can be used with alg. Callers run it before
// the (expensive) canonicalization so a mismatched key fails early.
func CheckKey(alg string, pub crypto.PublicKey) error {
	ok := false
	switch alg {
	case EdDSA:
		k, isEd := pub.(ed25519.PublicKey)
		ok = isEd && len(k) == ed25519.PublicKeySize
	case ES256:
		k, isEC := pub.(*ecdsa.PublicKey)
		ok = isEC && k.Curve == elliptic.P256()
	case ES256K:
		_, ok = pub.(*btcec.PublicKey)
	case RS256, PS256:
		k, isRSA := pub.(*rsa.PublicKey)
		ok = isRSA && k.N.BitLen() >= minRSAKeyBits
	default:
		return fmt.Errorf("%w: %q", ErrUnsupportedAlg, alg)
	}
	if !ok {
		return fmt.Errorf("%w: %s cannot use %T", ErrKeyMismatch, alg, pub)
	}
	return nil
}

// Verify checks signature over msg. The algorithm applies its own digest
// (SHA-256 for ECDSA and RSA; none for Ed25519).
func Verify(alg string, pub crypto.PublicKey, msg, signature []byte) error {
	if err := CheckKey(alg, pub); err != nil {
		return err
	}
	var ok bool
	switch alg {
	case EdDSA:
		ok = ed25519.Verify(pub.(ed25519.PublicKey), msg, signature)
	case ES256:
		ok = verifyP256(pub.(*ecdsa.PublicKey), msg, signature)
	case ES256K:
		ok = verifySecp256k1(pub.(*btcec.PublicKey), msg, signature)
	case RS256:
		h := sha256.Sum256(msg)
		ok = rsa.VerifyPKCS1v15(pub.(*rsa.PublicKey), crypto.SHA256, h[:], signature) == nil
	case PS256:
		h := sha256.Sum256(msg)
		opts := &rsa.PSSOptions{SaltLength: pssSaltLength, Hash: crypto.SHA256}
		ok = rsa.VerifyPSS(pub.(*rsa.PublicKey), crypto.SHA256, h[:], signature, opts) == nil
	}
	if !ok {
		return ErrInvalidSignature
	}
	return nil
}

func verifyP256(pub *ecdsa.PublicKey, msg, signature []byte) bool {
	if len(signature) != ecdsaSigSize {
		return false
	}
	der, err := asn1.Marshal(struct{ R, S *big.Int }{
		new(big.Int).SetBytes(signature[:32]),
		new(big.Int).SetBytes(signature[32:]),
	})
	if err != nil {
		return false
	}
	h := sha256.Sum256(msg)
	return ecdsa.VerifyASN1(pub, h[:], der)
}

// verifySecp256k1 builds the signature from raw scalars rather than DER, so
// high-S signatures are accepted (ES256K does not require low-S).
func verifySecp256k1(pub *btcec.PublicKey, msg, signature []byte) bool {
	if len(signature) != ecdsaSigSize {
		return false
	}
	var r, s btcec.ModNScalar
	if r.SetByteSlice(signature[:32]) || s.SetByteSlice(signature[32:]) {
		return false // scalar >= group order
	}
	h := sha256.Sum256(msg)
	return btcecdsa.NewSignature(&r, &s).Verify(h[:], pub)
}
