package cryptomanager_test

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
)

func mustGenerateSymmetricKey(t *testing.T, env *aesTestEnv, refID string) {
	t.Helper()
	_, err := env.KM.GenerateSymmetricKey(context.Background(), keymanager.SymmetricKeyRequest{
		ApplicationID: env.AppID, ReferenceID: refID,
	})
	require.NoError(t, err)
}

func TestEncryptAESDecryptAESRoundTrip_BothGenerated(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_1")
	mustGenerateSymmetricKey(t, env, "AES_KEY_1")
	ctx := context.Background()
	plaintext := []byte("hello AES-GCM world")

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_1",
		Data: base64.RawURLEncoding.EncodeToString(plaintext),
	})
	require.NoError(t, err)
	require.NotEmpty(t, encResp.Data)

	decResp, err := env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_1", Data: encResp.Data,
	})
	require.NoError(t, err)
	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	require.NoError(t, err)
	require.Equal(t, plaintext, got)
}

func TestEncryptAESDecryptAESRoundTrip_CallerSuppliedNonceAndAAD(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_2")
	mustGenerateSymmetricKey(t, env, "AES_KEY_2")
	ctx := context.Background()
	plaintext := []byte("caller controls nonce and aad")

	nonce := make([]byte, 12)
	_, err := rand.Read(nonce)
	require.NoError(t, err)
	aad := []byte("my-application-context")
	nonceB64 := base64.RawURLEncoding.EncodeToString(nonce)
	aadB64 := base64.RawURLEncoding.EncodeToString(aad)

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_2",
		Data: base64.RawURLEncoding.EncodeToString(plaintext), Nonce: nonceB64, AAD: aadB64,
	})
	require.NoError(t, err)

	// Envelope must be exactly uniIdent(40) + ciphertext+tag — neither
	// nonce nor aad was generated, so neither should be embedded.
	raw, err := base64.RawURLEncoding.DecodeString(encResp.Data)
	require.NoError(t, err)
	require.Equal(t, cryptomanager.SymmetricUniIdentLength+len(plaintext)+16 /* GCM tag */, len(raw))

	decResp, err := env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_2", Data: encResp.Data,
		Nonce: nonceB64, AAD: aadB64,
	})
	require.NoError(t, err)
	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	require.NoError(t, err)
	require.Equal(t, plaintext, got)
}

func TestEncryptAESDecryptAESRoundTrip_MixedNonceGeneratedAADSupplied(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_3")
	mustGenerateSymmetricKey(t, env, "AES_KEY_3")
	ctx := context.Background()
	plaintext := []byte("nonce generated, aad supplied")
	aad := []byte("fixed-aad-context")
	aadB64 := base64.RawURLEncoding.EncodeToString(aad)

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_3",
		Data: base64.RawURLEncoding.EncodeToString(plaintext), AAD: aadB64,
		// Nonce omitted -> generated and embedded.
	})
	require.NoError(t, err)

	// Decrypt must mirror exactly: supply the same AAD, omit Nonce so it's
	// extracted from the envelope.
	decResp, err := env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_3", Data: encResp.Data, AAD: aadB64,
	})
	require.NoError(t, err)
	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	require.NoError(t, err)
	require.Equal(t, plaintext, got)
}

func TestEncryptAES_RejectsWhenNoKeyExists(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_NONE")
	// Deliberately never generated.
	_, err := env.CM.EncryptAES(context.Background(), cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_NONE", Data: "aGVsbG8",
	})
	require.ErrorIs(t, err, cryptomanager.ErrSymmetricKeyNotFound)
}

func TestEncryptAES_RejectsRefIDNotInAllowList(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "ALLOWED_ONLY")
	_, err := env.CM.EncryptAES(context.Background(), cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "NOT_ALLOWED", Data: "aGVsbG8",
	})
	require.ErrorIs(t, err, keymanager.ErrSymmetricKeyRefIDNotAllowed)
}

func TestEncryptAES_ValidatesApplicationIDBeforeReferenceIDBeforeData(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_4")
	_, err := env.CM.EncryptAES(context.Background(), cryptomanager.EncryptAESRequest{
		ApplicationID: "", ReferenceID: "", Data: "",
	})
	require.ErrorIs(t, err, cryptomanager.ErrBlankApplicationID)

	_, err = env.CM.EncryptAES(context.Background(), cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "", Data: "",
	})
	require.ErrorIs(t, err, cryptomanager.ErrBlankReferenceID)
}

func TestEncryptAES_RejectsBlankData(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_5")
	mustGenerateSymmetricKey(t, env, "AES_KEY_5")
	_, err := env.CM.EncryptAES(context.Background(), cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_5", Data: "   ",
	})
	require.ErrorIs(t, err, cryptomanager.ErrInvalidRequest)
}

