/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"math/big"
	"testing"
	"time"

	jose "github.com/go-jose/go-jose/v4"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/kmtest"
)

// fakeAuthnProviderForCrypto is a hand-written stub of
// shared.ConsolidatedAuthnProvider used only to drive
// runtimeCryptoProvider.idSystemPublicKeys — every method besides
// GetSigningCertificates is unreachable from that code path.
type fakeAuthnProviderForCrypto struct {
	certs  []shared.CertificateData
	svcErr *common.ServiceError
}

func (f *fakeAuthnProviderForCrypto) InitiateAuthentication(context.Context, string, any,
	*providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProviderForCrypto) Authenticate(context.Context, map[string]interface{}, map[string]interface{},
	*providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProviderForCrypto) GetEntityReference(context.Context, any) (*providers.EntityReference,
	*common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProviderForCrypto) GetAttributes(context.Context, any, *providers.RequestedAttributes,
	*providers.GetAttributesMetadata) (*providers.AttributesResponse, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProviderForCrypto) InitiateEnrollment(context.Context, string, any,
	*providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProviderForCrypto) Enroll(context.Context, map[string]interface{}, map[string]interface{},
	*providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProviderForCrypto) SendOTP(context.Context, map[string]interface{},
	*providers.AuthnMetadata) (*shared.SendOTPResult, *common.ServiceError) {
	return nil, nil
}

func (f *fakeAuthnProviderForCrypto) GetSigningCertificates(context.Context) ([]shared.CertificateData, *common.ServiceError) {
	return f.certs, f.svcErr
}

var _ shared.ConsolidatedAuthnProvider = (*fakeAuthnProviderForCrypto)(nil)

// testCertPEM generates a self-signed RSA certificate PEM for a given
// CommonName, so idSystemPublicKeys tests exercise genuine
// keymanager.ParseCertPEM/ThumbprintForCert calls rather than mocked ones.
func testCertPEM(t *testing.T, cn string) string {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate RSA key: %v", err)
	}
	tmpl := &x509.Certificate{
		SerialNumber:          big.NewInt(time.Now().UnixNano()),
		Subject:               pkix.Name{CommonName: cn},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(time.Hour),
		KeyUsage:              x509.KeyUsageDigitalSignature,
		BasicConstraintsValid: true,
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		t.Fatalf("create certificate: %v", err)
	}
	return keymanager.EncodeCertPEM(der)
}

// testECCertPEM generates a self-signed P-256 certificate PEM for a given
// CommonName — used to confirm idSystemPublicKeys derives the JWS algorithm
// from the actual key type rather than any hint in the KeyID string.
func testECCertPEM(t *testing.T, cn string) string {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatalf("generate EC key: %v", err)
	}
	tmpl := &x509.Certificate{
		SerialNumber:          big.NewInt(time.Now().UnixNano()),
		Subject:               pkix.Name{CommonName: cn},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(time.Hour),
		KeyUsage:              x509.KeyUsageDigitalSignature,
		BasicConstraintsValid: true,
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		t.Fatalf("create certificate: %v", err)
	}
	return keymanager.EncodeCertPEM(der)
}

// testRSAJWKMap builds the map[string]interface{} shape a providers.KeyRef.
// PublicKeyJWK carries — round-tripped through jose.JSONWebKey's own
// marshaling, so it matches exactly what getPublicKey has to parse in
// production.
func testRSAJWKMap(t *testing.T) map[string]interface{} {
	t.Helper()
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate RSA key: %v", err)
	}
	jwk := jose.JSONWebKey{Key: &priv.PublicKey, KeyID: "test-key", Algorithm: "RSA-OAEP-256", Use: "enc"}
	raw, err := jwk.MarshalJSON()
	if err != nil {
		t.Fatalf("marshal jwk: %v", err)
	}
	var m map[string]interface{}
	if err := json.Unmarshal(raw, &m); err != nil {
		t.Fatalf("unmarshal jwk into map: %v", err)
	}
	return m
}

type RuntimeCryptoProviderTestSuite struct {
	suite.Suite
}

func TestRuntimeCryptoProviderTestSuite(t *testing.T) {
	suite.Run(t, new(RuntimeCryptoProviderTestSuite))
}

func (ts *RuntimeCryptoProviderTestSuite) TestNewRuntimeCryptoProvider_DefaultsSignReferenceIDWhenUnset() {
	cfg := &config.AppConfig{}

	p := NewRuntimeCryptoProvider(cfg, nil, nil, nil, nil).(*runtimeCryptoProvider)

	ts.Require().Equal(defaultSignReferenceID, p.signReferenceID)
}

