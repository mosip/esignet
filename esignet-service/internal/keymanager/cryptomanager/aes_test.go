package cryptomanager_test

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"testing"
	"time"

	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
	"github.com/mosip/esignet/internal/keymanager/db"
)

func mustGenerateSymmetricKey(t *testing.T, env *aesTestEnv, refID string) {
	t.Helper()
	_, err := env.KM.GenerateSymmetricKey(context.Background(), keymanager.SymmetricKeyRequest{
		ApplicationID: env.AppID, ReferenceID: refID,
	})
	require.NoError(t, err)
}

func (ts *CryptomanagerTestSuite) TestEncryptAESDecryptAESRoundTrip_BothGenerated() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_1")
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_1")
	ctx := context.Background()
	plaintext := []byte("hello AES-GCM world")

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_1",
		Data: base64.RawURLEncoding.EncodeToString(plaintext),
	})
	ts.Require().NoError(err)
	ts.Require().NotEmpty(encResp.Data)

	decResp, err := env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_1", Data: encResp.Data,
	})
	ts.Require().NoError(err)
	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	ts.Require().NoError(err)
	ts.Require().Equal(plaintext, got)
}

func (ts *CryptomanagerTestSuite) TestEncryptAESDecryptAESRoundTrip_CallerSuppliedNonceAndAAD() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_2")
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_2")
	ctx := context.Background()
	plaintext := []byte("caller controls nonce and aad")

	nonce := make([]byte, 12)
	_, err := rand.Read(nonce)
	ts.Require().NoError(err)
	aad := []byte("my-application-context")
	nonceB64 := base64.RawURLEncoding.EncodeToString(nonce)
	aadB64 := base64.RawURLEncoding.EncodeToString(aad)

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_2",
		Data: base64.RawURLEncoding.EncodeToString(plaintext), Nonce: nonceB64, AAD: aadB64,
	})
	ts.Require().NoError(err)

	// Envelope must be exactly uniIdent(40) + ciphertext+tag — neither
	// nonce nor aad was generated, so neither should be embedded.
	raw, err := base64.RawURLEncoding.DecodeString(encResp.Data)
	ts.Require().NoError(err)
	ts.Require().Equal(cryptomanager.SymmetricUniIdentLength+len(plaintext)+16 /* GCM tag */, len(raw))

	decResp, err := env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_2", Data: encResp.Data,
		Nonce: nonceB64, AAD: aadB64,
	})
	ts.Require().NoError(err)
	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	ts.Require().NoError(err)
	ts.Require().Equal(plaintext, got)
}

func (ts *CryptomanagerTestSuite) TestEncryptAESDecryptAESRoundTrip_MixedNonceGeneratedAADSupplied() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_3")
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_3")
	ctx := context.Background()
	plaintext := []byte("nonce generated, aad supplied")
	aad := []byte("fixed-aad-context")
	aadB64 := base64.RawURLEncoding.EncodeToString(aad)

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_3",
		Data: base64.RawURLEncoding.EncodeToString(plaintext), AAD: aadB64,
		// Nonce omitted -> generated and embedded.
	})
	ts.Require().NoError(err)

	// Decrypt must mirror exactly: supply the same AAD, omit Nonce so it's
	// extracted from the envelope.
	decResp, err := env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_3", Data: encResp.Data, AAD: aadB64,
	})
	ts.Require().NoError(err)
	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	ts.Require().NoError(err)
	ts.Require().Equal(plaintext, got)
}

func (ts *CryptomanagerTestSuite) TestEncryptAES_RejectsWhenNoKeyExists() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_NONE")
	// Deliberately never generated.
	_, err := env.CM.EncryptAES(context.Background(), cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_NONE", Data: "aGVsbG8",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrSymmetricKeyNotFound)
}

func (ts *CryptomanagerTestSuite) TestEncryptAES_RejectsRefIDNotInAllowList() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "ALLOWED_ONLY")
	_, err := env.CM.EncryptAES(context.Background(), cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "NOT_ALLOWED", Data: "aGVsbG8",
	})
	ts.Require().ErrorIs(err, keymanager.ErrSymmetricKeyRefIDNotAllowed)
}

