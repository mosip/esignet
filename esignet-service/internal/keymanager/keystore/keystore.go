// Package keystore is the vendor-agnostic key-storage port for the
// keymanager service, with PKCS#11 (HSM/SoftHSM) and PKCS#12 (software
// keystore file) backends selected by config. There is no offline/stub
// backend and no reflection-based custom-driver loading — PKCS#11 and
// PKCS#12 are the only two supported keystore types.
package keystore

import (
	"crypto"
	"crypto/x509"
	"fmt"
	"time"
)

// CertificateParameters carries the subject/validity fields needed to build
// a self-signed or CA-signed certificate for a generated key pair.
type CertificateParameters struct {
	CommonName       string
	OrganizationUnit string
	Organization     string
	Location         string
	State            string
	Country          string
	NotBefore        time.Time
	NotAfter         time.Time
}

// KeyPairEntry bundles a private key with its certificate (and any
// intermediate chain), analogous to java.security.KeyStore.PrivateKeyEntry.
type KeyPairEntry struct {
	PrivateKey  crypto.PrivateKey
	Certificate *x509.Certificate
	Chain       []*x509.Certificate
}

// Algorithm names accepted by GenerateAndStoreAsymmetricKey's algoName parameter.
const (
	AlgoRSA = "RSA"
	AlgoEC  = "EC"
)

// Curve names accepted by GenerateAndStoreAsymmetricKey's curveName parameter
// when algoName == AlgoEC. Empty string means RSA (no curve).
const (
	CurveSECP256K1 = "SECP256K1"
	CurveSECP256R1 = "SECP256R1"
	CurveED25519   = "ED25519"
)

// KeyStore is the port the keymanager service programs against; PKCS#11 and
// PKCS#12 adapters implement it identically from the caller's perspective.
type KeyStore interface {
	GetPrivateKey(alias string) (crypto.PrivateKey, error)
	GetPublicKey(alias string) (crypto.PublicKey, error)
	GetCertificate(alias string) (*x509.Certificate, error)
	GetSymmetricKey(alias string) ([]byte, error)
	GetAsymmetricKey(alias string) (*KeyPairEntry, error)
	GetAllAlias() ([]string, error)

	GenerateAndStoreSymmetricKey(alias string) error

	// GenerateAndStoreAsymmetricKey generates and stores an asymmetric key
	// pair plus a certificate signed by signKeyAlias (or self-signed when
	// signKeyAlias == alias).
	//   algoName:  AlgoRSA | AlgoEC
	//   curveName: "" for RSA; CurveSECP256K1 | CurveSECP256R1 | CurveED25519 when algoName == AlgoEC
	GenerateAndStoreAsymmetricKey(alias, signKeyAlias string, params CertificateParameters, algoName, curveName string) error

	DeleteKey(alias string) error
	StoreCertificate(alias string, privateKey crypto.PrivateKey, cert *x509.Certificate) error
	ProviderName() string
}

// Factory constructs a KeyStore backend from its configuration parameters.
type Factory func(params map[string]string) (KeyStore, error)

var registry = map[string]Factory{}

// Register adds a backend factory to the registry. Called from each
// backend's init() (pkcs11, pkcs12) so the keystore package can reference
// them without an import cycle (the backends import keystore for the
// KeyStore interface and shared types, not the other way around) — this is
// the compile-time equivalent of the plan's factory registry, just wired
// via package init instead of a literal map naming the subpackages.
func Register(name string, f Factory) { registry[name] = f }

// New constructs the configured keystore backend. keystoreType must be
// exactly "PKCS11" or "PKCS12" — there is no fallback for an unknown value.
func New(keystoreType string, params map[string]string) (KeyStore, error) {
	f, ok := registry[keystoreType]
	if !ok {
		return nil, fmt.Errorf("unsupported keystore type %q: only PKCS11 and PKCS12 are supported", keystoreType)
	}
	return f(params)
}
