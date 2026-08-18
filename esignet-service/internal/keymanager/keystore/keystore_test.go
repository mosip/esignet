/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package keystore

import (
	"crypto"
	"crypto/x509"
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

// stubKeyStore is a minimal KeyStore implementation used to verify that
// Register/New wire a factory through correctly, without depending on a
// real PKCS#11/PKCS#12 backend.
type stubKeyStore struct{}

func (stubKeyStore) GetPrivateKey(_ string) (crypto.PrivateKey, error)  { return nil, nil }
func (stubKeyStore) GetPublicKey(_ string) (crypto.PublicKey, error)    { return nil, nil }
func (stubKeyStore) GetCertificate(_ string) (*x509.Certificate, error) { return nil, nil }
func (stubKeyStore) GetSymmetricKey(_ string) ([]byte, error)           { return nil, nil }
func (stubKeyStore) GetAsymmetricKey(_ string) (*KeyPairEntry, error)   { return nil, nil }
func (stubKeyStore) GetAllAlias() ([]string, error)                     { return nil, nil }
func (stubKeyStore) GenerateAndStoreSymmetricKey(_ string) error        { return nil }
func (stubKeyStore) GenerateAndStoreAsymmetricKey(_, _ string, _ CertificateParameters, _, _ string) error {
	return nil
}
func (stubKeyStore) DeleteKey(_ string) error { return nil }
func (stubKeyStore) StoreCertificate(_ string, _ crypto.PrivateKey, _ *x509.Certificate) error {
	return nil
}
func (stubKeyStore) ProviderName() string { return "stub" }
func (stubKeyStore) Close() error         { return nil }

type KeystoreTestSuite struct {
	suite.Suite
}

func TestKeystoreTestSuite(t *testing.T) {
	suite.Run(t, new(KeystoreTestSuite))
}

func (ts *KeystoreTestSuite) TestBuildCertificateTemplate() {
	notBefore := time.Now()
	notAfter := notBefore.AddDate(1, 0, 0)
	params := CertificateParameters{
		CommonName:       "MOSIP Root CA",
		OrganizationUnit: "unit",
		Organization:     "org",
		Location:         "loc",
		State:            "state",
		Country:          "IN",
		NotBefore:        notBefore,
		NotAfter:         notAfter,
	}

	tmpl, err := BuildCertificateTemplate(params)
	require.NoError(ts.T(), err)

	assert.Equal(ts.T(), "MOSIP Root CA", tmpl.Subject.CommonName)
	assert.Equal(ts.T(), []string{"unit"}, tmpl.Subject.OrganizationalUnit)
	assert.Equal(ts.T(), []string{"org"}, tmpl.Subject.Organization)
	assert.Equal(ts.T(), []string{"loc"}, tmpl.Subject.Locality)
	assert.Equal(ts.T(), []string{"state"}, tmpl.Subject.Province)
	assert.Equal(ts.T(), []string{"IN"}, tmpl.Subject.Country)
	assert.Equal(ts.T(), notBefore, tmpl.NotBefore)
	assert.Equal(ts.T(), notAfter, tmpl.NotAfter)
	assert.Equal(ts.T(), x509.KeyUsageDigitalSignature|x509.KeyUsageKeyEncipherment, tmpl.KeyUsage)
	assert.Empty(ts.T(), tmpl.ExtKeyUsage, "signing/encryption keys must not carry TLS ExtKeyUsage")
	require.NotNil(ts.T(), tmpl.SerialNumber)
	assert.NotZero(ts.T(), tmpl.SerialNumber.Sign(), "serial number must not be zero")
}

func (ts *KeystoreTestSuite) TestBuildCertificateTemplate_SerialNumbersAreUnique() {
	tmpl1, err := BuildCertificateTemplate(CertificateParameters{})
	require.NoError(ts.T(), err)
	tmpl2, err := BuildCertificateTemplate(CertificateParameters{})
	require.NoError(ts.T(), err)

	assert.NotEqual(ts.T(), tmpl1.SerialNumber, tmpl2.SerialNumber)
}

func (ts *KeystoreTestSuite) TestRegisterAndNew() {
	const typeName = "test-keystore-type"

	var gotParams map[string]string
	stub := &stubKeyStore{}
	Register(typeName, func(p map[string]string) (KeyStore, error) {
		gotParams = p
		return stub, nil
	})
	ts.T().Cleanup(func() { delete(registry, typeName) })

	params := map[string]string{"path": "/tmp/keystore"}
	ks, err := New(typeName, params)
	require.NoError(ts.T(), err)
	assert.Same(ts.T(), stub, ks)
	assert.Equal(ts.T(), params, gotParams)
}

func (ts *KeystoreTestSuite) TestNew_UnsupportedType() {
	ks, err := New("NOT-A-REAL-TYPE", nil)
	require.Error(ts.T(), err)
	assert.Nil(ts.T(), ks)
	assert.Contains(ts.T(), err.Error(), "NOT-A-REAL-TYPE")
}

func (ts *KeystoreTestSuite) TestRegister_FactoryErrorPropagates() {
	const typeName = "test-keystore-type-error"

	wantErr := errors.New("boom")
	Register(typeName, func(_ map[string]string) (KeyStore, error) {
		return nil, wantErr
	})
	ts.T().Cleanup(func() { delete(registry, typeName) })

	ks, err := New(typeName, nil)
	assert.Nil(ts.T(), ks)
	assert.ErrorIs(ts.T(), err, wantErr)
}
