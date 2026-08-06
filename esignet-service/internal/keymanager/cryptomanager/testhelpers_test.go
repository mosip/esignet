package cryptomanager_test

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
	"sort"
	"sync"
	"testing"
	"time"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
	"github.com/mosip/esignet/internal/keymanager/db"
	"github.com/mosip/esignet/internal/keymanager/keystore"
)

// memQuerier is a stateful in-memory db.Querier, distinct from
// internal/keymanager's own stateless func-field fakeQuerier: cryptomanager
// tests need real persistence (a key generated through keymanager.Service
// must then be resolvable by cryptomanager.Service against the same rows,
// including by certificate thumbprint), which a func-field fake can't
// express without duplicating that state by hand.
type memQuerier struct {
	mu       sync.Mutex
	aliases  map[string]db.KeyAlias
	stores   map[string]db.KeyStoreRecord
	policies map[string]db.KeyPolicy
}

func newMemQuerier() *memQuerier {
	return &memQuerier{
		aliases:  map[string]db.KeyAlias{},
		stores:   map[string]db.KeyStoreRecord{},
		policies: map[string]db.KeyPolicy{},
	}
}

func (q *memQuerier) seedPolicy(p db.KeyPolicy) {
	q.mu.Lock()
	defer q.mu.Unlock()
	q.policies[p.AppID] = p
}

func (q *memQuerier) GetKeyAliasesByAppRef(_ context.Context, appID, refID string) ([]db.KeyAlias, error) {
	q.mu.Lock()
	defer q.mu.Unlock()
	var rows []db.KeyAlias
	for _, a := range q.aliases {
		if a.AppID == appID && refEquals(a.RefID, refID) {
			rows = append(rows, a)
		}
	}
	sort.Slice(rows, func(i, j int) bool {
		return genTime(rows[i]).After(genTime(rows[j]))
	})
	return rows, nil
}

func (q *memQuerier) GetKeyAliasByCertThumbprint(_ context.Context, thumbprintHex string) (db.KeyAlias, error) {
	q.mu.Lock()
	defer q.mu.Unlock()
	var best *db.KeyAlias
	for i := range q.aliases {
		a := q.aliases[i]
		if a.CertThumbprint != nil && *a.CertThumbprint == thumbprintHex {
			if best == nil || genTime(a).After(genTime(*best)) {
				aCopy := a
				best = &aCopy
			}
		}
	}
	if best == nil {
		return db.KeyAlias{}, sql.ErrNoRows
	}
	return *best, nil
}

func (q *memQuerier) GetKeyAliasByUniIdent(_ context.Context, uniIdent string) (db.KeyAlias, error) {
	q.mu.Lock()
	defer q.mu.Unlock()
	var best *db.KeyAlias
	for i := range q.aliases {
		a := q.aliases[i]
		if a.UniIdent != nil && *a.UniIdent == uniIdent {
			if best == nil || genTime(a).After(genTime(*best)) {
				aCopy := a
				best = &aCopy
			}
		}
	}
	if best == nil {
		return db.KeyAlias{}, sql.ErrNoRows
	}
	return *best, nil
}

func (q *memQuerier) InsertKeyAlias(_ context.Context, k db.KeyAlias) error {
	q.mu.Lock()
	defer q.mu.Unlock()
	if k.UniIdent != nil {
		for _, a := range q.aliases {
			if a.UniIdent != nil && *a.UniIdent == *k.UniIdent {
				return fmt.Errorf("pq: duplicate key value violates unique constraint \"uni_ident_const\" (SQLSTATE 23505)")
			}
		}
	}
	q.aliases[k.ID] = k
	return nil
}

func (q *memQuerier) UpdateKeyAlias(_ context.Context, k db.KeyAlias) error {
	q.mu.Lock()
	defer q.mu.Unlock()
	existing, ok := q.aliases[k.ID]
	if !ok {
		return sql.ErrNoRows
	}
	existing.KeyExpireDtimes = k.KeyExpireDtimes
	existing.StatusCode = k.StatusCode
	existing.CertThumbprint = k.CertThumbprint
	existing.UpdBy = k.UpdBy
	existing.UpdDtimes = k.UpdDtimes
	q.aliases[k.ID] = existing
	return nil
}