// TestEncryptAES_RejectsCallerSuppliedNonceNotInAllowList covers the "no
// silent default" stance for a caller-supplied Nonce: a ReferenceID that is
// allowed to hold a symmetric key (and would succeed with a generated
// nonce) must still be rejected the moment the caller supplies their own
// nonce, unless that ReferenceID is also in
// Config.CallerNonceAllowedRefIDs — a caller-supplied nonce reused under
// the same key breaks AES-GCM confidentiality.
func (ts *CryptomanagerTestSuite) TestEncryptAES_RejectsCallerSuppliedNonceNotInAllowList() {
	q := newMemQuerier()
	q.seedPolicy(db.KeyPolicy{AppID: "TESTAPP", KeyValidityDuration: 1095, IsActive: true, PreExpireDays: 60})
	q.seedPolicy(db.KeyPolicy{AppID: "BASE", KeyValidityDuration: 730, IsActive: true, PreExpireDays: 30})
	ks := newMemKeyStore()
	km := keymanager.NewServiceWithQuerier(q, ks, keymanager.Config{
		AsymmetricKeyLength:       2048,
		CertCommonName:            "test.mosip.io",
		SymmetricKeyAllowedRefIDs: []string{"AES_KEY_6"},
		SymmetricKeyValidity:      365 * 24 * time.Hour,
	})
	cm := cryptomanager.NewService(q, km, cryptomanager.Config{}) // CallerNonceAllowedRefIDs left nil
	env := &aesTestEnv{Q: q, KS: ks, KM: km, CM: cm, AppID: "TESTAPP"}
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_6")

	nonce := make([]byte, 12)
	_, err := rand.Read(nonce)
	ts.Require().NoError(err)

	_, err = env.CM.EncryptAES(context.Background(), cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_6",
		Data: base64.RawURLEncoding.EncodeToString([]byte("hello")), Nonce: base64.RawURLEncoding.EncodeToString(nonce),
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrCallerNonceNotAllowed)
}

func (ts *CryptomanagerTestSuite) TestEncryptAES_ValidatesApplicationIDBeforeReferenceIDBeforeData() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_4")
	_, err := env.CM.EncryptAES(context.Background(), cryptomanager.EncryptAESRequest{
		ApplicationID: "", ReferenceID: "", Data: "",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrBlankApplicationID)

	_, err = env.CM.EncryptAES(context.Background(), cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "", Data: "",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrBlankReferenceID)
}

func (ts *CryptomanagerTestSuite) TestEncryptAES_RejectsBlankData() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_5")
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_5")
	_, err := env.CM.EncryptAES(context.Background(), cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_5", Data: "   ",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrInvalidRequest)
}

func (ts *CryptomanagerTestSuite) TestDecryptAES_RejectsWrongReferenceID_KeyExistsElsewhere() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_A", "AES_KEY_B")
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_A")
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_B")
	ctx := context.Background()

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_A", Data: base64.RawURLEncoding.EncodeToString([]byte("secret")),
	})
	ts.Require().NoError(err)

	// The uni_ident embedded in the envelope resolves to a real key_alias
	// row (globally) — it just belongs to AES_KEY_A, not the AES_KEY_B
	// this decrypt call asks for. Must be reported as a distinct mismatch,
	// not conflated with "no such key exists at all".
	_, err = env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_B", Data: encResp.Data,
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrKeyIdentifierMismatch)
	ts.Require().NotErrorIs(err, cryptomanager.ErrSymmetricKeyNotFound)
	// Must not leak which app/ref the key actually belongs to.
	ts.Require().Equal("mismatch of application id and reference id", err.Error())
}

