package cryptomanager_test

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
)

func TestSymmetricEncryptDecryptRoundTrip(t *testing.T) {
	cases := []struct {
		name string
		size int
	}{
		{"empty", 0},
		{"small", 13},
		{"exactly-one-block", 16},
		{"large", 100_000},
	}
	sessionKey := make([]byte, 32)
	_, err := rand.Read(sessionKey)
	require.NoError(t, err)

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			plaintext := make([]byte, tc.size)
			_, err := rand.Read(plaintext)
			require.NoError(t, err)

			ciphertext, err := cryptomanager.SymmetricEncrypt(sessionKey, plaintext)
			require.NoError(t, err)
			require.NotEqual(t, plaintext, ciphertext)

			decrypted, err := cryptomanager.SymmetricDecrypt(sessionKey, ciphertext)
			require.NoError(t, err)
			require.True(t, bytes.Equal(plaintext, decrypted))
		})
	}
}

func TestSymmetricEncrypt_FreshAADEachCall(t *testing.T) {
	sessionKey := make([]byte, 32)
	_, err := rand.Read(sessionKey)
	require.NoError(t, err)
	plaintext := []byte("same plaintext every time")

	c1, err := cryptomanager.SymmetricEncrypt(sessionKey, plaintext)
	require.NoError(t, err)
	c2, err := cryptomanager.SymmetricEncrypt(sessionKey, plaintext)
	require.NoError(t, err)
	require.NotEqual(t, c1, c2, "each call must use a freshly generated 32-byte AAD, so identical plaintext must not produce identical ciphertext")
}

func TestSymmetricDecrypt_TooShort(t *testing.T) {
	sessionKey := make([]byte, 32)
	_, err := cryptomanager.SymmetricDecrypt(sessionKey, []byte("too short"))
	require.Error(t, err)
}

func TestBuildParseEnvelopeRoundTrip(t *testing.T) {
	const splitter = "#KEY_SPLITTER#"
	var thumbprint [32]byte
	_, err := rand.Read(thumbprint[:])
	require.NoError(t, err)
	encryptedSessionKey := make([]byte, 256) // RSA-2048-OAEP ciphertext length
	_, err = rand.Read(encryptedSessionKey)
	require.NoError(t, err)
	encryptedData := []byte("arbitrary encrypted payload bytes")

	envelope := cryptomanager.BuildEnvelope(splitter, thumbprint, encryptedSessionKey, encryptedData)
	require.NotEmpty(t, envelope)

	gotThumbHex, gotEncKey, gotData, err := cryptomanager.ParseEnvelope(splitter, envelope)
	require.NoError(t, err)
	require.Equal(t, encryptedSessionKey, gotEncKey)
	require.Equal(t, encryptedData, gotData)

	require.Equal(t, hex.EncodeToString(thumbprint[:]), gotThumbHex)
}

func TestParseEnvelope_SplitterNotFound(t *testing.T) {
	_, _, _, err := cryptomanager.ParseEnvelope("#KEY_SPLITTER#", "bm90LWFuLWVudmVsb3Bl")
	require.ErrorIs(t, err, cryptomanager.ErrEnvelopeMalformed)
}

func TestParseEnvelope_MalformedBase64(t *testing.T) {
	_, _, _, err := cryptomanager.ParseEnvelope("#KEY_SPLITTER#", "not valid base64url!!!")
	require.ErrorIs(t, err, cryptomanager.ErrEnvelopeMalformed)
}

func TestParseEnvelope_LegacyFormatUnsupported(t *testing.T) {
	const splitter = "#KEY_SPLITTER#"
	// BuildEnvelope always prepends the VER_R2 header, so what's "wrong"
	// here is the length of the wrapped-key portion (9 bytes, not the
	// expected 256-byte RSA-2048-OAEP ciphertext) — simulating pre-1.1.4
	// (no key identifier) or otherwise non-conforming key material.
	envelope := cryptomanager.BuildEnvelope(splitter, [32]byte{}, []byte("short-key"), []byte("data"))
	_, _, _, err := cryptomanager.ParseEnvelope(splitter, envelope)
	require.ErrorIs(t, err, cryptomanager.ErrLegacyFormatUnsupported)
}