func (q *memQuerier) GetKeyPolicy(_ context.Context, appID string) (db.KeyPolicy, error) {
	q.mu.Lock()
	defer q.mu.Unlock()
	p, ok := q.policies[appID]
	if !ok || !p.IsActive {
		return db.KeyPolicy{}, sql.ErrNoRows
	}
	return p, nil
}

func (q *memQuerier) HasKeyPolicy(_ context.Context, appID string) (bool, error) {
	q.mu.Lock()
	defer q.mu.Unlock()
	_, ok := q.policies[appID]
	return ok, nil
}

func (q *memQuerier) GetKeyStoreRecord(_ context.Context, id string) (db.KeyStoreRecord, error) {
	q.mu.Lock()
	defer q.mu.Unlock()
	r, ok := q.stores[id]
	if !ok {
		return db.KeyStoreRecord{}, sql.ErrNoRows
	}
	return r, nil
}

func (q *memQuerier) InsertKeyStoreRecord(_ context.Context, k db.KeyStoreRecord) error {
	q.mu.Lock()
	defer q.mu.Unlock()
	q.stores[k.ID] = k
	return nil
}

func (q *memQuerier) UpdateKeyStoreRecord(_ context.Context, k db.KeyStoreRecord) error {
	q.mu.Lock()
	defer q.mu.Unlock()
	existing, ok := q.stores[k.ID]
	if !ok {
		return sql.ErrNoRows
	}
	existing.CertificateData = k.CertificateData
	existing.PrivateKey = k.PrivateKey
	existing.UpdBy = k.UpdBy
	existing.UpdDtimes = k.UpdDtimes
	q.stores[k.ID] = existing
	return nil
}

func refEquals(rowRefID *string, want string) bool {
	if rowRefID == nil {
		return want == ""
	}
	return *rowRefID == want
}

func genTime(a db.KeyAlias) time.Time {
	if a.KeyGenDtimes == nil {
		return time.Time{}
	}
	return *a.KeyGenDtimes
}

// memKeyStore is an in-memory keystore.KeyStore, duplicating
// internal/keymanager's own test-only fakeKeyStore (unreachable from this
// external test package) rather than reusing it.
type memKeyStore struct {
	mu      sync.Mutex
	keys    map[string]crypto.PrivateKey
	certs   map[string]*x509.Certificate
	symKeys map[string][]byte
}

func newMemKeyStore() *memKeyStore {
	return &memKeyStore{
		keys:    map[string]crypto.PrivateKey{},
		certs:   map[string]*x509.Certificate{},
		symKeys: map[string][]byte{},
	}
}

func (f *memKeyStore) ProviderName() string { return "MEM" }

func (f *memKeyStore) GetPrivateKey(alias string) (crypto.PrivateKey, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	k, ok := f.keys[alias]
	if !ok {
		return nil, fmt.Errorf("not found: %s", alias)
	}
	return k, nil
}

func (f *memKeyStore) GetPublicKey(alias string) (crypto.PublicKey, error) {
	c, err := f.GetCertificate(alias)
	if err != nil {
		return nil, err
	}
	return c.PublicKey, nil
}

func (f *memKeyStore) GetCertificate(alias string) (*x509.Certificate, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	c, ok := f.certs[alias]
	if !ok {
		return nil, fmt.Errorf("not found: %s", alias)
	}
	return c, nil
}

func (f *memKeyStore) GetSymmetricKey(alias string) ([]byte, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	k, ok := f.symKeys[alias]
	if !ok {
		return nil, fmt.Errorf("not found: %s", alias)
	}
	return k, nil
}

