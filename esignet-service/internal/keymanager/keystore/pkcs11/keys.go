//go:build cgo

package pkcs11

import (
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
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
	defer func() { _ = s.ctx.FindObjectsFinal(sh) }()
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
	defer func() { _ = s.ctx.FindObjectsFinal(sh) }()
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

// GenerateAndStoreAsymmetricKey implements keystore.KeyStore.
func (s *Store) GenerateAndStoreAsymmetricKey(alias, signKeyAlias string, params keystore.CertificateParameters, algoName, curveName string) (retErr error) {
	if curveName == keystore.CurveSECP256K1 {
		return errSECP256K1Unsupported
	}
	var pub crypto.PublicKey
	var privHandle pkcs11.ObjectHandle
	var keyGenerated bool

	// Any failure past key generation must not leave an orphaned token
	// object: the private (and public) key objects are written with
	// CKA_TOKEN=true by GenerateKeyPair, so they persist on the HSM even if
	// this function returns early.
	defer func() {
		if retErr != nil && keyGenerated {
			if derr := s.DeleteKey(alias); derr != nil {
				retErr = fmt.Errorf("%w (cleanup of orphaned key %q failed: %w)", retErr, alias, derr)
			}
		}
	}()

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
		keyGenerated = true

		pub, err = readPublicKey(s.ctx, sh, pubHandle, algoName, curveName)
		return err
	})
	if err != nil {
		return err
	}

	priv := &privateKey{store: s, alias: alias, handle: privHandle, keyType: pkcs11KeyType(algoName, curveName), pub: pub}

	template, err := keystore.BuildCertificateTemplate(params)
	if err != nil {
		return err
	}

	var certDER []byte
	if signKeyAlias == alias {
		// Self-signed (ROOT tier, §6.1 of the implementation plan).
		template.IsCA = true
		template.BasicConstraintsValid = true
		template.KeyUsage |= x509.KeyUsageCertSign
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
		// crypto/ecdh only supports the NIST curves (P224/P256/P384/P521), but
		// ellipticCurves also carries SECP256K1 (via secp256k1()/btcec), so we
		// stay on the generic elliptic.Curve-based API here.
		x, y := elliptic.Unmarshal(curve, point) //nolint:staticcheck
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

// GenerateAndStoreSymmetricKey implements keystore.KeyStore.
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
		if _, err := s.ctx.GenerateKey(sh, mech, tmpl); err != nil {
			return fmt.Errorf("generate symmetric key: %w", err)
		}
		return nil
	})
}

// decodeCKULong decodes a CK_ULONG-typed PKCS#11 attribute value (e.g.
// CKA_KEY_TYPE, CKA_CLASS) into a Go uint. CK_ULONG attributes are returned
// by the token as raw bytes in the *host's native byte order* (little-endian
// on every real deployment target — x86_64, ARM64), NOT as a big-endian
// byte string — unlike byte-string attributes such as CKA_MODULUS or an
// ECDSA signature's r/s, which genuinely are big-endian per the PKCS#11
// spec and are correctly decoded elsewhere in this package via
// big.Int.SetBytes. Using big.Int.SetBytes here (as this code used to)
// silently misinterprets the value: CKK_EC (3), returned as the 8-byte
// little-endian sequence [0x03,0,0,0,0,0,0,0], decodes big-endian as
// 0x0300000000000000, which matches none of the CKK_* constants this
// package checks against and surfaces as a baffling "unsupported key type
// 216172782113783808" error at signing time. Length-agnostic (handles both
// 4-byte and 8-byte CK_ULONG widths) since the byte slice length is
// token/platform dependent.
func decodeCKULong(b []byte) uint {
	var v uint64
	for i, by := range b {
		v |= uint64(by) << (8 * i)
	}
	return uint(v)
}

