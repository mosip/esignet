package pkcs11

import (
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/asn1"
	"fmt"
	"math/big"

	"github.com/miekg/pkcs11"

	"github.com/mosip/esignet/internal/keymanager/keystore"
)

// PKCS#11 v3.0 constants for Edwards-curve (Ed25519) support, not present in
// miekg/pkcs11 v1.1.2 (which only covers the v2.20 constant set). Values are
// from the OASIS PKCS#11 v3.0 specification (pkcs11t.h).
const (
	ckkECEdwards           uint = 0x00000040
	ckmECEdwardsKeyPairGen uint = 0x00001055
	ckmEDDSA               uint = 0x00001057
)

// Curve OIDs, DER-encoded as the CKA_EC_PARAMS value (an ASN.1 OBJECT
// IDENTIFIER), for each curve name accepted by keystore.GenerateAndStoreAsymmetricKey.
var curveEncodedOIDs = map[string][]byte{
	keystore.CurveSECP256K1: {0x06, 0x05, 0x2b, 0x81, 0x04, 0x00, 0x0a},                   // 1.3.132.0.10
	keystore.CurveSECP256R1: {0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07}, // 1.2.840.10045.3.1.7 (prime256v1)
	keystore.CurveED25519:   {0x06, 0x03, 0x2b, 0x65, 0x70},                               // 1.3.101.112 (edwards25519 / Ed25519)
}

var ellipticCurves = map[string]elliptic.Curve{
	keystore.CurveSECP256R1: elliptic.P256(),
	keystore.CurveSECP256K1: secp256k1(),
}

// curveNameFromOID reverse-looks-up a DER-encoded CKA_EC_PARAMS value
// against curveEncodedOIDs.
func curveNameFromOID(oid []byte) (string, bool) {
	for name, encoded := range curveEncodedOIDs {
		if string(encoded) == string(oid) {
			return name, true
		}
	}
	return "", false
}

func labelAttr(alias string) []*pkcs11.Attribute {
	return []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_LABEL, alias)}
}

// findObject returns the handle of the first object matching class + label.
func (s *Store) findObject(sh pkcs11.SessionHandle, class uint, alias string) (pkcs11.ObjectHandle, bool, error) {
	tmpl := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, class),
		pkcs11.NewAttribute(pkcs11.CKA_LABEL, alias),
	}
	if err := s.ctx.FindObjectsInit(sh, tmpl); err != nil {
		return 0, false, err
	}
	defer s.ctx.FindObjectsFinal(sh)
	handles, _, err := s.ctx.FindObjects(sh, 1)
	if err != nil {
		return 0, false, err
	}
	if len(handles) == 0 {
		return 0, false, nil
	}
	return handles[0], true, nil
}

func (s *Store) findAllObjectsWithLabel(sh pkcs11.SessionHandle, alias string) ([]pkcs11.ObjectHandle, error) {
	if err := s.ctx.FindObjectsInit(sh, labelAttr(alias)); err != nil {
		return nil, err
	}
	defer s.ctx.FindObjectsFinal(sh)
	handles, _, err := s.ctx.FindObjects(sh, 100)
	return handles, err
}

// GenerateAndStoreAsymmetricKey implements keystore.KeyStore.
// errSECP256K1Unsupported documents a real, verified Go standard-library
// limitation (not a bug here): crypto/x509's CreateCertificate/
// ParseCertificate only recognize the NIST curves (P224/P256/P384/P521) in
// their SubjectPublicKeyInfo OID tables — an ecdsa.PublicKey on any other
// curve, including SECP256K1, is rejected on *both* the encode and the
// decode path ("x509: unsupported elliptic curve"), even though the curve
// arithmetic itself (via btcec.S256(), see secp256k1.go) is correct.
// Supporting SECP256K1 certificates in Go requires a custom X.509
// encoder/decoder that bypasses crypto/x509's curve-OID restriction on both
// sides — real, scoped follow-up work, deliberately not attempted here.
// Checked before any HSM key generation so a rejected request doesn't leave
// an orphaned key object on the token. SECP256R1 and ED25519 are
// unaffected and fully supported.
var errSECP256K1Unsupported = fmt.Errorf("pkcs11: SECP256K1 certificates are not supported: crypto/x509 only supports NIST curves (P224/P256/P384/P521) for EC certificates; SECP256K1 needs a custom X.509 codec")

