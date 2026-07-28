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

	"github.com/stretchr/testify/assert"
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

func TestGenerateMasterKey_Root_GeneratesNewSelfSignedKey(t *testing.T) {
	var inserted *db.KeyAlias
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return nil, nil // no current key yet
		},
		insertKeyAliasFn: func(ctx context.Context, k db.KeyAlias) error {
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

	require.NoError(t, err)
	assert.True(t, strings.Contains(resp.Certificate, "BEGIN CERTIFICATE"))
	require.NotNil(t, inserted)
	assert.Equal(t, "ROOT", inserted.AppID)
	require.NotNil(t, inserted.RefID)
	assert.Equal(t, "", *inserted.RefID, "ROOT key must always use a blank reference id")
}

func TestGenerateMasterKey_UniIdentConflict_SelfHeals(t *testing.T) {
	ks := newFakeKeyStore()
	// Seed the "winner" alias directly into the keystore, as if another
	// concurrent request had already generated and committed it.
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("winner-alias", "winner-alias", testCertTemplateParams(), "RSA", ""))

	lookupCount := 0
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			lookupCount++
			if lookupCount == 1 {
				return nil, nil // first check: no current key, proceed to generate
			}
			return []db.KeyAlias{validAliasRow("winner-alias")}, nil // second check (post-conflict): the other request's row
		},
		insertKeyAliasFn: func(ctx context.Context, k db.KeyAlias) error {
			return errors.New(`pq: duplicate key value violates unique constraint "uni_ident_const" (SQLSTATE 23505)`)
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	resp, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ROOT", ObjectType: keymanager.ObjectTypeCertificate, CommonName: "MOSIP Root CA",
	})

	require.NoError(t, err, "a uni_ident conflict must self-heal, not surface as an error")
	assert.True(t, strings.Contains(resp.Certificate, "BEGIN CERTIFICATE"))
}

// TestGenerateMasterKey_SameDayRegeneration_ClearError covers a scenario
// found via manual exploration (cmd/keymanagertest): revoking a key and
// regenerating it the same calendar day collides on uni_ident (which is
// only unique per (appID, refID, day) — inherited as-is from the Java
// service) against the now-revoked, no-longer-current row. This isn't the
// concurrent-request race persistNewAlias's self-heal handles (there's no
// *current* alias to hand back), so it must surface as the named
// ErrKeyAlreadyGeneratedToday rather than a raw DB constraint error.
func TestGenerateMasterKey_SameDayRegeneration_ClearError(t *testing.T) {
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return nil, nil // the earlier-today key was revoked; nothing current remains
		},
		insertKeyAliasFn: func(ctx context.Context, k db.KeyAlias) error {
			return errors.New(`pq: duplicate key value violates unique constraint "uni_ident_const" (SQLSTATE 23505)`)
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ROOT", ObjectType: keymanager.ObjectTypeCertificate, CommonName: "MOSIP Root CA",
	})
	assert.ErrorIs(t, err, keymanager.ErrKeyAlreadyGeneratedToday)
}

func TestGenerateMasterKey_ComponentMasterKey_FailsWithoutRoot(t *testing.T) {
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return nil, nil // nothing exists, including ROOT
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ESIGNET_RSA", ReferenceID: "RSA_2048", ObjectType: keymanager.ObjectTypeCertificate,
	})
	assert.ErrorIs(t, err, keymanager.ErrRootKeyNotFound)
}

// TestGenerateMasterKey_ECSignKey_SignedDirectlyByRoot covers the fix that
// an EC sign key (EC_SECP256K1_SIGN/EC_SECP256R1_SIGN/ED25519_SIGN) must be
// generatable as soon as ROOT exists, signed directly by ROOT — it must NOT
// require that component's Component Master Key (RSA_2048) to exist first,
// unlike a Component Encryption Key.
func TestGenerateMasterKey_ECSignKey_SignedDirectlyByRoot(t *testing.T) {
	ks := newFakeKeyStore()
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))

	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ROOT" && refID == "" {
				return []db.KeyAlias{validAliasRow("root-alias")}, nil
			}
			return nil, nil // no Component Master Key exists — must not be required
		},
		insertKeyAliasFn: func(ctx context.Context, k db.KeyAlias) error { return nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	resp, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "THUNDER_ID", ReferenceID: "EC_SECP256R1_SIGN", ObjectType: keymanager.ObjectTypeCertificate,
	})
	require.NoError(t, err)
	assert.True(t, strings.Contains(resp.Certificate, "BEGIN CERTIFICATE"))
}