func (ts *CryptomanagerTestSuite) TestDecryptAES_RejectsWrongApplicationID_KeyExistsElsewhere() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_C")
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_C")
	ctx := context.Background()

	// A second, genuinely different application, sharing the same
	// backing store, also has a key under the same reference id.
	otherKM := env.addApp(ts.T(), "OTHERAPP", "AES_KEY_C")
	_, err := otherKM.GenerateSymmetricKey(ctx, keymanager.SymmetricKeyRequest{
		ApplicationID: "OTHERAPP", ReferenceID: "AES_KEY_C",
	})
	ts.Require().NoError(err)

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_C", Data: base64.RawURLEncoding.EncodeToString([]byte("secret")),
	})
	ts.Require().NoError(err)

	_, err = env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: "OTHERAPP", ReferenceID: "AES_KEY_C", Data: encResp.Data,
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrKeyIdentifierMismatch)
}

func (ts *CryptomanagerTestSuite) TestDecryptAES_RejectsWhenKeyDoesNotExistAnywhere() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_D")
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_D")
	ctx := context.Background()

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_D", Data: base64.RawURLEncoding.EncodeToString([]byte("secret")),
	})
	ts.Require().NoError(err)

	// Tamper the embedded unique identifier itself so it can never match
	// any real key_alias row anywhere — genuine "not found", not a mismatch.
	raw, err := base64.RawURLEncoding.DecodeString(encResp.Data)
	ts.Require().NoError(err)
	tampered := append([]byte{}, raw...)
	tampered[0] ^= 0xFF // first byte of the 40-byte uni_ident field
	tamperedB64 := base64.RawURLEncoding.EncodeToString(tampered)

	_, err = env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_D", Data: tamperedB64,
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrSymmetricKeyNotFound)
	ts.Require().NotErrorIs(err, cryptomanager.ErrKeyIdentifierMismatch)
}

func (ts *CryptomanagerTestSuite) TestDecryptAES_RejectsTamperedCiphertext() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_6")
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_6")
	ctx := context.Background()

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_6", Data: base64.RawURLEncoding.EncodeToString([]byte("tamper me")),
	})
	ts.Require().NoError(err)

	raw, err := base64.RawURLEncoding.DecodeString(encResp.Data)
	ts.Require().NoError(err)
	tampered := append([]byte{}, raw...)
	tampered[len(tampered)-1] ^= 0xFF // flip the last byte of the GCM tag
	tamperedB64 := base64.RawURLEncoding.EncodeToString(tampered)

	_, err = env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_6", Data: tamperedB64,
	})
	ts.Require().Error(err)
}

func (ts *CryptomanagerTestSuite) TestDecryptAES_RejectsMalformedEnvelope() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_7")
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_7")
	_, err := env.CM.DecryptAES(context.Background(), cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_7", Data: "dG9vLXNob3J0", // "too-short", well under 40 bytes
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrEnvelopeMalformed)
}

func (ts *CryptomanagerTestSuite) TestDecryptAES_RejectsBlankData() {
	env := newAESTestEnv(ts.T(), "TESTAPP", "AES_KEY_8")
	mustGenerateSymmetricKey(ts.T(), env, "AES_KEY_8")
	_, err := env.CM.DecryptAES(context.Background(), cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_8", Data: "",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrInvalidRequest)
}

func (ts *CryptomanagerTestSuite) TestBuildParseAESEnvelopeRoundTrip() {
	const uniIdent = "ABCDEF0123456789ABCDEF0123456789ABCDEF01" // 40 chars
	ts.Require().Len(uniIdent, cryptomanager.SymmetricUniIdentLength)
	nonce := make([]byte, 12)
	_, err := rand.Read(nonce)
	ts.Require().NoError(err)
	aad := make([]byte, 32)
	_, err = rand.Read(aad)
	ts.Require().NoError(err)
	ciphertext := []byte("some ciphertext bytes")

	envelope := cryptomanager.BuildAESEnvelope(uniIdent, nonce, aad, true, true, ciphertext)
	gotUniIdent, gotNonce, gotAAD, gotCiphertext, err := cryptomanager.ParseAESEnvelope(envelope, true, true)
	ts.Require().NoError(err)
	ts.Require().Equal(uniIdent, gotUniIdent)
	ts.Require().Equal(nonce, gotNonce)
	ts.Require().Equal(aad, gotAAD)
	ts.Require().Equal(ciphertext, gotCiphertext)
}
