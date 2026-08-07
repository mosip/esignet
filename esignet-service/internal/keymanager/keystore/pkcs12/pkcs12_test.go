package pkcs12_test

import (
	"path/filepath"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/keymanager/keystore"
)

type PKCS12TestSuite struct {
	suite.Suite
}

func TestPKCS12TestSuite(t *testing.T) {
	suite.Run(t, new(PKCS12TestSuite))
}

func (ts *PKCS12TestSuite) newTestStore() keystore.KeyStore {
	path := filepath.Join(ts.T().TempDir(), "keystore.p12")
	ks, err := keystore.New("PKCS12", map[string]string{
		"config-path":                      path,
		"keystore-pass":                    "test-password",
		"allow-insecure-software-keystore": "true",
	})
	ts.Require().NoError(err)
	return ks
}

func testParams() keystore.CertificateParameters {
	now := time.Now().UTC()
	return keystore.CertificateParameters{CommonName: "Test Root", NotBefore: now, NotAfter: now.AddDate(3, 0, 0)}
}

func (ts *PKCS12TestSuite) TestPKCS12_GenerateStoreReload_MultipleAliases() {
	path := filepath.Join(ts.T().TempDir(), "keystore.p12")
	params := map[string]string{"config-path": path, "keystore-pass": "test-password", "allow-insecure-software-keystore": "true"}

	ks1, err := keystore.New("PKCS12", params)
	ts.Require().NoError(err)
	ts.Require().NoError(ks1.GenerateAndStoreAsymmetricKey("root", "root", testParams(), keystore.AlgoRSA, ""))
	ts.Require().NoError(ks1.GenerateAndStoreAsymmetricKey("master", "root", testParams(), keystore.AlgoRSA, ""))
	ts.Require().NoError(ks1.GenerateAndStoreSymmetricKey("sym1"))

	// Reload from the same file in a fresh Store instance — this is the
	// "single file on disk, multiple entries" round trip.
	ks2, err := keystore.New("PKCS12", params)
	ts.Require().NoError(err)

	aliases, err := ks2.GetAllAlias()
	ts.Require().NoError(err)
	ts.Assert().ElementsMatch([]string{"root", "master", "sym1"}, aliases)

	rootCert, err := ks2.GetCertificate("root")
	ts.Require().NoError(err)
	ts.Assert().Equal("Test Root", rootCert.Subject.CommonName)

	masterCert, err := ks2.GetCertificate("master")
	ts.Require().NoError(err)
	ts.Require().NoError(masterCert.CheckSignatureFrom(rootCert))

	symKey, err := ks2.GetSymmetricKey("sym1")
	ts.Require().NoError(err)
	ts.Assert().Len(symKey, 32)
}

func (ts *PKCS12TestSuite) generateAndVerifyECKey(alias, algo, curve string) {
	ks := ts.newTestStore()
	err := ks.GenerateAndStoreAsymmetricKey(alias, alias, testParams(), algo, curve)
	ts.Require().NoError(err)
	cert, err := ks.GetCertificate(alias)
	ts.Require().NoError(err)
	ts.Require().NoError(cert.CheckSignatureFrom(cert)) // self-signed
}

func (ts *PKCS12TestSuite) TestSECP256R1KeyGeneration() {
	ts.generateAndVerifyECKey("ec-r1", keystore.AlgoEC, keystore.CurveSECP256R1)
}

func (ts *PKCS12TestSuite) TestEd25519KeyGeneration() {
	ts.generateAndVerifyECKey("ed25519", keystore.AlgoEC, keystore.CurveED25519)
}

// TestPKCS12_SECP256K1_NotSupported documents a real Go standard-library
// limitation (see errSECP256K1Unsupported in keys.go): crypto/x509 cannot
// encode or decode certificates carrying a SECP256K1 public key, so this
// backend rejects it with a clear error rather than producing a broken
// certificate.
func (ts *PKCS12TestSuite) TestPKCS12_SECP256K1_NotSupported() {
	ks := ts.newTestStore()
	err := ks.GenerateAndStoreAsymmetricKey("ec-k1", "ec-k1", testParams(), keystore.AlgoEC, keystore.CurveSECP256K1)
	ts.Assert().Error(err)
	ts.Assert().Contains(err.Error(), "SECP256K1")
}

func (ts *PKCS12TestSuite) TestPKCS12_DeleteKey() {
	ks := ts.newTestStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("a", "a", testParams(), keystore.AlgoRSA, ""))
	ts.Require().NoError(ks.DeleteKey("a"))
	_, err := ks.GetCertificate("a")
	ts.Assert().Error(err)
}
