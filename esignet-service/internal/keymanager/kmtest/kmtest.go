// Package kmtest provides shared hand-written test fakes for keymanager's
// db.Querier and keystore.KeyStore ports, for use by _test.go files in
// keymanager's sibling packages (signature, engine/mosip, ...) that need to
// exercise keymanager.Service end to end without a real database or HSM.
//
// These were previously copied independently into each consuming package's
// own fakes_test.go (unexported types in a _test.go file can't be imported
// across packages), which let the copies drift. This package is the single
// source of truth instead.
package kmtest

import (
	"context"
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"database/sql"
	"fmt"
	"math/big"
	"sync"
	"time"

	"github.com/mosip/esignet/internal/keymanager/db"
	"github.com/mosip/esignet/internal/keymanager/keystore"
)

// StateQuerier is a minimal, hand-written, stateful in-memory implementation
// of db.Querier that actually persists inserted key_alias rows across calls,
// since exercising keymanager.Service (and its consumers, e.g. signature and
// mosip) end to end requires a realistic multi-step sequence:
// GenerateMasterKey(ROOT), then GenerateMasterKey(component key), then a
// sign/verify or IDA-request-signing round trip against it.
type StateQuerier struct {
	mu      sync.Mutex
	aliases map[string][]db.KeyAlias // key: appID+"|"+refID, index 0 = most recently inserted
}

// NewStateQuerier constructs an empty StateQuerier.
func NewStateQuerier() *StateQuerier {
	return &StateQuerier{aliases: map[string][]db.KeyAlias{}}
}

func aliasMapKey(appID, refID string) string { return appID + "|" + refID }

func (q *StateQuerier) GetKeyAliasesByAppRef(_ context.Context, appID, refID string) ([]db.KeyAlias, error) {
	q.mu.Lock()
	defer q.mu.Unlock()
	return append([]db.KeyAlias(nil), q.aliases[aliasMapKey(appID, refID)]...), nil
}

func (q *StateQuerier) GetKeyAliasByCertThumbprint(_ context.Context, _ string) (db.KeyAlias, error) {
	return db.KeyAlias{}, sql.ErrNoRows
}

func (q *StateQuerier) GetKeyAliasByUniIdent(_ context.Context, _ string) (db.KeyAlias, error) {
	return db.KeyAlias{}, sql.ErrNoRows
}

func (q *StateQuerier) InsertKeyAlias(_ context.Context, k db.KeyAlias) error {
	q.mu.Lock()
	defer q.mu.Unlock()
	refID := ""
	if k.RefID != nil {
		refID = *k.RefID
	}
	key := aliasMapKey(k.AppID, refID)
	q.aliases[key] = append([]db.KeyAlias{k}, q.aliases[key]...)
	return nil
}

func (q *StateQuerier) UpdateKeyAlias(_ context.Context, k db.KeyAlias) error {
	q.mu.Lock()
	defer q.mu.Unlock()
	refID := ""
	if k.RefID != nil {
		refID = *k.RefID
	}
	key := aliasMapKey(k.AppID, refID)
	for i, existing := range q.aliases[key] {
		if existing.ID == k.ID {
			q.aliases[key][i] = k
			return nil
		}
	}
	return nil
}

// GetKeyPolicy always returns a permissive, active policy — sufficient for
// tests that don't exercise policy-driven rejection paths (that's covered by
// internal/keymanager's own test suite).
func (q *StateQuerier) GetKeyPolicy(_ context.Context, _ string) (db.KeyPolicy, error) {
	return db.KeyPolicy{KeyValidityDuration: 3650, PreExpireDays: 30, IsActive: true}, nil
}

// HasKeyPolicy mirrors GetKeyPolicy's permissiveness: every appID is
// "registered" in these tests.
func (q *StateQuerier) HasKeyPolicy(_ context.Context, _ string) (bool, error) {
	return true, nil
}

func (q *StateQuerier) GetKeyStoreRecord(_ context.Context, _ string) (db.KeyStoreRecord, error) {
	return db.KeyStoreRecord{}, sql.ErrNoRows
}

func (q *StateQuerier) InsertKeyStoreRecord(_ context.Context, _ db.KeyStoreRecord) error { return nil }
func (q *StateQuerier) UpdateKeyStoreRecord(_ context.Context, _ db.KeyStoreRecord) error { return nil }

// FakeKeyStore is a hand-written in-memory keystore.KeyStore — real crypto
// key generation and real x509.CreateCertificate calls, so sign/verify or
// encrypt/decrypt round trips exercise genuine cryptography rather than
// mocked behavior.
type FakeKeyStore struct {
	keys  map[string]crypto.PrivateKey
	certs map[string]*x509.Certificate
}

