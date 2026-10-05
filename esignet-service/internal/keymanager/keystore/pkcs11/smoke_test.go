//go:build cgo

package pkcs11_test

import (
	"bytes"
	"crypto"
	"crypto/ecdsa"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/miekg/pkcs11"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/keymanager/keystore"
	pkcs11store "github.com/mosip/esignet/internal/keymanager/keystore/pkcs11"
)

// TestDecrypt_RSAOAEP_RoundTrip validates envelope.go's exact usage
// (RSA-OAEP/SHA-256 wrap in software, unwrap through the token) against a
// real PKCS#11 token — the fakeKeyStore used everywhere else in this
// package's tests doesn't exercise real PKCS#11 mechanisms, which is
// exactly how the CKM_RSA_PKCS_OAEP/SHA-256 incompatibility this test guards
// against went unnoticed until manual testing against real SoftHSM2. Skipped
// unless PKCS11_SMOKE_MODULE is set; run manually via:
//
//	SOFTHSM2_CONF=... PKCS11_SMOKE_MODULE=... PKCS11_SMOKE_TOKEN_LABEL=... PKCS11_SMOKE_PIN=... \
//	  go test ./internal/keymanager/keystore/pkcs11/... -run TestDecrypt_RSAOAEP_RoundTrip -v
func (ts *SmokeTestSuite) TestDecrypt_RSAOAEP_RoundTrip() {
	ks := ts.openSmokeStore()

	alias := "oaep-smoke-master"
	params := keystore.CertificateParameters{CommonName: "oaep-smoke"}
	err := ks.GenerateAndStoreAsymmetricKey(alias, alias, params, "RSA", "")
	ts.Require().NoError(err, "generate master key")
	ts.T().Cleanup(func() {
		if err := ks.DeleteKey(alias); err != nil {
			ts.T().Logf("cleanup: delete key %q: %v", alias, err)
		}
	})

	pub, err := ks.GetPublicKey(alias)
	ts.Require().NoError(err, "get public key")
	rsaPub, ok := pub.(*rsa.PublicKey)
	ts.Require().True(ok, "public key is not an *rsa.PublicKey: %T", pub)

	dek := make([]byte, 32)
	_, err = rand.Read(dek)
	ts.Require().NoError(err, "generate dek")
	// Mirrors envelopeEncrypt exactly: SHA-256 OAEP wrap in software.
	wrapped, err := rsa.EncryptOAEP(crypto.SHA256.New(), rand.Reader, rsaPub, dek, nil)
	ts.Require().NoError(err, "wrap dek")

	priv, err := ks.GetPrivateKey(alias)
	ts.Require().NoError(err, "get private key")
	decrypter, ok := priv.(crypto.Decrypter)
	ts.Require().True(ok, "private key is not a crypto.Decrypter: %T", priv)
	unwrapped, err := decrypter.Decrypt(rand.Reader, wrapped, &rsa.OAEPOptions{Hash: crypto.SHA256})
	ts.Require().NoError(err, "unwrap dek (this is the exact failure mode found against SoftHSM2 — CKM_RSA_PKCS_OAEP/SHA-256 rejected with CKR_ARGUMENTS_BAD)")
	ts.Require().True(bytes.Equal(dek, unwrapped), "unwrapped DEK does not match original")
}

// The tests below reproduce the token being logged out by some *other*
// client while the store is running — what another esignet pod's shutdown
// (C_Logout is token-wide) or a SoftHSM/pkcs11-proxy restart does in a
// deployment — and check the store re-authenticates instead of reporting
// live keys as "not found" until restart. See Store.ensureLoggedIn.

func (ts *SmokeTestSuite) TestGetSymmetricKey_RecoversAfterExternalLogout() {
	ks := ts.openSmokeStore()
	alias := ts.uniqueAlias("sym-relogin")
	ts.Require().NoError(ks.GenerateAndStoreSymmetricKey(alias), "generate symmetric key")
	ts.deleteOnCleanup(ks, alias)

	before, err := ks.GetSymmetricKey(alias)
	ts.Require().NoError(err, "get symmetric key while logged in")

	ts.logoutFromAnotherClient()

	after, err := ks.GetSymmetricKey(alias)
	ts.Require().NoError(err, "get symmetric key after external logout (the 'symmetric key alias not found' bug)")
	ts.True(bytes.Equal(before, after), "key bytes changed across re-login")
}

