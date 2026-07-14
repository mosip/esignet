package keymanager_test

import (
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"fmt"
	"io"
	"math/big"
	"time"

	"github.com/mosip/esignet/internal/keymanager/keystore"
)

func testRandReader() io.Reader { return rand.Reader }

func generateTestKeyPair(algoName, curveName string) (crypto.Signer, crypto.PublicKey, error) {
	switch algoName {
	case keystore.AlgoRSA, "":
		priv, err := rsa.GenerateKey(rand.Reader, 2048)
		if err != nil {
			return nil, nil, err
		}
		return priv, &priv.PublicKey, nil
	case keystore.AlgoEC:
		switch curveName {
		case keystore.CurveED25519:
			pub, priv, err := ed25519.GenerateKey(rand.Reader)
			if err != nil {
				return nil, nil, err
			}
			return priv, pub, nil
		default:
			priv, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
			if err != nil {
				return nil, nil, err
			}
			return priv, &priv.PublicKey, nil
		}
	default:
		return nil, nil, fmt.Errorf("unsupported algo %q", algoName)
	}
}

func testCertTemplate(params keystore.CertificateParameters) *x509.Certificate {
	notBefore, notAfter := params.NotBefore, params.NotAfter
	if notBefore.IsZero() {
		notBefore = time.Now().UTC()
	}
	if notAfter.IsZero() {
		notAfter = notBefore.AddDate(1, 0, 0)
	}
	return &x509.Certificate{
		SerialNumber: big.NewInt(time.Now().UnixNano()),
		Subject: pkix.Name{
			CommonName:         params.CommonName,
			OrganizationalUnit: []string{params.OrganizationUnit},
			Organization:       []string{params.Organization},
			Locality:           []string{params.Location},
			Province:           []string{params.State},
			Country:            []string{params.Country},
		},
		NotBefore: notBefore,
		NotAfter:  notAfter,
		KeyUsage:  x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment | x509.KeyUsageCertSign,
	}
}
