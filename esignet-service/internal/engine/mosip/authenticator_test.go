/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"context"
	"crypto"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"io"
	"math/big"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"software.sslmate.com/src/go-pkcs12"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/clientmgmt/db"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/runtimestores/inmemory"
	"github.com/mosip/esignet/internal/engine/shared"
)

// ---------------------------------------------------------------------------
// Test fixtures: a fake clientmgmt.db.Querier, RSA keys/certs, and a P12
// keystore generated on the fly (in t.TempDir()) since no fixture keystore
// exists anywhere in the repo (verified: no *.p12 files, no MOSIP_P12
// references outside authenticator.go/config.go).
// ---------------------------------------------------------------------------

type fakeQuerier struct {
	row db.ClientDetail
	err error
}

var _ db.Querier = (*fakeQuerier)(nil)

func (f *fakeQuerier) CreateClient(context.Context, db.CreateClientParams) (db.ClientDetail, error) {
	return db.ClientDetail{}, errors.New("not implemented in test fake")
}

func (f *fakeQuerier) GetClient(context.Context, string) (db.ClientDetail, error) {
	return f.row, f.err
}

func (f *fakeQuerier) PatchClient(context.Context, db.PatchClientParams) (db.ClientDetail, error) {
	return db.ClientDetail{}, errors.New("not implemented in test fake")
}

func (f *fakeQuerier) UpdateClient(context.Context, db.UpdateClientParams) (db.ClientDetail, error) {
	return db.ClientDetail{}, errors.New("not implemented in test fake")
}

func validClientRow(clientID, rpID string) db.ClientDetail {
	return db.ClientDetail{
		ID:            clientID,
		Name:          "Test Client",
		RpID:          rpID,
		RedirectUris:  "[]",
		Claims:        "[]",
		AcrValues:     "[]",
		GrantTypes:    "[]",
		AuthMethods:   "[]",
		PublicKey:     "{}",
		PublicKeyHash: "hash",
		Status:        "ACTIVE",
		CrDtimes:      time.Now().UTC(),
	}
}

// newTestClientService builds a clientmgmt.Service backed by the fake Querier
// above and the module-standard in-memory RuntimeStoreProvider fake.
func newTestClientService(row db.ClientDetail, err error) *clientmgmt.Service {
	return clientmgmt.NewServiceWithQuerier(&fakeQuerier{row: row, err: err}, inmemory.Initialize("test"), 0)
}

func newValidClientService() *clientmgmt.Service {
	return newTestClientService(validClientRow("client-1", "rp-1"), nil)
}

func authnMetadataFor(clientID string) *providers.AuthnMetadata {
	return &providers.AuthnMetadata{RuntimeMetadata: map[string][]string{runtimeKeyClientID: {clientID}}}
}

func getAttributesMetadataFor(clientID string) *providers.GetAttributesMetadata {
	return &providers.GetAttributesMetadata{RuntimeMetadata: map[string][]string{runtimeKeyClientID: {clientID}}}
}

func newProvider(clientSvc *clientmgmt.Service) *mosipAuthnProvider {
	return &mosipAuthnProvider{
		appConfig: &config.AppConfig{},
		client:    &http.Client{},
		clientSvc: clientSvc,
		cfg:       Config{Env: "Staging", DomainURI: "http://domain"},
	}
}

// genRSAKeyAndCert generates a self-signed RSA certificate/key pair for tests.
func genRSAKeyAndCert(t *testing.T, cn string) (*rsa.PrivateKey, *x509.Certificate) {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)

	tmpl := &x509.Certificate{
		SerialNumber:          big.NewInt(time.Now().UnixNano()),
		Subject:               pkix.Name{CommonName: cn},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(time.Hour),
		KeyUsage:              x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment,
		BasicConstraintsValid: true,
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	require.NoError(t, err)
	cert, err := x509.ParseCertificate(der)
	require.NoError(t, err)
	return key, cert
}

func certToPEM(cert *x509.Certificate) []byte {
	return pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: cert.Raw})
}

// writeTestP12 encodes key/cert into a PKCS#12 file in t.TempDir() and
// returns its path.
func writeTestP12(t *testing.T, key *rsa.PrivateKey, cert *x509.Certificate, password string) string {
	t.Helper()
	data, err := pkcs12.Modern.Encode(key, cert, nil, password)
	require.NoError(t, err)
	path := filepath.Join(t.TempDir(), "keystore.p12")
	require.NoError(t, os.WriteFile(path, data, 0o600))
	return path
}

// configureSigning generates a throwaway signing keystore and wires it into
// p.cfg so getRequestSignature succeeds.
func configureSigning(t *testing.T, p *mosipAuthnProvider) {
	t.Helper()
	key, cert := genRSAKeyAndCert(t, "signer")
	p.cfg.P12Path = writeTestP12(t, key, cert, "pw")
	p.cfg.P12Password = "pw"
}

func jsonHandler(status int, body string) http.HandlerFunc {
	return func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(status)
		_, _ = w.Write([]byte(body))
	}
}

