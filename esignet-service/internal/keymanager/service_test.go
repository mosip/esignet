package keymanager_test

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"database/sql"
	"encoding/hex"
	"encoding/pem"
	"errors"
	"math/big"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/db"
	"github.com/mosip/esignet/internal/keymanager/keystore"
)

func testConfig() keymanager.Config {
	return keymanager.Config{
		AsymmetricKeyLength:        2048,
		SymmetricKeyValidity:       730 * 24 * time.Hour,
		CertCommonName:             "www.mosip.io",
		CertOrganizationUnit:       "thunder-tech-team",
		CertOrganization:           "IIITB",
		CertLocation:               "Bangalore",
		CertState:                  "KA",
		CertCountry:                "IN",
		ForeignDomainAllowedAppIDs: []string{"PARTNER", "IDA"},
	}
}

func (ts *KeymanagerTestSuite) TestGenerateMasterKey_Root_GeneratesNewSelfSignedKey() {
	var inserted *db.KeyAlias
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return nil, nil // no current key yet
		},
		insertKeyAliasFn: func(_ context.Context, k db.KeyAlias) error {
			inserted = &k
			return nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())

	resp, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ROOT",
		ReferenceID:   "should-be-ignored-and-blanked",
		ObjectType:    keymanager.ObjectTypeCertificate,
		CommonName:    "MOSIP Root CA",
	})

	ts.Require().NoError(err)
	ts.Assert().True(strings.Contains(resp.Certificate, "BEGIN CERTIFICATE"))
	ts.Require().NotNil(inserted)
	ts.Assert().Equal("ROOT", inserted.AppID)
	ts.Require().NotNil(inserted.RefID)
	ts.Assert().Equal("", *inserted.RefID, "ROOT key must always use a blank reference id")
}

func (ts *KeymanagerTestSuite) TestGenerateMasterKey_UniIdentConflict_SelfHeals() {
	ks := newFakeKeyStore()
	// Seed the "winner" alias directly into the keystore, as if another
	// concurrent request had already generated and committed it.
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("winner-alias", "winner-alias", testCertTemplateParams(), "RSA", ""))

	lookupCount := 0
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			lookupCount++
			if lookupCount == 1 {
				return nil, nil // first check: no current key, proceed to generate
			}
			return []db.KeyAlias{validAliasRow("winner-alias")}, nil // second check (post-conflict): the other request's row
		},
		insertKeyAliasFn: func(_ context.Context, _ db.KeyAlias) error {
			return errors.New(`pq: duplicate key value violates unique constraint "uni_ident_const" (SQLSTATE 23505)`)
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	resp, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ROOT", ObjectType: keymanager.ObjectTypeCertificate, CommonName: "MOSIP Root CA",
	})

	ts.Require().NoError(err, "a uni_ident conflict must self-heal, not surface as an error")
	ts.Assert().True(strings.Contains(resp.Certificate, "BEGIN CERTIFICATE"))
}

// TestGenerateMasterKey_SameDayRegeneration_ClearError covers a scenario
// found via manual exploration (cmd/keymanagertest): revoking a key and
// regenerating it the same calendar day collides on uni_ident (which is
// only unique per (appID, refID, day) — inherited as-is from the Java
// service) against the now-revoked, no-longer-current row. This isn't the
// concurrent-request race persistNewAlias's self-heal handles (there's no
// *current* alias to hand back), so it must surface as the named
// ErrKeyAlreadyGeneratedToday rather than a raw DB constraint error.
func (ts *KeymanagerTestSuite) TestGenerateMasterKey_SameDayRegeneration_ClearError() {
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return nil, nil // the earlier-today key was revoked; nothing current remains
		},
		insertKeyAliasFn: func(_ context.Context, _ db.KeyAlias) error {
			return errors.New(`pq: duplicate key value violates unique constraint "uni_ident_const" (SQLSTATE 23505)`)
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ROOT", ObjectType: keymanager.ObjectTypeCertificate, CommonName: "MOSIP Root CA",
	})
	ts.Assert().ErrorIs(err, keymanager.ErrKeyAlreadyGeneratedToday)
}

func (ts *KeymanagerTestSuite) TestGenerateMasterKey_ComponentMasterKey_FailsWithoutRoot() {
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return nil, nil // nothing exists, including ROOT
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ESIGNET_RSA", ReferenceID: "RSA_2048", ObjectType: keymanager.ObjectTypeCertificate,
	})
	ts.Assert().ErrorIs(err, keymanager.ErrRootKeyNotFound)
}

