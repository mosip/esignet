package cryptomanager_test

import (
	"context"
	"testing"
	"time"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
	"github.com/mosip/esignet/internal/keymanager/db"
)

// newBareCryptomanagerService builds a cryptomanager.Service directly on a
// memQuerier with hand-seeded rows, for precise control over
// resolveDecryptionKey's edge cases — unlike newTestEnv, it does not drive
// key generation through keymanager.Service's public API, since none of
// these cases need real key material (each is rejected before
// resolveDecryptionKey would ever call km.ResolveKeyMaterial).
func newBareCryptomanagerService(t *testing.T) (*cryptomanager.Service, *memQuerier) {
	t.Helper()
	q := newMemQuerier()
	ks := newMemKeyStore()
	km := keymanager.NewServiceWithQuerier(q, ks, keymanager.Config{})
	return cryptomanager.NewService(q, km, cryptomanager.LoadConfig()), q
}

func strPtr(s string) *string { return &s }

func (ts *CryptomanagerTestSuite) TestResolveDecryptionKey_ThumbprintNotFound() {
	svc, _ := newBareCryptomanagerService(ts.T())
	_, _, err := cryptomanager.ResolveDecryptionKey(svc, context.Background(), "TESTAPP", "ENC_KEY", "deadbeef")
	ts.Require().ErrorIs(err, cryptomanager.ErrKeyNotFoundForThumbprint)
}

func (ts *CryptomanagerTestSuite) TestResolveDecryptionKey_AppRefMismatch() {
	svc, q := newBareCryptomanagerService(ts.T())
	now := time.Now().UTC()
	ts.Require().NoError(q.InsertKeyAlias(context.Background(), db.KeyAlias{
		ID: "alias-1", AppID: "OTHERAPP", RefID: strPtr("OTHER_ENC_KEY"),
		KeyGenDtimes: &now, CertThumbprint: strPtr("aabbcc"), UniIdent: strPtr("u1"),
	}))

	_, _, err := cryptomanager.ResolveDecryptionKey(svc, context.Background(), "TESTAPP", "ENC_KEY", "aabbcc")
	ts.Require().ErrorIs(err, cryptomanager.ErrKeyIdentifierMismatch)
	ts.Require().NotContains(err.Error(), "OTHERAPP")
	ts.Require().NotContains(err.Error(), "OTHER_ENC_KEY")
}

func (ts *CryptomanagerTestSuite) TestResolveDecryptionKey_RejectsKeystoreResidentResult() {
	svc, q := newBareCryptomanagerService(ts.T())
	now := time.Now().UTC()
	// A Component Master Key row (RefID=RSA_2048) — keystore-resident.
	ts.Require().NoError(q.InsertKeyAlias(context.Background(), db.KeyAlias{
		ID: "master-alias", AppID: "TESTAPP", RefID: strPtr(keymanager.RefIDRSA2048),
		KeyGenDtimes: &now, CertThumbprint: strPtr("ffeedd"), UniIdent: strPtr("u2"),
	}))

	_, _, err := cryptomanager.ResolveDecryptionKey(svc, context.Background(), "TESTAPP", keymanager.RefIDRSA2048, "ffeedd")
	ts.Require().ErrorIs(err, cryptomanager.ErrDecryptionNotAllowed)
	ts.Require().NotContains(err.Error(), "master-alias")
}

func (ts *CryptomanagerTestSuite) TestResolveDecryptionKey_RejectsForeignDomainKey() {
	svc, q := newBareCryptomanagerService(ts.T())
	now := time.Now().UTC()
	ts.Require().NoError(q.InsertKeyAlias(context.Background(), db.KeyAlias{
		ID: "foreign-alias", AppID: "PARTNER", RefID: strPtr("RSA_2048"),
		KeyGenDtimes: &now, CertThumbprint: strPtr("112233"), UniIdent: strPtr("u3"),
	}))
	ts.Require().NoError(q.InsertKeyStoreRecord(context.Background(), db.KeyStoreRecord{
		ID: "foreign-alias", CertificateData: "irrelevant", PrivateKey: "NA", MasterKey: strPtr("foreign-alias"),
	}))

	// PARTNER/RSA_2048 is keystore-resident by keymanager's own rules
	// (RefID==RSA_2048), so this actually hits ErrDecryptionNotAllowed
	// before the "NA" check — demonstrating that a foreign-domain upload
	// under RSA_2048 is caught by the same defensive check as a real
	// Component Master Key. See the non-master-refID case below for the
	// "NA" check itself.
	_, _, err := cryptomanager.ResolveDecryptionKey(svc, context.Background(), "PARTNER", "RSA_2048", "112233")
	ts.Require().ErrorIs(err, cryptomanager.ErrDecryptionNotAllowed)
}

func (ts *CryptomanagerTestSuite) TestResolveDecryptionKey_RejectsForeignDomainKey_NonMasterRefID() {
	svc, q := newBareCryptomanagerService(ts.T())
	now := time.Now().UTC()
	ts.Require().NoError(q.InsertKeyAlias(context.Background(), db.KeyAlias{
		ID: "foreign-alias-2", AppID: "PARTNER", RefID: strPtr("SOME_CERT"),
		KeyGenDtimes: &now, CertThumbprint: strPtr("445566"), UniIdent: strPtr("u4"),
	}))
	ts.Require().NoError(q.InsertKeyStoreRecord(context.Background(), db.KeyStoreRecord{
		ID: "foreign-alias-2", CertificateData: "irrelevant", PrivateKey: "NA", MasterKey: strPtr("foreign-alias-2"),
	}))

	_, _, err := cryptomanager.ResolveDecryptionKey(svc, context.Background(), "PARTNER", "SOME_CERT", "445566")
	ts.Require().ErrorIs(err, cryptomanager.ErrForeignDomainKeyNotDecryptable)
}
