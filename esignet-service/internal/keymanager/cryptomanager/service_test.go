package cryptomanager_test

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
)

func (ts *CryptomanagerTestSuite) TestEncryptDecryptRoundTrip() {
	t := ts.T()
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
		ts.T().Run(tc.name, func(_ *testing.T) {
			plaintext := make([]byte, tc.size)
			if tc.name == "single-byte" {
				plaintext[0] = 'A'
			} else {
				_, err := rand.Read(plaintext)
				ts.Require().NoError(err)
			}
			dataB64 := base64.RawURLEncoding.EncodeToString(plaintext)

			encResp, err := env.CM.Encrypt(ctx, cryptomanager.EncryptRequest{
				ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: dataB64,
			})
			ts.Require().NoError(err)
			ts.Require().NotEmpty(encResp.Data)

			decResp, err := env.CM.Decrypt(ctx, cryptomanager.DecryptRequest{
				ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: encResp.Data,
			})
			ts.Require().NoError(err)

			got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
			ts.Require().NoError(err)
			ts.Require().Equal(plaintext, got)
		})
	}
}

func (ts *CryptomanagerTestSuite) TestEncrypt_RejectsBlankReferenceID() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "", Data: "ZGF0YQ",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrBlankReferenceID)
}

func (ts *CryptomanagerTestSuite) TestEncrypt_RejectsBlankApplicationID() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: "", ReferenceID: "SOME_ENC_KEY", Data: "ZGF0YQ",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrBlankApplicationID)
}

func (ts *CryptomanagerTestSuite) TestEncrypt_ValidatesApplicationIDBeforeReferenceIDBeforeData() {
	env := newTestEnv(ts.T(), "TESTAPP")
	// All three are invalid at once — ApplicationID must be reported first.
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: "", ReferenceID: "", Data: "",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrBlankApplicationID)

	// ApplicationID valid, ReferenceID and Data both invalid — ReferenceID
	// must be reported next, before Data is ever looked at.
	_, err = env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "", Data: "",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrBlankReferenceID)
}

func (ts *CryptomanagerTestSuite) TestEncrypt_RejectsMasterKeyTarget() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: env.AppID, ReferenceID: keymanager.RefIDRSA2048, Data: "ZGF0YQ",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrEncryptionAgainstReservedKey)
	// Error message must be a plain, static message — no echoed appID/refID.
	ts.Require().Equal("not allowed to use Component Master Key/Root for encryption purpose", err.Error())
}

func (ts *CryptomanagerTestSuite) TestEncrypt_RejectsRootTarget() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: keymanager.AppIDRoot, ReferenceID: "anything", Data: "ZGF0YQ",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrEncryptionAgainstReservedKey)
}

func (ts *CryptomanagerTestSuite) TestEncrypt_RejectsInvalidBase64Data() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: "not base64!!!",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrInvalidData)
}

func (ts *CryptomanagerTestSuite) TestEncrypt_RejectsBlankData() {
	env := newTestEnv(ts.T(), "TESTAPP")
	for _, tc := range []struct {
		name string
		data string
	}{
		{"empty string", ""},
		{"only spaces", "   "},
		{"only tabs and newlines", "\t\n  \t"},
	} {
		ts.T().Run(tc.name, func(_ *testing.T) {
			_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
				ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: tc.data,
			})
			ts.Require().ErrorIs(err, cryptomanager.ErrInvalidRequest)
		})
	}
}

func (ts *CryptomanagerTestSuite) TestEncrypt_RejectsWhitespaceOnlyDecodedData() {
	env := newTestEnv(ts.T(), "TESTAPP")
	for _, tc := range []struct {
		name      string
		plaintext string
	}{
		{"three spaces", "   "},
		{"tabs and newlines", "\t\n\t"},
	} {
		ts.T().Run(tc.name, func(_ *testing.T) {
			// The base64 STRING itself is well-formed and non-blank (this
			// is exactly what a CLI/caller gets from base64-encoding
			// whitespace) — only the decoded content is blank.
			data := base64.RawURLEncoding.EncodeToString([]byte(tc.plaintext))
			ts.Require().NotEmpty(data)

			_, err := env.CM.Encrypt(context.Background(), cryptomanager.EncryptRequest{
				ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: data,
			})
			ts.Require().ErrorIs(err, cryptomanager.ErrInvalidRequest)
		})
	}
}

func (ts *CryptomanagerTestSuite) TestDecrypt_RejectsBlankApplicationID() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.Decrypt(context.Background(), cryptomanager.DecryptRequest{
		ApplicationID: "", ReferenceID: "SOME_ENC_KEY", Data: "irrelevant-checked-after-appid",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrBlankApplicationID)
}

func (ts *CryptomanagerTestSuite) TestDecrypt_RejectsMalformedEnvelope() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.Decrypt(context.Background(), cryptomanager.DecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: "not-a-real-envelope",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrEnvelopeMalformed)
}

func (ts *CryptomanagerTestSuite) TestDecrypt_RejectsBlankData() {
	env := newTestEnv(ts.T(), "TESTAPP")
	// Previously fell through to parseEnvelope and surfaced the generic
	// "envelope is malformed: splitter not found" instead of a clear
	// blank-data error.
	_, err := env.CM.Decrypt(context.Background(), cryptomanager.DecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "SOME_ENC_KEY", Data: "",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrInvalidRequest)
}

func (ts *CryptomanagerTestSuite) TestDecrypt_DifferentReferenceIDFails() {
	env := newTestEnv(ts.T(), "TESTAPP")
	ctx := context.Background()

	encResp, err := env.CM.Encrypt(ctx, cryptomanager.EncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "ENC_KEY_A", Data: base64.RawURLEncoding.EncodeToString([]byte("secret")),
	})
	ts.Require().NoError(err)

	_, err = env.CM.Decrypt(ctx, cryptomanager.DecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "ENC_KEY_B", Data: encResp.Data,
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrKeyIdentifierMismatch)
	// Error message must not disclose which (appID, refID) the thumbprint
	// actually resolved to.
	ts.Require().Equal("mismatch of application id and reference id", err.Error())
	ts.Require().NotContains(err.Error(), "ENC_KEY_A")
}

type CryptomanagerTestSuite struct {
	suite.Suite
}

func TestCryptomanagerTestSuite(t *testing.T) {
	suite.Run(t, new(CryptomanagerTestSuite))
}
