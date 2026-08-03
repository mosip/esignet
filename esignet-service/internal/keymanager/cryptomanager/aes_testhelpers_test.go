package cryptomanager_test

import (
	"testing"
	"time"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
	"github.com/mosip/esignet/internal/keymanager/db"
)

// aesTestEnv is a testEnv variant for EncryptAES/DecryptAES tests, built
// independently of newTestEnv (rather than modifying it) — widening
// keymanager.Config.SymmetricKeyAllowedRefIDs for these tests this way can
// never change any existing (asymmetric-only) test's observed behavior.
type aesTestEnv struct {
	Q     *memQuerier
	KS    *memKeyStore
	KM    *keymanager.Service
	CM    *cryptomanager.Service
	AppID string
}

// newAESTestEnv seeds policy rows for appID and "BASE" (GenerateSymmetricKey/
// ResolveCurrentSymmetricKey both go through the same currentAlias ->
// policyForKeyTier machinery asymmetric keys use, which for a non-resident
// ref id — true of any ordinary AES ref id — resolves to the "BASE" policy;
// see keymanager.Service.policyForKeyTier), and configures
// SymmetricKeyAllowedRefIDs to allowedRefIDs.
func newAESTestEnv(t *testing.T, appID string, allowedRefIDs ...string) *aesTestEnv {
	t.Helper()

	q := newMemQuerier()
	q.seedPolicy(db.KeyPolicy{AppID: appID, KeyValidityDuration: 1095, IsActive: true, PreExpireDays: 60})
	q.seedPolicy(db.KeyPolicy{AppID: "BASE", KeyValidityDuration: 730, IsActive: true, PreExpireDays: 30})

	ks := newMemKeyStore()
	kmCfg := keymanager.Config{
		AsymmetricKeyLength:       2048,
		CertCommonName:            "test.mosip.io",
		SymmetricKeyAllowedRefIDs: allowedRefIDs,
		SymmetricKeyValidity:      365 * 24 * time.Hour,
	}
	km := keymanager.NewServiceWithQuerier(q, ks, kmCfg)

	cm := cryptomanager.NewService(q, km, cryptomanager.LoadConfig())

	return &aesTestEnv{Q: q, KS: ks, KM: km, CM: cm, AppID: appID}
}

// addApp seeds a key_policy_def row for a second application on the same
// shared querier/keystore as env, and returns a *keymanager.Service
// configured to allow allowedRefIDs for that application's symmetric
// keys — for tests that need a genuine cross-application scenario (two
// different applications, each with their own key under the same
// reference id) against one shared backing store, rather than two
// unrelated in-memory stores that could never produce a real collision.
func (env *aesTestEnv) addApp(t *testing.T, appID string, allowedRefIDs ...string) *keymanager.Service {
	t.Helper()
	env.Q.seedPolicy(db.KeyPolicy{AppID: appID, KeyValidityDuration: 1095, IsActive: true, PreExpireDays: 60})
	return keymanager.NewServiceWithQuerier(env.Q, env.KS, keymanager.Config{
		AsymmetricKeyLength:       2048,
		CertCommonName:            "test.mosip.io",
		SymmetricKeyAllowedRefIDs: allowedRefIDs,
		SymmetricKeyValidity:      365 * 24 * time.Hour,
	})
}
