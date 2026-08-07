package keymanager

import (
	"crypto/x509"
	"fmt"

	"go.mozilla.org/pkcs7"
)

// buildPKCS7TrustPath builds a degenerate PKCS#7 SignedData (a "p7b" file)
// containing every certificate in chain (leaf first), PEM-encoded.
func buildPKCS7TrustPath(chain []*x509.Certificate) (string, error) {
	raw := make([]byte, 0)
	for _, c := range chain {
		raw = append(raw, c.Raw...)
	}
	p7, err := pkcs7.DegenerateCertificate(raw)
	if err != nil {
		return "", fmt.Errorf("build pkcs7 trust path: %w", err)
	}
	return encodePEM("PKCS7", p7), nil
}
