package pkcs11

import (
	"crypto/rand"
	"fmt"
	"hash"
)

// emsaPSSEncode implements EMSA-PSS-ENCODE per RFC 8017 §9.1.1 — the padding
// step of RSASSA-PSS, independent of the RSA private key. Used by
// signRSAPSS (signer.go) to build the block submitted to the token's raw
// CKM_RSA_X_509 private-key operation, since PKCS#11 PSS mechanism support
// is unreliable across tokens (see signRSAPSS's doc comment).
func emsaPSSEncode(mHash []byte, emBits int, saltLen int, h hash.Hash) ([]byte, error) {
	hLen := h.Size()
	emLen := (emBits + 7) / 8

	if emLen < hLen+saltLen+2 {
		return nil, fmt.Errorf("modulus too short for PSS with hash %d bytes and salt length %d", hLen, saltLen)
	}

	salt := make([]byte, saltLen)
	if saltLen > 0 {
		if _, err := rand.Read(salt); err != nil {
			return nil, fmt.Errorf("generate PSS salt: %w", err)
		}
	}

	// M' = (8 zero octets) || mHash || salt ; H = Hash(M')
	h.Reset()
	h.Write(make([]byte, 8))
	h.Write(mHash)
	h.Write(salt)
	hSum := h.Sum(nil)

	// DB = PS || 0x01 || salt, where PS is emLen-saltLen-hLen-2 zero octets.
	db := make([]byte, emLen-hLen-1)
	db[len(db)-saltLen-1] = 0x01
	copy(db[len(db)-saltLen:], salt)

	dbMask := mgf1(hSum, len(db), h)
	maskedDB := make([]byte, len(db))
	for i := range db {
		maskedDB[i] = db[i] ^ dbMask[i]
	}
	// Zero the leftmost 8*emLen-emBits bits of the leftmost octet.
	maskedDB[0] &= 0xFF >> uint(8*emLen-emBits)

	em := make([]byte, emLen)
	copy(em, maskedDB)
	copy(em[len(maskedDB):], hSum)
	em[emLen-1] = 0xBC
	return em, nil
}

// mgf1 implements the MGF1 mask generation function (RFC 8017 Appendix
// B.2.1) using h as the underlying hash.
func mgf1(seed []byte, maskLen int, h hash.Hash) []byte {
	var out []byte
	var counter [4]byte
	for len(out) < maskLen {
		h.Reset()
		h.Write(seed)
		h.Write(counter[:])
		out = h.Sum(out)
		for i := 3; i >= 0; i-- {
			counter[i]++
			if counter[i] != 0 {
				break
			}
		}
	}
	return out[:maskLen]
}