func (s *Store) GenerateAndStoreAsymmetricKey(alias, signKeyAlias string, params keystore.CertificateParameters, algoName, curveName string) error {
	if curveName == keystore.CurveSECP256K1 {
		return errSECP256K1Unsupported
	}
	var pub crypto.PublicKey
	var privHandle pkcs11.ObjectHandle

	err := s.withSession(func(sh pkcs11.SessionHandle) error {
		var mech []*pkcs11.Mechanism
		pubTmpl := []*pkcs11.Attribute{
			pkcs11.NewAttribute(pkcs11.CKA_TOKEN, true),
			pkcs11.NewAttribute(pkcs11.CKA_LABEL, alias),
			pkcs11.NewAttribute(pkcs11.CKA_VERIFY, true),
			pkcs11.NewAttribute(pkcs11.CKA_ENCRYPT, algoName == keystore.AlgoRSA),
		}
		privTmpl := []*pkcs11.Attribute{
			pkcs11.NewAttribute(pkcs11.CKA_TOKEN, true),
			pkcs11.NewAttribute(pkcs11.CKA_PRIVATE, true),
			pkcs11.NewAttribute(pkcs11.CKA_SENSITIVE, true),
			pkcs11.NewAttribute(pkcs11.CKA_EXTRACTABLE, false),
			pkcs11.NewAttribute(pkcs11.CKA_LABEL, alias),
			pkcs11.NewAttribute(pkcs11.CKA_SIGN, true),
			pkcs11.NewAttribute(pkcs11.CKA_DECRYPT, algoName == keystore.AlgoRSA),
		}

		switch algoName {
		case keystore.AlgoRSA:
			mech = []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_RSA_PKCS_KEY_PAIR_GEN, nil)}
			pubTmpl = append(pubTmpl,
				pkcs11.NewAttribute(pkcs11.CKA_MODULUS_BITS, 2048),
				pkcs11.NewAttribute(pkcs11.CKA_PUBLIC_EXPONENT, []byte{0x01, 0x00, 0x01}),
			)
		case keystore.AlgoEC:
			oid, ok := curveEncodedOIDs[curveName]
			if !ok {
				return fmt.Errorf("pkcs11: unsupported curve %q", curveName)
			}
			if curveName == keystore.CurveED25519 {
				mech = []*pkcs11.Mechanism{pkcs11.NewMechanism(ckmECEdwardsKeyPairGen, nil)}
			} else {
				mech = []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_EC_KEY_PAIR_GEN, nil)}
			}
			pubTmpl = append(pubTmpl, pkcs11.NewAttribute(pkcs11.CKA_EC_PARAMS, oid))
		default:
			return fmt.Errorf("pkcs11: unsupported algorithm %q", algoName)
		}

		pubHandle, ph, err := s.ctx.GenerateKeyPair(sh, mech, pubTmpl, privTmpl)
		if err != nil {
			return fmt.Errorf("generate key pair: %w", err)
		}
		privHandle = ph

		pub, err = readPublicKey(s.ctx, sh, pubHandle, algoName, curveName)
		return err
	})
	if err != nil {
		return err
	}

	priv := &privateKey{store: s, alias: alias, handle: privHandle, keyType: pkcs11KeyType(algoName, curveName), pub: pub}

	template, err := buildCertTemplate(params)
	if err != nil {
		return err
	}

	var certDER []byte
	if signKeyAlias == alias {
		// Self-signed (ROOT tier, §6.1 of the implementation plan).
		template.IsCA = true
		template.BasicConstraintsValid = true
		certDER, err = x509.CreateCertificate(rand.Reader, template, template, pub, priv)
	} else {
		signerCert, gerr := s.GetCertificate(signKeyAlias)
		if gerr != nil {
			return fmt.Errorf("pkcs11: signing certificate for alias %q not found: %w", signKeyAlias, gerr)
		}
		signerPriv, gerr := s.GetPrivateKey(signKeyAlias)
		if gerr != nil {
			return fmt.Errorf("pkcs11: signing private key for alias %q not found: %w", signKeyAlias, gerr)
		}
		certDER, err = x509.CreateCertificate(rand.Reader, template, signerCert, pub, signerPriv)
	}
	if err != nil {
		return fmt.Errorf("pkcs11: create certificate: %w", err)
	}

	cert, err := x509.ParseCertificate(certDER)
	if err != nil {
		return fmt.Errorf("pkcs11: parse generated certificate: %w", err)
	}
	if err := s.StoreCertificate(alias, priv, cert); err != nil {
		return err
	}
	s.cachePut(alias, privHandle)
	return nil
}

