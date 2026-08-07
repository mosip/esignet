package keymanager_test

import (
	"context"
	"testing"
	"time"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/db"
)

func (ts *KeymanagerTestSuite) TestResolveKeyType() {
	tests := []struct {
		refID     string
		wantAlgo  string
		wantCurve string
	}{
		{"RSA_2048", "RSA", ""},
		{"EC_SECP256K1_SIGN", "EC", "SECP256K1"},
		{"EC_SECP256R1_SIGN", "EC", "SECP256R1"},
		{"ED25519_SIGN", "EC", "ED25519"},
		{"SIGN", "RSA", ""},
		{"", "RSA", ""},
		{"SOME_APP_ENCRYPTION_KEY", "RSA", ""},
	}
	for _, tt := range tests {
		ts.T().Run(tt.refID, func(t *testing.T) {
			algo, curve := keymanager.ResolveKeyType(tt.refID)
			ts.Assert().Equal(tt.wantAlgo, algo)
			ts.Assert().Equal(tt.wantCurve, curve)
		})
	}
}

func validAliasRow(id string) db.KeyAlias {
	now := time.Now().UTC()
	gen := now.Add(-24 * time.Hour)
	expire := now.Add(24 * 365 * time.Hour)
	return db.KeyAlias{ID: id, KeyGenDtimes: &gen, KeyExpireDtimes: &expire}
}

func alwaysActivePolicy() db.KeyPolicy {
	return db.KeyPolicy{KeyValidityDuration: 3650, PreExpireDays: 30, IsActive: true}
}

func (ts *KeymanagerTestSuite) TestResolveSignKeyAlias_Root() {
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), keymanager.Config{})
	alias, err := keymanager.ResolveSignKeyAlias(svc, context.Background(), "ROOT", "")
	ts.Require().NoError(err)
	ts.Assert().Equal("", alias) // self-signed
}

func (ts *KeymanagerTestSuite) TestResolveSignKeyAlias_ComponentMaster() {
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ROOT" && refID == "" {
				return []db.KeyAlias{validAliasRow("root-alias-id")}, nil
			}
			return nil, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), keymanager.Config{})
	alias, err := keymanager.ResolveSignKeyAlias(svc, context.Background(), "ESIGNET_RSA", "RSA_2048")
	ts.Require().NoError(err)
	ts.Assert().Equal("root-alias-id", alias)
}

func (ts *KeymanagerTestSuite) TestResolveSignKeyAlias_ComponentMasterMissing() {
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return nil, nil // ROOT not found
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), keymanager.Config{})
	_, err := keymanager.ResolveSignKeyAlias(svc, context.Background(), "ESIGNET_RSA", "RSA_2048")
	ts.Assert().ErrorIs(err, keymanager.ErrRootKeyNotFound)
}

func (ts *KeymanagerTestSuite) TestResolveSignKeyAlias_ComponentEncryption() {
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ESIGNET_RSA" && refID == "RSA_2048" {
				return []db.KeyAlias{validAliasRow("component-master-alias-id")}, nil
			}
			return nil, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), keymanager.Config{})
	alias, err := keymanager.ResolveSignKeyAlias(svc, context.Background(), "ESIGNET_RSA", "XYZ")
	ts.Require().NoError(err)
	ts.Assert().Equal("component-master-alias-id", alias)
}

func (ts *KeymanagerTestSuite) TestResolveSignKeyAlias_ComponentEncryptionMasterMissing() {
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return nil, nil // component master not found
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), keymanager.Config{})
	_, err := keymanager.ResolveSignKeyAlias(svc, context.Background(), "ESIGNET_RSA", "XYZ")
	ts.Assert().ErrorIs(err, keymanager.ErrComponentMasterKeyNotFound)
}
