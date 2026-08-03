package pkcs11

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"math/big"
	"testing"
)

// TestEmsaPSSEncode_VerifiesAgainstStdlib exercises emsaPSSEncode/mgf1 (the
// software PSS-padding half of signRSAPSS) end-to-end: PSS-encode a digest,
// perform the raw RSA private-key operation directly via big.Int (standing
// in for the PKCS#11 CKM_RSA_X_509 raw op signRSAPSS delegates to the
// token), then confirm crypto/rsa's own VerifyPSS accepts the result — this
// is the cross-check that our from-scratch padding implementation actually
// produces standards-valid RSASSA-PSS signatures, without needing a real
// HSM/SoftHSM2 session (see smoke_test.go for that).
func TestEmsaPSSEncode_VerifiesAgainstStdlib(t *testing.T) {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}

	message := []byte("EMSA-PSS-ENCODE round trip test message")
	digest := sha256.Sum256(message)

	for _, saltLen := range []int{32, 0, rsa.PSSSaltLengthEqualsHash} {
		resolvedSaltLen := saltLen
		if saltLen == rsa.PSSSaltLengthEqualsHash {
			resolvedSaltLen = crypto.SHA256.Size()
		}

		em, err := emsaPSSEncode(digest[:], priv.N.BitLen()-1, resolvedSaltLen, sha256.New())
		if err != nil {
			t.Fatalf("saltLen=%d: emsaPSSEncode: %v", saltLen, err)
		}

		// Raw RSA private-key operation: s = EM^d mod n — the software
		// stand-in for CKM_RSA_X_509's plain modular exponentiation.
		emInt := new(big.Int).SetBytes(em)
		sInt := new(big.Int).Exp(emInt, priv.D, priv.N)
		modulusSize := (priv.N.BitLen() + 7) / 8
		sig := make([]byte, modulusSize)
		sInt.FillBytes(sig)

		opts := &rsa.PSSOptions{SaltLength: resolvedSaltLen, Hash: crypto.SHA256}
		if err := rsa.VerifyPSS(&priv.PublicKey, crypto.SHA256, digest[:], sig, opts); err != nil {
			t.Errorf("saltLen=%d: rsa.VerifyPSS rejected our EMSA-PSS-encoded signature: %v", saltLen, err)
		}
	}
}

// TestEmsaPSSEncode_ModulusTooShort confirms the explicit length guard fires
// instead of silently producing a malformed (or panicking) encoding.
func TestEmsaPSSEncode_ModulusTooShort(t *testing.T) {
	digest := sha256.Sum256([]byte("x"))
	// hLen=32, saltLen=32 needs emLen >= 66 bytes = 528 bits; ask for far less.
	_, err := emsaPSSEncode(digest[:], 64, 32, sha256.New())
	if err == nil {
		t.Fatal("expected an error for an emBits too small to hold hash+salt+2, got nil")
	}
}
