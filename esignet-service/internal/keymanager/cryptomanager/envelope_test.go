package cryptomanager_test

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"testing"

	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
)

func (ts *CryptomanagerTestSuite) TestSymmetricEncryptDecryptRoundTrip() {
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
	ts.Require().NoError(err)

	for _, tc := range cases {
		ts.T().Run(tc.name, func(t *testing.T) {
			plaintext := make([]byte, tc.size)
			_, err := rand.Read(plaintext)
			ts.Require().NoError(err)

			ciphertext, err := cryptomanager.SymmetricEncrypt(sessionKey, plaintext)
			ts.Require().NoError(err)
			ts.Require().NotEqual(plaintext, ciphertext)

			decrypted, err := cryptomanager.SymmetricDecrypt(sessionKey, ciphertext)
			ts.Require().NoError(err)
			ts.Require().True(bytes.Equal(plaintext, decrypted))
		})
	}
}

func (ts *CryptomanagerTestSuite) TestSymmetricEncrypt_FreshAADEachCall() {
	sessionKey := make([]byte, 32)
	_, err := rand.Read(sessionKey)
	ts.Require().NoError(err)
	plaintext := []byte("same plaintext every time")

	c1, err := cryptomanager.SymmetricEncrypt(sessionKey, plaintext)
	ts.Require().NoError(err)
	c2, err := cryptomanager.SymmetricEncrypt(sessionKey, plaintext)
	ts.Require().NoError(err)
	ts.Require().NotEqual(c1, c2, "each call must use a freshly generated 32-byte AAD, so identical plaintext must not produce identical ciphertext")
}

func (ts *CryptomanagerTestSuite) TestSymmetricDecrypt_TooShort() {
	sessionKey := make([]byte, 32)
	_, err := cryptomanager.SymmetricDecrypt(sessionKey, []byte("too short"))
	ts.Require().Error(err)
}

func (ts *CryptomanagerTestSuite) TestBuildParseEnvelopeRoundTrip() {
	const splitter = "#KEY_SPLITTER#"
	var thumbprint [32]byte
	_, err := rand.Read(thumbprint[:])
	ts.Require().NoError(err)
	encryptedSessionKey := make([]byte, 256) // RSA-2048-OAEP ciphertext length
	_, err = rand.Read(encryptedSessionKey)
	ts.Require().NoError(err)
	encryptedData := []byte("arbitrary encrypted payload bytes")

	envelope := cryptomanager.BuildEnvelope(splitter, thumbprint, encryptedSessionKey, encryptedData)
	ts.Require().NotEmpty(envelope)

	gotThumbHex, gotEncKey, gotData, err := cryptomanager.ParseEnvelope(splitter, envelope)
	ts.Require().NoError(err)
	ts.Require().Equal(encryptedSessionKey, gotEncKey)
	ts.Require().Equal(encryptedData, gotData)

	ts.Require().Equal(hex.EncodeToString(thumbprint[:]), gotThumbHex)
}

func (ts *CryptomanagerTestSuite) TestParseEnvelope_SplitterNotFound() {
	_, _, _, err := cryptomanager.ParseEnvelope("#KEY_SPLITTER#", "bm90LWFuLWVudmVsb3Bl")
	ts.Require().ErrorIs(err, cryptomanager.ErrEnvelopeMalformed)
}

func (ts *CryptomanagerTestSuite) TestParseEnvelope_MalformedBase64() {
	_, _, _, err := cryptomanager.ParseEnvelope("#KEY_SPLITTER#", "not valid base64url!!!")
	ts.Require().ErrorIs(err, cryptomanager.ErrEnvelopeMalformed)
}

func (ts *CryptomanagerTestSuite) TestParseEnvelope_LegacyFormatUnsupported() {
	const splitter = "#KEY_SPLITTER#"
	// BuildEnvelope always prepends the VER_R2 header, so what's "wrong"
	// here is the length of the wrapped-key portion (9 bytes, not the
	// expected 256-byte RSA-2048-OAEP ciphertext) — simulating pre-1.1.4
	// (no key identifier) or otherwise non-conforming key material.
	envelope := cryptomanager.BuildEnvelope(splitter, [32]byte{}, []byte("short-key"), []byte("data"))
	_, _, _, err := cryptomanager.ParseEnvelope(splitter, envelope)
	ts.Require().ErrorIs(err, cryptomanager.ErrLegacyFormatUnsupported)
}
