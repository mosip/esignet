package pkcs11

import (
	"crypto"
	"crypto/rsa"
	"encoding/asn1"
	"fmt"
	"io"
	"math/big"

	"github.com/miekg/pkcs11"
)

// digestInfoPrefixes holds the DER-encoded DigestInfo AlgorithmIdentifier
// prefix for each supported hash, per PKCS#1 v1.5 (used with CKM_RSA_PKCS,
// which performs only the raw RSA private-key operation and expects the
// caller to supply the full DigestInfo structure).
var digestInfoPrefixes = map[crypto.Hash][]byte{
	crypto.SHA1:   {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14},
	crypto.SHA224: {0x30, 0x2d, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x04, 0x05, 0x00, 0x04, 0x1c},
	crypto.SHA256: {0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20},
	crypto.SHA384: {0x30, 0x41, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x02, 0x05, 0x00, 0x04, 0x30},
	crypto.SHA512: {0x30, 0x51, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x03, 0x05, 0x00, 0x04, 0x40},
}

// privateKey is a crypto.Signer backed by an HSM-resident PKCS#11 private
// key object — the raw key material never leaves the token. It implements
// crypto.PrivateKey (the empty interface) so it can be returned from
// keystore.KeyStore.GetPrivateKey and used directly as the priv argument to
// x509.CreateCertificate.
type privateKey struct {
	store   *Store
	alias   string
	handle  pkcs11.ObjectHandle
	keyType uint // pkcs11.CKK_RSA, pkcs11.CKK_EC, or ckkECEdwards
	pub     crypto.PublicKey
}

var _ crypto.Signer = (*privateKey)(nil)

func (k *privateKey) Public() crypto.PublicKey { return k.pub }

// Sign implements crypto.Signer. For RSA and ECDSA, digest is a pre-computed
// hash and opts.HashFunc() identifies which hash. For Ed25519, per Go
// convention, digest is the full message and opts.HashFunc() == 0.
func (k *privateKey) Sign(_ io.Reader, digest []byte, opts crypto.SignerOpts) ([]byte, error) {
	switch k.keyType {
	case pkcs11.CKK_RSA:
		return k.signRSA(digest, opts)
	case pkcs11.CKK_EC:
		return k.signECDSA(digest)
	case ckkECEdwards:
		return k.signEdDSA(digest)
	default:
		return nil, fmt.Errorf("pkcs11: unsupported key type %d for signing", k.keyType)
	}
}

func (k *privateKey) signRSA(digest []byte, opts crypto.SignerOpts) ([]byte, error) {
	if opts != nil && opts.HashFunc() == crypto.SHA256 && isPSS(opts) {
		return nil, fmt.Errorf("pkcs11: RSA-PSS not supported by this adapter")
	}
	prefix, ok := digestInfoPrefixes[opts.HashFunc()]
	if !ok {
		return nil, fmt.Errorf("pkcs11: unsupported hash %v for RSA signing", opts.HashFunc())
	}
	digestInfo := append(append([]byte{}, prefix...), digest...)

	var sig []byte
	err := k.store.withSession(func(sh pkcs11.SessionHandle) error {
		mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_RSA_PKCS, nil)}
		if err := k.store.ctx.SignInit(sh, mech, k.handle); err != nil {
			return err
		}
		out, err := k.store.ctx.Sign(sh, digestInfo)
		if err != nil {
			return err
		}
		sig = out
		return nil
	})
	return sig, err
}

func isPSS(opts crypto.SignerOpts) bool {
	_, ok := opts.(*rsa.PSSOptions)
	return ok
}

func (k *privateKey) signECDSA(digest []byte) ([]byte, error) {
	var raw []byte
	err := k.store.withSession(func(sh pkcs11.SessionHandle) error {
		mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_ECDSA, nil)}
		if err := k.store.ctx.SignInit(sh, mech, k.handle); err != nil {
			return err
		}
		out, err := k.store.ctx.Sign(sh, digest)
		if err != nil {
			return err
		}
		raw = out
		return nil
	})
	if err != nil {
		return nil, err
	}
	// PKCS#11 CKM_ECDSA returns raw r||s (each half the field size); Go's
	// x509/ecdsa signature encoding expects an ASN.1 SEQUENCE{r, s}.
	half := len(raw) / 2
	r := new(big.Int).SetBytes(raw[:half])
	s := new(big.Int).SetBytes(raw[half:])
	return asn1.Marshal(struct{ R, S *big.Int }{r, s})
}