func TestDecryptAES_RejectsWrongReferenceID_KeyExistsElsewhere(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_A", "AES_KEY_B")
	mustGenerateSymmetricKey(t, env, "AES_KEY_A")
	mustGenerateSymmetricKey(t, env, "AES_KEY_B")
	ctx := context.Background()

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_A", Data: base64.RawURLEncoding.EncodeToString([]byte("secret")),
	})
	require.NoError(t, err)

	// The uni_ident embedded in the envelope resolves to a real key_alias
	// row (globally) — it just belongs to AES_KEY_A, not the AES_KEY_B
	// this decrypt call asks for. Must be reported as a distinct mismatch,
	// not conflated with "no such key exists at all".
	_, err = env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_B", Data: encResp.Data,
	})
	require.ErrorIs(t, err, cryptomanager.ErrKeyIdentifierMismatch)
	require.NotErrorIs(t, err, cryptomanager.ErrSymmetricKeyNotFound)
	// Must not leak which app/ref the key actually belongs to.
	require.Equal(t, "mismatch of application id and reference id", err.Error())
}

func TestDecryptAES_RejectsWrongApplicationID_KeyExistsElsewhere(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_C")
	mustGenerateSymmetricKey(t, env, "AES_KEY_C")
	ctx := context.Background()

	// A second, genuinely different application, sharing the same
	// backing store, also has a key under the same reference id.
	otherKM := env.addApp(t, "OTHERAPP", "AES_KEY_C")
	_, err := otherKM.GenerateSymmetricKey(ctx, keymanager.SymmetricKeyRequest{
		ApplicationID: "OTHERAPP", ReferenceID: "AES_KEY_C",
	})
	require.NoError(t, err)

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_C", Data: base64.RawURLEncoding.EncodeToString([]byte("secret")),
	})
	require.NoError(t, err)

	_, err = env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: "OTHERAPP", ReferenceID: "AES_KEY_C", Data: encResp.Data,
	})
	require.ErrorIs(t, err, cryptomanager.ErrKeyIdentifierMismatch)
}

func TestDecryptAES_RejectsWhenKeyDoesNotExistAnywhere(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_D")
	mustGenerateSymmetricKey(t, env, "AES_KEY_D")
	ctx := context.Background()

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_D", Data: base64.RawURLEncoding.EncodeToString([]byte("secret")),
	})
	require.NoError(t, err)

	// Tamper the embedded unique identifier itself so it can never match
	// any real key_alias row anywhere — genuine "not found", not a mismatch.
	raw, err := base64.RawURLEncoding.DecodeString(encResp.Data)
	require.NoError(t, err)
	tampered := append([]byte{}, raw...)
	tampered[0] ^= 0xFF // first byte of the 40-byte uni_ident field
	tamperedB64 := base64.RawURLEncoding.EncodeToString(tampered)

	_, err = env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_D", Data: tamperedB64,
	})
	require.ErrorIs(t, err, cryptomanager.ErrSymmetricKeyNotFound)
	require.NotErrorIs(t, err, cryptomanager.ErrKeyIdentifierMismatch)
}

func TestDecryptAES_RejectsTamperedCiphertext(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_6")
	mustGenerateSymmetricKey(t, env, "AES_KEY_6")
	ctx := context.Background()

	encResp, err := env.CM.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_6", Data: base64.RawURLEncoding.EncodeToString([]byte("tamper me")),
	})
	require.NoError(t, err)

	raw, err := base64.RawURLEncoding.DecodeString(encResp.Data)
	require.NoError(t, err)
	tampered := append([]byte{}, raw...)
	tampered[len(tampered)-1] ^= 0xFF // flip the last byte of the GCM tag
	tamperedB64 := base64.RawURLEncoding.EncodeToString(tampered)

	_, err = env.CM.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_6", Data: tamperedB64,
	})
	require.Error(t, err)
}

func TestDecryptAES_RejectsMalformedEnvelope(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_7")
	mustGenerateSymmetricKey(t, env, "AES_KEY_7")
	_, err := env.CM.DecryptAES(context.Background(), cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_7", Data: "dG9vLXNob3J0", // "too-short", well under 40 bytes
	})
	require.ErrorIs(t, err, cryptomanager.ErrEnvelopeMalformed)
}

func TestDecryptAES_RejectsBlankData(t *testing.T) {
	env := newAESTestEnv(t, "TESTAPP", "AES_KEY_8")
	mustGenerateSymmetricKey(t, env, "AES_KEY_8")
	_, err := env.CM.DecryptAES(context.Background(), cryptomanager.DecryptAESRequest{
		ApplicationID: env.AppID, ReferenceID: "AES_KEY_8", Data: "",
	})
	require.ErrorIs(t, err, cryptomanager.ErrInvalidRequest)
}

func TestBuildParseAESEnvelopeRoundTrip(t *testing.T) {
	const uniIdent = "ABCDEF0123456789ABCDEF0123456789ABCDEF01" // 40 chars
	require.Len(t, uniIdent, cryptomanager.SymmetricUniIdentLength)
	nonce := make([]byte, 12)
	_, err := rand.Read(nonce)
	require.NoError(t, err)
	aad := make([]byte, 32)
	_, err = rand.Read(aad)
	require.NoError(t, err)
	ciphertext := []byte("some ciphertext bytes")

	envelope := cryptomanager.BuildAESEnvelope(uniIdent, nonce, aad, true, true, ciphertext)
	gotUniIdent, gotNonce, gotAAD, gotCiphertext, err := cryptomanager.ParseAESEnvelope(envelope, true, true)
	require.NoError(t, err)
	require.Equal(t, uniIdent, gotUniIdent)
	require.Equal(t, nonce, gotNonce)
	require.Equal(t, aad, gotAAD)
	require.Equal(t, ciphertext, gotCiphertext)
}
