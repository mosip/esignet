//go:build cgo

package pkcs11

import (
	"testing"

	"github.com/miekg/pkcs11"
)

// TestDecodeCKULong_RealTokenByteSequences uses the exact byte sequences a
// real PKCS#11 token returns for CKA_KEY_TYPE on 64-bit little-endian
// platforms — reproducing the bug where big.Int.SetBytes (big-endian)
// misdecoded CKK_EC (3) as 216172782113783808 and CKK_EC_EDWARDS (0x40) as
// 4611686018427387904, both reported as "unsupported key type" at signing
// time even though the key itself was generated and stored correctly.
func TestDecodeCKULong_RealTokenByteSequences(t *testing.T) {
	cases := []struct {
		name string
		b    []byte
		want uint
	}{
		{"CKK_RSA, 8-byte CK_ULONG", []byte{0x00, 0, 0, 0, 0, 0, 0, 0}, pkcs11.CKK_RSA},
		{"CKK_EC, 8-byte CK_ULONG", []byte{0x03, 0, 0, 0, 0, 0, 0, 0}, pkcs11.CKK_EC},
		{"CKK_EC_EDWARDS (0x40), 8-byte CK_ULONG", []byte{0x40, 0, 0, 0, 0, 0, 0, 0}, ckkECEdwards},
		{"CKK_EC, 4-byte CK_ULONG (32-bit token)", []byte{0x03, 0, 0, 0}, pkcs11.CKK_EC},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := decodeCKULong(c.b); got != c.want {
				t.Errorf("decodeCKULong(% x) = %d, want %d", c.b, got, c.want)
			}
		})
	}
}

// TestDecodeCKULong_ReproducesReportedBugValues confirms the specific
// large values from the original bug report would NOT be produced by the
// fixed decoder for the same raw token bytes — i.e. that big-endian
// decoding (the old, broken behavior) and little-endian decoding (the
// fix) genuinely diverge for these inputs, so a regression back to
// big.Int.SetBytes would be caught.
func TestDecodeCKULong_ReproducesReportedBugValues(t *testing.T) {
	ecBytes := []byte{0x03, 0, 0, 0, 0, 0, 0, 0}
	edBytes := []byte{0x40, 0, 0, 0, 0, 0, 0, 0}

	const buggyECValue = 216172782113783808
	const buggyEdValue = 4611686018427387904

	if got := decodeCKULong(ecBytes); got == buggyECValue {
		t.Fatalf("decodeCKULong regressed to big-endian decoding: got the reported bug value %d", got)
	}
	if got := decodeCKULong(ecBytes); got != pkcs11.CKK_EC {
		t.Errorf("decodeCKULong(EC bytes) = %d, want CKK_EC (%d)", got, pkcs11.CKK_EC)
	}
	if got := decodeCKULong(edBytes); got == buggyEdValue {
		t.Fatalf("decodeCKULong regressed to big-endian decoding: got the reported bug value %d", got)
	}
	if got := decodeCKULong(edBytes); got != ckkECEdwards {
		t.Errorf("decodeCKULong(Ed25519 bytes) = %d, want ckkECEdwards (%d)", got, ckkECEdwards)
	}
}