// TestGenerateMasterKey_RejectsEncryptionKeyTier covers the restriction that
// GenerateMasterKey may only provision ROOT/Component Master Key tiers —
// Component Encryption Key generation must go through GetCertificate/
// GenerateCSR instead (see TestGetCertificate_GeneratesEncryptionKeyWhenAbsent).
func TestGenerateMasterKey_RejectsEncryptionKeyTier(t *testing.T) {
	ks := newFakeKeyStore()
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("master-alias", "root-alias", testCertTemplateParams(), "RSA", ""))

	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
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
	assert.ErrorIs(t, err, keymanager.ErrEncryptionKeyGenerationNotAllowed)
}

// TestGetCertificate_NeverOriginatesRoot confirms GetCertificate does NOT
// generate ROOT (or a Component Master Key) from scratch — only
// GenerateMasterKey originates those, since only it accepts the DN needed
// to do so. If ROOT has never been generated, GetCertificate must fail with
// ErrKeyNotFound rather than silently creating one with a blank identity.
func TestGetCertificate_NeverOriginatesRoot(t *testing.T) {
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return nil, nil // ROOT has never been generated
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())

	_, err := svc.GetCertificate(context.Background(), "ROOT", "")
	assert.ErrorIs(t, err, keymanager.ErrKeyNotFound)
}

// TestGetCertificate_RotatesExpiredRootReusingDN confirms GetCertificate
// DOES auto-rotate ROOT once it exists but has passed its pre-expiry
// cutoff — reusing the expiring certificate's own DN, since GetCertificate
// has no DN input of its own to supply.
func TestGetCertificate_RotatesExpiredRootReusingDN(t *testing.T) {
	ks := newFakeKeyStore()
	expiredParams := testCertTemplateParams()
	expiredParams.CommonName = "Original Root CA"
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("expired-root", "expired-root", expiredParams, "RSA", ""))

	now := time.Now().UTC()
	longAgo := now.AddDate(-10, 0, 0)
	pastExpiry := now.Add(-time.Hour) // already past its pre-expiry cutoff
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{{ID: "expired-root", KeyGenDtimes: &longAgo, KeyExpireDtimes: &pastExpiry}}, nil
		},
		insertKeyAliasFn: func(ctx context.Context, k db.KeyAlias) error { return nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	resp, err := svc.GetCertificate(context.Background(), "ROOT", "")
	require.NoError(t, err)

	block, _ := pem.Decode([]byte(resp.Certificate))
	require.NotNil(t, block)
	cert, err := x509.ParseCertificate(block.Bytes)
	require.NoError(t, err)
	assert.Equal(t, "Original Root CA", cert.Subject.CommonName,
		"rotated ROOT certificate must reuse the expiring certificate's own DN")
}

// TestGenerateMasterKey_BlankReferenceID_RejectedForNonRoot covers the
// CLI-driven finding that a blank ReferenceID must be rejected for any
// ApplicationID other than ROOT (it was previously silently accepted and
// treated as a valid, if oddly-named, Component Encryption Key).
func TestGenerateMasterKey_BlankReferenceID_RejectedForNonRoot(t *testing.T) {
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), testConfig())

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ESIGNET_RSA", ReferenceID: "", ObjectType: keymanager.ObjectTypeCertificate,
	})
	assert.ErrorIs(t, err, keymanager.ErrBlankReferenceID)
}