func (f *memKeyStore) GetAsymmetricKey(alias string) (*keystore.KeyPairEntry, error) {
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

func (f *memKeyStore) GetAllAlias() ([]string, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	aliases := make([]string, 0, len(f.certs))
	for a := range f.certs {
		aliases = append(aliases, a)
	}
	return aliases, nil
}

func (f *memKeyStore) GenerateAndStoreSymmetricKey(alias string) error {
	key := make([]byte, 32) // AES-256
	if _, err := rand.Read(key); err != nil {
		return err
	}
	f.mu.Lock()
	f.symKeys[alias] = key
	f.mu.Unlock()
	return nil
}

func (f *memKeyStore) GenerateAndStoreAsymmetricKey(alias, signKeyAlias string, params keystore.CertificateParameters, algoName, curveName string) error {
	priv, pub, err := generateKeyPair(algoName, curveName)
	if err != nil {
		return err
	}
	template := certTemplate(params)

	var certDER []byte
	f.mu.Lock()
	if signKeyAlias == alias {
		template.IsCA = true
		template.BasicConstraintsValid = true
		certDER, err = x509.CreateCertificate(rand.Reader, template, template, pub, priv)
	} else {
		signerCert, ok := f.certs[signKeyAlias]
		if !ok {
			f.mu.Unlock()
			return fmt.Errorf("not found: %s", signKeyAlias)
		}
		signerPriv := f.keys[signKeyAlias]
		certDER, err = x509.CreateCertificate(rand.Reader, template, signerCert, pub, signerPriv)
	}
	f.mu.Unlock()
	if err != nil {
		return err
	}
	cert, err := x509.ParseCertificate(certDER)
	if err != nil {
		return err
	}
	f.mu.Lock()
	f.keys[alias] = priv
	f.certs[alias] = cert
	f.mu.Unlock()
	return nil
}

func (f *memKeyStore) DeleteKey(alias string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	delete(f.keys, alias)
	delete(f.certs, alias)
	delete(f.symKeys, alias)
	return nil
}

func (f *memKeyStore) StoreCertificate(alias string, privateKey crypto.PrivateKey, cert *x509.Certificate) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if privateKey != nil {
		f.keys[alias] = privateKey
	}
	f.certs[alias] = cert
	return nil
}

func generateKeyPair(algoName, curveName string) (crypto.Signer, crypto.PublicKey, error) {
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

func certTemplate(params keystore.CertificateParameters) *x509.Certificate {
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
		},
		NotBefore: notBefore,
		NotAfter:  notAfter,
		KeyUsage:  x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment | x509.KeyUsageCertSign,
	}
}

// testEnv bundles a fully-wired keymanager.Service + cryptomanager.Service
// pair sharing the same in-memory querier/keystore, with the ROOT and
// Component Master Key already provisioned for appID — mirroring what a
// real deployment's DB/keystore would already contain before any
// Encrypt/Decrypt call. The Component Encryption Key itself is left
// unprovisioned; Encrypt's first call auto-generates it, same as in
// production (see keymanager.Service.ensureCurrentKey).
type testEnv struct {
	Q     *memQuerier
	KS    *memKeyStore
	KM    *keymanager.Service
	CM    *cryptomanager.Service
	AppID string
}

func newTestEnv(t *testing.T, appID string) *testEnv {
	t.Helper()
	ctx := context.Background()

	q := newMemQuerier()
	q.seedPolicy(db.KeyPolicy{AppID: keymanager.AppIDRoot, KeyValidityDuration: 2920, IsActive: true, PreExpireDays: 30})
	q.seedPolicy(db.KeyPolicy{AppID: appID, KeyValidityDuration: 1095, IsActive: true, PreExpireDays: 60})
	q.seedPolicy(db.KeyPolicy{AppID: "BASE", KeyValidityDuration: 730, IsActive: true, PreExpireDays: 30})

	ks := newMemKeyStore()
	kmCfg := keymanager.Config{
		AsymmetricKeyLength: 2048,
		CertCommonName:      "test.mosip.io",
	}
	km := keymanager.NewServiceWithQuerier(q, ks, kmCfg)

	if _, err := km.GenerateMasterKey(ctx, keymanager.GenerateMasterKeyRequest{ApplicationID: keymanager.AppIDRoot}); err != nil {
		t.Fatalf("generate ROOT key: %v", err)
	}
	if _, err := km.GenerateMasterKey(ctx, keymanager.GenerateMasterKeyRequest{ApplicationID: appID, ReferenceID: keymanager.RefIDRSA2048}); err != nil {
		t.Fatalf("generate component master key: %v", err)
	}

	cmCfg := cryptomanager.LoadConfig()
	cm := cryptomanager.NewService(q, km, cmCfg)

	return &testEnv{Q: q, KS: ks, KM: km, CM: cm, AppID: appID}
}