func (k *privateKey) signEdDSA(message []byte) ([]byte, error) {
	var sig []byte
	err := k.store.withSession(func(sh pkcs11.SessionHandle) error {
		mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(ckmEDDSA, nil)}
		if err := k.store.ctx.SignInit(sh, mech, k.handle); err != nil {
			return err
		}
		out, err := k.store.ctx.Sign(sh, message)
		if err != nil {
			return err
		}
		sig = out
		return nil
	})
	return sig, err
}

// Decrypt implements crypto.Decrypter for RSA keys — used exclusively to
// unwrap the envelope-encryption DEK for Component Encryption Keys
// (envelope.go's envelopeDecrypt), which always requests RSA-OAEP/SHA-256
// (matching envelopeEncrypt's rsa.EncryptOAEP(sha256.New(), ...)).
//
// Deliberately does NOT ask the token to do the OAEP unpadding itself (via
// CKM_RSA_PKCS_OAEP) — that mechanism's hash/MGF support is not reliably
// portable across PKCS#11 tokens. Verified against a real SoftHSM2 build
// that accepts CKM_RSA_PKCS_OAEP only with SHA-1 and rejects the SHA-256
// variant outright with CKR_ARGUMENTS_BAD. Instead this performs a raw,
// mechanism-agnostic RSA private-key operation (CKM_RSA_X_509 — universally
// supported, since it's the most fundamental RSA capability any token has)
// and unpads OAEP/SHA-256 in software (oaep.go's unpadOAEPSHA256) — the same
// approach the reference Java implementation takes (CryptoCore.java:
// RSA/ECB/NoPadding raw decrypt via the PKCS#11 provider, then
// OAEPEncoding-based unpadding via BouncyCastle), and the only way to keep
// SHA-256 (not fall back to a weaker/token-specific hash) while working
// across arbitrary tokens.
func (k *privateKey) Decrypt(_ io.Reader, ciphertext []byte, opts crypto.DecrypterOpts) ([]byte, error) {
	if k.keyType != pkcs11.CKK_RSA {
		return nil, fmt.Errorf("pkcs11: Decrypt only supported for RSA keys")
	}
	oaep, ok := opts.(*rsa.OAEPOptions)
	if !ok || oaep.Hash != crypto.SHA256 {
		return nil, fmt.Errorf("pkcs11: Decrypt only supports RSA-OAEP with SHA-256")
	}
	rsaPub, ok := k.pub.(*rsa.PublicKey)
	if !ok {
		return nil, fmt.Errorf("pkcs11: Decrypt: key %q has no RSA public key on record", k.alias)
	}
	modulusSize := (rsaPub.N.BitLen() + 7) / 8

	var raw []byte
	err := k.store.withSession(func(sh pkcs11.SessionHandle) error {
		mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_RSA_X_509, nil)}
		if err := k.store.ctx.DecryptInit(sh, mech, k.handle); err != nil {
			return err
		}
		out, err := k.store.ctx.Decrypt(sh, ciphertext)
		if err != nil {
			return err
		}
		raw = out
		return nil
	})
	if err != nil {
		return nil, err
	}
	// CKM_RSA_X_509 may return fewer than modulusSize bytes if the raw
	// result has leading zero bytes (implementation-dependent) — OAEP
	// unpadding needs the full, left-zero-padded modulus-size block.
	if len(raw) < modulusSize {
		padded := make([]byte, modulusSize)
		copy(padded[modulusSize-len(raw):], raw)
		raw = padded
	}
	return unpadOAEPSHA256(raw, modulusSize)
}

var _ crypto.Decrypter = (*privateKey)(nil)