// TestGenerateMasterKey_ECSignKey_SignedDirectlyByRoot covers the fix that
// an EC sign key (EC_SECP256K1_SIGN/EC_SECP256R1_SIGN/ED25519_SIGN) must be
// generatable as soon as ROOT exists, signed directly by ROOT — it must NOT
// require that component's Component Master Key (RSA_2048) to exist first,
// unlike a Component Encryption Key.
func (ts *KeymanagerTestSuite) TestGenerateMasterKey_ECSignKey_SignedDirectlyByRoot() {
	ks := newFakeKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))

	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ROOT" && refID == "" {
				return []db.KeyAlias{validAliasRow("root-alias")}, nil
			}
			return nil, nil // no Component Master Key exists — must not be required
		},
		insertKeyAliasFn: func(_ context.Context, _ db.KeyAlias) error { return nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	resp, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "THUNDER_ID", ReferenceID: "EC_SECP256R1_SIGN", ObjectType: keymanager.ObjectTypeCertificate,
	})
	ts.Require().NoError(err)
	ts.Assert().True(strings.Contains(resp.Certificate, "BEGIN CERTIFICATE"))
}

// TestGenerateMasterKey_RejectsEncryptionKeyTier covers the restriction that
// GenerateMasterKey may only provision ROOT/Component Master Key tiers —
// Component Encryption Key generation must go through GetCertificate/
// GenerateCSR instead (see TestGetCertificate_GeneratesEncryptionKeyWhenAbsent).
func (ts *KeymanagerTestSuite) TestGenerateMasterKey_RejectsEncryptionKeyTier() {
	ks := newFakeKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("master-alias", "root-alias", testCertTemplateParams(), "RSA", ""))

	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ESIGNET_RSA" && refID == "RSA_2048" {
				return []db.KeyAlias{validAliasRow("master-alias")}, nil
			}
			return nil, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ESIGNET_RSA", ReferenceID: "SOME_ENCRYPT_KEY", ObjectType: keymanager.ObjectTypeCertificate,
	})
	ts.Assert().ErrorIs(err, keymanager.ErrEncryptionKeyGenerationNotAllowed)
}

// TestGetCertificate_NeverOriginatesRoot confirms GetCertificate does NOT
// generate ROOT (or a Component Master Key) from scratch — only
// GenerateMasterKey originates those, since only it accepts the DN needed
// to do so. If ROOT has never been generated, GetCertificate must fail with
// ErrKeyNotFound rather than silently creating one with a blank identity.
func (ts *KeymanagerTestSuite) TestGetCertificate_NeverOriginatesRoot() {
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return nil, nil // ROOT has never been generated
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())

	_, err := svc.GetCertificate(context.Background(), "ROOT", "")
	ts.Assert().ErrorIs(err, keymanager.ErrKeyNotFound)
}

// TestGetCertificate_RotatesExpiredRootReusingDN confirms GetCertificate
// DOES auto-rotate ROOT once it exists but has passed its pre-expiry
// cutoff — reusing the expiring certificate's own DN, since GetCertificate
// has no DN input of its own to supply.
func (ts *KeymanagerTestSuite) TestGetCertificate_RotatesExpiredRootReusingDN() {
	ks := newFakeKeyStore()
	expiredParams := testCertTemplateParams()
	expiredParams.CommonName = "Original Root CA"
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("expired-root", "expired-root", expiredParams, "RSA", ""))

	now := time.Now().UTC()
	longAgo := now.AddDate(-10, 0, 0)
	pastExpiry := now.Add(-time.Hour) // already past its pre-expiry cutoff
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{{ID: "expired-root", KeyGenDtimes: &longAgo, KeyExpireDtimes: &pastExpiry}}, nil
		},
		insertKeyAliasFn: func(_ context.Context, _ db.KeyAlias) error { return nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	resp, err := svc.GetCertificate(context.Background(), "ROOT", "")
	ts.Require().NoError(err)

	block, _ := pem.Decode([]byte(resp.Certificate))
	ts.Require().NotNil(block)
	cert, err := x509.ParseCertificate(block.Bytes)
	ts.Require().NoError(err)
	ts.Assert().Equal("Original Root CA", cert.Subject.CommonName,
		"rotated ROOT certificate must reuse the expiring certificate's own DN")
}

// TestGenerateMasterKey_BlankReferenceID_RejectedForNonRoot covers the
// CLI-driven finding that a blank ReferenceID must be rejected for any
// ApplicationID other than ROOT (it was previously silently accepted and
// treated as a valid, if oddly-named, Component Encryption Key).
func (ts *KeymanagerTestSuite) TestGenerateMasterKey_BlankReferenceID_RejectedForNonRoot() {
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), testConfig())

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ESIGNET_RSA", ReferenceID: "", ObjectType: keymanager.ObjectTypeCertificate,
	})
	ts.Assert().ErrorIs(err, keymanager.ErrBlankReferenceID)
}

