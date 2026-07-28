package pkcs11_test

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"os"
	"testing"

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
func TestDecrypt_RSAOAEP_RoundTrip(t *testing.T) {
	modulePath := os.Getenv("PKCS11_SMOKE_MODULE")
	if modulePath == "" {
		t.Skip("PKCS11_SMOKE_MODULE not set; skipping real-PKCS#11 smoke test")
	}
	ks, err := pkcs11store.New(map[string]string{
		"module-path": modulePath,
		"token-label": os.Getenv("PKCS11_SMOKE_TOKEN_LABEL"),
		"pin":         os.Getenv("PKCS11_SMOKE_PIN"),
	})
	if err != nil {
		t.Fatalf("open keystore: %v", err)
	}

	alias := "oaep-smoke-master"
	params := keystore.CertificateParameters{CommonName: "oaep-smoke"}
	if err := ks.GenerateAndStoreAsymmetricKey(alias, alias, params, "RSA", ""); err != nil {
		t.Fatalf("generate master key: %v", err)
	}
	defer ks.DeleteKey(alias)

	pub, err := ks.GetPublicKey(alias)
	if err != nil {
		t.Fatalf("get public key: %v", err)
	}
	rsaPub := pub.(*rsa.PublicKey)

	dek := make([]byte, 32)
	if _, err := rand.Read(dek); err != nil {
		t.Fatalf("generate dek: %v", err)
	}
	// Mirrors envelopeEncrypt exactly: SHA-256 OAEP wrap in software.
	wrapped, err := rsa.EncryptOAEP(crypto.SHA256.New(), rand.Reader, rsaPub, dek, nil)
	if err != nil {
		t.Fatalf("wrap dek: %v", err)
	}

	priv, err := ks.GetPrivateKey(alias)
	if err != nil {
		t.Fatalf("get private key: %v", err)
	}
	decrypter := priv.(crypto.Decrypter)
	unwrapped, err := decrypter.Decrypt(rand.Reader, wrapped, &rsa.OAEPOptions{Hash: crypto.SHA256})
	if err != nil {
		t.Fatalf("unwrap dek (this is the exact failure mode found against SoftHSM2 — CKM_RSA_PKCS_OAEP/SHA-256 rejected with CKR_ARGUMENTS_BAD): %v", err)
	}
	if len(unwrapped) != len(dek) {
		t.Fatalf("unwrapped DEK length = %d, want %d", len(unwrapped), len(dek))
	}
	for i := range dek {
		if dek[i] != unwrapped[i] {
			t.Fatalf("unwrapped DEK does not match original at byte %d", i)
		}
	}
}
