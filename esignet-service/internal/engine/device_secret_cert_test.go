package engine

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"math/big"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

func selfSignedPEM(t *testing.T, priv, pub any) string {
	t.Helper()
	tmpl := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: "DEVICE_SECRET_ENC"},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(time.Hour),
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, pub, priv)
	require.NoError(t, err)
	return string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}))
}

func TestCheckDeviceSecretEncryptionCertificate(t *testing.T) {
	rsa2048, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	require.NoError(t, CheckDeviceSecretEncryptionCertificate(selfSignedPEM(t, rsa2048, &rsa2048.PublicKey)))

	rsa1024, err := rsa.GenerateKey(rand.Reader, 1024)
	require.NoError(t, err)
	require.ErrorContains(t, CheckDeviceSecretEncryptionCertificate(selfSignedPEM(t, rsa1024, &rsa1024.PublicKey)), "1024-bit")

	ec, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	require.ErrorContains(t, CheckDeviceSecretEncryptionCertificate(selfSignedPEM(t, ec, &ec.PublicKey)), "RSA")

	require.ErrorContains(t, CheckDeviceSecretEncryptionCertificate("not a certificate"), "PEM")
}