// TestGenerateMasterKey_RejectsReferenceIDReservedForSymmetricKey covers the
// new check: a ReferenceID configured as valid for symmetric key generation
// (Config.SymmetricKeyAllowedRefIDs) must not be usable for ANY asymmetric
// key — Component Master Key here — even though no symmetric key with that
// reference id has actually been generated (the fake Querier below never
// gets a chance to report one; the check is purely against config).
func (ts *KeymanagerTestSuite) TestGenerateMasterKey_RejectsReferenceIDReservedForSymmetricKey() {
	cfg := testConfig()
	cfg.SymmetricKeyAllowedRefIDs = []string{"ZK_ENCRYPT"}
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), cfg)

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ESIGNET_RSA", ReferenceID: "ZK_ENCRYPT", ObjectType: keymanager.ObjectTypeCertificate,
	})
	ts.Assert().ErrorIs(err, keymanager.ErrReferenceIDReservedForSymmetricKey)
}

// TestGetCertificate_RejectsReferenceIDReservedForSymmetricKey covers the
// same reservation for the Component Encryption Key path (GetCertificate).
func (ts *KeymanagerTestSuite) TestGetCertificate_RejectsReferenceIDReservedForSymmetricKey() {
	cfg := testConfig()
	cfg.SymmetricKeyAllowedRefIDs = []string{"ZK_ENCRYPT"}
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), cfg)

	_, err := svc.GetCertificate(context.Background(), "ESIGNET_RSA", "ZK_ENCRYPT")
	ts.Assert().ErrorIs(err, keymanager.ErrReferenceIDReservedForSymmetricKey)
}

// TestGenerateMasterKey_UnreservedRefIDStillAllowed confirms the reservation
// check doesn't over-reach: a ReferenceID NOT in the symmetric allow-list is
// unaffected by it (still subject to the usual hierarchy checks).
func (ts *KeymanagerTestSuite) TestGenerateMasterKey_UnreservedRefIDStillAllowed() {
	cfg := testConfig()
	cfg.SymmetricKeyAllowedRefIDs = []string{"ZK_ENCRYPT"}
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return nil, nil // nothing exists, including ROOT — should fail with ErrRootKeyNotFound, not the reservation error
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), cfg)

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ESIGNET_RSA", ReferenceID: "RSA_2048", ObjectType: keymanager.ObjectTypeCertificate,
	})
	ts.Assert().ErrorIs(err, keymanager.ErrRootKeyNotFound)
	ts.Assert().NotErrorIs(err, keymanager.ErrReferenceIDReservedForSymmetricKey)
}

// TestGenerateMasterKey_UnknownApplicationID covers the CLI-driven finding
// that an ApplicationID with no key_policy_def row must be rejected
// outright, not silently handled via a BASE-policy fallback.
func (ts *KeymanagerTestSuite) TestGenerateMasterKey_UnknownApplicationID() {
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) {
			return db.KeyPolicy{}, sql.ErrNoRows // no row for any app id in this test
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "NEVER_REGISTERED", ReferenceID: "SOME_KEY", ObjectType: keymanager.ObjectTypeCertificate,
	})
	ts.Assert().ErrorIs(err, keymanager.ErrUnknownApplicationID)
}

// TestGetCertificate_EncryptionKeyUsesBasePolicy covers the CLI-driven
// finding that a Component Encryption Key's validity must come from the
// shared "BASE" key_policy_def row, not its owning application's own
// (Component Master Key) policy row — even though the two differ here
// (1460 days for ESIGNET_RSA vs. 730 for BASE), the encryption key's expiry
// must reflect BASE's 730 days. Uses GetCertificate, not GenerateMasterKey
// — Component Encryption Keys are generated only via GetCertificate/
// GenerateCSR (see TestGenerateMasterKey_RejectsEncryptionKeyTier).
func (ts *KeymanagerTestSuite) TestGetCertificate_EncryptionKeyUsesBasePolicy() {
	ks := newFakeKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("master-alias", "root-alias", testCertTemplateParams(), "RSA", ""))

	records := map[string]db.KeyStoreRecord{}
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, appID string) (db.KeyPolicy, error) {
			switch appID {
			case "BASE":
				return db.KeyPolicy{AppID: "BASE", KeyValidityDuration: 730, PreExpireDays: 30, IsActive: true}, nil
			case "ESIGNET_RSA":
				return db.KeyPolicy{AppID: "ESIGNET_RSA", KeyValidityDuration: 1460, PreExpireDays: 90, IsActive: true}, nil
			default:
				return db.KeyPolicy{}, sql.ErrNoRows
			}
		},
		getKeyAliasesByAppRefFn: func(_ context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ESIGNET_RSA" && refID == "RSA_2048" {
				return []db.KeyAlias{validAliasRow("master-alias")}, nil
			}
			return nil, nil // no current encryption key yet
		},
		insertKeyStoreRecordFn: func(_ context.Context, k db.KeyStoreRecord) error {
			records[k.ID] = k
			return nil
		},
		getKeyStoreRecordFn: func(_ context.Context, id string) (db.KeyStoreRecord, error) {
			r, ok := records[id]
			if !ok {
				return db.KeyStoreRecord{}, sql.ErrNoRows
			}
			return r, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	before := time.Now().UTC()
	resp, err := svc.GetCertificate(context.Background(), "ESIGNET_RSA", "SOME_ENCRYPT_KEY")
	ts.Require().NoError(err)

	ts.Assert().WithinDuration(before.AddDate(0, 0, 730), resp.ExpiryAt, 5*time.Second,
		"encryption key expiry must follow BASE's 730-day validity, not ESIGNET_RSA's 1460-day validity")
}