func pkcs11KeyType(algoName, curveName string) uint {
	if algoName == keystore.AlgoRSA {
		return pkcs11.CKK_RSA
	}
	if curveName == keystore.CurveED25519 {
		return ckkECEdwards
	}
	return pkcs11.CKK_EC
}

func buildCertTemplate(params keystore.CertificateParameters) (*x509.Certificate, error) {
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	if err != nil {
		return nil, fmt.Errorf("pkcs11: generate serial: %w", err)
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
		NotBefore:   params.NotBefore,
		NotAfter:    params.NotAfter,
		KeyUsage:    x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment | x509.KeyUsageCertSign,
		ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth},
	}, nil
}

// readPublicKey reads a freshly generated public-key object's attributes and
// reconstructs the corresponding Go crypto.PublicKey.
func readPublicKey(ctx *pkcs11.Ctx, sh pkcs11.SessionHandle, handle pkcs11.ObjectHandle, algoName, curveName string) (crypto.PublicKey, error) {
	switch algoName {
	case keystore.AlgoRSA:
		attrs, err := ctx.GetAttributeValue(sh, handle, []*pkcs11.Attribute{
			pkcs11.NewAttribute(pkcs11.CKA_MODULUS, nil),
			pkcs11.NewAttribute(pkcs11.CKA_PUBLIC_EXPONENT, nil),
		})
		if err != nil {
			return nil, fmt.Errorf("read RSA public key: %w", err)
		}
		n := new(big.Int).SetBytes(attrs[0].Value)
		e := new(big.Int).SetBytes(attrs[1].Value)
		return &rsa.PublicKey{N: n, E: int(e.Int64())}, nil
	case keystore.AlgoEC:
		attrs, err := ctx.GetAttributeValue(sh, handle, []*pkcs11.Attribute{
			pkcs11.NewAttribute(pkcs11.CKA_EC_POINT, nil),
		})
		if err != nil {
			return nil, fmt.Errorf("read EC public key: %w", err)
		}
		point, err := unwrapECPoint(attrs[0].Value)
		if err != nil {
			return nil, err
		}
		if curveName == keystore.CurveED25519 {
			if len(point) != ed25519.PublicKeySize {
				return nil, fmt.Errorf("pkcs11: unexpected Ed25519 point length %d", len(point))
			}
			return ed25519.PublicKey(point), nil
		}
		curve, ok := ellipticCurves[curveName]
		if !ok {
			return nil, fmt.Errorf("pkcs11: unsupported curve %q for public key reconstruction", curveName)
		}
		x, y := elliptic.Unmarshal(curve, point)
		if x == nil {
			return nil, fmt.Errorf("pkcs11: failed to unmarshal EC point for curve %q", curveName)
		}
		return &ecdsa.PublicKey{Curve: curve, X: x, Y: y}, nil
	default:
		return nil, fmt.Errorf("pkcs11: unsupported algorithm %q", algoName)
	}
}

// unwrapECPoint strips the ASN.1 OCTET STRING wrapper that CKA_EC_POINT
// values carry (the point itself is DER-encoded as an OCTET STRING).
func unwrapECPoint(raw []byte) ([]byte, error) {
	var octet []byte
	if _, err := asn1.Unmarshal(raw, &octet); err != nil {
		// Some tokens return the raw point without the OCTET STRING wrapper.
		return raw, nil
	}
	return octet, nil
}

