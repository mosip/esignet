package cryptomanager_test

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"math/big"
	"strings"
	"testing"
	"time"

	jose "github.com/go-jose/go-jose/v4"
	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
)

func selfSignedCertPEM(t *testing.T, bits int) (certPEM string, priv *rsa.PrivateKey) {
	t.Helper()
	priv, err := rsa.GenerateKey(rand.Reader, bits)
	require.NoError(t, err)
	template := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: "test-cert"},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(24 * time.Hour),
		KeyUsage:     x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment,
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &priv.PublicKey, priv)
	require.NoError(t, err)
	block := &pem.Block{Type: "CERTIFICATE", Bytes: der}
	return string(pem.EncodeToMemory(block)), priv
}

func (ts *CryptomanagerTestSuite) TestJWTEncryptDecryptRoundTrip() {
	env := newTestEnv(ts.T(), "TESTAPP")
	ctx := context.Background()
	payload := []byte(`{"hello":"world"}`)

	encResp, err := env.CM.JWTEncrypt(ctx, cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY",
		Data: base64.RawURLEncoding.EncodeToString(payload),
	})
	ts.Require().NoError(err)
	ts.Require().NotEmpty(encResp.Data)

	decResp, err := env.CM.JWTDecrypt(ctx, cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY", EncData: encResp.Data,
	})
	ts.Require().NoError(err)

	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	ts.Require().NoError(err)
	ts.Require().JSONEq(string(payload), string(got))
}

func (ts *CryptomanagerTestSuite) TestJWTEncrypt_HeadersMatchRequestFlags() {
	env := newTestEnv(ts.T(), "TESTAPP")
	ctx := context.Background()
	payload := base64.RawURLEncoding.EncodeToString([]byte(`{"a":1}`))
	includeCert := true
	includeHash := true

	encResp, err := env.CM.JWTEncrypt(ctx, cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_2", Data: payload,
		IncludeCertificate: includeCert, IncludeCertHash: includeHash, JWKSetURL: "https://example.test/jwks",
	})
	ts.Require().NoError(err)

	jwe, err := jose.ParseEncryptedCompact(encResp.Data, []jose.KeyAlgorithm{jose.RSA_OAEP_256}, []jose.ContentEncryption{jose.A256GCM})
	ts.Require().NoError(err)
	ts.Require().NotEmpty(jwe.Header.KeyID)
	// go-jose treats "x5c" specially at parse time (available only via
	// Header.Certificates(), which performs full chain verification) —
	// excluded from ExtraHeaders by design, so it's checked here by
	// decoding the raw protected header JSON directly instead.
	protected := decodeProtectedHeader(ts.T(), encResp.Data)
	ts.Require().Contains(protected, "x5c")
	ts.Require().Contains(jwe.Header.ExtraHeaders, jose.HeaderKey("x5t#S256"))
	ts.Require().Contains(jwe.Header.ExtraHeaders, jose.HeaderKey("jku"))
}

// decodeProtectedHeader base64url-decodes and JSON-unmarshals a compact
// JWE's first (protected header) segment.
func decodeProtectedHeader(t *testing.T, compact string) map[string]any {
	t.Helper()
	segments := strings.Split(compact, ".")
	require.Len(t, segments, 5, "compact JWE must have 5 dot-separated segments")
	raw, err := base64.RawURLEncoding.DecodeString(segments[0])
	require.NoError(t, err)
	var m map[string]any
	require.NoError(t, json.Unmarshal(raw, &m))
	return m
}

func (ts *CryptomanagerTestSuite) TestJWTEncrypt_CallerSuppliedCertificateBypassesResolution() {
	env := newTestEnv(ts.T(), "TESTAPP")
	certPEM, _ := selfSignedCertPEM(ts.T(), 2048)

	// Neither ApplicationID nor ReferenceID correspond to anything
	// provisioned in env — this must still succeed because X509Certificate
	// bypasses key resolution entirely.
	encResp, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: "NONEXISTENT", ReferenceID: "",
		Data:            base64.RawURLEncoding.EncodeToString([]byte(`{"x":true}`)),
		X509Certificate: certPEM,
	})
	ts.Require().NoError(err)
	ts.Require().NotEmpty(encResp.Data)
	_ = env
}