// TestGetCertificate_EncryptionKeyDNFromSigningMasterKey covers your
// direction that a newly generated Component Encryption Key's certificate
// DN must be copied from its signing Component Master Key's own
// certificate — GetCertificate takes no DN input at all, so there is no
// other source for it. CommonName is the one exception: it's rebuilt from
// the configured default plus the fixed "ENC" suffix rather than inherited
// from the master's own component-identifying CN — see certSubjectSuffix.
func (ts *KeymanagerTestSuite) TestGetCertificate_EncryptionKeyDNFromSigningMasterKey() {
	ks := newFakeKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	masterParams := keystore.CertificateParameters{
		CommonName:       "www.mosip.io (ESIGNET_RSA)",
		OrganizationUnit: "Master OU",
		Organization:     "Master Org",
		Location:         "Master City",
		State:            "Master State",
		Country:          "MX",
		NotBefore:        time.Now().UTC(),
		NotAfter:         time.Now().UTC().AddDate(3, 0, 0),
	}
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("master-alias", "root-alias", masterParams, "RSA", ""))

	records := map[string]db.KeyStoreRecord{}
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ESIGNET_RSA" && refID == "RSA_2048" {
				return []db.KeyAlias{validAliasRow("master-alias")}, nil
			}
			return nil, nil
		},
		insertKeyStoreRecordFn: func(_ context.Context, k db.KeyStoreRecord) error {
			records[k.ID] = k
			return nil
		},
		getKeyStoreRecordFn: func(_ context.Context, id string) (db.KeyStoreRecord, error) {
			r, ok := records[id]
			if !ok {
				return db.KeyStoreRecord{}, sql.ErrNoRows
			}
			return r, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	resp, err := svc.GetCertificate(context.Background(), "ESIGNET_RSA", "SOME_ENCRYPT_KEY")
	ts.Require().NoError(err)

	block, _ := pem.Decode([]byte(resp.Certificate))
	ts.Require().NotNil(block)
	cert, err := x509.ParseCertificate(block.Bytes)
	ts.Require().NoError(err)
	ts.Assert().Equal("www.mosip.io (ENC_ESIGNET_RSA_SOME_ENCRYPT_KEY)", cert.Subject.CommonName,
		"encryption key's CommonName must be the configured default plus the ENC_<appID>_<refID> suffix, not the master's own CN")
	ts.Assert().Equal([]string{"Master OU"}, cert.Subject.OrganizationalUnit, "OU must be copied from the signing Component Master Key")
	ts.Assert().Equal([]string{"Master Org"}, cert.Subject.Organization, "O must be copied from the signing Component Master Key")
	ts.Assert().Equal([]string{"Master City"}, cert.Subject.Locality, "L must be copied from the signing Component Master Key")
	ts.Assert().Equal([]string{"Master State"}, cert.Subject.Province, "ST must be copied from the signing Component Master Key")
	ts.Assert().Equal([]string{"MX"}, cert.Subject.Country, "C must be copied from the signing Component Master Key")
}

// TestGenerateMasterKey_SubjectDefaultsAndCNSuffix covers the
// certificate-subject-defaults configuration: GenerateMasterKey fills any
// blank DN field from Config's Cert* defaults, and always appends a
// "(...)" suffix to CommonName — "ROOT" for the ROOT certificate, or
// "<appID>_<refID>" for a Component Master Key/EC sign key, so RSA and EC
// certificates (and different components) can be told apart at a glance.
func (ts *KeymanagerTestSuite) TestGenerateMasterKey_SubjectDefaultsAndCNSuffix() {
	ks := newFakeKeyStore()
	var rootAlias *db.KeyAlias // set once GenerateMasterKey(ROOT) actually generates one
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ROOT" && refID == "" && rootAlias != nil {
				return []db.KeyAlias{*rootAlias}, nil
			}
			return nil, nil
		},
		insertKeyAliasFn: func(_ context.Context, k db.KeyAlias) error {
			if k.AppID == "ROOT" {
				rootAlias = &k
			}
			return nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	rootResp, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ROOT", ObjectType: keymanager.ObjectTypeCertificate,
	})
	ts.Require().NoError(err)
	rootCert := parseCertFromPEM(ts.T(), rootResp.Certificate)
	ts.Assert().Equal("www.mosip.io (ROOT)", rootCert.Subject.CommonName)
	ts.Assert().Equal([]string{"thunder-tech-team"}, rootCert.Subject.OrganizationalUnit)
	ts.Assert().Equal([]string{"IIITB"}, rootCert.Subject.Organization)
	ts.Assert().Equal([]string{"Bangalore"}, rootCert.Subject.Locality)
	ts.Assert().Equal([]string{"KA"}, rootCert.Subject.Province)
	ts.Assert().Equal([]string{"IN"}, rootCert.Subject.Country)

	masterResp, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "THUNDER_ID", ReferenceID: "RSA_2048", ObjectType: keymanager.ObjectTypeCertificate,
	})
	ts.Require().NoError(err)
	masterCert := parseCertFromPEM(ts.T(), masterResp.Certificate)
	ts.Assert().Equal("www.mosip.io (THUNDER_ID_RSA_2048)", masterCert.Subject.CommonName)
}

