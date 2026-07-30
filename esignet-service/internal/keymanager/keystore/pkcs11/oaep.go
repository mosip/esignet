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
	lHashOK := subtle.ConstantTimeCompare(db[:hLen], lHash[:])
	firstByteZero := subtle.ConstantTimeByteEq(y, 0)
	rest := db[hLen:]

	// Constant-time scan for the 0x01 separator (mirrors Go stdlib's
	// crypto/rsa decryptOAEP): visit every byte of rest regardless of
	// where — or whether — 0x01 appears, and never branch on secret
	// data, so the time taken cannot leak the separator's position to a
	// Manger's-attack-style padding oracle.
	//   lookingForIndex: 1 while still searching for the 0x01 byte
	//   index: offset of the first 0x01 byte found
	//   invalid: 1 iff a non-zero byte was seen before the 0x01
	var lookingForIndex, index, invalid int
	lookingForIndex = 1
	for i := 0; i < len(rest); i++ {
		equals0 := subtle.ConstantTimeByteEq(rest[i], 0)
		equals1 := subtle.ConstantTimeByteEq(rest[i], 1)
		index = subtle.ConstantTimeSelect(lookingForIndex&equals1, i, index)
		lookingForIndex = subtle.ConstantTimeSelect(equals1, 0, lookingForIndex)
		invalid = subtle.ConstantTimeSelect(lookingForIndex&^equals0, 1, invalid)
	}

	// Single generic error regardless of which check failed (Y, lHash, or
	// the PS/0x01 structure) — a Manger's-attack-style padding oracle
	// exploits exactly this kind of error differentiation.
	if firstByteZero&lHashOK&^invalid&^lookingForIndex != 1 {
		return nil, fmt.Errorf("pkcs11: oaep unpad: decryption error")
	}
	return rest[index+1:], nil
}