func (s *Store) GenerateAndStoreSymmetricKey(alias string) error {
	return s.withSession(func(sh pkcs11.SessionHandle) error {
		mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_AES_KEY_GEN, nil)}
		tmpl := []*pkcs11.Attribute{
			pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_SECRET_KEY),
			pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, pkcs11.CKK_AES),
			pkcs11.NewAttribute(pkcs11.CKA_TOKEN, true),
			pkcs11.NewAttribute(pkcs11.CKA_LABEL, alias),
			pkcs11.NewAttribute(pkcs11.CKA_VALUE_LEN, 32),
			pkcs11.NewAttribute(pkcs11.CKA_ENCRYPT, true),
			pkcs11.NewAttribute(pkcs11.CKA_DECRYPT, true),
			// Extractable: unlike asymmetric private keys, the interface
			// contract (keystore.KeyStore.GetSymmetricKey) requires
			// returning the raw key bytes to the caller, so this key
			// cannot be CKA_SENSITIVE/non-extractable like the private
			// keys above.
			pkcs11.NewAttribute(pkcs11.CKA_SENSITIVE, false),
			pkcs11.NewAttribute(pkcs11.CKA_EXTRACTABLE, true),
		}
		h, err := s.ctx.GenerateKey(sh, mech, tmpl)
		if err != nil {
			return fmt.Errorf("generate symmetric key: %w", err)
		}
		s.cachePut(alias, h)
		return nil
	})
}

func (s *Store) GetPrivateKey(alias string) (crypto.PrivateKey, error) {
	var priv *privateKey
	err := s.withSession(func(sh pkcs11.SessionHandle) error {
		h, ok, err := s.findObject(sh, pkcs11.CKO_PRIVATE_KEY, alias)
		if err != nil {
			return err
		}
		if !ok {
			return fmt.Errorf("pkcs11: private key alias %q not found", alias)
		}
		attrs, err := s.ctx.GetAttributeValue(sh, h, []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, nil)})
		if err != nil {
			return fmt.Errorf("read key type: %w", err)
		}
		keyType := uint(new(big.Int).SetBytes(attrs[0].Value).Uint64())

		pubHandle, ok, err := s.findObject(sh, pkcs11.CKO_PUBLIC_KEY, alias)
		var pub crypto.PublicKey
		if err == nil && ok {
			algoName, curveName, aerr := algoAndCurveFor(s.ctx, sh, pubHandle, keyType)
			if aerr == nil {
				pub, _ = readPublicKey(s.ctx, sh, pubHandle, algoName, curveName)
			}
		}
		priv = &privateKey{store: s, alias: alias, handle: h, keyType: keyType, pub: pub}
		return nil
	})
	return priv, err
}

// algoAndCurveFor determines (algoName, curveName) for a public-key object,
// reading CKA_EC_PARAMS to disambiguate SECP256K1 vs SECP256R1 (both report
// CKK_EC) rather than guessing.
func algoAndCurveFor(ctx *pkcs11.Ctx, sh pkcs11.SessionHandle, pubHandle pkcs11.ObjectHandle, keyType uint) (algoName, curveName string, err error) {
	if keyType == pkcs11.CKK_RSA {
		return keystore.AlgoRSA, "", nil
	}
	if keyType == ckkECEdwards {
		return keystore.AlgoEC, keystore.CurveED25519, nil
	}
	attrs, err := ctx.GetAttributeValue(sh, pubHandle, []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_EC_PARAMS, nil)})
	if err != nil {
		return "", "", fmt.Errorf("read EC params: %w", err)
	}
	name, ok := curveNameFromOID(attrs[0].Value)
	if !ok {
		return "", "", fmt.Errorf("pkcs11: unrecognized EC curve OID")
	}
	return keystore.AlgoEC, name, nil
}

func (s *Store) GetPublicKey(alias string) (crypto.PublicKey, error) {
	priv, err := s.GetPrivateKey(alias)
	if err != nil {
		return nil, err
	}
	return priv.(*privateKey).pub, nil
}

