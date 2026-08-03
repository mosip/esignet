package keymanager_test

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/db"
)

func TestResolveKeyType(t *testing.T) {
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
		t.Run(tt.refID, func(t *testing.T) {
			algo, curve := keymanager.ResolveKeyType(tt.refID)
			assert.Equal(t, tt.wantAlgo, algo)
			assert.Equal(t, tt.wantCurve, curve)
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

func TestResolveSignKeyAlias_Root(t *testing.T) {
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), keymanager.Config{})
	alias, err := keymanager.ResolveSignKeyAlias(svc, context.Background(), "ROOT", "")
	require.NoError(t, err)
	assert.Equal(t, "", alias) // self-signed
}

func TestResolveSignKeyAlias_ComponentMaster(t *testing.T) {
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ROOT" && refID == "" {
				return []db.KeyAlias{validAliasRow("root-alias-id")}, nil
			}
			return nil, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), keymanager.Config{})
	alias, err := keymanager.ResolveSignKeyAlias(svc, context.Background(), "ESIGNET_RSA", "RSA_2048")
	require.NoError(t, err)
	assert.Equal(t, "root-alias-id", alias)
}

func TestResolveSignKeyAlias_ComponentMasterMissing(t *testing.T) {
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return nil, nil // ROOT not found
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), keymanager.Config{})
	_, err := keymanager.ResolveSignKeyAlias(svc, context.Background(), "ESIGNET_RSA", "RSA_2048")
	assert.ErrorIs(t, err, keymanager.ErrRootKeyNotFound)
}

func TestResolveSignKeyAlias_ComponentEncryption(t *testing.T) {
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			if appID == "ESIGNET_RSA" && refID == "RSA_2048" {
				return []db.KeyAlias{validAliasRow("component-master-alias-id")}, nil
			}
			return nil, nil
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), keymanager.Config{})
	alias, err := keymanager.ResolveSignKeyAlias(svc, context.Background(), "ESIGNET_RSA", "XYZ")
	require.NoError(t, err)
	assert.Equal(t, "component-master-alias-id", alias)
}

func TestResolveSignKeyAlias_ComponentEncryptionMasterMissing(t *testing.T) {
	q := &fakeQuerier{
		getKeyPolicyFn: func(ctx context.Context, appID string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(ctx context.Context, appID, refID string) ([]db.KeyAlias, error) {
			return nil, nil // component master not found
		},
	}
	svc := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), keymanager.Config{})
	_, err := keymanager.ResolveSignKeyAlias(svc, context.Background(), "ESIGNET_RSA", "XYZ")
	assert.ErrorIs(t, err, keymanager.ErrComponentMasterKeyNotFound)
}
