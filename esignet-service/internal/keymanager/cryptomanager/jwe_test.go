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

func TestJWTEncryptDecryptRoundTrip(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	ctx := context.Background()
	payload := []byte(`{"hello":"world"}`)

	encResp, err := env.CM.JWTEncrypt(ctx, cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY",
		Data: base64.RawURLEncoding.EncodeToString(payload),
	})
	require.NoError(t, err)
	require.NotEmpty(t, encResp.Data)

	decResp, err := env.CM.JWTDecrypt(ctx, cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY", EncData: encResp.Data,
	})
	require.NoError(t, err)

	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	require.NoError(t, err)
	require.JSONEq(t, string(payload), string(got))
}

func TestJWTEncrypt_HeadersMatchRequestFlags(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	ctx := context.Background()
	payload := base64.RawURLEncoding.EncodeToString([]byte(`{"a":1}`))
	includeCert := true
	includeHash := true

	encResp, err := env.CM.JWTEncrypt(ctx, cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_2", Data: payload,
		IncludeCertificate: includeCert, IncludeCertHash: includeHash, JWKSetURL: "https://example.test/jwks",
	})
	require.NoError(t, err)

	jwe, err := jose.ParseEncryptedCompact(encResp.Data, []jose.KeyAlgorithm{jose.RSA_OAEP_256}, []jose.ContentEncryption{jose.A256GCM})
	require.NoError(t, err)
	require.NotEmpty(t, jwe.Header.KeyID)
	// go-jose treats "x5c" specially at parse time (available only via
	// Header.Certificates(), which performs full chain verification) —
	// excluded from ExtraHeaders by design, so it's checked here by
	// decoding the raw protected header JSON directly instead.
	protected := decodeProtectedHeader(t, encResp.Data)
	require.Contains(t, protected, "x5c")
	require.Contains(t, jwe.Header.ExtraHeaders, jose.HeaderKey("x5t#S256"))
	require.Contains(t, jwe.Header.ExtraHeaders, jose.HeaderKey("jku"))
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

func TestJWTEncrypt_CallerSuppliedCertificateBypassesResolution(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	certPEM, _ := selfSignedCertPEM(t, 2048)

	// Neither ApplicationID nor ReferenceID correspond to anything
	// provisioned in env — this must still succeed because X509Certificate
	// bypasses key resolution entirely.
	encResp, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: "NONEXISTENT", ReferenceID: "",
		Data:            base64.RawURLEncoding.EncodeToString([]byte(`{"x":true}`)),
		X509Certificate: certPEM,
	})
	require.NoError(t, err)
	require.NotEmpty(t, encResp.Data)
	_ = env
}

func TestJWTEncrypt_RejectsNon2048BitCertificate(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	certPEM, _ := selfSignedCertPEM(t, 3072)

	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: "NONEXISTENT", ReferenceID: "",
		Data:            base64.RawURLEncoding.EncodeToString([]byte(`{"x":true}`)),
		X509Certificate: certPEM,
	})
	require.ErrorIs(t, err, cryptomanager.ErrJWECertificateKeyLengthInvalid)
	_ = env
}

func TestJWTEncrypt_RejectsBlankApplicationID(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: "", ReferenceID: "JWE_ENC_KEY",
		Data: base64.RawURLEncoding.EncodeToString([]byte(`{"a":1}`)),
	})
	require.ErrorIs(t, err, cryptomanager.ErrBlankApplicationID)
}

func TestJWTEncrypt_ValidatesApplicationIDBeforeReferenceIDBeforeData(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	// All three invalid at once — ApplicationID must be reported first,
	// exactly the ordering issue reported: running jwt-encrypt with
	// neither -app nor -ref set previously surfaced "reference id is
	// required" instead of an ApplicationID error.
	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: "", ReferenceID: "", Data: "",
	})
	require.ErrorIs(t, err, cryptomanager.ErrBlankApplicationID)

	_, err = env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "", Data: "",
	})
	require.ErrorIs(t, err, cryptomanager.ErrBlankReferenceID)
}