// TestGenerateMasterKey_RejectsReferenceIDReservedForSymmetricKey covers the
// new check: a ReferenceID configured as valid for symmetric key generation
// (Config.SymmetricKeyAllowedRefIDs) must not be usable for ANY asymmetric
// key — Component Master Key here — even though no symmetric key with that
// reference id has actually been generated (the fake Querier below never
// gets a chance to report one; the check is purely against config).
func TestGenerateMasterKey_RejectsReferenceIDReservedForSymmetricKey(t *testing.T) {
	cfg := testConfig()
	cfg.SymmetricKeyAllowedRefIDs = []string{"ZK_ENCRYPT"}
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), cfg)

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ESIGNET_RSA", ReferenceID: "ZK_ENCRYPT", ObjectType: keymanager.ObjectTypeCertificate,
	})
	assert.ErrorIs(t, err, keymanager.ErrReferenceIDReservedForSymmetricKey)
}

// TestGetCertificate_RejectsReferenceIDReservedForSymmetricKey covers the
// same reservation for the Component Encryption Key path (GetCertificate).
func TestGetCertificate_RejectsReferenceIDReservedForSymmetricKey(t *testing.T) {
	cfg := testConfig()
	cfg.SymmetricKeyAllowedRefIDs = []string{"ZK_ENCRYPT"}
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), cfg)

	_, err := svc.GetCertificate(context.Background(), "ESIGNET_RSA", "ZK_ENCRYPT")
	assert.ErrorIs(t, err, keymanager.ErrReferenceIDReservedForSymmetricKey)
}

// TestGenerateMasterKey_UnreservedRefIDStillAllowed confirms the reservation
// check doesn't over-reach: a ReferenceID NOT in the symmetric allow-list is
// unaffected by it (still subject to the usual hierarchy checks).
func TestGenerateMasterKey_UnreservedRefIDStillAllowed(t *testing.T) {
	cfg := testConfig()
	cfg.SymmetricKeyAllowedRefIDs = []string{"ZK_ENCRYPT"}
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return nil, nil // nothing exists, including ROOT — should fail with ErrRootKeyNotFound, not the reservation error
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), cfg)

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ESIGNET_RSA", ReferenceID: "RSA_2048", ObjectType: keymanager.ObjectTypeCertificate,
	})
	assert.ErrorIs(t, err, keymanager.ErrRootKeyNotFound)
	assert.NotErrorIs(t, err, keymanager.ErrReferenceIDReservedForSymmetricKey)
}

// TestGenerateMasterKey_UnknownApplicationID covers the CLI-driven finding
// that an ApplicationID with no key_policy_def row must be rejected
// outright, not silently handled via a BASE-policy fallback.
func TestGenerateMasterKey_UnknownApplicationID(t *testing.T) {
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) {
			return db.KeyPolicy{}, sql.ErrNoRows // no row for any app id in this test
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())

	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "NEVER_REGISTERED", ReferenceID: "SOME_KEY", ObjectType: keymanager.ObjectTypeCertificate,
	})
	assert.ErrorIs(t, err, keymanager.ErrUnknownApplicationID)
}

// TestGetCertificate_EncryptionKeyUsesBasePolicy covers the CLI-driven
// finding that a Component Encryption Key's validity must come from the
// shared "BASE" key_policy_def row, not its owning application's own
// (Component Master Key) policy row — even though the two differ here
// (1460 days for ESIGNET_RSA vs. 730 for BASE), the encryption key's expiry
// must reflect BASE's 730 days. Uses GetCertificate, not GenerateMasterKey
// — Component Encryption Keys are generated only via GetCertificate/
// GenerateCSR (see TestGenerateMasterKey_RejectsEncryptionKeyTier).
func TestGetCertificate_EncryptionKeyUsesBasePolicy(t *testing.T) {
	ks := newFakeKeyStore()
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("master-alias", "root-alias", testCertTemplateParams(), "RSA", ""))

	records := map[string]db.KeyStoreRecord{}
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) {
			switch appID {
			case "BASE":
				return db.KeyPolicy{AppID: "BASE", KeyValidityDuration: 730, PreExpireDays: 30, IsActive: true}, nil
			case "ESIGNET_RSA":
				return db.KeyPolicy{AppID: "ESIGNET_RSA", KeyValidityDuration: 1460, PreExpireDays: 90, IsActive: true}, nil
			default:
				return db.KeyPolicy{}, sql.ErrNoRows
			}
		},
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ESIGNET_RSA" && refID == "RSA_2048" {
				return []db.KeyAlias{validAliasRow("master-alias")}, nil
			}
			return nil, nil // no current encryption key yet
		},
		insertKeyStoreRecordFn: func(ctx context.Context, k db.KeyStoreRecord) error {
			records[k.ID] = k
			return nil
		},
		getKeyStoreRecordFn: func(ctx context.Context, id string) (db.KeyStoreRecord, error) {
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
	require.NoError(t, err)

	assert.WithinDuration(t, before.AddDate(0, 0, 730), resp.ExpiryAt, 5*time.Second,
		"encryption key expiry must follow BASE's 730-day validity, not ESIGNET_RSA's 1460-day validity")
}

