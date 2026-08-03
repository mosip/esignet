package signature

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/asn1"
	"fmt"
	"math/big"
)

// ecSignatureLength is the JOSE raw-concatenated ECDSA signature length for
// ES256/ES256K: 32-byte R || 32-byte S (both curves have a 32-byte field
// size), matching Java's SignatureConstant.EC256_SIGNATURE_LENGTH.
const ecSignatureLength = 64

// signDigest signs signingInput with signer per the resolved JWS algorithm,
// returning the raw (not base64-encoded) JOSE-format signature bytes —
// already DER-to-concatenated for ES256/ES256K, matching Java's
// EcdsaUsingShaAlgorithm.convertDerToConcatenated step. Collapses Java's
// four *SignatureProviderImpl classes into one function because Go's
// crypto.Signer already abstracts the PKCS#11/PKCS#12/JCA-provider
// indirection those classes existed to route around.
func signDigest(signer crypto.Signer, alg string, signingInput []byte) ([]byte, error) {
	switch alg {
	case algPS256:
		digest := sha256.Sum256(signingInput)
		sig, err := signer.Sign(rand.Reader, digest[:], &rsa.PSSOptions{SaltLength: 32, Hash: crypto.SHA256})
		if err != nil {
			return nil, fmt.Errorf("%w: %w", ErrSignFailed, err)
		}
		return sig, nil

	case algRS256:
		digest := sha256.Sum256(signingInput)
		sig, err := signer.Sign(rand.Reader, digest[:], crypto.SHA256)
		if err != nil {
			return nil, fmt.Errorf("%w: %w", ErrSignFailed, err)
		}
		return sig, nil

	case algES256, algES256K:
		digest := sha256.Sum256(signingInput)
		der, err := signer.Sign(rand.Reader, digest[:], crypto.SHA256)
		if err != nil {
			return nil, fmt.Errorf("%w: %w", ErrSignFailed, err)
		}
		concat, err := derToConcat(der, ecSignatureLength)
		if err != nil {
			return nil, fmt.Errorf("%w: %w", ErrSignFailed, err)
		}
		return concat, nil

	case algEdDSA:
		// Ed25519 signs the message directly — no pre-hashing, per Go
		// convention (opts.HashFunc() == 0). Unlike Java (which always uses
		// the default JVM provider for Ed25519, bypassing any configured
		// HSM), Go's crypto.Signer abstraction routes this transparently
		// through whichever backend (pkcs11/pkcs12) produced the key.
		sig, err := signer.Sign(rand.Reader, signingInput, crypto.Hash(0))
		if err != nil {
			return nil, fmt.Errorf("%w: %w", ErrSignFailed, err)
		}
		return sig, nil

	default:
		return nil, fmt.Errorf("%w: %q", ErrUnsupportedAlgorithm, alg)
	}
}

// derToConcat converts an ASN.1 DER-encoded ECDSA signature (SEQUENCE{r,s})
// into JOSE's raw concatenated r||s format, each half sigLen/2 bytes,
// left-zero-padded. The inverse of keystore/pkcs11's signECDSA (which
// converts the token's raw r||s into DER for Go's crypto.Signer contract);
// equivalent to Java's EcdsaUsingShaAlgorithm.convertDerToConcatenated.
func derToConcat(der []byte, sigLen int) ([]byte, error) {
	var parsed struct{ R, S *big.Int }
	if _, err := asn1.Unmarshal(der, &parsed); err != nil {
		return nil, fmt.Errorf("parse DER ECDSA signature: %w", err)
	}
	half := sigLen / 2
	rBytes := parsed.R.Bytes()
	sBytes := parsed.S.Bytes()
	if len(rBytes) > half || len(sBytes) > half {
		return nil, fmt.Errorf("ECDSA signature component too large for %d-byte concatenated format", sigLen)
	}
	out := make([]byte, sigLen)
	copy(out[half-len(rBytes):half], rBytes)
	copy(out[sigLen-len(sBytes):], sBytes)
	return out, nil
}
