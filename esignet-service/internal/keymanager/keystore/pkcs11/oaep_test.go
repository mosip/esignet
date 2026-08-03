package pkcs11

import (
	"bytes"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"math/big"
	"testing"
)

// rawRSADecrypt computes c^d mod n directly (what a PKCS#11 CKM_RSA_X_509
// raw decrypt returns), left-padded to the modulus size — used here to
// exercise unpadOAEPSHA256 without a real HSM/SoftHSM2.
func rawRSADecrypt(t *testing.T, priv *rsa.PrivateKey, ciphertext []byte) []byte {
	t.Helper()
	c := new(big.Int).SetBytes(ciphertext)
	m := new(big.Int).Exp(c, priv.D, priv.N)
	k := (priv.N.BitLen() + 7) / 8
	out := make([]byte, k)
	m.FillBytes(out)
	return out
}

func TestUnpadOAEPSHA256_RoundTripsWithRSAEncryptOAEP(t *testing.T) {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	plaintext := make([]byte, 32) // a real DEK, envelope.go's actual use case
	if _, err := rand.Read(plaintext); err != nil {
		t.Fatalf("generate plaintext: %v", err)
	}
	ciphertext, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, &priv.PublicKey, plaintext, nil)
	if err != nil {
		t.Fatalf("EncryptOAEP: %v", err)
	}

	raw := rawRSADecrypt(t, priv, ciphertext)
	k := (priv.N.BitLen() + 7) / 8
	got, err := unpadOAEPSHA256(raw, k)
	if err != nil {
		t.Fatalf("unpadOAEPSHA256: %v", err)
	}
	if !bytes.Equal(got, plaintext) {
		t.Fatalf("unpadOAEPSHA256 = %q, want %q", got, plaintext)
	}
}

func TestUnpadOAEPSHA256_RejectsCorruptedBlock(t *testing.T) {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	plaintext := []byte("this is a 32-byte test DEK!!!!!")
	ciphertext, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, &priv.PublicKey, plaintext, nil)
	if err != nil {
		t.Fatalf("EncryptOAEP: %v", err)
	}
	raw := rawRSADecrypt(t, priv, ciphertext)
	raw[len(raw)-1] ^= 0xFF // flip a byte inside the encoded message

	k := (priv.N.BitLen() + 7) / 8
	if _, err := unpadOAEPSHA256(raw, k); err == nil {
		t.Fatal("expected an error unpadding a corrupted block, got nil")
	}
}

func TestUnpadOAEPSHA256_RejectsWrongLength(t *testing.T) {
	if _, err := unpadOAEPSHA256(make([]byte, 10), 256); err == nil {
		t.Fatal("expected an error for a block shorter than k, got nil")
	}
}