// unsignedJWT builds a compact JWS (header.payload.signature) with alg=none
// and no real signature, sufficient for jwt.ParseUnverified.
func unsignedJWT(t *testing.T, claims map[string]interface{}) string {
	t.Helper()
	header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"none","typ":"JWT"}`))
	payloadBytes, err := json.Marshal(claims)
	require.NoError(t, err)
	payload := base64.RawURLEncoding.EncodeToString(payloadBytes)
	return header + "." + payload + "."
}

// ---------------------------------------------------------------------------
// Pure helper function tests
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestGetUTCDateTimeIsParseableAndRecent() {
	t := ts.T()
	s := GetUTCDateTime()
	parsed, err := time.Parse(utcTimeFormat, s)
	require.NoError(t, err)
	require.WithinDuration(t, time.Now().UTC(), parsed, 5*time.Second)
}

func (ts *AuthenticatorTestSuite) TestB64EncodeDecodeRoundTrips() {
	t := ts.T()
	data := []byte("hello world")

	encoded := B64EncodeBytes(data)
	decoded, err := B64Decode(encoded)
	require.NoError(t, err)
	require.Equal(t, data, decoded)

	strEncoded := B64EncodeString("hello world")
	require.Equal(t, encoded, strEncoded)
}

func (ts *AuthenticatorTestSuite) TestB64DecodeAcceptsAllVariants() {
	t := ts.T()
	data := []byte{0xfb, 0xff, 0x3e, 0x01, 0x02, 0x03}

	stdEncoded := base64.StdEncoding.EncodeToString(data)
	decoded, err := B64Decode(stdEncoded)
	require.NoError(t, err)
	require.Equal(t, data, decoded)

	urlEncoded := base64.URLEncoding.EncodeToString(data)
	decoded, err = B64Decode(urlEncoded)
	require.NoError(t, err)
	require.Equal(t, data, decoded)

	rawURLEncoded := base64.RawURLEncoding.EncodeToString(data)
	decoded, err = B64Decode(rawURLEncoded)
	require.NoError(t, err)
	require.Equal(t, data, decoded)

	decoded, err = B64Decode("  " + rawURLEncoded + "  ")
	require.NoError(t, err)
	require.Equal(t, data, decoded)
}

func (ts *AuthenticatorTestSuite) TestB64DecodeErrors() {
	t := ts.T()
	_, err := B64Decode("")
	require.EqualError(t, err, "empty base64 input")

	_, err = B64Decode("   ")
	require.EqualError(t, err, "empty base64 input")

	_, err = B64Decode("!!! not base64 !!!")
	require.Error(t, err)
}

func (ts *AuthenticatorTestSuite) TestBiometricCredential() {
	t := ts.T()

	v, ok := biometricCredential(map[string]interface{}{"biometric": "abc"})
	require.True(t, ok)
	require.Equal(t, "abc", v)

	_, ok = biometricCredential(map[string]interface{}{})
	require.False(t, ok)

	_, ok = biometricCredential(map[string]interface{}{"biometric": ""})
	require.False(t, ok)

	_, ok = biometricCredential(map[string]interface{}{"biometric": 42})
	require.False(t, ok)
}

func (ts *AuthenticatorTestSuite) TestGenerateHashAndHexEncode() {
	t := ts.T()
	hash, err := GenerateHashWithErr([]byte("data"))
	require.NoError(t, err)
	wantHash := sha256.Sum256([]byte("data"))
	require.Equal(t, wantHash[:], hash)

	hexBytes, err := EncodeBytesToHexUpper([]byte{0xab, 0xcd})
	require.NoError(t, err)
	require.Equal(t, "ABCD", string(hexBytes))
}

func (ts *AuthenticatorTestSuite) TestGenerateAESKey() {
	t := ts.T()
	key, err := GenerateAESKey()
	require.NoError(t, err)
	require.Len(t, key, 32)

	key2, err := GenerateAESKey()
	require.NoError(t, err)
	require.NotEqual(t, key, key2)
}

func (ts *AuthenticatorTestSuite) TestSymmetricEncryptErrors() {
	t := ts.T()
	_, err := SymmetricEncrypt([]byte("data"), []byte("short-key"))
	require.Error(t, err)

	key, err := GenerateAESKey()
	require.NoError(t, err)
	_, err = SymmetricEncrypt(nil, key)
	require.Error(t, err)
}

func (ts *AuthenticatorTestSuite) TestSymmetricEncryptRoundTrips() {
	t := ts.T()
	key, err := GenerateAESKey()
	require.NoError(t, err)

	plaintext := []byte("secret payload")
	ciphertext, err := SymmetricEncrypt(plaintext, key)
	require.NoError(t, err)
	require.True(t, len(ciphertext) > 16)

	nonce := ciphertext[len(ciphertext)-16:]
	sealed := ciphertext[:len(ciphertext)-16]

	block, err := aesNewCipher(key)
	require.NoError(t, err)
	gcm, err := gcmWithNonceSize(block, 16)
	require.NoError(t, err)
	decrypted, err := gcm.Open(nil, nonce, sealed, nil)
	require.NoError(t, err)
	require.Equal(t, plaintext, decrypted)
}

func (ts *AuthenticatorTestSuite) TestGetCertificateThumbprint() {
	t := ts.T()
	_, err := GetCertificateThumbprint(nil)
	require.ErrorIs(t, err, ErrInvalidCertificate)

	_, err = GetCertificateThumbprint(&x509.Certificate{})
	require.ErrorIs(t, err, ErrInvalidCertificate)

	_, cert := genRSAKeyAndCert(t, "thumb-test")
	thumb, err := GetCertificateThumbprint(cert)
	require.NoError(t, err)
	want := sha256.Sum256(cert.Raw)
	require.Equal(t, want[:], thumb)
}

func (ts *AuthenticatorTestSuite) TestAsymmetricEncryptErrors() {
	t := ts.T()
	_, err := AsymmetricEncrypt(nil, []byte("data"))
	require.Error(t, err)

	key, _ := genRSAKeyAndCert(t, "asym-test")
	_, err = AsymmetricEncrypt(&key.PublicKey, nil)
	require.Error(t, err)
}

func (ts *AuthenticatorTestSuite) TestAsymmetricEncryptRoundTrips() {
	t := ts.T()
	key, _ := genRSAKeyAndCert(t, "asym-test-2")
	plaintext := []byte("session-key-bytes-0123456789012")

	ciphertext, err := AsymmetricEncrypt(&key.PublicKey, plaintext)
	require.NoError(t, err)

	decrypted, err := rsa.DecryptOAEP(sha256.New(), rand.Reader, key, ciphertext, nil)
	require.NoError(t, err)
	require.Equal(t, plaintext, decrypted)
}

func (ts *AuthenticatorTestSuite) TestLoadRSAPrivateKeyAndCertFromP12() {
	t := ts.T()
	_, err := os.Stat("/nonexistent/keystore.p12")
	require.Error(t, err)

	_, _, err = LoadRSAPrivateKeyAndCertFromP12("/nonexistent/keystore.p12", "pw")
	require.ErrorContains(t, err, "failed to read .p12 file")

	key, cert := genRSAKeyAndCert(t, "p12-test")
	path := writeTestP12(t, key, cert, "correct-pw")

	_, _, err = LoadRSAPrivateKeyAndCertFromP12(path, "wrong-pw")
	require.ErrorContains(t, err, "decode .p12")

	loadedKey, loadedCert, err := LoadRSAPrivateKeyAndCertFromP12(path, "correct-pw")
	require.NoError(t, err)
	require.True(t, key.Equal(loadedKey))
	require.Equal(t, cert.Raw, loadedCert.Raw)
}

func (ts *AuthenticatorTestSuite) TestCreateAndSignJWTWithX5C() {
	t := ts.T()
	key, cert := genRSAKeyAndCert(t, "jwt-test")
	payload := base64.RawURLEncoding.EncodeToString([]byte(`{"foo":"bar"}`))

	jwtStr, err := CreateAndSignJWTWithX5C(payload, key, cert, "")
	require.NoError(t, err)
	parts := strings.Split(jwtStr, ".")
	require.Len(t, parts, 3)
	require.Empty(t, parts[1])

	headerJSON, err := base64.RawURLEncoding.DecodeString(parts[0])
	require.NoError(t, err)
	var header map[string]interface{}
	require.NoError(t, json.Unmarshal(headerJSON, &header))
	require.Equal(t, "RS256", header["alg"])
	require.NotContains(t, header, "kid")

	hash := sha256.Sum256([]byte(parts[0] + "." + payload))
	sig, err := base64.RawURLEncoding.DecodeString(parts[2])
	require.NoError(t, err)
	require.NoError(t, rsa.VerifyPKCS1v15(&key.PublicKey, cryptoSHA256(), hash[:], sig))

	jwtWithKid, err := CreateAndSignJWTWithX5C(payload, key, cert, "kid-1")
	require.NoError(t, err)
	headerJSON2, err := base64.RawURLEncoding.DecodeString(strings.Split(jwtWithKid, ".")[0])
	require.NoError(t, err)
	var header2 map[string]interface{}
	require.NoError(t, json.Unmarshal(headerJSON2, &header2))
	require.Equal(t, "kid-1", header2["kid"])
}

func (ts *AuthenticatorTestSuite) TestBuildIDAEndpointURL() {
	t := ts.T()
	// Note: relyingPartyID/clientID are escaped via url.PathEscape and then
	// assigned directly to u.Path (which url.URL treats as the *unescaped*
	// path), so the escaped "%20" is itself re-escaped by u.String(). This
	// documents the existing (double-escaping) behavior rather than
	// asserting a "correct" single-escaped URL.
	u, err := buildIDAEndpointURL(context.Background(), "http://host/base/", "rp id", "client id")
	require.NoError(t, err)
	require.Equal(t, "http://host/base/rp%2520id/client%2520id", u)

	_, err = buildIDAEndpointURL(context.Background(), "http://host/%zz", "rp", "cid")
	require.Error(t, err)
}

// ---------------------------------------------------------------------------
// getRequestSignature
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestGetRequestSignatureConfigErrors() {
	t := ts.T()
	p := newProvider(nil)

	_, err := p.getRequestSignature([]byte("body"))
	require.ErrorContains(t, err, "MOSIP_P12_PATH is not configured")

	p.cfg.P12Path = "/some/path.p12"
	_, err = p.getRequestSignature([]byte("body"))
	require.ErrorContains(t, err, "MOSIP_P12_PASSWORD is not configured")
}

func (ts *AuthenticatorTestSuite) TestGetRequestSignatureSuccess() {
	t := ts.T()
	key, cert := genRSAKeyAndCert(t, "signer")
	path := writeTestP12(t, key, cert, "p12-pass")

	p := newProvider(nil)
	p.cfg.P12Path = path
	p.cfg.P12Password = "p12-pass"

	sig, err := p.getRequestSignature([]byte(`{"a":"b"}`))
	require.NoError(t, err)
	require.Len(t, strings.Split(sig, "."), 3)
}

// ---------------------------------------------------------------------------
// fetchIDAPartnerCertificate
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestFetchIDAPartnerCertificateSuccess() {
	t := ts.T()
	_, cert := genRSAKeyAndCert(t, "ida-partner")
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write(certToPEM(cert))
	}))
	defer srv.Close()

	p := newProvider(nil)
	p.cfg.IDAPartnerCertificateURL = srv.URL

	got, err := p.fetchIDAPartnerCertificate(context.Background())
	require.NoError(t, err)
	require.Equal(t, cert.Raw, got.Raw)
}

func (ts *AuthenticatorTestSuite) TestFetchIDAPartnerCertificateErrors() {
	t := ts.T()

	closedSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(200) }))
	closedSrv.Close()
	p := newProvider(nil)
	p.cfg.IDAPartnerCertificateURL = closedSrv.URL
	_, err := p.fetchIDAPartnerCertificate(context.Background())
	require.Error(t, err)

	nonOKSrv := httptest.NewServer(jsonHandler(http.StatusInternalServerError, "boom"))
	defer nonOKSrv.Close()
	p.cfg.IDAPartnerCertificateURL = nonOKSrv.URL
	_, err = p.fetchIDAPartnerCertificate(context.Background())
	require.ErrorContains(t, err, "instead of 200 OK")

	badPEMSrv := httptest.NewServer(jsonHandler(http.StatusOK, "not a pem block"))
	defer badPEMSrv.Close()
	p.cfg.IDAPartnerCertificateURL = badPEMSrv.URL
	_, err = p.fetchIDAPartnerCertificate(context.Background())
	require.ErrorIs(t, err, ErrCertificateParsing)

	badDER := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: []byte("not-a-real-der-cert")})
	badDERSrv := httptest.NewServer(jsonHandler(http.StatusOK, string(badDER)))
	defer badDERSrv.Close()
	p.cfg.IDAPartnerCertificateURL = badDERSrv.URL
	_, err = p.fetchIDAPartnerCertificate(context.Background())
	require.ErrorIs(t, err, ErrCertificateParsing)
}

// ---------------------------------------------------------------------------
// callSendOtpEndpoint
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestCallSendOtpEndpointInvalidURL() {
	t := ts.T()
	p := newProvider(nil)
	p.cfg.SendOTPBaseURL = "http://host/%zz"
	_, err := p.callSendOtpEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.ErrorContains(t, err, "invalid send OTP URL")
}

func (ts *AuthenticatorTestSuite) TestCallSendOtpEndpointRequestFails() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, "{}"))
	srv.Close()
	p := newProvider(nil)
	p.cfg.SendOTPBaseURL = srv.URL
	_, err := p.callSendOtpEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.ErrorContains(t, err, "send OTP request failed")
}

func (ts *AuthenticatorTestSuite) TestCallSendOtpEndpointNonOKStatus() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusInternalServerError, "server error"))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.SendOTPBaseURL = srv.URL
	_, err := p.callSendOtpEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.ErrorContains(t, err, "unexpected send OTP status: 500")
}

func (ts *AuthenticatorTestSuite) TestCallSendOtpEndpointMalformedBody() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, "not-json"))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.SendOTPBaseURL = srv.URL
	_, err := p.callSendOtpEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.ErrorContains(t, err, "failed to parse IdaSendOtpResponse")
}

func (ts *AuthenticatorTestSuite) TestCallSendOtpEndpointResponseMissingAlwaysErrors() {
	t := ts.T()
	// Documents existing behavior: when "response" is absent, the function
	// always returns "response object is missing in wrapper", even when
	// "errors" is populated (the later error-formatting branch is dead code
	// because the nil-response check above it always matches first).
	srv := httptest.NewServer(jsonHandler(http.StatusOK, `{"errors":[{"errorCode":"E1","errorMessage":"bad"}]}`))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.SendOTPBaseURL = srv.URL
	_, err := p.callSendOtpEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.EqualError(t, err, "response object is missing in wrapper")
}

func (ts *AuthenticatorTestSuite) TestCallSendOtpEndpointSuccess() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, `{"response":{"maskedEmail":"a***@b.com","maskedMobile":"***123"}}`))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.SendOTPBaseURL = srv.URL
	result, err := p.callSendOtpEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.NoError(t, err)
	require.Equal(t, "a***@b.com", result.MaskedEmail)
	require.Equal(t, "***123", result.MaskedMobile)
}

// ---------------------------------------------------------------------------
// callKycAuthEndpoint
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestCallKycAuthEndpointInvalidURL() {
	t := ts.T()
	p := newProvider(nil)
	p.cfg.KYCAuthBaseURL = "http://host/%zz"
	_, _, err := p.callKycAuthEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid", false)
	require.Error(t, err)
}

func (ts *AuthenticatorTestSuite) TestCallKycAuthEndpointRequestFails() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, "{}"))
	srv.Close()
	p := newProvider(nil)
	p.cfg.KYCAuthBaseURL = srv.URL
	_, _, err := p.callKycAuthEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid", false)
	require.Error(t, err)
}

func (ts *AuthenticatorTestSuite) TestCallKycAuthEndpointNonOKStatusReturnsError() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusInternalServerError, "boom"))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCAuthBaseURL = srv.URL
	psut, kycToken, err := p.callKycAuthEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid", false)
	require.ErrorContains(t, err, "500")
	require.Empty(t, psut)
	require.Empty(t, kycToken)
}

func (ts *AuthenticatorTestSuite) TestCallKycAuthEndpointMalformedBody() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, "not-json"))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCAuthBaseURL = srv.URL
	_, _, err := p.callKycAuthEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid", false)
	require.Error(t, err)
}

func (ts *AuthenticatorTestSuite) TestCallKycAuthEndpointResponseMissingReturnsError() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, `{"errors":[{"errorCode":"E1","errorMessage":"bad"}]}`))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCAuthBaseURL = srv.URL
	psut, kycToken, err := p.callKycAuthEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid", false)
	require.EqualError(t, err, "response object is missing in wrapper")
	require.Empty(t, psut)
	require.Empty(t, kycToken)
}

func (ts *AuthenticatorTestSuite) TestCallKycAuthEndpointKycStatusFalseWithErrors() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK,
		`{"response":{"kycStatus":false},"errors":[{"errorMessage":"denied","actionMessage":"retry"}]}`))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCAuthBaseURL = srv.URL
	_, _, err := p.callKycAuthEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid", false)
	require.EqualError(t, err, "denied: retry")
}

func (ts *AuthenticatorTestSuite) TestCallKycAuthEndpointKycStatusFalseWithoutErrorsReturnsError() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, `{"response":{"kycStatus":false}}`))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCAuthBaseURL = srv.URL
	psut, kycToken, err := p.callKycAuthEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid", false)
	require.EqualError(t, err, "no errors in response wrapper")
	require.Empty(t, psut)
	require.Empty(t, kycToken)
}

func (ts *AuthenticatorTestSuite) TestCallKycAuthEndpointSuccess() {
	t := ts.T()
	var gotPath string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		_, _ = w.Write([]byte(`{"response":{"kycStatus":true,"kycToken":"kyctok","authToken":"authtok"}}`))
	}))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCAuthBaseURL = srv.URL

	authToken, kycToken, err := p.callKycAuthEndpoint(context.Background(), []byte("{}"), "sig", "rp-1", "client-1", false)
	require.NoError(t, err)
	require.Equal(t, "authtok", authToken)
	require.Equal(t, "kyctok", kycToken)
	require.Equal(t, "/rp-1/client-1", gotPath)
}

// ---------------------------------------------------------------------------
// callKycExchangeEndpoint
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestCallKycExchangeEndpointInvalidURL() {
	t := ts.T()
	p := newProvider(nil)
	p.cfg.KYCExchangeBaseURL = "http://host/%zz"
	_, err := p.callKycExchangeEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.ErrorContains(t, err, "invalid KYC exchange URL")
}

func (ts *AuthenticatorTestSuite) TestCallKycExchangeEndpointNonOKStatus() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusBadGateway, "bad gateway"))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCExchangeBaseURL = srv.URL
	_, err := p.callKycExchangeEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.ErrorContains(t, err, "unexpected KYC exchange status: 502")
}

func (ts *AuthenticatorTestSuite) TestCallKycExchangeEndpointMalformedBody() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, "not-json"))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCExchangeBaseURL = srv.URL
	_, err := p.callKycExchangeEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.ErrorContains(t, err, "failed to parse IdaKycExchangeResponseWrapper")
}

func (ts *AuthenticatorTestSuite) TestCallKycExchangeEndpointResponseMissing() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, `{"errors":[{"errorMessage":"bad"}]}`))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCExchangeBaseURL = srv.URL
	_, err := p.callKycExchangeEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.EqualError(t, err, "response object is missing in wrapper")
}

func (ts *AuthenticatorTestSuite) TestCallKycExchangeEndpointEmptyEncryptedKycWithErrors() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK,
		`{"response":{"encryptedKyc":""},"errors":[{"errorMessage":"denied","actionMessage":"retry"}]}`))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCExchangeBaseURL = srv.URL
	_, err := p.callKycExchangeEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.EqualError(t, err, "denied: retry")
}

func (ts *AuthenticatorTestSuite) TestCallKycExchangeEndpointEmptyEncryptedKycNoErrors() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, `{"response":{"encryptedKyc":""}}`))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCExchangeBaseURL = srv.URL
	_, err := p.callKycExchangeEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.EqualError(t, err, "no errors in response wrapper")
}

func (ts *AuthenticatorTestSuite) TestCallKycExchangeEndpointInvalidJWT() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, `{"response":{"encryptedKyc":"not-a-jwt"}}`))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCExchangeBaseURL = srv.URL
	_, err := p.callKycExchangeEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.ErrorContains(t, err, "failed to parse KYC JWT payload")
}

func (ts *AuthenticatorTestSuite) TestCallKycExchangeEndpointSuccess() {
	t := ts.T()
	jwtStr := unsignedJWT(t, map[string]interface{}{"sub": "user-1", "name": "John"})
	body, err := json.Marshal(map[string]interface{}{"response": map[string]string{"encryptedKyc": jwtStr}})
	require.NoError(t, err)

	srv := httptest.NewServer(jsonHandler(http.StatusOK, string(body)))
	defer srv.Close()
	p := newProvider(nil)
	p.cfg.KYCExchangeBaseURL = srv.URL

	resp, callErr := p.callKycExchangeEndpoint(context.Background(), []byte("{}"), "sig", "rp", "cid")
	require.NoError(t, callErr)
	require.Equal(t, "user-1", resp.Attributes["sub"].Value)
	require.Equal(t, "John", resp.Attributes["name"].Value)
}

// ---------------------------------------------------------------------------
// getApplicationAndClientID
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestGetApplicationAndClientIDErrors() {
	t := ts.T()
	p := newProvider(newValidClientService())

	_, err := p.getApplicationAndClientID(context.Background(), nil)
	require.ErrorContains(t, err, "missing runtime metadata")

	pNoSvc := newProvider(nil)
	_, err = pNoSvc.getApplicationAndClientID(context.Background(), map[string][]string{runtimeKeyClientID: {"client-1"}})
	require.ErrorContains(t, err, "client service is not initialized")

	_, err = p.getApplicationAndClientID(context.Background(), map[string][]string{})
	require.ErrorContains(t, err, "missing client_id")

	pErr := newProvider(newTestClientService(db.ClientDetail{}, errors.New("boom")))
	_, err = pErr.getApplicationAndClientID(context.Background(), map[string][]string{runtimeKeyClientID: {"client-1"}})
	require.ErrorContains(t, err, "failed to resolve client")
}

func (ts *AuthenticatorTestSuite) TestGetApplicationAndClientIDSuccess() {
	t := ts.T()
	p := newProvider(newValidClientService())
	dtl, err := p.getApplicationAndClientID(context.Background(), map[string][]string{runtimeKeyClientID: {"client-1"}})
	require.NoError(t, err)
	require.Equal(t, "client-1", dtl.ClientID)
	require.Equal(t, "rp-1", dtl.RpID)
}

// ---------------------------------------------------------------------------
// Authenticate
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestAuthenticateValidationErrors() {
	t := ts.T()
	validSvc := newValidClientService()
	validMeta := authnMetadataFor("client-1")

	cases := []struct {
		name        string
		provider    *mosipAuthnProvider
		metadata    *providers.AuthnMetadata
		identifiers map[string]interface{}
		credentials map[string]interface{}
		wantErr     interface{}
	}{
		{
			name:        "missing client id in runtime metadata",
			provider:    newProvider(validSvc),
			metadata:    &providers.AuthnMetadata{},
			identifiers: map[string]interface{}{"username": "ind-1"},
			credentials: map[string]interface{}{"otp": "111111"},
			wantErr:     shared.ClientNotFoundError,
		},
		{
			name:        "missing username",
			provider:    newProvider(validSvc),
			metadata:    validMeta,
			identifiers: map[string]interface{}{},
			credentials: map[string]interface{}{"otp": "111111"},
			wantErr:     shared.InvalidIndividualIDError,
		},
		{
			name:        "empty credentials",
			provider:    newProvider(validSvc),
			metadata:    validMeta,
			identifiers: map[string]interface{}{"username": "ind-1"},
			credentials: map[string]interface{}{},
			wantErr:     shared.InvalidRequestError,
		},
		{
			name:        "unrecognized credential key",
			provider:    newProvider(validSvc),
			metadata:    validMeta,
			identifiers: map[string]interface{}{"username": "ind-1"},
			credentials: map[string]interface{}{"foo": "bar"},
			wantErr:     shared.InvalidRequestError,
		},
		{
			name:        "biometric invalid base64",
			provider:    newProvider(validSvc),
			metadata:    validMeta,
			identifiers: map[string]interface{}{"username": "ind-1"},
			credentials: map[string]interface{}{"biometric": "!!!not-base64!!!"},
			wantErr:     shared.InvalidRequestError,
		},
		{
			name:        "biometric invalid json",
			provider:    newProvider(validSvc),
			metadata:    validMeta,
			identifiers: map[string]interface{}{"username": "ind-1"},
			credentials: map[string]interface{}{"biometric": base64.StdEncoding.EncodeToString([]byte("not-json"))},
			wantErr:     shared.InvalidRequestError,
		},
		{
			name:        "biometric empty array",
			provider:    newProvider(validSvc),
			metadata:    validMeta,
			identifiers: map[string]interface{}{"username": "ind-1"},
			credentials: map[string]interface{}{"biometric": base64.StdEncoding.EncodeToString([]byte("[]"))},
			wantErr:     shared.InvalidRequestError,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			result, svcErr := tc.provider.Authenticate(context.Background(), tc.identifiers, tc.credentials, tc.metadata)
			require.Nil(t, result)
			require.Same(t, tc.wantErr, svcErr)
		})
	}
}

func (ts *AuthenticatorTestSuite) TestAuthenticateFailsWhenPartnerCertificateFetchFails() {
	t := ts.T()
	badCertSrv := httptest.NewServer(jsonHandler(http.StatusInternalServerError, "boom"))
	defer badCertSrv.Close()

	p := newProvider(newValidClientService())
	p.cfg.IDAPartnerCertificateURL = badCertSrv.URL

	result, svcErr := p.Authenticate(context.Background(),
		map[string]interface{}{"username": "ind-1"},
		map[string]interface{}{"otp": "111111"},
		authnMetadataFor("client-1"))

	require.Nil(t, result)
	require.Same(t, shared.AuthenticationFailedError, svcErr)
}

func (ts *AuthenticatorTestSuite) TestAuthenticateFailsWhenRequestSigningFails() {
	t := ts.T()
	_, cert := genRSAKeyAndCert(t, "ida-partner")
	certSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write(certToPEM(cert))
	}))
	defer certSrv.Close()

	p := newProvider(newValidClientService())
	p.cfg.IDAPartnerCertificateURL = certSrv.URL
	// P12Path/P12Password left empty on purpose.

	result, svcErr := p.Authenticate(context.Background(),
		map[string]interface{}{"username": "ind-1"},
		map[string]interface{}{"password": "pw"},
		authnMetadataFor("client-1"))

	require.Nil(t, result)
	require.Same(t, shared.AuthenticationFailedError, svcErr)
}

func (ts *AuthenticatorTestSuite) TestAuthenticateFailsWhenKycAuthCallErrors() {
	t := ts.T()
	_, cert := genRSAKeyAndCert(t, "ida-partner")
	certSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write(certToPEM(cert))
	}))
	defer certSrv.Close()

	signKey, signCert := genRSAKeyAndCert(t, "signer")
	p12Path := writeTestP12(t, signKey, signCert, "pw")

	kycAuthSrv := httptest.NewServer(jsonHandler(http.StatusOK, "not-json"))
	defer kycAuthSrv.Close()

	p := newProvider(newValidClientService())
	p.cfg.IDAPartnerCertificateURL = certSrv.URL
	p.cfg.P12Path = p12Path
	p.cfg.P12Password = "pw"
	p.cfg.KYCAuthBaseURL = kycAuthSrv.URL

	result, svcErr := p.Authenticate(context.Background(),
		map[string]interface{}{"username": "ind-1"},
		map[string]interface{}{"otp": "111111"},
		authnMetadataFor("client-1"))

	require.Nil(t, result)
	require.Same(t, shared.AuthenticationFailedError, svcErr)
}

func (ts *AuthenticatorTestSuite) TestAuthenticateSuccess() {
	t := ts.T()
	_, cert := genRSAKeyAndCert(t, "ida-partner")
	certSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write(certToPEM(cert))
	}))
	defer certSrv.Close()

	signKey, signCert := genRSAKeyAndCert(t, "signer")
	p12Path := writeTestP12(t, signKey, signCert, "pw")

	kycAuthSrv := httptest.NewServer(jsonHandler(http.StatusOK,
		`{"response":{"kycStatus":true,"kycToken":"kyctok","authToken":"authtok"}}`))
	defer kycAuthSrv.Close()

	newTestProvider := func() *mosipAuthnProvider {
		p := newProvider(newValidClientService())
		p.cfg.IDAPartnerCertificateURL = certSrv.URL
		p.cfg.P12Path = p12Path
		p.cfg.P12Password = "pw"
		p.cfg.KYCAuthBaseURL = kycAuthSrv.URL
		return p
	}

	assertSuccess := func(t *testing.T, credentials map[string]interface{}) {
		result, svcErr := newTestProvider().Authenticate(context.Background(),
			map[string]interface{}{"username": "ind-1"}, credentials, authnMetadataFor("client-1"))
		require.Nil(t, svcErr)
		require.Equal(t, "authtok", result.EntityReferenceToken)
		parts := strings.Split(result.AttributeToken.(string), "||")
		require.Len(t, parts, 3)
		require.Equal(t, "kyctok", parts[0])
		require.Equal(t, "ind-1", parts[1])
		require.NotEmpty(t, parts[2])
	}

	t.Run("otp credential", func(t *testing.T) {
		assertSuccess(t, map[string]interface{}{"otp": "111111"})
	})
	t.Run("password credential", func(t *testing.T) {
		assertSuccess(t, map[string]interface{}{"password": "pw123"})
	})
	t.Run("biometric credential", func(t *testing.T) {
		bioJSON, err := json.Marshal([]Biometric{{Data: "abc"}})
		require.NoError(t, err)
		assertSuccess(t, map[string]interface{}{"biometric": base64.StdEncoding.EncodeToString(bioJSON)})
	})
}

// ---------------------------------------------------------------------------
// GetEntityReference / InitiateAuthentication / InitiateEnrollment / Enroll
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestGetEntityReference() {
	t := ts.T()
	p := newProvider(nil)

	ref, err := p.GetEntityReference(context.Background(), "psut-1")
	require.Nil(t, err)
	require.Equal(t, "psut-1", ref.EntityID)

	_, err = p.GetEntityReference(context.Background(), "")
	require.Same(t, shared.AuthenticationFailedError, err)

	_, err = p.GetEntityReference(context.Background(), 123)
	require.Same(t, shared.AuthenticationFailedError, err)
}

func (ts *AuthenticatorTestSuite) TestNoOpMethods() {
	t := ts.T()
	p := newProvider(nil)

	res, err := p.InitiateAuthentication(context.Background(), "", nil, nil)
	require.Nil(t, res)
	require.Nil(t, err)

	res, err = p.InitiateEnrollment(context.Background(), "", nil, nil)
	require.Nil(t, res)
	require.Nil(t, err)

	authRes, err := p.Enroll(context.Background(), nil, nil, nil)
	require.Nil(t, authRes)
	require.Nil(t, err)
}

// ---------------------------------------------------------------------------
// SendOTP
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestSendOTPValidationErrors() {
	t := ts.T()
	p := newProvider(newValidClientService())

	_, svcErr := p.SendOTP(context.Background(), map[string]interface{}{"username": "ind-1"}, &providers.AuthnMetadata{})
	require.Same(t, shared.ClientNotFoundError, svcErr)

	_, svcErr = p.SendOTP(context.Background(), map[string]interface{}{}, authnMetadataFor("client-1"))
	require.Same(t, shared.InvalidRequestError, svcErr)
}

func (ts *AuthenticatorTestSuite) TestSendOTPEndpointFailure() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusInternalServerError, "boom"))
	defer srv.Close()

	p := newProvider(newValidClientService())
	configureSigning(t, p)
	p.cfg.SendOTPBaseURL = srv.URL

	_, svcErr := p.SendOTP(context.Background(), map[string]interface{}{"username": "ind-1"}, authnMetadataFor("client-1"))
	require.Same(t, shared.SendOTPFailedError, svcErr)
}

func (ts *AuthenticatorTestSuite) TestSendOTPSuccess() {
	t := ts.T()
	srv := httptest.NewServer(jsonHandler(http.StatusOK, `{"response":{"maskedEmail":"a***@b.com","maskedMobile":"***123"}}`))
	defer srv.Close()

	p := newProvider(newValidClientService())
	configureSigning(t, p)
	p.cfg.SendOTPBaseURL = srv.URL

	result, svcErr := p.SendOTP(context.Background(), map[string]interface{}{"username": "ind-1"}, authnMetadataFor("client-1"))
	require.Nil(t, svcErr)
	require.Equal(t, "a***@b.com", result.MaskedEmail)
	require.Equal(t, "***123", result.MaskedMobile)
	require.NotEmpty(t, result.TransactionID)
}

// ---------------------------------------------------------------------------
// GetAttributes
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestGetAttributesValidationErrors() {
	t := ts.T()
	p := newProvider(newValidClientService())

	_, svcErr := p.GetAttributes(context.Background(), "tok", nil, getAttributesMetadataFor("client-1"))
	require.Same(t, shared.InvalidRequestError, svcErr)

	reqAttrs := &providers.RequestedAttributes{}
	_, svcErr = p.GetAttributes(context.Background(), "tok", reqAttrs, &providers.GetAttributesMetadata{})
	require.Same(t, shared.ClientNotFoundError, svcErr)

	resp, svcErr := p.GetAttributes(context.Background(), nil, reqAttrs, getAttributesMetadataFor("client-1"))
	require.Nil(t, resp)
	require.Nil(t, svcErr)

	_, svcErr = p.GetAttributes(context.Background(), "only-two|parts", reqAttrs, getAttributesMetadataFor("client-1"))
	require.Same(t, shared.AuthenticationFailedError, svcErr)
}

func (ts *AuthenticatorTestSuite) TestGetAttributesSuccess() {
	t := ts.T()
	signKey, signCert := genRSAKeyAndCert(t, "signer")
	p12Path := writeTestP12(t, signKey, signCert, "pw")

	jwtStr := unsignedJWT(t, map[string]interface{}{"sub": "user-1", "name": "John"})
	body, err := json.Marshal(map[string]interface{}{"response": map[string]string{"encryptedKyc": jwtStr}})
	require.NoError(t, err)
	exchangeSrv := httptest.NewServer(jsonHandler(http.StatusOK, string(body)))
	defer exchangeSrv.Close()

	p := newProvider(newValidClientService())
	p.cfg.P12Path = p12Path
	p.cfg.P12Password = "pw"
	p.cfg.KYCExchangeBaseURL = exchangeSrv.URL

	reqAttrs := &providers.RequestedAttributes{
		Attributes: map[string]*providers.AttributeMetadataRequest{"name": {}},
	}
	attributeToken := strings.Join([]string{"kyctok", "user-1", "txn-1"}, "||")

	resp, svcErr := p.GetAttributes(context.Background(), attributeToken, reqAttrs, getAttributesMetadataFor("client-1"))
	require.Nil(t, svcErr)
	require.Equal(t, "user-1", resp.Attributes["sub"].Value)
	require.Equal(t, "John", resp.Attributes["name"].Value)
}

func (ts *AuthenticatorTestSuite) TestGetAttributesDefaultsToSubWhenNoAttributesRequested() {
	t := ts.T()
	signKey, signCert := genRSAKeyAndCert(t, "signer")
	p12Path := writeTestP12(t, signKey, signCert, "pw")

	var capturedBody []byte
	jwtStr := unsignedJWT(t, map[string]interface{}{"sub": "user-1"})
	respBody, err := json.Marshal(map[string]interface{}{"response": map[string]string{"encryptedKyc": jwtStr}})
	require.NoError(t, err)
	exchangeSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedBody, _ = readAll(r)
		_, _ = w.Write(respBody)
	}))
	defer exchangeSrv.Close()

	p := newProvider(newValidClientService())
	p.cfg.P12Path = p12Path
	p.cfg.P12Password = "pw"
	p.cfg.KYCExchangeBaseURL = exchangeSrv.URL

	attributeToken := strings.Join([]string{"kyctok", "user-1", "txn-1"}, "||")
	resp, svcErr := p.GetAttributes(context.Background(), attributeToken, &providers.RequestedAttributes{}, getAttributesMetadataFor("client-1"))
	require.Nil(t, svcErr)
	require.Equal(t, "user-1", resp.Attributes["sub"].Value)

	var req IdaKycExchangeRequest
	require.NoError(t, json.Unmarshal(capturedBody, &req))
	require.Equal(t, []string{"sub"}, req.ConsentObtained)
}

// ---------------------------------------------------------------------------
// NewMosipAuthnProvider
// ---------------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestNewMosipAuthnProviderWiresConfig() {
	t := ts.T()
	t.Setenv("MOSIP_API_INTERNAL_HOST", "http://internal.example.org")
	t.Setenv("MOSIP_ESIGNET_MISP_KEY", "misp-1")

	provider, err := NewMosipAuthnProvider(&config.AppConfig{}, nil, &http.Client{})
	require.NoError(t, err)
	require.NotNil(t, provider)

	impl, ok := provider.(*mosipAuthnProvider)
	require.True(t, ok)
	require.Equal(t, "misp-1", impl.cfg.LicenseKey)
}

type AuthenticatorTestSuite struct {
	suite.Suite
}

func TestAuthenticatorTestSuite(t *testing.T) {
	suite.Run(t, new(AuthenticatorTestSuite))
}

// ---------------------------------------------------------------------------
// small local shims so the test file doesn't need extra crypto imports beyond
// what's already pulled in for readability above
// ---------------------------------------------------------------------------

func cryptoSHA256() crypto.Hash {
	return crypto.SHA256
}

func aesNewCipher(key []byte) (cipher.Block, error) {
	return aes.NewCipher(key)
}

func gcmWithNonceSize(block cipher.Block, size int) (cipher.AEAD, error) {
	return cipher.NewGCMWithNonceSize(block, size)
}

func readAll(r *http.Request) ([]byte, error) {
	defer func() { _ = r.Body.Close() }()
	return io.ReadAll(r.Body)
}
