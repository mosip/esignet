//go:build cgo

package pkcs11_test

import (
	"bytes"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"os"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/keymanager/keystore"
	pkcs11store "github.com/mosip/esignet/internal/keymanager/keystore/pkcs11"
)

// TestDecrypt_RSAOAEP_RoundTrip validates envelope.go's exact usage
// (RSA-OAEP/SHA-256 wrap in software, unwrap through the token) against a
// real PKCS#11 token — the fakeKeyStore used everywhere else in this
// package's tests doesn't exercise real PKCS#11 mechanisms, which is
// exactly how the CKM_RSA_PKCS_OAEP/SHA-256 incompatibility this test guards
// against went unnoticed until manual testing against real SoftHSM2. Skipped
// unless PKCS11_SMOKE_MODULE is set; run manually via:
//
//	SOFTHSM2_CONF=... PKCS11_SMOKE_MODULE=... PKCS11_SMOKE_TOKEN_LABEL=... PKCS11_SMOKE_PIN=... \
//	  go test ./internal/keymanager/keystore/pkcs11/... -run TestDecrypt_RSAOAEP_RoundTrip -v
func (ts *SmokeTestSuite) TestDecrypt_RSAOAEP_RoundTrip() {
	modulePath := os.Getenv("PKCS11_SMOKE_MODULE")
	if modulePath == "" {
		ts.T().Skip("PKCS11_SMOKE_MODULE not set; skipping real-PKCS#11 smoke test")
	}
	ks, err := pkcs11store.New(map[string]string{
		"module-path": modulePath,
		"token-label": os.Getenv("PKCS11_SMOKE_TOKEN_LABEL"),
		"pin":         os.Getenv("PKCS11_SMOKE_PIN"),
	})
	ts.Require().NoError(err, "open keystore")

	alias := "oaep-smoke-master"
	params := keystore.CertificateParameters{CommonName: "oaep-smoke"}
	err = ks.GenerateAndStoreAsymmetricKey(alias, alias, params, "RSA", "")
	ts.Require().NoError(err, "generate master key")
	ts.T().Cleanup(func() {
		if err := ks.DeleteKey(alias); err != nil {
			ts.T().Logf("cleanup: delete key %q: %v", alias, err)
		}
	})

	pub, err := ks.GetPublicKey(alias)
	ts.Require().NoError(err, "get public key")
	rsaPub, ok := pub.(*rsa.PublicKey)
	ts.Require().True(ok, "public key is not an *rsa.PublicKey: %T", pub)

	dek := make([]byte, 32)
	_, err = rand.Read(dek)
	ts.Require().NoError(err, "generate dek")
	// Mirrors envelopeEncrypt exactly: SHA-256 OAEP wrap in software.
	wrapped, err := rsa.EncryptOAEP(crypto.SHA256.New(), rand.Reader, rsaPub, dek, nil)
	ts.Require().NoError(err, "wrap dek")

	priv, err := ks.GetPrivateKey(alias)
	ts.Require().NoError(err, "get private key")
	decrypter, ok := priv.(crypto.Decrypter)
	ts.Require().True(ok, "private key is not a crypto.Decrypter: %T", priv)
	unwrapped, err := decrypter.Decrypt(rand.Reader, wrapped, &rsa.OAEPOptions{Hash: crypto.SHA256})
	ts.Require().NoError(err, "unwrap dek (this is the exact failure mode found against SoftHSM2 — CKM_RSA_PKCS_OAEP/SHA-256 rejected with CKR_ARGUMENTS_BAD)")
	ts.Require().True(bytes.Equal(dek, unwrapped), "unwrapped DEK does not match original")
}

type SmokeTestSuite struct {
	suite.Suite
}

func TestSmokeTestSuite(t *testing.T) {
	suite.Run(t, new(SmokeTestSuite))
}
