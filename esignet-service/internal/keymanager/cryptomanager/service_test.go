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

func TestEncryptDecryptRoundTrip(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	ctx := context.Background()

	cases := []struct {
		name string
		size int
	}{
		// Not 0: a zero-length plaintext base64-encodes to "", which is
		// correctly rejected as blank input — see TestEncrypt_RejectsBlankData.
		// single-byte is fixed (not random): a fully random single byte has
		// a ~2.3% chance (6/256 whitespace byte values) of legitimately
		// tripping the same blank-content rejection, which would make this
		// case flaky — a single whitespace byte genuinely has nothing but
		// whitespace after trimming, so that isn't a bug, just a bad fit for
		// a size-focused round-trip test. "small"/"large" stay random: the
		// odds of an entire 17- or 250,000-byte buffer being whitespace-only
		// are negligible.
		{"single-byte", 1},
		{"small", 17},
		{"large", 250_000},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			plaintext := make([]byte, tc.size)
			if tc.name == "single-byte" {
				plaintext[0] = 'A'
			} else {
				_, err := rand.Read(plaintext)
				require.NoError(t, err)
			}
			dataB64 := base64.RawURLEncoding.EncodeToString(plaintext)

			encResp, err := env.CM.Encrypt(ctx, cryptomanager.EncryptRequest{
				ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: dataB64,
			})
			require.NoError(t, err)
			require.NotEmpty(t, encResp.Data)

			decResp, err := env.CM.Decrypt(ctx, cryptomanager.DecryptRequest{
				ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: encResp.Data,
			})
			require.NoError(t, err)

			got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
			require.NoError(t, err)
			require.Equal(t, plaintext, got)
		})
	}
}

func TestEncrypt_RejectsBlankReferenceID(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "", Data: "ZGF0YQ",
	})
	require.ErrorIs(t, err, cryptomanager.ErrBlankReferenceID)
}

func TestEncrypt_RejectsBlankApplicationID(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: "", ReferenceID: "SOME_ENC_KEY", Data: "ZGF0YQ",
	})
	require.ErrorIs(t, err, cryptomanager.ErrBlankApplicationID)
}

func TestEncrypt_ValidatesApplicationIDBeforeReferenceIDBeforeData(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	// All three are invalid at once — ApplicationID must be reported first.
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: "", ReferenceID: "", Data: "",
	})
	require.ErrorIs(t, err, cryptomanager.ErrBlankApplicationID)

	// ApplicationID valid, ReferenceID and Data both invalid — ReferenceID
	// must be reported next, before Data is ever looked at.
	_, err = env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "", Data: "",
	})
	require.ErrorIs(t, err, cryptomanager.ErrBlankReferenceID)
}

func TestEncrypt_RejectsMasterKeyTarget(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: env.AppID, ReferenceID: keymanager.RefIDRSA2048, Data: "ZGF0YQ",
	})
	require.ErrorIs(t, err, cryptomanager.ErrEncryptionAgainstReservedKey)
	// Error message must be a plain, static message — no echoed appID/refID.
	require.Equal(t, "not allowed to use Component Master Key/Root for encryption purpose", err.Error())
}

func TestEncrypt_RejectsRootTarget(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: keymanager.AppIDRoot, ReferenceID: "anything", Data: "ZGF0YQ",
	})
	require.ErrorIs(t, err, cryptomanager.ErrEncryptionAgainstReservedKey)
}

func TestEncrypt_RejectsInvalidBase64Data(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: "not base64!!!",
	})
	require.ErrorIs(t, err, cryptomanager.ErrInvalidData)
}

func TestEncrypt_RejectsBlankData(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	for _, tc := range []struct {
		name string
		data string
	}{
		{"empty string", ""},
		{"only spaces", "   "},
		{"only tabs and newlines", "\t\n  \t"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
				ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: tc.data,
			})
			require.ErrorIs(t, err, cryptomanager.ErrInvalidRequest)
		})
	}
}

func TestEncrypt_RejectsWhitespaceOnlyDecodedData(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	for _, tc := range []struct {
		name      string
		plaintext string
	}{
		{"three spaces", "   "},
		{"tabs and newlines", "\t\n\t"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			// The base64 STRING itself is well-formed and non-blank (this
			// is exactly what a CLI/caller gets from base64-encoding
			// whitespace) — only the decoded content is blank.
			data := base64.RawURLEncoding.EncodeToString([]byte(tc.plaintext))
			require.NotEmpty(t, data)

			_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
				ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: data,
			})
			require.ErrorIs(t, err, cryptomanager.ErrInvalidRequest)
		})
	}
}

func TestDecrypt_RejectsBlankApplicationID(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.Decrypt(context.Background(), cryptomanager.DecryptRequest{
		ApplicationID: "", ReferenceID: "SOME_ENC_KEY", Data: "irrelevant-checked-after-appid",
	})
	require.ErrorIs(t, err, cryptomanager.ErrBlankApplicationID)
}

func TestDecrypt_RejectsMalformedEnvelope(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.Decrypt(context.Background(), cryptomanager.DecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: "not-a-real-envelope",
	})
	require.ErrorIs(t, err, cryptomanager.ErrEnvelopeMalformed)
}

func TestDecrypt_RejectsBlankData(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	// Previously fell through to parseEnvelope and surfaced the generic
	// "envelope is malformed: splitter not found" instead of a clear
	// blank-data error.
	_, err := env.CM.Decrypt(context.Background(), cryptomanager.DecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: "",
	})
	require.ErrorIs(t, err, cryptomanager.ErrInvalidRequest)
}

func TestDecrypt_DifferentReferenceIDFails(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	ctx := context.Background()

	encResp, err := env.CM.Encrypt(ctx, cryptomanager.EncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "ENC_KEY_A", Data: base64.RawURLEncoding.EncodeToString([]byte("secret")),
	})
	require.NoError(t, err)

	_, err = env.CM.Decrypt(ctx, cryptomanager.DecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "ENC_KEY_B", Data: encResp.Data,
	})
	require.ErrorIs(t, err, cryptomanager.ErrKeyIdentifierMismatch)
	// Error message must not disclose which (appID, refID) the thumbprint
	// actually resolved to.
	require.Equal(t, "mismatch of application id and reference id", err.Error())
	require.NotContains(t, err.Error(), "ENC_KEY_A")
}