// TestGetCertificate_EncryptionKeyDNFromSigningMasterKey covers your
// direction that a newly generated Component Encryption Key's certificate
// DN must be copied from its signing Component Master Key's own
// certificate — GetCertificate takes no DN input at all, so there is no
// other source for it. CommonName is the one exception: it's rebuilt from
// the configured default plus the fixed "ENC" suffix rather than inherited
// from the master's own component-identifying CN — see certSubjectSuffix.
func TestGetCertificate_EncryptionKeyDNFromSigningMasterKey(t *testing.T) {
	ks := newFakeKeyStore()
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
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
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("master-alias", "root-alias", masterParams, "RSA", ""))

	records := map[string]db.KeyStoreRecord{}
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ESIGNET_RSA" && refID == "RSA_2048" {
				return []db.KeyAlias{validAliasRow("master-alias")}, nil
			}
			return nil, nil
		},
		insertKeyStoreRecordFn: func(ctx context.Context, k db.KeyStoreRecord) error {
			records[k.ID] = k
			return nil
		},
		getKeyStoreRecordFn: func(ctx context.Context, id string) (db.KeyStoreRecord, error) {
			r, ok := records[id]
			if !ok {
				return db.KeyStoreRecord{}, sql.ErrNoRows
			}
			return r, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	resp, err := svc.GetCertificate(context.Background(), "ESIGNET_RSA", "SOME_ENCRYPT_KEY")
	require.NoError(t, err)

	block, _ := pem.Decode([]byte(resp.Certificate))
	require.NotNil(t, block)
	cert, err := x509.ParseCertificate(block.Bytes)
	require.NoError(t, err)
	assert.Equal(t, "www.mosip.io (ENC_ESIGNET_RSA_SOME_ENCRYPT_KEY)", cert.Subject.CommonName,
		"encryption key's CommonName must be the configured default plus the ENC_<appID>_<refID> suffix, not the master's own CN")
	assert.Equal(t, []string{"Master OU"}, cert.Subject.OrganizationalUnit, "OU must be copied from the signing Component Master Key")
	assert.Equal(t, []string{"Master Org"}, cert.Subject.Organization, "O must be copied from the signing Component Master Key")
	assert.Equal(t, []string{"Master City"}, cert.Subject.Locality, "L must be copied from the signing Component Master Key")
	assert.Equal(t, []string{"Master State"}, cert.Subject.Province, "ST must be copied from the signing Component Master Key")
	assert.Equal(t, []string{"MX"}, cert.Subject.Country, "C must be copied from the signing Component Master Key")
}

