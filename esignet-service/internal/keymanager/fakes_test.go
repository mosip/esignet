package keymanager_test

import (
	"context"
	"crypto"
	"crypto/x509"

	"github.com/mosip/esignet/internal/keymanager/db"
	"github.com/mosip/esignet/internal/keymanager/keystore"
)

// fakeQuerier is a hand-written fake implementing db.Querier, mirroring the
// mockQuerier convention in internal/clientmgmt/service_test.go: a struct of
// function fields set per test case. Unset fields return zero values
// (nil error) rather than panicking, to keep test cases terse.
type fakeQuerier struct {
	getKeyAliasesByAppRefFn func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error)
	insertKeyAliasFn        func(ctx context.Context, k db.KeyAlias) error
	updateKeyAliasFn        func(ctx context.Context, k db.KeyAlias) error
	getKeyPolicyFn          func(ctx context.Context, appID string) (db.KeyPolicy, error)
	getKeyStoreRecordFn     func(ctx context.Context, id string) (db.KeyStoreRecord, error)
	insertKeyStoreRecordFn  func(ctx context.Context, k db.KeyStoreRecord) error
	updateKeyStoreRecordFn  func(ctx context.Context, k db.KeyStoreRecord) error
}

func (f *fakeQuerier) GetKeyAliasesByAppRef(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
	if f.getKeyAliasesByAppRefFn == nil {
		return nil, nil
	}
	return f.getKeyAliasesByAppRefFn(ctx, appID, refID)
}

func (f *fakeQuerier) InsertKeyAlias(ctx context.Context, k db.KeyAlias) error {
	if f.insertKeyAliasFn == nil {
		return nil
	}
	return f.insertKeyAliasFn(ctx, k)
}

func (f *fakeQuerier) UpdateKeyAlias(ctx context.Context, k db.KeyAlias) error {
	if f.updateKeyAliasFn == nil {
		return nil
	}
	return f.updateKeyAliasFn(ctx, k)
}

func (f *fakeQuerier) GetKeyPolicy(ctx context.Context, appID string) (db.KeyPolicy, error) {
	if f.getKeyPolicyFn == nil {
		return db.KeyPolicy{}, nil
	}
	return f.getKeyPolicyFn(ctx, appID)
}

func (f *fakeQuerier) GetKeyStoreRecord(ctx context.Context, id string) (db.KeyStoreRecord, error) {
	if f.getKeyStoreRecordFn == nil {
		return db.KeyStoreRecord{}, nil
	}
	return f.getKeyStoreRecordFn(ctx, id)
}

func (f *fakeQuerier) InsertKeyStoreRecord(ctx context.Context, k db.KeyStoreRecord) error {
	if f.insertKeyStoreRecordFn == nil {
		return nil
	}
	return f.insertKeyStoreRecordFn(ctx, k)
}

func (f *fakeQuerier) UpdateKeyStoreRecord(ctx context.Context, k db.KeyStoreRecord) error {
	if f.updateKeyStoreRecordFn == nil {
		return nil
	}
	return f.updateKeyStoreRecordFn(ctx, k)
}

// fakeKeyStore is a hand-written fake implementing keystore.KeyStore,
// in-memory, sufficient for exercising service.go's control flow without a
// real PKCS#11/PKCS#12 backend.
type fakeKeyStore struct {
	keys  map[string]crypto.PrivateKey
	certs map[string]*x509.Certificate
	syms  map[string][]byte
}

func newFakeKeyStore() *fakeKeyStore {
	return &fakeKeyStore{
		keys:  map[string]crypto.PrivateKey{},
		certs: map[string]*x509.Certificate{},
		syms:  map[string][]byte{},
	}
}

func (f *fakeKeyStore) ProviderName() string { return "FAKE" }

func (f *fakeKeyStore) GetPrivateKey(alias string) (crypto.PrivateKey, error) {
	k, ok := f.keys[alias]
	if !ok {
		return nil, errNotFound(alias)
	}
	return k, nil
}

func (f *fakeKeyStore) GetPublicKey(alias string) (crypto.PublicKey, error) {
	c, ok := f.certs[alias]
	if !ok {
		return nil, errNotFound(alias)
	}
	return c.PublicKey, nil
}

func (f *fakeKeyStore) GetCertificate(alias string) (*x509.Certificate, error) {
	c, ok := f.certs[alias]
	if !ok {
		return nil, errNotFound(alias)
	}
	return c, nil
}

func (f *fakeKeyStore) GetSymmetricKey(alias string) ([]byte, error) {
	k, ok := f.syms[alias]
	if !ok {
		return nil, errNotFound(alias)
	}
	return k, nil
}

func (f *fakeKeyStore) GetAsymmetricKey(alias string) (*keystore.KeyPairEntry, error) {
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

func (f *fakeKeyStore) GetAllAlias() ([]string, error) {
	aliases := make([]string, 0, len(f.certs))
	for a := range f.certs {
		aliases = append(aliases, a)
	}
	return aliases, nil
}

func (f *fakeKeyStore) GenerateAndStoreSymmetricKey(alias string) error {
	f.syms[alias] = []byte("fake-symmetric-key-bytes-000000")
	return nil
}

func (f *fakeKeyStore) GenerateAndStoreAsymmetricKey(alias, signKeyAlias string, params keystore.CertificateParameters, algoName, curveName string) error {
	priv, pub, err := generateTestKeyPair(algoName, curveName)
	if err != nil {
		return err
	}
	template := testCertTemplate(params)
	var certDER []byte
	if signKeyAlias == alias {
		template.IsCA = true
		template.BasicConstraintsValid = true
		certDER, err = x509.CreateCertificate(testRandReader(), template, template, pub, priv)
	} else {
		signerCert, ok := f.certs[signKeyAlias]
		if !ok {
			return errNotFound(signKeyAlias)
		}
		signerPriv := f.keys[signKeyAlias]
		certDER, err = x509.CreateCertificate(testRandReader(), template, signerCert, pub, signerPriv)
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

func (f *fakeKeyStore) DeleteKey(alias string) error {
	delete(f.keys, alias)
	delete(f.certs, alias)
	delete(f.syms, alias)
	return nil
}

func (f *fakeKeyStore) StoreCertificate(alias string, privateKey crypto.PrivateKey, cert *x509.Certificate) error {
	if privateKey != nil {
		f.keys[alias] = privateKey
	}
	f.certs[alias] = cert
	return nil
}

type notFoundErr string

func (e notFoundErr) Error() string  { return "not found: " + string(e) }
func errNotFound(alias string) error { return notFoundErr(alias) }
