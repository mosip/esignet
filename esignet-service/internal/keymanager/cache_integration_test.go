package keymanager_test

import (
	"context"
	"crypto/x509"
	"time"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/db"
	"github.com/mosip/esignet/internal/keymanager/keystore"
)

// countingKeyStore wraps fakeKeyStore, counting calls to the keystore
// operations GetSigningCertificate/ResolveCurrentKey/
// ResolveCurrentSymmetricKey's caches exist to avoid repeating —
// GetAsymmetricKey (signing), GetCertificate (encryption cert resolution),
// and GetSymmetricKey (AES). Mirrors finding #2's PKCS#11/PKCS#12 concern: a
// cache hit at the keymanager.Service layer must mean these are never
// called at all.
type countingKeyStore struct {
	*fakeKeyStore
	getAsymmetricKeyCalls int
	getCertificateCalls   int
	getSymmetricKeyCalls  int
}

func newCountingKeyStore() *countingKeyStore {
	return &countingKeyStore{fakeKeyStore: newFakeKeyStore()}
}

func (k *countingKeyStore) GetAsymmetricKey(alias string) (*keystore.KeyPairEntry, error) {
	k.getAsymmetricKeyCalls++
	return k.fakeKeyStore.GetAsymmetricKey(alias)
}

func (k *countingKeyStore) GetCertificate(alias string) (*x509.Certificate, error) {
	k.getCertificateCalls++
	return k.fakeKeyStore.GetCertificate(alias)
}

func (k *countingKeyStore) GetSymmetricKey(alias string) ([]byte, error) {
	k.getSymmetricKeyCalls++
	return k.fakeKeyStore.GetSymmetricKey(alias)
}

// countingAliasQuerier wraps fakeQuerier, counting GetKeyAliasesByAppRef/
// GetKeyPolicy calls — the DB round trips finding #1 identified as
// uncached, ≥3-per-signature overhead on the hot path.
type countingAliasQuerier struct {
	*fakeQuerier
	getKeyAliasesByAppRefCalls int
	getKeyPolicyCalls          int
}

func newCountingAliasQuerier(aliases []db.KeyAlias, policy db.KeyPolicy) *countingAliasQuerier {
	q := &countingAliasQuerier{}
	q.fakeQuerier = &fakeQuerier{
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			q.getKeyAliasesByAppRefCalls++
			return aliases, nil
		},
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) {
			q.getKeyPolicyCalls++
			return policy, nil
		},
	}
	return q
}

func cachingTestConfig() keymanager.Config {
	cfg := testConfig()
	cfg.KeyCacheExpiry = time.Hour
	return cfg
}

func (ts *KeymanagerTestSuite) TestGetSigningCertificate_CachesResolution() {
	ks := newCountingKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	q := newCountingAliasQuerier([]db.KeyAlias{validAliasRow("root-alias")}, alwaysActivePolicy())
	svc := keymanager.NewServiceWithQuerier(q, ks, cachingTestConfig())

	_, err := svc.GetSigningCertificate(context.Background(), "ROOT", "")
	ts.Require().NoError(err)
	firstDBCalls := q.getKeyAliasesByAppRefCalls
	firstKeystoreCalls := ks.getAsymmetricKeyCalls
	ts.Require().Positive(firstDBCalls)
	ts.Require().Equal(1, firstKeystoreCalls)

	_, err = svc.GetSigningCertificate(context.Background(), "ROOT", "")
	ts.Require().NoError(err)
	ts.Assert().Equal(firstDBCalls, q.getKeyAliasesByAppRefCalls, "second call must be served from cache, not re-query the DB")
	ts.Assert().Equal(firstKeystoreCalls, ks.getAsymmetricKeyCalls, "second call must not re-resolve the keystore/HSM material either")
}

func (ts *KeymanagerTestSuite) TestGetSigningCertificate_KeyCacheExpiryDisabled_ResolvesFreshEveryCall() {
	ks := newCountingKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	q := newCountingAliasQuerier([]db.KeyAlias{validAliasRow("root-alias")}, alwaysActivePolicy())
	cfg := testConfig() // KeyCacheExpiry left at its zero value
	svc := keymanager.NewServiceWithQuerier(q, ks, cfg)

	_, err := svc.GetSigningCertificate(context.Background(), "ROOT", "")
	ts.Require().NoError(err)
	_, err = svc.GetSigningCertificate(context.Background(), "ROOT", "")
	ts.Require().NoError(err)

	ts.Assert().Equal(2, ks.getAsymmetricKeyCalls, "KeyCacheExpiry <= 0 must disable caching entirely")
}