// TestGetSymmetricKey_ConcurrentAfterExternalLogout has more concurrent
// lookups than pooled sessions all hit the logout at once. Only one of them
// re-authenticates; the rest must notice that (Store.loginGen) and retry,
// not report the live key as "not found".
func (ts *SmokeTestSuite) TestGetSymmetricKey_ConcurrentAfterExternalLogout() {
	ks := ts.openSmokeStore()
	alias := ts.uniqueAlias("sym-relogin-concurrent")
	ts.Require().NoError(ks.GenerateAndStoreSymmetricKey(alias), "generate symmetric key")
	ts.deleteOnCleanup(ks, alias)

	for round := 0; round < 5; round++ {
		ts.logoutFromAnotherClient()
		const n = 16
		errs := make(chan error, n)
		var wg sync.WaitGroup
		for i := 0; i < n; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				_, err := ks.GetSymmetricKey(alias)
				errs <- err
			}()
		}
		wg.Wait()
		close(errs)
		for err := range errs {
			ts.Require().NoError(err, "round %d: concurrent get symmetric key after external logout", round)
		}
	}
}

func (ts *SmokeTestSuite) TestGenerateAndStoreSymmetricKey_RecoversAfterExternalLogout() {
	ks := ts.openSmokeStore()
	ts.logoutFromAnotherClient()

	alias := ts.uniqueAlias("sym-gen-relogin")
	ts.Require().NoError(ks.GenerateAndStoreSymmetricKey(alias), "generate symmetric key after external logout")
	ts.deleteOnCleanup(ks, alias)
	_, err := ks.GetSymmetricKey(alias)
	ts.Require().NoError(err, "get freshly generated symmetric key")
}

func (ts *SmokeTestSuite) TestSign_RecoversAfterExternalLogout() {
	ks := ts.openSmokeStore()
	alias := ts.uniqueAlias("ec-relogin")
	err := ks.GenerateAndStoreAsymmetricKey(alias, alias, keystore.CertificateParameters{CommonName: alias}, keystore.AlgoEC, keystore.CurveSECP256R1)
	ts.Require().NoError(err, "generate EC key")
	ts.deleteOnCleanup(ks, alias)

	// Handle obtained while logged in, then used after the logout: SoftHSM2
	// drops private objects' handles on logout (CKR_OBJECT_HANDLE_INVALID),
	// so this exercises privateKey.withKeyHandle's re-resolve on top of the
	// re-login.
	priv, err := ks.GetPrivateKey(alias)
	ts.Require().NoError(err, "get private key")
	signer, ok := priv.(crypto.Signer)
	ts.Require().True(ok, "private key is not a crypto.Signer: %T", priv)

	ts.logoutFromAnotherClient()

	digest := sha256.Sum256([]byte("relogin"))
	sig, err := signer.Sign(rand.Reader, digest[:], crypto.SHA256)
	ts.Require().NoError(err, "sign after external logout")
	pub, ok := signer.Public().(*ecdsa.PublicKey)
	ts.Require().True(ok, "public key is not an *ecdsa.PublicKey: %T", signer.Public())
	ts.True(ecdsa.VerifyASN1(pub, digest[:], sig), "signature does not verify")

	ts.logoutFromAnotherClient()
	_, err = ks.GetPrivateKey(alias)
	ts.Require().NoError(err, "look up private key after external logout")
}

func (ts *SmokeTestSuite) TestDeleteKey_AfterExternalLogout_RemovesPrivateKey() {
	ks := ts.openSmokeStore()
	alias := ts.uniqueAlias("ec-delete-relogin")
	err := ks.GenerateAndStoreAsymmetricKey(alias, alias, keystore.CertificateParameters{CommonName: alias}, keystore.AlgoEC, keystore.CurveSECP256R1)
	ts.Require().NoError(err, "generate EC key")

	ts.logoutFromAnotherClient()
	ts.Require().NoError(ks.DeleteKey(alias), "delete key after external logout")

	// Before the fix the logged-out search only saw the public objects, so
	// the private key survived the "successful" delete. Counted as a
	// logged-in client: a logged-out lookup would say "not found" either way.
	ts.Zero(ts.countObjectsAsLoggedInClient(alias), "objects (the private key) survived DeleteKey")
}

func (ts *SmokeTestSuite) TestGetSymmetricKey_MissingAlias_StillNotFound() {
	ks := ts.openSmokeStore()
	alias := ts.uniqueAlias("sym-missing")

	_, err := ks.GetSymmetricKey(alias)
	ts.Require().Error(err)
	ts.Equal(fmt.Sprintf("pkcs11: symmetric key alias %q not found", alias), err.Error())
}