// GetPrivateKey implements keystore.KeyStore.
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
		keyType := decodeCKULong(attrs[0].Value)

		pubHandle, ok, err := s.findObject(sh, pkcs11.CKO_PUBLIC_KEY, alias)
		if err != nil {
			return fmt.Errorf("pkcs11: find public key for alias %q: %w", alias, err)
		}
		var pub crypto.PublicKey
		if ok {
			algoName, curveName, aerr := algoAndCurveFor(s.ctx, sh, pubHandle, keyType)
			if aerr != nil {
				return fmt.Errorf("pkcs11: resolve algo/curve for alias %q: %w", alias, aerr)
			}
			pub, err = readPublicKey(s.ctx, sh, pubHandle, algoName, curveName)
			if err != nil {
				return fmt.Errorf("pkcs11: reconstruct public key for alias %q: %w", alias, err)
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

// GetPublicKey implements keystore.KeyStore.
func (s *Store) GetPublicKey(alias string) (crypto.PublicKey, error) {
	priv, err := s.GetPrivateKey(alias)
	if err != nil {
		return nil, err
	}
	pub := priv.(*privateKey).pub
	if pub == nil {
		return nil, fmt.Errorf("pkcs11: no public key object for alias %q", alias)
	}
	return pub, nil
}

// GetCertificate implements keystore.KeyStore.
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

// GetSymmetricKey implements keystore.KeyStore.
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

// GetAsymmetricKey implements keystore.KeyStore.
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

// GetAllAlias implements keystore.KeyStore.
func (s *Store) GetAllAlias() ([]string, error) {
	var aliases []string
	err := s.withSession(func(sh pkcs11.SessionHandle) error {
		tmpl := []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_PRIVATE_KEY)}
		if err := s.ctx.FindObjectsInit(sh, tmpl); err != nil {
			return err
		}
		defer func() { _ = s.ctx.FindObjectsFinal(sh) }()
		for {
			handles, _, err := s.ctx.FindObjects(sh, 256)
			if err != nil {
				return err
			}
			if len(handles) == 0 {
				return nil
			}
			for _, h := range handles {
				attrs, aerr := s.ctx.GetAttributeValue(sh, h, []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_LABEL, nil)})
				if aerr != nil {
					return fmt.Errorf("pkcs11: read label of private key object: %w", aerr)
				}
				aliases = append(aliases, string(attrs[0].Value))
			}
		}
	})
	return aliases, err
}

// DeleteKey implements keystore.KeyStore.
func (s *Store) DeleteKey(alias string) error {
	return s.withSession(func(sh pkcs11.SessionHandle) error {
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
}

// StoreCertificate stores/replaces the certificate object for alias. The
// privateKey argument is accepted for interface compatibility with
// keystore.KeyStore (mirrors the Java API, where a cert upload can carry an
// externally supplied private key); for the PKCS#11 backend the private key
// already resides on the token from generation, so it is not re-imported
// here — only the certificate object is written.
func (s *Store) StoreCertificate(alias string, _ crypto.PrivateKey, cert *x509.Certificate) error {
	return s.withSession(func(sh pkcs11.SessionHandle) error {
		oldHandle, hasOld, err := s.findObject(sh, pkcs11.CKO_CERTIFICATE, alias)
		if err != nil {
			return fmt.Errorf("pkcs11: find existing certificate for alias %q: %w", alias, err)
		}
		tmpl := []*pkcs11.Attribute{
			pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_CERTIFICATE),
			pkcs11.NewAttribute(pkcs11.CKA_CERTIFICATE_TYPE, pkcs11.CKC_X_509),
			pkcs11.NewAttribute(pkcs11.CKA_TOKEN, true),
			pkcs11.NewAttribute(pkcs11.CKA_LABEL, alias),
			pkcs11.NewAttribute(pkcs11.CKA_SUBJECT, cert.RawSubject),
			pkcs11.NewAttribute(pkcs11.CKA_VALUE, cert.Raw),
		}
		if _, err := s.ctx.CreateObject(sh, tmpl); err != nil {
			return fmt.Errorf("create certificate object: %w", err)
		}
		if hasOld {
			if err := s.ctx.DestroyObject(sh, oldHandle); err != nil {
				return fmt.Errorf("destroy superseded certificate object: %w", err)
			}
		}
		return nil
	})
}