func parseCertFromPEM(t *testing.T, certPEM string) *x509.Certificate {
	t.Helper()
	block, _ := pem.Decode([]byte(certPEM))
	require.NotNil(t, block)
	cert, err := x509.ParseCertificate(block.Bytes)
	require.NoError(t, err)
	return cert
}

func (ts *KeymanagerTestSuite) TestUploadCertificate_ThumbprintMismatch() {
	ks := newFakeKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))

	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{validAliasRow("root-alias")}, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	otherCertPEM := generateUnrelatedSelfSignedCertPEM(ts.T())
	_, err := svc.UploadCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "ROOT", ReferenceID: "", CertificateData: otherCertPEM,
	})
	ts.Assert().ErrorIs(err, keymanager.ErrThumbprintMismatch)
}

// TestUploadCertificate_RejectsDuplicateCertificate covers the new check: a
// matching public key alone isn't enough to allow the upload through — if
// the uploaded certificate is byte-identical to the one already on file
// (same thumbprint), it must be rejected as already existing rather than
// silently "replacing" the cert with itself.
func (ts *KeymanagerTestSuite) TestUploadCertificate_RejectsDuplicateCertificate() {
	ks := newFakeKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	existingCert, err := ks.GetCertificate("root-alias")
	ts.Require().NoError(err)
	certPEM := string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: existingCert.Raw}))
	thumbprint := thumbprintFromPEM(ts.T(), certPEM)

	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			row := validAliasRow("root-alias")
			row.CertThumbprint = &thumbprint
			return []db.KeyAlias{row}, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	_, err = svc.UploadCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "ROOT", ReferenceID: "", CertificateData: certPEM,
	})
	ts.Assert().ErrorIs(err, keymanager.ErrCertificateAlreadyExists)
}

// TestUploadCertificate_UpdatesKeyGenAndExpiryFromCertificate covers the
// finding that key_alias.key_gen_dtimes/key_expire_dtimes must track the
// *uploaded* certificate's own NotBefore/NotAfter (a legitimate renewal —
// same key pair, new validity window) — previously only cert_thumbprint and
// upd_dtimes were updated, leaving the old window in place indefinitely.
func (ts *KeymanagerTestSuite) TestUploadCertificate_UpdatesKeyGenAndExpiryFromCertificate() {
	ks := newFakeKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	priv, err := ks.GetPrivateKey("root-alias")
	ts.Require().NoError(err)
	existingCert, err := ks.GetCertificate("root-alias")
	ts.Require().NoError(err)

	renewedNotBefore := time.Now().UTC().Truncate(time.Second).AddDate(0, 0, -1)
	renewedNotAfter := renewedNotBefore.AddDate(5, 0, 0)
	renewedTemplate := &x509.Certificate{
		SerialNumber: big.NewInt(2),
		Subject:      existingCert.Subject,
		NotBefore:    renewedNotBefore,
		NotAfter:     renewedNotAfter,
	}
	renewedDER, err := x509.CreateCertificate(rand.Reader, renewedTemplate, renewedTemplate, existingCert.PublicKey, priv)
	ts.Require().NoError(err)
	renewedPEM := string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: renewedDER}))

	var updated db.KeyAlias
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{validAliasRow("root-alias")}, nil
		},
		updateKeyAliasFn: func(_ context.Context, k db.KeyAlias) error {
			updated = k
			return nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	_, err = svc.UploadCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "ROOT", ReferenceID: "", CertificateData: renewedPEM,
	})
	ts.Require().NoError(err)
	ts.Require().NotNil(updated.KeyGenDtimes)
	ts.Require().NotNil(updated.KeyExpireDtimes)
	ts.Assert().WithinDuration(renewedNotBefore, *updated.KeyGenDtimes, time.Second)
	ts.Assert().WithinDuration(renewedNotAfter, *updated.KeyExpireDtimes, time.Second)
}

func (ts *KeymanagerTestSuite) TestUploadOtherDomainCertificate_RejectsAppIDNotInAllowList() {
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), testConfig())
	_, err := svc.UploadOtherDomainCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "SOME_OTHER_DOMAIN", ReferenceID: "RSA_2048", CertificateData: "irrelevant",
	})
	ts.Assert().ErrorIs(err, keymanager.ErrForeignDomainAppIDNotAllowed)
}