// NewFakeKeyStore constructs an empty FakeKeyStore.
func NewFakeKeyStore() *FakeKeyStore {
	return &FakeKeyStore{keys: map[string]crypto.PrivateKey{}, certs: map[string]*x509.Certificate{}}
}

func (f *FakeKeyStore) ProviderName() string { return "FAKE" }

func (f *FakeKeyStore) Close() error { return nil }

func (f *FakeKeyStore) GetPrivateKey(alias string) (crypto.PrivateKey, error) {
	k, ok := f.keys[alias]
	if !ok {
		return nil, errNotFound(alias)
	}
	return k, nil
}

func (f *FakeKeyStore) GetPublicKey(alias string) (crypto.PublicKey, error) {
	c, ok := f.certs[alias]
	if !ok {
		return nil, errNotFound(alias)
	}
	return c.PublicKey, nil
}

func (f *FakeKeyStore) GetCertificate(alias string) (*x509.Certificate, error) {
	c, ok := f.certs[alias]
	if !ok {
		return nil, errNotFound(alias)
	}
	return c, nil
}

func (f *FakeKeyStore) GetSymmetricKey(_ string) ([]byte, error) {
	return nil, fmt.Errorf("kmtest.FakeKeyStore: symmetric keys not supported")
}

func (f *FakeKeyStore) GetAsymmetricKey(alias string) (*keystore.KeyPairEntry, error) {
	priv, err := f.GetPrivateKey(alias)
	if err != nil {
		return nil, err
	}
	cert, err := f.GetCertificate(alias)
	if err != nil {
		return nil, err
	}
	return &keystore.KeyPairEntry{PrivateKey: priv, Certificate: cert}, nil
}

func (f *FakeKeyStore) GetAllAlias() ([]string, error) {
	aliases := make([]string, 0, len(f.certs))
	for a := range f.certs {
		aliases = append(aliases, a)
	}
	return aliases, nil
}

func (f *FakeKeyStore) GenerateAndStoreSymmetricKey(_ string) error {
	return fmt.Errorf("kmtest.FakeKeyStore: symmetric keys not supported")
}

func (f *FakeKeyStore) GenerateAndStoreAsymmetricKey(alias, signKeyAlias string, params keystore.CertificateParameters, algoName, curveName string) error {
	priv, pub, err := GenerateTestKeyPair(algoName, curveName)
	if err != nil {
		return err
	}
	template := TestCertTemplate(params)
	var certDER []byte
	if signKeyAlias == alias {
		template.IsCA = true
		template.BasicConstraintsValid = true
		certDER, err = x509.CreateCertificate(rand.Reader, template, template, pub, priv)
	} else {
		signerCert, ok := f.certs[signKeyAlias]
		if !ok {
			return errNotFound(signKeyAlias)
		}
		signerPriv := f.keys[signKeyAlias]
		certDER, err = x509.CreateCertificate(rand.Reader, template, signerCert, pub, signerPriv)
	}
	if err != nil {
		return err
	}
	cert, err := x509.ParseCertificate(certDER)
	if err != nil {
		return err
	}
	f.keys[alias] = priv
	f.certs[alias] = cert
	return nil
}

func (f *FakeKeyStore) DeleteKey(alias string) error {
	delete(f.keys, alias)
	delete(f.certs, alias)
	return nil
}

func (f *FakeKeyStore) StoreCertificate(alias string, privateKey crypto.PrivateKey, cert *x509.Certificate) error {
	if privateKey != nil {
		f.keys[alias] = privateKey
	}
	f.certs[alias] = cert
	return nil
}

type notFoundErr string

func (e notFoundErr) Error() string  { return "not found: " + string(e) }
func errNotFound(alias string) error { return notFoundErr(alias) }

// GenerateTestKeyPair generates a fresh key pair for algoName/curveName.
// SECP256K1 falls through to the P256 default: real SECP256K1 certificate
// generation is unsupported by both keystore backends in this Go port
// (crypto/x509's curve-OID table only recognizes NIST curves — see
// keystore/pkcs11/keys.go's errSECP256K1Unsupported), so ES256K's
// signing/verification *logic* is tested via the shared ES256 code path
// instead.
func GenerateTestKeyPair(algoName, curveName string) (crypto.Signer, crypto.PublicKey, error) {
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

// TestCertTemplate builds a certificate template from params, defaulting
// NotBefore/NotAfter to a one-year window from now when unset.
func TestCertTemplate(params keystore.CertificateParameters) *x509.Certificate {
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
