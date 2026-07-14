package keymanager

import (
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"fmt"
	"math/big"

	"go.mozilla.org/pkcs7"

	"github.com/mosip/esignet/internal/keymanager/keystore"
)

// buildCertTemplate builds an *x509.Certificate template for a
// DB-resident (Component Encryption) key — mirrors the equivalent
// unexported helper in each keystore backend, used here because
// generateDBResidentKey signs and issues the certificate itself rather than
// delegating to the keystore.KeyStore port.
func buildCertTemplate(params keystore.CertificateParameters) (*x509.Certificate, error) {
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	if err != nil {
		return nil, fmt.Errorf("generate serial: %w", err)
	}
	return &x509.Certificate{
		SerialNumber: serial,
		Subject: pkix.Name{
			CommonName:         params.CommonName,
			OrganizationalUnit: []string{params.OrganizationUnit},
			Organization:       []string{params.Organization},
			Locality:           []string{params.Location},
			Province:           []string{params.State},
			Country:            []string{params.Country},
		},
		NotBefore: params.NotBefore,
		NotAfter:  params.NotAfter,
		KeyUsage:  x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment,
	}, nil
}

// buildPKCS7TrustPath builds a degenerate PKCS#7 SignedData (a "p7b" file)
// containing every certificate in chain (leaf first), PEM-encoded.
func buildPKCS7TrustPath(chain []*x509.Certificate) string {
	raw := make([]byte, 0)
	for _, c := range chain {
		raw = append(raw, c.Raw...)
	}
	p7, err := pkcs7.DegenerateCertificate(raw)
	if err != nil {
		return ""
	}
	return encodePEM("PKCS7", p7)
}