func (ts *KeymanagerTestSuite) TestGetSigningCertificate_CacheInvalidatedOnRevoke() {
	ks := newCountingKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("comp-alias", "root-alias", testCertTemplateParams(), "EC", "SECP256R1"))
	rootRow := validAliasRow("root-alias") // ROOT itself is never revoked in this test — stays current throughout
	var compRow *db.KeyAlias
	row := validAliasRow("comp-alias")
	compRow = &row
	updateCalls := 0
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, appID, _ string) ([]db.KeyAlias, error) {
			if appID == "ROOT" {
				return []db.KeyAlias{rootRow}, nil
			}
			if compRow == nil {
				return nil, nil
			}
			return []db.KeyAlias{*compRow}, nil
		},
		updateKeyAliasFn: func(_ context.Context, k db.KeyAlias) error {
			updateCalls++
			compRow = &k // RevokeKey's expiry update is now visible to the next uncached resolve
			return nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, cachingTestConfig())
	ctx := context.Background()

	_, err := svc.GetSigningCertificate(ctx, "ESIGNET_RSA", "EC_SECP256R1_SIGN")
	ts.Require().NoError(err)
	ts.Require().Equal(1, ks.getAsymmetricKeyCalls, "sanity: first call resolves and caches")

	_, err = svc.RevokeKey(ctx, keymanager.RevokeKeyRequest{ApplicationID: "ESIGNET_RSA", ReferenceID: "EC_SECP256R1_SIGN"})
	ts.Require().NoError(err)
	ts.Require().Equal(1, updateCalls)

	// compRow now has an expired KeyExpireDtimes, so a cache-bypassing
	// resolve must generate a new key rather than reuse the revoked one.
	sc, err := svc.GetSigningCertificate(ctx, "ESIGNET_RSA", "EC_SECP256R1_SIGN")
	ts.Require().NoError(err)
	ts.Assert().Equal(2, ks.getAsymmetricKeyCalls, "RevokeKey must invalidate the cache so the next call resolves fresh, not from a stale cached entry")
	ts.Assert().NotEqual("comp-alias", sc.Alias, "the revoked alias must not still be served as current")
}

func (ts *KeymanagerTestSuite) TestResolveCurrentKey_CachesResolution() {
	ks := newCountingKeyStore()
	rootParams := testCertTemplateParams()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", rootParams, "RSA", ""))
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("master-alias", "root-alias", rootParams, "RSA", ""))
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, appID, _ string) ([]db.KeyAlias, error) {
			if appID == "ROOT" {
				return []db.KeyAlias{validAliasRow("root-alias")}, nil
			}
			return []db.KeyAlias{validAliasRow("master-alias")}, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, cachingTestConfig())
	ctx := context.Background()

	_, _, _, err := svc.ResolveCurrentKey(ctx, "ESIGNET_RSA", "RSA_2048")
	ts.Require().NoError(err)
	firstCalls := ks.getCertificateCalls
	ts.Require().Positive(firstCalls)

	_, _, _, err = svc.ResolveCurrentKey(ctx, "ESIGNET_RSA", "RSA_2048")
	ts.Require().NoError(err)
	ts.Assert().Equal(firstCalls, ks.getCertificateCalls, "second call must be served from cache")
}

func (ts *KeymanagerTestSuite) TestResolveCurrentSymmetricKey_CachesResolution() {
	ks := newCountingKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreSymmetricKey("sym-alias"))
	q := newCountingAliasQuerier([]db.KeyAlias{validAliasRow("sym-alias")}, alwaysActivePolicy())
	svc := keymanager.NewServiceWithQuerier(q, ks, cachingTestConfig())
	ctx := context.Background()

	_, _, _, err := svc.ResolveCurrentSymmetricKey(ctx, "ESIGNET", "CACHE_ENCRYPT")
	ts.Require().NoError(err)
	ts.Require().Equal(1, ks.getSymmetricKeyCalls)
	firstDBCalls := q.getKeyAliasesByAppRefCalls

	_, _, _, err = svc.ResolveCurrentSymmetricKey(ctx, "ESIGNET", "CACHE_ENCRYPT")
	ts.Require().NoError(err)
	ts.Assert().Equal(1, ks.getSymmetricKeyCalls, "second call must be served from cache")
	ts.Assert().Equal(firstDBCalls, q.getKeyAliasesByAppRefCalls)
}

func (ts *KeymanagerTestSuite) TestResolveCurrentSymmetricKey_CacheInvalidatedOnForceRegenerate() {
	ks := newCountingKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreSymmetricKey("sym-alias-1"))
	var currentRow *db.KeyAlias
	row := validAliasRow("sym-alias-1")
	currentRow = &row
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			if currentRow == nil {
				return nil, nil
			}
			return []db.KeyAlias{*currentRow}, nil
		},
		insertKeyAliasFn: func(_ context.Context, k db.KeyAlias) error {
			currentRow = &k
			return nil
		},
	}
	cfg := cachingTestConfig()
	cfg.SymmetricKeyAllowedRefIDs = []string{"CACHE_ENCRYPT"}
	svc := keymanager.NewServiceWithQuerier(q, ks, cfg)
	ctx := context.Background()

	_, _, uniIdent1, err := svc.ResolveCurrentSymmetricKey(ctx, "ESIGNET", "CACHE_ENCRYPT")
	ts.Require().NoError(err)

	_, err = svc.GenerateSymmetricKey(ctx, keymanager.SymmetricKeyRequest{ApplicationID: "ESIGNET", ReferenceID: "CACHE_ENCRYPT", Force: true})
	ts.Require().NoError(err)

	_, _, uniIdent2, err := svc.ResolveCurrentSymmetricKey(ctx, "ESIGNET", "CACHE_ENCRYPT")
	ts.Require().NoError(err)
	ts.Assert().NotEqual(uniIdent1, uniIdent2, "forced regeneration must invalidate the cache so the next resolve sees the new key")
}

// keystore.KeyStore compile-time assertion — countingKeyStore must satisfy
// the same interface fakeKeyStore does, since it's substituted directly for
// it in every test above.
var _ keystore.KeyStore = (*countingKeyStore)(nil)