func (ts *CryptomanagerTestSuite) TestJWTEncrypt_RejectsNon2048BitCertificate() {
	env := newTestEnv(ts.T(), "TESTAPP")
	certPEM, _ := selfSignedCertPEM(ts.T(), 3072)

	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: "NONEXISTENT", ReferenceID: "",
		Data:            base64.RawURLEncoding.EncodeToString([]byte(`{"x":true}`)),
		X509Certificate: certPEM,
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrJWECertificateKeyLengthInvalid)
	_ = env
}

func (ts *CryptomanagerTestSuite) TestJWTEncrypt_RejectsBlankApplicationID() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: "", ReferenceID: "JWE_ENC_KEY",
		Data: base64.RawURLEncoding.EncodeToString([]byte(`{"a":1}`)),
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrBlankApplicationID)
}

func (ts *CryptomanagerTestSuite) TestJWTEncrypt_ValidatesApplicationIDBeforeReferenceIDBeforeData() {
	env := newTestEnv(ts.T(), "TESTAPP")
	// All three invalid at once — ApplicationID must be reported first,
	// exactly the ordering issue reported: running jwt-encrypt with
	// neither -app nor -ref set previously surfaced "reference id is
	// required" instead of an ApplicationID error.
	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: "", ReferenceID: "", Data: "",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrBlankApplicationID)

	_, err = env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "", Data: "",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrBlankReferenceID)
}

func (ts *CryptomanagerTestSuite) TestJWTEncrypt_ValidatesDataBeforeResolvingCertificate() {
	env := newTestEnv(ts.T(), "TESTAPP")
	// "abc" has no key_policy_def row at all — resolving a certificate for
	// it would fail with a DB-layer "application id not found" error. With
	// invalid Data, that DB call must never happen: the Data error must
	// surface instead, proving input validation completes before any
	// key/certificate resolution is attempted.
	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: "abc", ReferenceID: "565", Data: "",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrInvalidRequest)
	ts.Require().NotContains(err.Error(), "key_policy_def")
	ts.Require().NotContains(err.Error(), "resolve encryption certificate")
}

func (ts *CryptomanagerTestSuite) TestJWTEncrypt_RejectsBlankData() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_3", Data: "",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrInvalidRequest)
}

func (ts *CryptomanagerTestSuite) TestJWTEncrypt_RejectsWhitespaceOnlyDecodedData() {
	env := newTestEnv(ts.T(), "TESTAPP")
	for _, tc := range []struct {
		name         string
		plaintext    string
		validateJSON bool
	}{
		{"two spaces, ValidateJSON=false", "  ", false},
		{"two spaces, ValidateJSON=true", "  ", true},
		{"tabs and newlines, ValidateJSON=false", "\t\n\t", false},
	} {
		ts.T().Run(tc.name, func(_ *testing.T) {
			// The base64 STRING itself is well-formed and non-blank — only
			// the decoded content is blank. Must be rejected regardless of
			// ValidateJSON, since "nothing to encrypt" isn't a JSON-shape
			// concern.
			data := base64.RawURLEncoding.EncodeToString([]byte(tc.plaintext))
			ts.Require().NotEmpty(data)

			_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
				ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_9", Data: data, ValidateJSON: tc.validateJSON,
			})
			ts.Require().ErrorIs(err, cryptomanager.ErrInvalidRequest)
		})
	}
}

func (ts *CryptomanagerTestSuite) TestJWTEncrypt_AcceptsAlreadySignedJWSWithoutJSONValidation() {
	env := newTestEnv(ts.T(), "TESTAPP")
	// 3 dot-separated segments — treated as already-signed JWS data, used
	// as-is without base64-decoding or JSON validation.
	jwsLike := "header.payload.signature"

	encResp, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_4", Data: jwsLike,
	})
	ts.Require().NoError(err)

	decResp, err := env.CM.JWTDecrypt(context.Background(), cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_4", EncData: encResp.Data,
	})
	ts.Require().NoError(err)
	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	ts.Require().NoError(err)
	ts.Require().Equal(jwsLike, string(got))
}