func (s *Store) GetCertificate(alias string) (*x509.Certificate, error) {
	var cert *x509.Certificate
	err := s.withSession(func(sh pkcs11.SessionHandle) error {
		h, ok, err := s.findObject(sh, pkcs11.CKO_CERTIFICATE, alias)
		if err != nil {
			return err
		}
		if !ok {
			return fmt.Errorf("pkcs11: certificate alias %q not found", alias)
		}
		attrs, err := s.ctx.GetAttributeValue(sh, h, []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_VALUE, nil)})
		if err != nil {
			return fmt.Errorf("read certificate value: %w", err)
		}
		cert, err = x509.ParseCertificate(attrs[0].Value)
		return err
	})
	return cert, err
}

func (s *Store) GetSymmetricKey(alias string) ([]byte, error) {
	var key []byte
	err := s.withSession(func(sh pkcs11.SessionHandle) error {
		h, ok, err := s.findObject(sh, pkcs11.CKO_SECRET_KEY, alias)
		if err != nil {
			return err
		}
		if !ok {
			return fmt.Errorf("pkcs11: symmetric key alias %q not found", alias)
		}
		attrs, err := s.ctx.GetAttributeValue(sh, h, []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_VALUE, nil)})
		if err != nil {
			return fmt.Errorf("read symmetric key value: %w", err)
		}
		key = attrs[0].Value
		return nil
	})
	return key, err
}

func (s *Store) GetAsymmetricKey(alias string) (*keystore.KeyPairEntry, error) {
	priv, err := s.GetPrivateKey(alias)
	if err != nil {
		return nil, err
	}
	cert, err := s.GetCertificate(alias)
	if err != nil {
		return nil, err
	}
	return &keystore.KeyPairEntry{PrivateKey: priv, Certificate: cert}, nil
}

func (s *Store) GetAllAlias() ([]string, error) {
	var aliases []string
	err := s.withSession(func(sh pkcs11.SessionHandle) error {
		tmpl := []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_PRIVATE_KEY)}
		if err := s.ctx.FindObjectsInit(sh, tmpl); err != nil {
			return err
		}
		defer s.ctx.FindObjectsFinal(sh)
		handles, _, err := s.ctx.FindObjects(sh, 1000)
		if err != nil {
			return err
		}
		for _, h := range handles {
			attrs, err := s.ctx.GetAttributeValue(sh, h, []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_LABEL, nil)})
			if err != nil {
				continue
			}
			aliases = append(aliases, string(attrs[0].Value))
		}
		return nil
	})
	return aliases, err
}

func (s *Store) DeleteKey(alias string) error {
	err := s.withSession(func(sh pkcs11.SessionHandle) error {
		handles, err := s.findAllObjectsWithLabel(sh, alias)
		if err != nil {
			return err
		}
		for _, h := range handles {
			if err := s.ctx.DestroyObject(sh, h); err != nil {
				return fmt.Errorf("destroy object: %w", err)
			}
		}
		return nil
	})
	if err == nil {
		s.cacheInvalidate(alias)
	}
	return err
}

// StoreCertificate stores/replaces the certificate object for alias. The
// privateKey argument is accepted for interface compatibility with
// keystore.KeyStore (mirrors the Java API, where a cert upload can carry an
// externally supplied private key); for the PKCS#11 backend the private key
// already resides on the token from generation, so it is not re-imported
// here — only the certificate object is written.
func (s *Store) StoreCertificate(alias string, _ crypto.PrivateKey, cert *x509.Certificate) error {
	return s.withSession(func(sh pkcs11.SessionHandle) error {
		if h, ok, err := s.findObject(sh, pkcs11.CKO_CERTIFICATE, alias); err == nil && ok {
			_ = s.ctx.DestroyObject(sh, h)
		}
		tmpl := []*pkcs11.Attribute{
			pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_CERTIFICATE),
			pkcs11.NewAttribute(pkcs11.CKA_CERTIFICATE_TYPE, pkcs11.CKC_X_509),
			pkcs11.NewAttribute(pkcs11.CKA_TOKEN, true),
			pkcs11.NewAttribute(pkcs11.CKA_LABEL, alias),
			pkcs11.NewAttribute(pkcs11.CKA_SUBJECT, cert.RawSubject),
			pkcs11.NewAttribute(pkcs11.CKA_VALUE, cert.Raw),
		}
		_, err := s.ctx.CreateObject(sh, tmpl)
		if err != nil {
			return fmt.Errorf("create certificate object: %w", err)
		}
		return nil
	})
}