// TestGenerateMasterKey_SubjectDefaultsAndCNSuffix covers the
// certificate-subject-defaults configuration: GenerateMasterKey fills any
// blank DN field from Config's Cert* defaults, and always appends a
// "(...)" suffix to CommonName — "ROOT" for the ROOT certificate, or
// "<appID>_<refID>" for a Component Master Key/EC sign key, so RSA and EC
// certificates (and different components) can be told apart at a glance.
func TestGenerateMasterKey_SubjectDefaultsAndCNSuffix(t *testing.T) {
	ks := newFakeKeyStore()
	var rootAlias *db.KeyAlias // set once GenerateMasterKey(ROOT) actually generates one
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ROOT" && refID == "" && rootAlias != nil {
				return []db.KeyAlias{*rootAlias}, nil
			}
			return nil, nil
		},
		insertKeyAliasFn: func(ctx context.Context, k db.KeyAlias) error {
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
	require.NoError(t, err)
	rootCert := parseCertFromPEM(t, rootResp.Certificate)
	assert.Equal(t, "www.mosip.io (ROOT)", rootCert.Subject.CommonName)
	assert.Equal(t, []string{"thunder-tech-team"}, rootCert.Subject.OrganizationalUnit)
	assert.Equal(t, []string{"IIITB"}, rootCert.Subject.Organization)
	assert.Equal(t, []string{"Bangalore"}, rootCert.Subject.Locality)
	assert.Equal(t, []string{"KA"}, rootCert.Subject.Province)
	assert.Equal(t, []string{"IN"}, rootCert.Subject.Country)

	masterResp, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "THUNDER_ID", ReferenceID: "RSA_2048", ObjectType: keymanager.ObjectTypeCertificate,
	})
	require.NoError(t, err)
	masterCert := parseCertFromPEM(t, masterResp.Certificate)
	assert.Equal(t, "www.mosip.io (THUNDER_ID_RSA_2048)", masterCert.Subject.CommonName)
}

func parseCertFromPEM(t *testing.T, certPEM string) *x509.Certificate {
	t.Helper()
	block, _ := pem.Decode([]byte(certPEM))
	require.NotNil(t, block)
	cert, err := x509.ParseCertificate(block.Bytes)
	require.NoError(t, err)
	return cert
}

func TestUploadCertificate_ThumbprintMismatch(t *testing.T) {
	ks := newFakeKeyStore()
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))

	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{validAliasRow("root-alias")}, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	otherCertPEM := generateUnrelatedSelfSignedCertPEM(t)
	_, err := svc.UploadCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "ROOT", ReferenceID: "", CertificateData: otherCertPEM,
	})
	assert.ErrorIs(t, err, keymanager.ErrThumbprintMismatch)
}

// TestUploadCertificate_RejectsDuplicateCertificate covers the new check: a
// matching public key alone isn't enough to allow the upload through — if
// the uploaded certificate is byte-identical to the one already on file
// (same thumbprint), it must be rejected as already existing rather than
// silently "replacing" the cert with itself.
func TestUploadCertificate_RejectsDuplicateCertificate(t *testing.T) {
	ks := newFakeKeyStore()
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	existingCert, err := ks.GetCertificate("root-alias")
	require.NoError(t, err)
	certPEM := string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: existingCert.Raw}))
	thumbprint := thumbprintFromPEM(t, certPEM)

	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			row := validAliasRow("root-alias")
			row.CertThumbprint = &thumbprint
			return []db.KeyAlias{row}, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	_, err = svc.UploadCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "ROOT", ReferenceID: "", CertificateData: certPEM,
	})
	assert.ErrorIs(t, err, keymanager.ErrCertificateAlreadyExists)
}