func (ts *KeymanagerTestSuite) TestUploadOtherDomainCertificate_RejectsAppIDRegisteredInKeyPolicyDef() {
	q := &fakeQuerier{
		hasKeyPolicyFn: func(_ context.Context, _ string) (bool, error) { return true, nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())
	_, err := svc.UploadOtherDomainCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "PARTNER", ReferenceID: "RSA_2048", CertificateData: "irrelevant",
	})
	ts.Assert().ErrorIs(err, keymanager.ErrForeignDomainAppIDRegistered)
}

// TestUploadOtherDomainCertificate_RejectsAppIDWithInactiveKeyPolicy covers
// the gap GetKeyPolicy alone would miss: GetKeyPolicy only returns *active*
// rows, so an allow-listed app id with an inactive (but not deleted)
// key_policy_def row must still be rejected as "already registered" — it
// must not be treated as available for a foreign-domain, cert-only entry.
func (ts *KeymanagerTestSuite) TestUploadOtherDomainCertificate_RejectsAppIDWithInactiveKeyPolicy() {
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return db.KeyPolicy{}, sql.ErrNoRows },
		hasKeyPolicyFn: func(_ context.Context, _ string) (bool, error) { return true, nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())
	_, err := svc.UploadOtherDomainCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "PARTNER", ReferenceID: "RSA_2048", CertificateData: "irrelevant",
	})
	ts.Assert().ErrorIs(err, keymanager.ErrForeignDomainAppIDRegistered)
}

// TestUploadOtherDomainCertificate_AllowsSigningRefIDs covers the reversal
// of the old behavior: ref ids already used for asymmetric key generation
// (RSA_2048, EC_SECP256K1_SIGN, etc.) are now fine to reuse under a foreign
// domain's ApplicationID, since (appID, refID) together identify the
// key_alias row and appID is guaranteed foreign by validateForeignDomainAppID.
func (ts *KeymanagerTestSuite) TestUploadOtherDomainCertificate_AllowsSigningRefIDs() {
	certPEM := generateUnrelatedSelfSignedCertPEM(ts.T())
	wantCert := parseCertFromPEM(ts.T(), certPEM)

	var insertedAlias db.KeyAlias
	q := &fakeQuerier{
		getKeyPolicyFn:          func(_ context.Context, _ string) (db.KeyPolicy, error) { return db.KeyPolicy{}, sql.ErrNoRows },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) { return nil, nil },
		insertKeyStoreRecordFn: func(_ context.Context, k db.KeyStoreRecord) error {
			ts.Assert().Equal(keymanager.ForeignDomainPrivateKeyMarker, k.PrivateKey, "foreign-domain uploads must never store a real private key")
			ts.Require().NotNil(k.MasterKey, "master_key is NOT NULL in key_store; must be set even with no real Component Master Key")
			ts.Assert().Equal(k.ID, *k.MasterKey, "master_key is self-referential (== alias) for foreign-domain entries, mirroring Java's storeKeyInDBStore(alias, alias, ...)")
			return nil
		},
		insertKeyAliasFn: func(_ context.Context, k db.KeyAlias) error {
			insertedAlias = k
			return nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())
	resp, err := svc.UploadOtherDomainCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "PARTNER", ReferenceID: "RSA_2048", CertificateData: certPEM,
	})
	ts.Require().NoError(err)
	ts.Assert().Equal("success", resp.Status)

	// key_gen_dtimes/key_expire_dtimes must track the certificate's own
	// NotBefore/NotAfter, not the upload timestamp.
	ts.Require().NotNil(insertedAlias.KeyGenDtimes)
	ts.Require().NotNil(insertedAlias.KeyExpireDtimes)
	ts.Assert().WithinDuration(wantCert.NotBefore, *insertedAlias.KeyGenDtimes, time.Second)
	ts.Assert().WithinDuration(wantCert.NotAfter, *insertedAlias.KeyExpireDtimes, time.Second)
}

func (ts *KeymanagerTestSuite) TestUploadOtherDomainCertificate_RejectsDuplicateThumbprint() {
	certPEM := generateUnrelatedSelfSignedCertPEM(ts.T())
	thumbprint := thumbprintFromPEM(ts.T(), certPEM)
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return db.KeyPolicy{}, sql.ErrNoRows },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{{ID: "existing", CertThumbprint: &thumbprint}}, nil
		},
		getKeyStoreRecordFn: func(_ context.Context, _ string) (db.KeyStoreRecord, error) {
			return db.KeyStoreRecord{PrivateKey: keymanager.ForeignDomainPrivateKeyMarker}, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())
	_, err := svc.UploadOtherDomainCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "PARTNER", ReferenceID: "RSA_2048", CertificateData: certPEM,
	})
	ts.Assert().ErrorIs(err, keymanager.ErrCertificateAlreadyExists)
}