func TestJWTEncrypt_ValidatesDataBeforeResolvingCertificate(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	// "abc" has no key_policy_def row at all — resolving a certificate for
	// it would fail with a DB-layer "application id not found" error. With
	// invalid Data, that DB call must never happen: the Data error must
	// surface instead, proving input validation completes before any
	// key/certificate resolution is attempted.
	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: "abc", ReferenceID: "565", Data: "",
	})
	require.ErrorIs(t, err, cryptomanager.ErrInvalidRequest)
	require.NotContains(t, err.Error(), "key_policy_def")
	require.NotContains(t, err.Error(), "resolve encryption certificate")
}

func TestJWTEncrypt_RejectsBlankData(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_3", Data: "",
	})
	require.ErrorIs(t, err, cryptomanager.ErrInvalidRequest)
}

func TestJWTEncrypt_RejectsWhitespaceOnlyDecodedData(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	for _, tc := range []struct {
		name         string
		plaintext    string
		validateJSON bool
	}{
		{"two spaces, ValidateJSON=false", "  ", false},
		{"two spaces, ValidateJSON=true", "  ", true},
		{"tabs and newlines, ValidateJSON=false", "\t\n\t", false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			// The base64 STRING itself is well-formed and non-blank — only
			// the decoded content is blank. Must be rejected regardless of
			// ValidateJSON, since "nothing to encrypt" isn't a JSON-shape
			// concern.
			data := base64.RawURLEncoding.EncodeToString([]byte(tc.plaintext))
			require.NotEmpty(t, data)

			_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
				ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_9", Data: data, ValidateJSON: tc.validateJSON,
			})
			require.ErrorIs(t, err, cryptomanager.ErrInvalidRequest)
		})
	}
}

func TestJWTEncrypt_AcceptsAlreadySignedJWSWithoutJSONValidation(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	// 3 dot-separated segments — treated as already-signed JWS data, used
	// as-is without base64-decoding or JSON validation.
	jwsLike := "header.payload.signature"

	encResp, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_4", Data: jwsLike,
	})
	require.NoError(t, err)

	decResp, err := env.CM.JWTDecrypt(context.Background(), cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_4", EncData: encResp.Data,
	})
	require.NoError(t, err)
	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	require.NoError(t, err)
	require.Equal(t, jwsLike, string(got))
}

func TestJWTEncrypt_RejectsInvalidJSON_WhenValidateJSONTrue(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_5",
		Data:         base64.RawURLEncoding.EncodeToString([]byte("not json at all")),
		ValidateJSON: true,
	})
	require.ErrorIs(t, err, cryptomanager.ErrInvalidJSON)
}

func TestJWTEncrypt_AllowsNonJSON_WhenValidateJSONFalse(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	ctx := context.Background()
	plaintext := "not json at all"

	encResp, err := env.CM.JWTEncrypt(ctx, cryptomanager.JWTEncryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_8",
		Data:         base64.RawURLEncoding.EncodeToString([]byte(plaintext)),
		ValidateJSON: false,
	})
	require.NoError(t, err)

	decResp, err := env.CM.JWTDecrypt(ctx, cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_8", EncData: encResp.Data,
	})
	require.NoError(t, err)
	got, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	require.NoError(t, err)
	require.Equal(t, plaintext, string(got))
}

func TestJWTDecrypt_RejectsInvalidCompactSerialization(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.JWTDecrypt(context.Background(), cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_6", EncData: "not.a.valid.jwe.token",
	})
	require.ErrorIs(t, err, cryptomanager.ErrJWEInvalidCompactSerialization)
}

func TestJWTDecrypt_RejectsBlankApplicationID(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.JWTDecrypt(context.Background(), cryptomanager.JWTDecryptRequest{
		ApplicationID: "", ReferenceID: "JWE_ENC_KEY", EncData: "irrelevant",
	})
	require.ErrorIs(t, err, cryptomanager.ErrBlankApplicationID)
}

func TestJWTDecrypt_RejectsBlankEncData(t *testing.T) {
	env := newTestEnv(t, "TESTAPP")
	_, err := env.CM.JWTDecrypt(context.Background(), cryptomanager.JWTDecryptRequest{
		ApplicationID: env.AppID, ReferenceID: "JWE_ENC_KEY_7", EncData: "",
	})
	require.ErrorIs(t, err, cryptomanager.ErrInvalidRequest)
}