func (ts *RuntimeCryptoProviderTestSuite) TestNewRuntimeCryptoProvider_UsesConfiguredPreferredKeyID() {
	cfg := &config.AppConfig{}
	cfg.JWT.PreferredKeyID = "RSA_2048"

	p := NewRuntimeCryptoProvider(cfg, nil, nil, nil, nil).(*runtimeCryptoProvider)

	ts.Require().Equal("RSA_2048", p.signReferenceID)
}

func (ts *RuntimeCryptoProviderTestSuite) TestReferenceID_IgnoresKeyIDAndUsesSignReferenceID() {
	p := &runtimeCryptoProvider{signReferenceID: "configured-ref-id"}

	ts.Require().Equal("configured-ref-id", p.referenceID("some-other-key-id"))
	ts.Require().Equal("configured-ref-id", p.referenceID(""))
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedSigningAlgorithms_FiltersToConfiguredSet() {
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{
		SupportedSigningAlgorithms: []string{"PS256", "EdDSA"},
	}}

	ts.Require().Equal([]string{"PS256", "EdDSA"}, p.GetSupportedSigningAlgorithms())
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedSigningAlgorithms_PreservesSignaturePackageOrder() {
	// signature.SupportedAlgorithms() returns PS256, RS256, ES256, ES256K, EdDSA;
	// the configured set here lists them in the opposite order, so the
	// filtered result should still come back in the signature package's order.
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{
		SupportedSigningAlgorithms: []string{"EdDSA", "ES256K", "PS256"},
	}}

	ts.Require().Equal([]string{"PS256", "ES256K", "EdDSA"}, p.GetSupportedSigningAlgorithms())
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedSigningAlgorithms_UnknownConfiguredAlgorithmIgnored() {
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{
		SupportedSigningAlgorithms: []string{"BOGUS"},
	}}

	ts.Require().Empty(p.GetSupportedSigningAlgorithms())
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedSigningAlgorithms_EmptyConfigReturnsEmpty() {
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{}}

	ts.Require().Empty(p.GetSupportedSigningAlgorithms())
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedEncryptionAlgorithms_ReturnsConfiguredList() {
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{
		SupportedEncAlgorithms: []string{"AES-GCM", "RSA-OAEP-256"},
	}}

	ts.Require().Equal([]string{"AES-GCM", "RSA-OAEP-256"}, p.GetSupportedEncryptionAlgorithms())
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedEncryptionAlgorithms_EmptyConfigReturnsEmpty() {
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{}}

	ts.Require().Empty(p.GetSupportedEncryptionAlgorithms())
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetPublicKey_RejectsEmptyJWK() {
	p := &runtimeCryptoProvider{jwkCache: newJWKCache()}

	_, err := p.getPublicKey(providers.KeyRef{})
	ts.Require().ErrorIs(err, providers.ErrKeyNotFound)
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetPublicKey_CachesParsedResult() {
	p := &runtimeCryptoProvider{jwkCache: newJWKCache()}
	jwkMap := testRSAJWKMap(ts.T())

	first, err := p.getPublicKey(providers.KeyRef{PublicKeyJWK: jwkMap})
	ts.Require().NoError(err)

	second, err := p.getPublicKey(providers.KeyRef{PublicKeyJWK: jwkMap})
	ts.Require().NoError(err)

	ts.Assert().Same(first, second, "the second call for the same JWK bytes must be served from the cache, not re-parsed")
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetPublicKey_DifferentJWKs_AreNotConfused() {
	p := &runtimeCryptoProvider{jwkCache: newJWKCache()}
	jwkA := testRSAJWKMap(ts.T())
	jwkB := testRSAJWKMap(ts.T())

	keyA, err := p.getPublicKey(providers.KeyRef{PublicKeyJWK: jwkA})
	ts.Require().NoError(err)
	keyB, err := p.getPublicKey(providers.KeyRef{PublicKeyJWK: jwkB})
	ts.Require().NoError(err)

	ts.Assert().NotSame(keyA, keyB, "distinct JWK content must not collide on the same cache entry")
	rsaA, ok := keyA.Key.(*rsa.PublicKey)
	ts.Require().True(ok)
	rsaB, ok := keyB.Key.(*rsa.PublicKey)
	ts.Require().True(ok)
	ts.Assert().False(rsaA.Equal(rsaB), "the two fixtures must carry genuinely different key material")
}

func (ts *RuntimeCryptoProviderTestSuite) TestIdSystemPublicKeys_NilAuthnProvider_ReturnsNil() {
	p := &runtimeCryptoProvider{authnProvider: nil}

	ts.Require().Nil(p.idSystemPublicKeys(context.Background()))
}

func (ts *RuntimeCryptoProviderTestSuite) TestIdSystemPublicKeys_ServiceErrorFromProvider_ReturnsNil() {
	p := &runtimeCryptoProvider{authnProvider: &fakeAuthnProviderForCrypto{
		svcErr: &common.ServiceError{Code: "certificate_fetch_failed"},
	}}

	ts.Require().Nil(p.idSystemPublicKeys(context.Background()))
}

func (ts *RuntimeCryptoProviderTestSuite) TestIdSystemPublicKeys_ConvertsEachCertificate() {
	certPEM := testCertPEM(ts.T(), "id-system-key")
	p := &runtimeCryptoProvider{authnProvider: &fakeAuthnProviderForCrypto{
		certs: []shared.CertificateData{{KeyID: "id-system-ref", Certificate: certPEM}},
	}}

	keys := p.idSystemPublicKeys(context.Background())

	ts.Require().Len(keys, 1)
	ts.Assert().Equal("id-system-ref", keys[0].KeyID)
	ts.Assert().NotEmpty(keys[0].Thumbprint)
	ts.Assert().NotNil(keys[0].PublicKey)
	ts.Assert().NotEmpty(keys[0].CertificateDER)
	ts.Assert().Equal("PS256", keys[0].Algorithm, "an RSA certificate must resolve to PS256")
}

// TestIdSystemPublicKeys_AlgorithmReflectsActualKeyType_NotKeyIDString
// guards the fix from computing Algorithm via
// signature.AlgorithmForPublicKey(cert.PublicKey) instead of
// signature.AlgorithmForRefID(certData.KeyID): an ID system's KeyID is an
// arbitrary external string with no relation to esignet's own refID
// constants, so matching it against those constants could silently report
// the wrong algorithm for a real key. Here the KeyID happens to collide with
// keymanager.RefIDECSECP256R1Sign ("EC_SECP256R1_SIGN") even though the
// certificate is RSA — the resolved algorithm must follow the certificate,
// not the KeyID.
func (ts *RuntimeCryptoProviderTestSuite) TestIdSystemPublicKeys_AlgorithmReflectsActualKeyType_NotKeyIDString() {
	rsaCertPEM := testCertPEM(ts.T(), "id-system-key")
	p := &runtimeCryptoProvider{authnProvider: &fakeAuthnProviderForCrypto{
		certs: []shared.CertificateData{{KeyID: keymanager.RefIDECSECP256R1Sign, Certificate: rsaCertPEM}},
	}}

	keys := p.idSystemPublicKeys(context.Background())

	ts.Require().Len(keys, 1)
	ts.Assert().Equal("PS256", keys[0].Algorithm,
		"algorithm must be derived from the RSA certificate's key, not misread from the EC_SECP256R1_SIGN-shaped KeyID")
}

func (ts *RuntimeCryptoProviderTestSuite) TestIdSystemPublicKeys_ECCertificate_ResolvesES256() {
	ecCertPEM := testECCertPEM(ts.T(), "id-system-ec-key")
	p := &runtimeCryptoProvider{authnProvider: &fakeAuthnProviderForCrypto{
		certs: []shared.CertificateData{{KeyID: "arbitrary-id-system-key-id", Certificate: ecCertPEM}},
	}}

	keys := p.idSystemPublicKeys(context.Background())

	ts.Require().Len(keys, 1)
	ts.Assert().Equal("ES256", keys[0].Algorithm)
}

func (ts *RuntimeCryptoProviderTestSuite) TestIdSystemPublicKeys_SkipsUnparsableCertificatesButKeepsOthers() {
	certPEM := testCertPEM(ts.T(), "id-system-key")
	p := &runtimeCryptoProvider{authnProvider: &fakeAuthnProviderForCrypto{
		certs: []shared.CertificateData{
			{KeyID: "bad-ref", Certificate: "not a certificate"},
			{KeyID: "good-ref", Certificate: certPEM},
		},
	}}

	keys := p.idSystemPublicKeys(context.Background())

	ts.Require().Len(keys, 1)
	ts.Assert().Equal("good-ref", keys[0].KeyID)
}

// newTestKeyManagerService builds an in-memory keymanager.Service (backed by
// kmtest's StateQuerier/FakeKeyStore, no postgres/HSM involved) with ROOT and
// the OIDC_SERVICE/defaultSignReferenceID EC sign key provisioned — mirrors
// cmd/esignet/main.go's provisionKeyHierarchy, just scoped to what
// GetPublicKeys needs.
func newTestKeyManagerService(t *testing.T) *keymanager.Service {
	t.Helper()
	ctx := context.Background()
	km := keymanager.NewServiceWithQuerier(kmtest.NewStateQuerier(), kmtest.NewFakeKeyStore(), keymanager.Config{
		AsymmetricKeyLength:  2048,
		CertCommonName:       "www.mosip.io",
		CertOrganizationUnit: "thunder-tech-team",
		CertOrganization:     "IIITB",
		CertLocation:         "Bangalore",
		CertState:            "KA",
		CertCountry:          "IN",
	})

	_, err := km.GenerateMasterKey(ctx, keymanager.GenerateMasterKeyRequest{
		ApplicationID: keymanager.AppIDRoot,
		ObjectType:    keymanager.ObjectTypeCertificate,
		CommonName:    "MOSIP Root CA",
	})
	require.NoError(t, err)

	_, err = km.GenerateMasterKey(ctx, keymanager.GenerateMasterKeyRequest{
		ApplicationID: config.OIDCServiceAppID,
		ReferenceID:   defaultSignReferenceID,
		ObjectType:    keymanager.ObjectTypeCertificate,
		CommonName:    "test esignet sign key",
	})
	require.NoError(t, err)

	return km
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetPublicKeys_ReturnsOwnKeyMergedWithIDSystemKeys() {
	t := ts.T()
	idCertPEM := testCertPEM(t, "id-system-key")
	p := &runtimeCryptoProvider{
		svc:             newTestKeyManagerService(t),
		signReferenceID: defaultSignReferenceID,
		authnProvider: &fakeAuthnProviderForCrypto{
			certs: []shared.CertificateData{{KeyID: "id-system-ref", Certificate: idCertPEM}},
		},
	}

	keys, err := p.GetPublicKeys(context.Background(), providers.PublicKeyFilter{})

	ts.Require().NoError(err)
	ts.Require().Len(keys, 2, "esignet's own key plus the merged ID-system key")
	ts.Assert().Equal(defaultSignReferenceID, keys[0].KeyID)
	ts.Assert().NotNil(keys[0].PublicKey)
	ts.Assert().NotEmpty(keys[0].Thumbprint)
	ts.Assert().Equal("id-system-ref", keys[1].KeyID)
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetPublicKeys_NoIDSystemProvider_ReturnsOnlyOwnKey() {
	t := ts.T()
	p := &runtimeCryptoProvider{
		svc:             newTestKeyManagerService(t),
		signReferenceID: defaultSignReferenceID,
	}

	keys, err := p.GetPublicKeys(context.Background(), providers.PublicKeyFilter{})

	ts.Require().NoError(err)
	ts.Require().Len(keys, 1)
	ts.Assert().Equal(defaultSignReferenceID, keys[0].KeyID)
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetPublicKeys_CertificateLookupFailure_WrapsErrKeyNotFound() {
	// No ROOT/master key provisioned on this Service, so GetCertificate has
	// nothing to lazily rotate from and returns an error.
	p := &runtimeCryptoProvider{
		svc:             keymanager.NewServiceWithQuerier(kmtest.NewStateQuerier(), kmtest.NewFakeKeyStore(), keymanager.Config{}),
		signReferenceID: defaultSignReferenceID,
	}

	_, err := p.GetPublicKeys(context.Background(), providers.PublicKeyFilter{})

	ts.Require().ErrorIs(err, providers.ErrKeyNotFound)
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetPublicKey_NilCache_DisablesCachingWithoutPanic() {
	p := &runtimeCryptoProvider{jwkCache: nil}
	jwkMap := testRSAJWKMap(ts.T())

	first, err := p.getPublicKey(providers.KeyRef{PublicKeyJWK: jwkMap})
	ts.Require().NoError(err)
	second, err := p.getPublicKey(providers.KeyRef{PublicKeyJWK: jwkMap})
	ts.Require().NoError(err)

	ts.Assert().NotSame(first, second, "a nil jwkCache must disable caching entirely, not panic or silently reuse state")
}
