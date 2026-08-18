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
)

func TestBuildCertificateTemplate(t *testing.T) {
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
	require.NoError(t, err)

	assert.Equal(t, "MOSIP Root CA", tmpl.Subject.CommonName)
	assert.Equal(t, []string{"unit"}, tmpl.Subject.OrganizationalUnit)
	assert.Equal(t, []string{"org"}, tmpl.Subject.Organization)
	assert.Equal(t, []string{"loc"}, tmpl.Subject.Locality)
	assert.Equal(t, []string{"state"}, tmpl.Subject.Province)
	assert.Equal(t, []string{"IN"}, tmpl.Subject.Country)
	assert.Equal(t, notBefore, tmpl.NotBefore)
	assert.Equal(t, notAfter, tmpl.NotAfter)
	assert.Equal(t, x509.KeyUsageDigitalSignature|x509.KeyUsageKeyEncipherment, tmpl.KeyUsage)
	assert.Empty(t, tmpl.ExtKeyUsage, "signing/encryption keys must not carry TLS ExtKeyUsage")
	require.NotNil(t, tmpl.SerialNumber)
	assert.NotZero(t, tmpl.SerialNumber.Sign(), "serial number must not be zero")
}

func TestBuildCertificateTemplate_SerialNumbersAreUnique(t *testing.T) {
	tmpl1, err := BuildCertificateTemplate(CertificateParameters{})
	require.NoError(t, err)
	tmpl2, err := BuildCertificateTemplate(CertificateParameters{})
	require.NoError(t, err)

	assert.NotEqual(t, tmpl1.SerialNumber, tmpl2.SerialNumber)
}

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

func TestRegisterAndNew(t *testing.T) {
	const typeName = "test-keystore-type"

	var gotParams map[string]string
	stub := &stubKeyStore{}
	Register(typeName, func(p map[string]string) (KeyStore, error) {
		gotParams = p
		return stub, nil
	})
	t.Cleanup(func() { delete(registry, typeName) })

	params := map[string]string{"path": "/tmp/keystore"}
	ks, err := New(typeName, params)
	require.NoError(t, err)
	assert.Same(t, stub, ks)
	assert.Equal(t, params, gotParams)
}

func TestNew_UnsupportedType(t *testing.T) {
	ks, err := New("NOT-A-REAL-TYPE", nil)
	require.Error(t, err)
	assert.Nil(t, ks)
	assert.Contains(t, err.Error(), "NOT-A-REAL-TYPE")
}

func TestRegister_FactoryErrorPropagates(t *testing.T) {
	const typeName = "test-keystore-type-error"

	wantErr := errors.New("boom")
	Register(typeName, func(_ map[string]string) (KeyStore, error) {
		return nil, wantErr
	})
	t.Cleanup(func() { delete(registry, typeName) })

	ks, err := New(typeName, nil)
	assert.Nil(t, ks)
	assert.ErrorIs(t, err, wantErr)
}