func (ts *CryptomanagerTestSuite) TestJWTEncrypt_RejectsInvalidJSON_WhenValidateJSONTrue() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_5",
		Data:         base64.RawURLEncoding.EncodeToString([]byte("not json at all")),
		ValidateJSON: true,
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrInvalidJSON)
}

func (ts *CryptomanagerTestSuite) TestJWTEncrypt_AllowsNonJSON_WhenValidateJSONFalse() {
	env := newTestEnv(ts.T(), "TESTAPP")
	ctx := context.Background()
	plaintext := "not json at all"

	encResp, err := env.CM.JWTEncrypt(ctx, cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_8",
		Data:         base64.RawURLEncoding.EncodeToString([]byte(plaintext)),
		ValidateJSON: false,
	})
	ts.Require().NoError(err)

	decResp, err := env.CM.JWTDecrypt(ctx, cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_8", EncData: encResp.Data,
	})
	ts.Require().NoError(err)
	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	ts.Require().NoError(err)
	ts.Require().Equal(plaintext, string(got))
}

func (ts *CryptomanagerTestSuite) TestJWTDecrypt_RejectsInvalidCompactSerialization() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.JWTDecrypt(context.Background(), cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_6", EncData: "not.a.valid.jwe.token",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrJWEInvalidCompactSerialization)
}

func (ts *CryptomanagerTestSuite) TestJWTDecrypt_RejectsBlankApplicationID() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.JWTDecrypt(context.Background(), cryptomanager.JWTDecryptRequest{
		ApplicationID: "", ReferenceID: "JWE_ENC_KEY", EncData: "irrelevant",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrBlankApplicationID)
}

func (ts *CryptomanagerTestSuite) TestJWTDecrypt_RejectsBlankEncData() {
	env := newTestEnv(ts.T(), "TESTAPP")
	_, err := env.CM.JWTDecrypt(context.Background(), cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_7", EncData: "",
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrInvalidRequest)
}

// TestJWTDecrypt_RejectsTamperedCiphertext exercises the GCM-auth-failure
// branch of JWTDecrypt: flipping a bit in the compact JWE's ciphertext
// segment must fail decryption with ErrJWEDecryptFailed, not silently
// succeed or panic.
func (ts *CryptomanagerTestSuite) TestJWTDecrypt_RejectsTamperedCiphertext() {
	env := newTestEnv(ts.T(), "TESTAPP")
	ctx := context.Background()
	payload := []byte(`{"hello":"world"}`)

	encResp, err := env.CM.JWTEncrypt(ctx, cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY",
		Data: base64.RawURLEncoding.EncodeToString(payload),
	})
	ts.Require().NoError(err)

	segments := strings.Split(encResp.Data, ".")
	ts.Require().Len(segments, 5, "compact JWE must be 5 dot-separated segments")
	ciphertext, err := base64.RawURLEncoding.DecodeString(segments[3])
	ts.Require().NoError(err)
	ts.Require().NotEmpty(ciphertext)
	ciphertext[0] ^= 0xFF // flip a bit — must break GCM authentication
	segments[3] = base64.RawURLEncoding.EncodeToString(ciphertext)
	tampered := strings.Join(segments, ".")

	_, err = env.CM.JWTDecrypt(ctx, cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY", EncData: tampered,
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrJWEDecryptFailed)
}

// TestJWTDecrypt_RejectsReferenceIDMismatch exercises the kid/thumbprint
// mismatch branch: decrypting a valid JWE under a ReferenceID other than the
// one its key was generated for must fail, not silently decrypt under the
// wrong key's identity.
func (ts *CryptomanagerTestSuite) TestJWTDecrypt_RejectsReferenceIDMismatch() {
	env := newTestEnv(ts.T(), "TESTAPP")
	ctx := context.Background()
	payload := []byte(`{"hello":"world"}`)

	encResp, err := env.CM.JWTEncrypt(ctx, cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY",
		Data: base64.RawURLEncoding.EncodeToString(payload),
	})
	ts.Require().NoError(err)

	_, err = env.CM.JWTDecrypt(ctx, cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_OTHER", EncData: encResp.Data,
	})
	ts.Require().ErrorIs(err, cryptomanager.ErrKeyIdentifierMismatch)
}