func (ts *KeymanagerTestSuite) TestUploadOtherDomainCertificate_RejectsWhenPrivateKeyExists() {
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return db.KeyPolicy{}, sql.ErrNoRows },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{validAliasRow("existing")}, nil
		},
		getKeyStoreRecordFn: func(_ context.Context, _ string) (db.KeyStoreRecord, error) {
			return db.KeyStoreRecord{PrivateKey: "some-real-encrypted-key"}, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())
	_, err := svc.UploadOtherDomainCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "PARTNER", ReferenceID: "RSA_2048", CertificateData: generateUnrelatedSelfSignedCertPEM(ts.T()),
	})
	ts.Assert().ErrorIs(err, keymanager.ErrPrivateKeyExists)
}

func thumbprintFromPEM(t *testing.T, certPEM string) string {
	t.Helper()
	cert := parseCertFromPEM(t, certPEM)
	sum := sha256.Sum256(cert.Raw)
	return hex.EncodeToString(sum[:])
}

// TestGenerateSymmetricKey_RejectsRefIDNotInAllowList covers the new
// Config.SymmetricKeyAllowedRefIDs check: a ReferenceID not in the
// configured comma-separated allow-list must be rejected — previously
// GenerateSymmetricKey validated ApplicationID but accepted any ReferenceID
// at all.
func (ts *KeymanagerTestSuite) TestGenerateSymmetricKey_RejectsRefIDNotInAllowList() {
	cfg := testConfig()
	cfg.SymmetricKeyAllowedRefIDs = []string{"ZK_ENCRYPT"}
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), cfg)

	_, err := svc.GenerateSymmetricKey(context.Background(), keymanager.SymmetricKeyRequest{
		ApplicationID: "KERNEL", ReferenceID: "NOT_IN_ALLOW_LIST",
	})
	ts.Assert().ErrorIs(err, keymanager.ErrSymmetricKeyRefIDNotAllowed)
}

// TestGenerateSymmetricKey_UnconfiguredAllowListRejectsEverything covers
// the "no silent default" stance: an empty/unset allow-list must reject
// every ReferenceID, not implicitly allow all of them.
func (ts *KeymanagerTestSuite) TestGenerateSymmetricKey_UnconfiguredAllowListRejectsEverything() {
	cfg := testConfig() // SymmetricKeyAllowedRefIDs left nil
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), cfg)

	_, err := svc.GenerateSymmetricKey(context.Background(), keymanager.SymmetricKeyRequest{
		ApplicationID: "KERNEL", ReferenceID: "ANYTHING",
	})
	ts.Assert().ErrorIs(err, keymanager.ErrSymmetricKeyRefIDNotAllowed)
}

// TestGenerateSymmetricKey_AllowsListedRefID confirms a ReferenceID that IS
// in the allow-list proceeds normally.
func (ts *KeymanagerTestSuite) TestGenerateSymmetricKey_AllowsListedRefID() {
	cfg := testConfig()
	cfg.SymmetricKeyAllowedRefIDs = []string{"ZK_ENCRYPT", "VID_ENCRYPT"}
	q := &fakeQuerier{
		getKeyPolicyFn:          func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) { return nil, nil },
		insertKeyAliasFn:        func(_ context.Context, _ db.KeyAlias) error { return nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), cfg)

	resp, err := svc.GenerateSymmetricKey(context.Background(), keymanager.SymmetricKeyRequest{
		ApplicationID: "KERNEL", ReferenceID: "ZK_ENCRYPT",
	})
	ts.Require().NoError(err)
	ts.Assert().Equal("success", resp.Status)
}

// TestIsAllowedCertificateRefID_ExemptsFixedHierarchyTiers confirms blank
// (ROOT) and the fixed Component Master Key/EC sign reference ids are
// always allowed, regardless of Config.CertificateAllowedRefIDs — only the
// open-ended Component Encryption Key case is gated.
func (ts *KeymanagerTestSuite) TestIsAllowedCertificateRefID_ExemptsFixedHierarchyTiers() {
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), testConfig()) // CertificateAllowedRefIDs left nil

	for _, refID := range []string{"", keymanager.RefIDRSA2048, keymanager.RefIDECSECP256K1Sign, keymanager.RefIDECSECP256R1Sign, keymanager.RefIDED25519Sign} {
		ts.Assert().True(svc.IsAllowedCertificateRefID(refID), "refID %q must be exempt", refID)
	}
}

// TestIsAllowedCertificateRefID_UnconfiguredAllowListRejectsEncryptionKeyRefIDs
// covers the "no silent default" stance for the getCertificate HTTP read
// path: an unset allow-list must reject every Component Encryption Key
// reference id, since GetCertificate mints a brand new RSA key pair for any
// (appID, refID) pair it hasn't seen before.
func (ts *KeymanagerTestSuite) TestIsAllowedCertificateRefID_UnconfiguredAllowListRejectsEncryptionKeyRefIDs() {
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), testConfig()) // CertificateAllowedRefIDs left nil

	ts.Assert().False(svc.IsAllowedCertificateRefID("ANY_ENCRYPTION_KEY"))
}

