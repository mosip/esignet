package pkcs12_test

import (
	"path/filepath"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/keymanager/keystore"
)

func newTestStore(t *testing.T) keystore.KeyStore {
	t.Helper()
	path := filepath.Join(t.TempDir(), "keystore.p12")
	ks, err := keystore.New("PKCS12", map[string]string{
		"config-path":   path,
		"keystore-pass": "test-password",
	})
	require.NoError(t, err)
	return ks
}

func testParams() keystore.CertificateParameters {
	now := time.Now().UTC()
	return keystore.CertificateParameters{CommonName: "Test Root", NotBefore: now, NotAfter: now.AddDate(3, 0, 0)}
}

func TestPKCS12_GenerateStoreReload_MultipleAliases(t *testing.T) {
	path := filepath.Join(t.TempDir(), "keystore.p12")
	params := map[string]string{"config-path": path, "keystore-pass": "test-password"}

	ks1, err := keystore.New("PKCS12", params)
	require.NoError(t, err)
	require.NoError(t, ks1.GenerateAndStoreAsymmetricKey("root", "root", testParams(), keystore.AlgoRSA, ""))
	require.NoError(t, ks1.GenerateAndStoreAsymmetricKey("master", "root", testParams(), keystore.AlgoRSA, ""))
	require.NoError(t, ks1.GenerateAndStoreSymmetricKey("sym1"))

	// Reload from the same file in a fresh Store instance — this is the
	// "single file on disk, multiple entries" round trip.
	ks2, err := keystore.New("PKCS12", params)
	require.NoError(t, err)

	aliases, err := ks2.GetAllAlias()
	require.NoError(t, err)
	assert.ElementsMatch(t, []string{"root", "master", "sym1"}, aliases)

	rootCert, err := ks2.GetCertificate("root")
	require.NoError(t, err)
	assert.Equal(t, "Test Root", rootCert.Subject.CommonName)

	masterCert, err := ks2.GetCertificate("master")
	require.NoError(t, err)
	require.NoError(t, masterCert.CheckSignatureFrom(rootCert))

	symKey, err := ks2.GetSymmetricKey("sym1")
	require.NoError(t, err)
	assert.Len(t, symKey, 32)
}

func TestPKCS12_ECAndEd25519KeyGeneration(t *testing.T) {
	ks := newTestStore(t)
	for _, tt := range []struct{ alias, algo, curve string }{
		{"ec-r1", keystore.AlgoEC, keystore.CurveSECP256R1},
		{"ed25519", keystore.AlgoEC, keystore.CurveED25519},
	} {
		t.Run(tt.alias, func(t *testing.T) {
			err := ks.GenerateAndStoreAsymmetricKey(tt.alias, tt.alias, testParams(), tt.algo, tt.curve)
			require.NoError(t, err)
			cert, err := ks.GetCertificate(tt.alias)
			require.NoError(t, err)
			require.NoError(t, cert.CheckSignatureFrom(cert)) // self-signed
		})
	}
}

// TestPKCS12_SECP256K1_NotSupported documents a real Go standard-library
// limitation (see errSECP256K1Unsupported in keys.go): crypto/x509 cannot
// encode or decode certificates carrying a SECP256K1 public key, so this
// backend rejects it with a clear error rather than producing a broken
// certificate.
func TestPKCS12_SECP256K1_NotSupported(t *testing.T) {
	ks := newTestStore(t)
	err := ks.GenerateAndStoreAsymmetricKey("ec-k1", "ec-k1", testParams(), keystore.AlgoEC, keystore.CurveSECP256K1)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "SECP256K1")
}

func TestPKCS12_DeleteKey(t *testing.T) {
	ks := newTestStore(t)
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("a", "a", testParams(), keystore.AlgoRSA, ""))
	require.NoError(t, ks.DeleteKey("a"))
	_, err := ks.GetCertificate("a")
	assert.Error(t, err)
}