// TestRelogin_RejectedPIN_StopsRetrying covers the PIN being changed on the
// token while the store runs (a rotation the deployment hasn't picked up).
// The store must try the stale PIN once and then stop: every further
// C_Login would count toward the token's lockout threshold.
func (ts *SmokeTestSuite) TestRelogin_RejectedPIN_StopsRetrying() {
	ks := ts.openSmokeStore()
	alias := ts.uniqueAlias("sym-stale-pin")
	ts.Require().NoError(ks.GenerateAndStoreSymmetricKey(alias), "generate symmetric key")

	oldPIN := os.Getenv("PKCS11_SMOKE_PIN")
	newPIN := oldPIN + "-rotated"
	ts.setPINFromAnotherClient(oldPIN, newPIN)
	// The store's login is latched off by the end of the test, so it can't
	// delete the key itself: restore the PIN and delete as another client.
	ts.T().Cleanup(func() {
		ts.setPINFromAnotherClient(newPIN, oldPIN)
		ts.deleteObjectsAsLoggedInClient(alias)
	})
	ts.logoutFromAnotherClient()

	_, err := ks.GetSymmetricKey(alias)
	ts.Require().Error(err, "lookup with a stale PIN")
	ts.Require().True(errors.Is(err, pkcs11.Error(pkcs11.CKR_PIN_INCORRECT)), "first failure should come from C_Login: %v", err)
	ts.NotContains(err.Error(), "not retrying", "first failure should be a real login attempt")

	for i := 0; i < 3; i++ {
		_, err = ks.GetSymmetricKey(alias)
		ts.Require().Error(err, "lookup %d with a stale PIN", i)
		ts.Contains(err.Error(), "not retrying until restart", "lookup %d should fail fast without calling C_Login", i)
	}
}

// openSmokeStore opens a Store on the PKCS11_SMOKE_* token, skipping the
// test if none is configured, and closes it on cleanup — the module can only
// be initialized once per process, so every test must release it.
func (ts *SmokeTestSuite) openSmokeStore() keystore.KeyStore {
	modulePath := os.Getenv("PKCS11_SMOKE_MODULE")
	if modulePath == "" {
		ts.T().Skip("PKCS11_SMOKE_MODULE not set; skipping real-PKCS#11 smoke test")
	}
	ks, err := pkcs11store.New(map[string]string{
		"module-path": modulePath,
		"token-label": os.Getenv("PKCS11_SMOKE_TOKEN_LABEL"),
		"pin":         os.Getenv("PKCS11_SMOKE_PIN"),
	})
	ts.Require().NoError(err, "open keystore")
	ts.T().Cleanup(func() {
		if err := ks.Close(); err != nil {
			ts.T().Logf("cleanup: close keystore: %v", err)
		}
	})
	return ks
}

// logoutFromAnotherClient logs the smoke token out through a session the
// Store doesn't own, the way a second client of the same token would.
// Login state is per token within the module, so this deauthenticates every
// session in the Store's pool.
func (ts *SmokeTestSuite) logoutFromAnotherClient() {
	ctx, sh := ts.openClientSession()
	defer ctx.Destroy()
	defer func() { _ = ctx.CloseSession(sh) }()
	ts.Require().NoError(ctx.Logout(sh), "logout from another client")
	info, err := ctx.GetSessionInfo(sh)
	ts.Require().NoError(err, "get session info")
	ts.Require().Equal(uint(pkcs11.CKS_RW_PUBLIC_SESSION), info.State, "token still logged in after Logout")
}