// TestUploadCertificate_UpdatesKeyGenAndExpiryFromCertificate covers the
// finding that key_alias.key_gen_dtimes/key_expire_dtimes must track the
// *uploaded* certificate's own NotBefore/NotAfter (a legitimate renewal —
// same key pair, new validity window) — previously only cert_thumbprint and
// upd_dtimes were updated, leaving the old window in place indefinitely.
func TestUploadCertificate_UpdatesKeyGenAndExpiryFromCertificate(t *testing.T) {
	ks := newFakeKeyStore()
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	priv, err := ks.GetPrivateKey("root-alias")
	require.NoError(t, err)
	existingCert, err := ks.GetCertificate("root-alias")
	require.NoError(t, err)

	renewedNotBefore := time.Now().UTC().Truncate(time.Second).AddDate(0, 0, -1)
	renewedNotAfter := renewedNotBefore.AddDate(5, 0, 0)
	renewedTemplate := &x509.Certificate{
		SerialNumber: big.NewInt(2),
		Subject:      existingCert.Subject,
		NotBefore:    renewedNotBefore,
		NotAfter:     renewedNotAfter,
	}
	renewedDER, err := x509.CreateCertificate(rand.Reader, renewedTemplate, renewedTemplate, existingCert.PublicKey, priv)
	require.NoError(t, err)
	renewedPEM := string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: renewedDER}))

	var updated db.KeyAlias
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{validAliasRow("root-alias")}, nil
		},
		updateKeyAliasFn: func(ctx context.Context, k db.KeyAlias) error {
			updated = k
			return nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, ks, testConfig())

	_, err = svc.UploadCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "ROOT", ReferenceID: "", CertificateData: renewedPEM,
	})
	require.NoError(t, err)
	require.NotNil(t, updated.KeyGenDtimes)
	require.NotNil(t, updated.KeyExpireDtimes)
	assert.WithinDuration(t, renewedNotBefore, *updated.KeyGenDtimes, time.Second)
	assert.WithinDuration(t, renewedNotAfter, *updated.KeyExpireDtimes, time.Second)
}

func TestUploadOtherDomainCertificate_RejectsAppIDNotInAllowList(t *testing.T) {
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), testConfig())
	_, err := svc.UploadOtherDomainCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "SOME_OTHER_DOMAIN", ReferenceID: "RSA_2048", CertificateData: "irrelevant",
	})
	assert.ErrorIs(t, err, keymanager.ErrForeignDomainAppIDNotAllowed)
}

func TestUploadOtherDomainCertificate_RejectsAppIDRegisteredInKeyPolicyDef(t *testing.T) {
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())
	_, err := svc.UploadOtherDomainCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "PARTNER", ReferenceID: "RSA_2048", CertificateData: "irrelevant",
	})
	assert.ErrorIs(t, err, keymanager.ErrForeignDomainAppIDRegistered)
}

// TestUploadOtherDomainCertificate_AllowsSigningRefIDs covers the reversal
// of the old behavior: ref ids already used for asymmetric key generation
// (RSA_2048, EC_SECP256K1_SIGN, etc.) are now fine to reuse under a foreign
// domain's ApplicationID, since (appID, refID) together identify the
// key_alias row and appID is guaranteed foreign by validateForeignDomainAppID.
func TestUploadOtherDomainCertificate_AllowsSigningRefIDs(t *testing.T) {
	certPEM := generateUnrelatedSelfSignedCertPEM(t)
	wantCert := parseCertFromPEM(t, certPEM)

	var insertedAlias db.KeyAlias
	q := &fakeQuerier{
		getKeyPolicyFn:          func(ctx context.Context, appID string) (db.KeyPolicy, error) { return db.KeyPolicy{}, sql.ErrNoRows },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) { return nil, nil },
		insertKeyStoreRecordFn: func(ctx context.Context, k db.KeyStoreRecord) error {
			assert.Equal(t, "NA", k.PrivateKey, "foreign-domain uploads must never store a real private key")
			require.NotNil(t, k.MasterKey, "master_key is NOT NULL in key_store; must be set even with no real Component Master Key")
			assert.Equal(t, k.ID, *k.MasterKey, "master_key is self-referential (== alias) for foreign-domain entries, mirroring Java's storeKeyInDBStore(alias, alias, ...)")
			return nil
		},
		insertKeyAliasFn: func(ctx context.Context, k db.KeyAlias) error {
			insertedAlias = k
			return nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())
	resp, err := svc.UploadOtherDomainCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "PARTNER", ReferenceID: "RSA_2048", CertificateData: certPEM,
	})
	require.NoError(t, err)
	assert.Equal(t, "success", resp.Status)

	// key_gen_dtimes/key_expire_dtimes must track the certificate's own
	// NotBefore/NotAfter, not the upload timestamp.
	require.NotNil(t, insertedAlias.KeyGenDtimes)
	require.NotNil(t, insertedAlias.KeyExpireDtimes)
	assert.WithinDuration(t, wantCert.NotBefore, *insertedAlias.KeyGenDtimes, time.Second)
	assert.WithinDuration(t, wantCert.NotAfter, *insertedAlias.KeyExpireDtimes, time.Second)
}

