package pkcs11

import (
	"bytes"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/binary"
	"fmt"
)

// mgf1SHA256 implements MGF1 (RFC 8017 Appendix B.2.1) with SHA-256 as the
// underlying hash — the mask generation function RSAES-OAEP-DECRYPT needs,
// matching Go's unexported crypto/rsa mgf1XOR and BouncyCastle's
// MGF1BytesGenerator(SHA256Digest) (the Java reference implementation's
// choice, see unpadOAEPSHA256).
func mgf1SHA256(seed []byte, length int) []byte {
	var out bytes.Buffer
	var counter uint32
	for out.Len() < length {
		h := sha256.New()
		h.Write(seed)
		var cbuf [4]byte
		binary.BigEndian.PutUint32(cbuf[:], counter)
		h.Write(cbuf[:])
		out.Write(h.Sum(nil))
		counter++
	}
	return out.Bytes()[:length]
}

// unpadOAEPSHA256 reverses RSAES-OAEP-ENCODE (RFC 8017 §7.1.1, empty label,
// SHA-256) given em, the raw output of an *unpadded* RSA private-key
// operation (i.e. c^d mod n, left-padded with zeros to k = the modulus size
// in bytes) — exactly what PKCS#11's CKM_RSA_X_509 mechanism returns.
//
// This exists because CKM_RSA_PKCS_OAEP mechanism support is not reliably
// portable across PKCS#11 tokens: this was verified against a real SoftHSM2
// build that accepts CKM_RSA_PKCS_OAEP only with SHA-1 (CKG_MGF1_SHA1) and
// rejects the SHA-256 variant outright with CKR_ARGUMENTS_BAD, even though
// the token fully supports SHA-256 in software. Doing the OAEP removal
// ourselves after a raw (mechanism-agnostic) RSA decrypt sidesteps this
// entirely — every PKCS#11 token supports the raw private-key operation,
// since it's the most fundamental RSA capability there is. This mirrors the
// reference Java implementation's own design (CryptoCore.java: RSA/ECB/
// NoPadding raw decrypt via the PKCS#11 provider, then OAEPEncoding-based
// unpadding via BouncyCastle in software) rather than trusting the token's
// own OAEP mechanism.
func unpadOAEPSHA256(em []byte, k int) ([]byte, error) {
	hLen := sha256.Size
	if len(em) != k || k < 2*hLen+2 {
		return nil, fmt.Errorf("pkcs11: oaep unpad: invalid encoded message length")
	}
	y := em[0]
	maskedSeed := em[1 : 1+hLen]
	maskedDB := em[1+hLen:]

	seedMask := mgf1SHA256(maskedDB, hLen)
	seed := make([]byte, hLen)
	for i := range seed {
		seed[i] = maskedSeed[i] ^ seedMask[i]
	}
	dbMask := mgf1SHA256(seed, k-hLen-1)
	db := make([]byte, len(maskedDB))
	for i := range db {
		db[i] = maskedDB[i] ^ dbMask[i]
	}

	lHash := sha256.Sum256(nil) // empty label, matching envelopeEncrypt's rsa.EncryptOAEP(..., label=nil)
	lHashOK := subtle.ConstantTimeCompare(db[:hLen], lHash[:]) == 1
	rest := db[hLen:]
	idx := bytes.IndexByte(rest, 0x01)
	psOK := idx >= 0
	if psOK {
		for _, b := range rest[:idx] {
			if b != 0 {
				psOK = false
				break
			}
		}
	}
	// Single generic error regardless of which check failed (Y, lHash, or
	// the PS/0x01 structure) — a Manger's-attack-style padding oracle
	// exploits exactly this kind of error differentiation.
	if y != 0 || !lHashOK || !psOK {
		return nil, fmt.Errorf("pkcs11: oaep unpad: decryption error")
	}
	return rest[idx+1:], nil
}