// openClientSession opens a session on the smoke token through its own
// module context, standing in for another client of the same token. The
// module is already initialized by the Store, so this reuses it
// (CKR_CRYPTOKI_ALREADY_INITIALIZED); the caller must Destroy the context
// but never Finalize it.
func (ts *SmokeTestSuite) openClientSession() (*pkcs11.Ctx, pkcs11.SessionHandle) {
	ctx := pkcs11.New(os.Getenv("PKCS11_SMOKE_MODULE"))
	ts.Require().NotNil(ctx, "load module")
	if err := ctx.Initialize(); err != nil {
		var perr pkcs11.Error
		ts.Require().True(errors.As(err, &perr) && uint(perr) == pkcs11.CKR_CRYPTOKI_ALREADY_INITIALIZED, "initialize: %v", err)
	}
	slots, err := ctx.GetSlotList(true)
	ts.Require().NoError(err, "get slot list")
	label := os.Getenv("PKCS11_SMOKE_TOKEN_LABEL")
	for _, slot := range slots {
		info, err := ctx.GetTokenInfo(slot)
		if err != nil || strings.TrimRight(info.Label, " ") != label {
			continue
		}
		sh, err := ctx.OpenSession(slot, pkcs11.CKF_SERIAL_SESSION|pkcs11.CKF_RW_SESSION)
		ts.Require().NoError(err, "open session")
		return ctx, sh
	}
	ctx.Destroy()
	ts.Require().FailNowf("token not found", "no slot with token label %q", label)
	return nil, 0
}

// setPINFromAnotherClient changes the smoke token's user PIN through a
// session the Store doesn't own, logging in with oldPIN first.
func (ts *SmokeTestSuite) setPINFromAnotherClient(oldPIN, newPIN string) {
	ctx, sh := ts.openClientSession()
	defer ctx.Destroy()
	defer func() { _ = ctx.CloseSession(sh) }()
	if err := ctx.Login(sh, pkcs11.CKU_USER, oldPIN); err != nil {
		var perr pkcs11.Error
		ts.Require().True(errors.As(err, &perr) && uint(perr) == pkcs11.CKR_USER_ALREADY_LOGGED_IN, "login: %v", err)
	}
	ts.Require().NoError(ctx.SetPIN(sh, oldPIN, newPIN), "set PIN")
}

// countObjectsAsLoggedInClient counts every object labelled alias, public
// and private, through a session the Store doesn't own, logging the token
// in first so private objects are visible regardless of the Store's view.
func (ts *SmokeTestSuite) countObjectsAsLoggedInClient(alias string) int {
	ctx, sh := ts.openClientSession()
	defer ctx.Destroy()
	defer func() { _ = ctx.CloseSession(sh) }()
	if err := ctx.Login(sh, pkcs11.CKU_USER, os.Getenv("PKCS11_SMOKE_PIN")); err != nil {
		var perr pkcs11.Error
		ts.Require().True(errors.As(err, &perr) && uint(perr) == pkcs11.CKR_USER_ALREADY_LOGGED_IN, "login: %v", err)
	}
	ts.Require().NoError(ctx.FindObjectsInit(sh, []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_LABEL, alias)}))
	defer func() { _ = ctx.FindObjectsFinal(sh) }()
	handles, _, err := ctx.FindObjects(sh, 100)
	ts.Require().NoError(err, "find objects")
	return len(handles)
}

// deleteObjectsAsLoggedInClient destroys every object labelled alias
// through a session the Store doesn't own, logging the token in first.
func (ts *SmokeTestSuite) deleteObjectsAsLoggedInClient(alias string) {
	ctx, sh := ts.openClientSession()
	defer ctx.Destroy()
	defer func() { _ = ctx.CloseSession(sh) }()
	if err := ctx.Login(sh, pkcs11.CKU_USER, os.Getenv("PKCS11_SMOKE_PIN")); err != nil {
		var perr pkcs11.Error
		ts.Require().True(errors.As(err, &perr) && uint(perr) == pkcs11.CKR_USER_ALREADY_LOGGED_IN, "login: %v", err)
	}
	ts.Require().NoError(ctx.FindObjectsInit(sh, []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_LABEL, alias)}))
	handles, _, err := ctx.FindObjects(sh, 100)
	_ = ctx.FindObjectsFinal(sh)
	ts.Require().NoError(err, "find objects")
	for _, h := range handles {
		ts.Require().NoError(ctx.DestroyObject(sh, h), "destroy object")
	}
}

func (ts *SmokeTestSuite) uniqueAlias(prefix string) string {
	return fmt.Sprintf("%s-%d", prefix, time.Now().UnixNano())
}

func (ts *SmokeTestSuite) deleteOnCleanup(ks keystore.KeyStore, alias string) {
	ts.T().Cleanup(func() {
		if err := ks.DeleteKey(alias); err != nil {
			ts.T().Logf("cleanup: delete key %q: %v", alias, err)
		}
	})
}

type SmokeTestSuite struct {
	suite.Suite
}

func TestSmokeTestSuite(t *testing.T) {
	suite.Run(t, new(SmokeTestSuite))
}