func TestUploadOtherDomainCertificate_RejectsDuplicateThumbprint(t *testing.T) {
	certPEM := generateUnrelatedSelfSignedCertPEM(t)
	thumbprint := thumbprintFromPEM(t, certPEM)
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return db.KeyPolicy{}, sql.ErrNoRows },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{{ID: "existing", CertThumbprint: &thumbprint}}, nil
		},
		getKeyStoreRecordFn: func(ctx context.Context, id string) (db.KeyStoreRecord, error) {
			return db.KeyStoreRecord{PrivateKey: "NA"}, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())
	_, err := svc.UploadOtherDomainCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "PARTNER", ReferenceID: "RSA_2048", CertificateData: certPEM,
	})
	assert.ErrorIs(t, err, keymanager.ErrCertificateAlreadyExists)
}

func TestUploadOtherDomainCertificate_RejectsWhenPrivateKeyExists(t *testing.T) {
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return db.KeyPolicy{}, sql.ErrNoRows },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{validAliasRow("existing")}, nil
		},
		getKeyStoreRecordFn: func(ctx context.Context, id string) (db.KeyStoreRecord, error) {
			return db.KeyStoreRecord{PrivateKey: "some-real-encrypted-key"}, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig())
	_, err := svc.UploadOtherDomainCertificate(context.Background(), keymanager.UploadCertificateRequest{
		ApplicationID: "PARTNER", ReferenceID: "RSA_2048", CertificateData: generateUnrelatedSelfSignedCertPEM(t),
	})
	assert.ErrorIs(t, err, keymanager.ErrPrivateKeyExists)
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
func TestGenerateSymmetricKey_RejectsRefIDNotInAllowList(t *testing.T) {
	cfg := testConfig()
	cfg.SymmetricKeyAllowedRefIDs = []string{"ZK_ENCRYPT"}
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), cfg)

	_, err := svc.GenerateSymmetricKey(context.Background(), keymanager.SymmetricKeyRequest{
		ApplicationID: "KERNEL", ReferenceID: "NOT_IN_ALLOW_LIST",
	})
	assert.ErrorIs(t, err, keymanager.ErrSymmetricKeyRefIDNotAllowed)
}

// TestGenerateSymmetricKey_UnconfiguredAllowListRejectsEverything covers
// the "no silent default" stance: an empty/unset allow-list must reject
// every ReferenceID, not implicitly allow all of them.
func TestGenerateSymmetricKey_UnconfiguredAllowListRejectsEverything(t *testing.T) {
	cfg := testConfig() // SymmetricKeyAllowedRefIDs left nil
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), cfg)

	_, err := svc.GenerateSymmetricKey(context.Background(), keymanager.SymmetricKeyRequest{
		ApplicationID: "KERNEL", ReferenceID: "ANYTHING",
	})
	assert.ErrorIs(t, err, keymanager.ErrSymmetricKeyRefIDNotAllowed)
}

// TestGenerateSymmetricKey_AllowsListedRefID confirms a ReferenceID that IS
// in the allow-list proceeds normally.
func TestGenerateSymmetricKey_AllowsListedRefID(t *testing.T) {
	cfg := testConfig()
	cfg.SymmetricKeyAllowedRefIDs = []string{"ZK_ENCRYPT", "VID_ENCRYPT"}
	q := &fakeQuerier{
		getKeyPolicyFn:          func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) { return nil, nil },
		insertKeyAliasFn:        func(ctx context.Context, k db.KeyAlias) error { return nil },
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), cfg)

	resp, err := svc.GenerateSymmetricKey(context.Background(), keymanager.SymmetricKeyRequest{
		ApplicationID: "KERNEL", ReferenceID: "ZK_ENCRYPT",
	})
	require.NoError(t, err)
	assert.Equal(t, "success", resp.Status)
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
