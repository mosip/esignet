package pkcs11

import (
	"bytes"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"math/big"
	"testing"

	"github.com/stretchr/testify/suite"
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

func (ts *PKCS11TestSuite) TestUnpadOAEPSHA256_RoundTripsWithRSAEncryptOAEP() {
	t := ts.T()
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	ts.Require().NoError(err, "generate key")
	plaintext := make([]byte, 32) // a real DEK, envelope.go's actual use case
	_, err = rand.Read(plaintext)
	ts.Require().NoError(err, "generate plaintext")
	ciphertext, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, &priv.PublicKey, plaintext, nil)
	ts.Require().NoError(err, "EncryptOAEP")

	raw := rawRSADecrypt(t, priv, ciphertext)
	k := (priv.N.BitLen() + 7) / 8
	got, err := unpadOAEPSHA256(raw, k)
	ts.Require().NoError(err, "unpadOAEPSHA256")
	ts.Assert().True(bytes.Equal(got, plaintext), "unpadOAEPSHA256 = %q, want %q", got, plaintext)
}

func (ts *PKCS11TestSuite) TestUnpadOAEPSHA256_RejectsCorruptedBlock() {
	t := ts.T()
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	ts.Require().NoError(err, "generate key")
	plaintext := []byte("this is a 32-byte test DEK!!!!!")
	ciphertext, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, &priv.PublicKey, plaintext, nil)
	ts.Require().NoError(err, "EncryptOAEP")
	raw := rawRSADecrypt(t, priv, ciphertext)
	raw[len(raw)-1] ^= 0xFF // flip a byte inside the encoded message

	k := (priv.N.BitLen() + 7) / 8
	_, err = unpadOAEPSHA256(raw, k)
	ts.Require().Error(err, "expected an error unpadding a corrupted block, got nil")
}

func (ts *PKCS11TestSuite) TestUnpadOAEPSHA256_RejectsWrongLength() {
	_, err := unpadOAEPSHA256(make([]byte, 10), 256)
	ts.Require().Error(err, "expected an error for a block shorter than k, got nil")
}

type PKCS11TestSuite struct {
	suite.Suite
}

func TestPKCS11TestSuite(t *testing.T) {
	suite.Run(t, new(PKCS11TestSuite))
}
