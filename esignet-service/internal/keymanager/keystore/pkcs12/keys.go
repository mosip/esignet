package pkcs12

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
	"math/big"

	"github.com/mosip/esignet/internal/keymanager/keystore"
)

// errSECP256K1Unsupported documents a real, verified Go standard-library
// limitation (not a bug here): crypto/x509's CreateCertificate/
// ParseCertificate only recognize the NIST curves (P224/P256/P384/P521) in
// their SubjectPublicKeyInfo OID tables — an ecdsa.PublicKey on any other
// curve, including SECP256K1, is rejected on *both* the encode and the
// decode path ("x509: unsupported elliptic curve"), even though the curve
// arithmetic itself (via btcec.S256()) is correct. Supporting SECP256K1
// certificates in Go requires a custom X.509 encoder/decoder that bypasses
// crypto/x509's curve-OID restriction on both sides — real, scoped
// follow-up work, deliberately not attempted here. SECP256R1 and ED25519
// are unaffected and fully supported.
var errSECP256K1Unsupported = fmt.Errorf("pkcs12: SECP256K1 certificates are not supported: %w", errCryptoX509CurveLimitation)

var errCryptoX509CurveLimitation = fmt.Errorf("crypto/x509 only supports NIST curves (P224/P256/P384/P521) for EC certificates; SECP256K1 needs a custom X.509 codec")

func buildCertTemplate(params keystore.CertificateParameters) (*x509.Certificate, error) {
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	if err != nil {
		return nil, fmt.Errorf("pkcs12: generate serial: %w", err)
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
		KeyUsage:  x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment | x509.KeyUsageCertSign,
	}, nil
}

func generateKeyPair(algoName, curveName string) (crypto.Signer, error) {
	switch algoName {
	case keystore.AlgoRSA:
		return rsa.GenerateKey(rand.Reader, 2048)
	case keystore.AlgoEC:
		switch curveName {
		case keystore.CurveED25519:
			_, priv, err := ed25519.GenerateKey(rand.Reader)
			if err != nil {
				return nil, err
			}
			return priv, nil
		case keystore.CurveSECP256R1:
			return ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
		case keystore.CurveSECP256K1:
			return nil, errSECP256K1Unsupported
		default:
			return nil, fmt.Errorf("pkcs12: unsupported curve %q", curveName)
		}
	default:
		return nil, fmt.Errorf("pkcs12: unsupported algorithm %q", algoName)
	}
}

func (s *Store) GenerateAndStoreAsymmetricKey(alias, signKeyAlias string, params keystore.CertificateParameters, algoName, curveName string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	priv, err := generateKeyPair(algoName, curveName)
	if err != nil {
		return err
	}
	template, err := buildCertTemplate(params)
	if err != nil {
		return err
	}

	var certDER []byte
	if signKeyAlias == alias {
		template.IsCA = true
		template.BasicConstraintsValid = true
		certDER, err = x509.CreateCertificate(rand.Reader, template, template, priv.Public(), priv)
	} else {
		signEntry, ok := s.entries[signKeyAlias]
		if !ok || signEntry.Type != entryAsymmetric {
			return fmt.Errorf("pkcs12: signing alias %q not found", signKeyAlias)
		}
		signerPriv, signerCert, derr := s.decodePFX(signEntry.Data)
		if derr != nil {
			return derr
		}
		certDER, err = x509.CreateCertificate(rand.Reader, template, signerCert, priv.Public(), signerPriv)
	}
	if err != nil {
		return fmt.Errorf("pkcs12: create certificate: %w", err)
	}
	cert, err := x509.ParseCertificate(certDER)
	if err != nil {
		return fmt.Errorf("pkcs12: parse generated certificate: %w", err)
	}

	encoded, err := s.encodePFX(priv, cert)
	if err != nil {
		return err
	}
	s.entries[alias] = fileEntry{Type: entryAsymmetric, Data: encoded}
	return s.saveLocked()
}

func (s *Store) GenerateAndStoreSymmetricKey(alias string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	raw := make([]byte, 32) // AES-256
	if _, err := rand.Read(raw); err != nil {
		return fmt.Errorf("pkcs12: generate symmetric key: %w", err)
	}
	encoded, err := s.encryptSymmetric(raw)
	if err != nil {
		return err
	}
	s.entries[alias] = fileEntry{Type: entrySymmetric, Data: encoded}
	return s.saveLocked()
}

func (s *Store) GetPrivateKey(alias string) (crypto.PrivateKey, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	entry, ok := s.entries[alias]
	if !ok || entry.Type != entryAsymmetric {
		return nil, fmt.Errorf("pkcs12: private key alias %q not found", alias)
	}
	priv, _, err := s.decodePFX(entry.Data)
	return priv, err
}

func (s *Store) GetPublicKey(alias string) (crypto.PublicKey, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	entry, ok := s.entries[alias]
	if !ok || entry.Type != entryAsymmetric {
		return nil, fmt.Errorf("pkcs12: public key alias %q not found", alias)
	}
	_, cert, err := s.decodePFX(entry.Data)
	if err != nil {
		return nil, err
	}
	return cert.PublicKey, nil
}

func (s *Store) GetCertificate(alias string) (*x509.Certificate, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	entry, ok := s.entries[alias]
	if !ok || entry.Type != entryAsymmetric {
		return nil, fmt.Errorf("pkcs12: certificate alias %q not found", alias)
	}
	_, cert, err := s.decodePFX(entry.Data)
	return cert, err
}

func (s *Store) GetSymmetricKey(alias string) ([]byte, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	entry, ok := s.entries[alias]
	if !ok || entry.Type != entrySymmetric {
		return nil, fmt.Errorf("pkcs12: symmetric key alias %q not found", alias)
	}
	return s.decryptSymmetric(entry.Data)
}

func (s *Store) GetAsymmetricKey(alias string) (*keystore.KeyPairEntry, error) {
	s.mu.Lock()
	entry, ok := s.entries[alias]
	s.mu.Unlock()
	if !ok || entry.Type != entryAsymmetric {
		return nil, fmt.Errorf("pkcs12: asymmetric key alias %q not found", alias)
	}
	priv, cert, err := s.decodePFX(entry.Data)
	if err != nil {
		return nil, err
	}
	return &keystore.KeyPairEntry{PrivateKey: priv, Certificate: cert}, nil
}

func (s *Store) GetAllAlias() ([]string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	aliases := make([]string, 0, len(s.entries))
	for alias := range s.entries {
		aliases = append(aliases, alias)
	}
	return aliases, nil
}

func (s *Store) DeleteKey(alias string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	delete(s.entries, alias)
	return s.saveLocked()
}

// StoreCertificate replaces the certificate for an existing asymmetric
// alias, re-encoding the PFX blob with the supplied certificate. privateKey
// must match the alias's existing key (or be non-nil so the entry can be
// (re)created if the alias doesn't exist yet, e.g. a foreign-domain
// cert-only upload — see keymanager service UploadOtherDomainCertificate).
func (s *Store) StoreCertificate(alias string, privateKey crypto.PrivateKey, cert *x509.Certificate) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if privateKey == nil {
		if entry, ok := s.entries[alias]; ok && entry.Type == entryAsymmetric {
			existingPriv, _, err := s.decodePFX(entry.Data)
			if err != nil {
				return err
			}
			privateKey = existingPriv
		}
	}
	encoded, err := s.encodePFX(privateKey, cert)
	if err != nil {
		return err
	}
	s.entries[alias] = fileEntry{Type: entryAsymmetric, Data: encoded}
	return s.saveLocked()
}