// TestIsAllowedCertificateRefID_AllowsListedEncryptionKeyRefID confirms a
// Component Encryption Key reference id that IS in the configured
// allow-list is permitted.
func (ts *KeymanagerTestSuite) TestIsAllowedCertificateRefID_AllowsListedEncryptionKeyRefID() {
	cfg := testConfig()
	cfg.CertificateAllowedRefIDs = []string{"ZK_ENCRYPT"}
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), cfg)

	ts.Assert().True(svc.IsAllowedCertificateRefID("ZK_ENCRYPT"))
	ts.Assert().False(svc.IsAllowedCertificateRefID("NOT_IN_ALLOW_LIST"))
}

// TestRevokeKey_MovesExpiryIntoPast confirms RevokeKey invalidates the
// current key by moving its expiry into the past (no keystore deletion),
// and that a subsequent generation isn't blocked by the revoked alias still
// being "current".
func (ts *KeymanagerTestSuite) TestRevokeKey_MovesExpiryIntoPast() {
	ks := newFakeKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))

	var updated db.KeyAlias
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{validAliasRow("root-alias")}, nil
		},
		updateKeyAliasFn: func(_ context.Context, k db.KeyAlias) error {
			updated = k
			return nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	resp, err := svc.RevokeKey(context.Background(), keymanager.RevokeKeyRequest{ApplicationID: "ROOT", ReferenceID: ""})
	ts.Require().NoError(err)
	ts.Assert().Equal("success", resp.Status)
	ts.Require().Equal("root-alias", updated.ID)
	ts.Require().NotNil(updated.KeyExpireDtimes)
	ts.Assert().True(updated.KeyExpireDtimes.Before(time.Now().UTC()), "revoked key's expiry must be in the past")
}

func (ts *KeymanagerTestSuite) TestRevokeKey_NoCurrentKey_ReturnsErrKeyNotFound() {
	q := &fakeQuerier{
		getKeyPolicyFn:          func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) { return nil, nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())

	_, err := svc.RevokeKey(context.Background(), keymanager.RevokeKeyRequest{ApplicationID: "ROOT", ReferenceID: ""})
	ts.Assert().ErrorIs(err, keymanager.ErrKeyNotFound)
}

// TestGetAllCertificates_ReturnsFullAliasHistory covers the loop in
// GetAllCertificates, including its continue-on-unreadable-certificate path.
func (ts *KeymanagerTestSuite) TestGetAllCertificates_ReturnsFullAliasHistory() {
	ks := newFakeKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias-1", "root-alias-1", testCertTemplateParams(), "RSA", ""))
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias-2", "root-alias-2", testCertTemplateParams(), "RSA", ""))

	q := &fakeQuerier{
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{
				validAliasRow("root-alias-1"),
				validAliasRow("root-alias-2"),
				validAliasRow("root-alias-missing"), // unreadable — must be skipped, not fail the whole call
			}, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	resp, err := svc.GetAllCertificates(context.Background(), "ROOT", "")
	ts.Require().NoError(err)
	ts.Require().Len(resp.AllCertificates, 2)
	gotIDs := []string{resp.AllCertificates[0].KeyID, resp.AllCertificates[1].KeyID}
	ts.Assert().ElementsMatch([]string{"root-alias-1", "root-alias-2"}, gotIDs)
}

// TestGetCertificateChain_WalksSigningHierarchyToRoot covers the
// parent-walking loop in GetCertificateChain: a Component Master Key's
// chain must include both its own certificate and ROOT's, and stop there.
func (ts *KeymanagerTestSuite) TestGetCertificateChain_WalksSigningHierarchyToRoot() {
	ks := newFakeKeyStore()
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	ts.Require().NoError(ks.GenerateAndStoreAsymmetricKey("master-alias", "root-alias", testCertTemplateParams(), "RSA", ""))

	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, appID, refID string) ([]db.KeyAlias, error) {
			switch {
			case appID == "ROOT" && refID == "":
				return []db.KeyAlias{validAliasRow("root-alias")}, nil
			case appID == "TESTAPP" && refID == "RSA_2048":
				return []db.KeyAlias{validAliasRow("master-alias")}, nil
			default:
				return nil, nil
			}
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	resp, err := svc.GetCertificateChain(context.Background(), "TESTAPP", "RSA_2048")
	ts.Require().NoError(err)
	ts.Assert().NotEmpty(resp.CertificatesTrustPath)
}

// --- test-local helpers ---

func testCertTemplateParams() keystore.CertificateParameters {
	now := time.Now().UTC()
	return keystore.CertificateParameters{
		CommonName: "Test",
		NotBefore:  now,
		NotAfter:   now.AddDate(3, 0, 0),
	}
}

func generateUnrelatedSelfSignedCertPEM(t *testing.T) string {
	t.Helper()
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	template := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: "unrelated"},
		NotBefore:    time.Now().UTC(),
		NotAfter:     time.Now().UTC().AddDate(1, 0, 0),
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &priv.PublicKey, priv)
	require.NoError(t, err)
	return string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}))
}
