/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"errors"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/clientmgmt/db"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/consentmgmt"
	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
	"github.com/mosip/esignet/internal/keymanager/kmtest"
	"github.com/mosip/esignet/internal/keymanager/signature"
)

const (
	dsTestUser      = "user-12345"
	dsTestSID       = "sid-abc-123"
	dsSuperClient   = "super-app-id"
	dsChildClient   = "child-app-id"
	dsCousinClient  = "cousin-app-id"
	dsOtherClient   = "other-vendor-app-id"
	dsLegacyClient  = "legacy-app-id"
	dsEnabledConfig = `{"allowed_authorization_scopes":["device_sso"]}`
)

type DeviceSecretProviderTestSuite struct {
	suite.Suite
	km        *keymanager.Service
	sigSvc    *signature.Service
	cryptoSvc *cryptomanager.Service
	appCfg    *config.AppConfig
	clientSvc *clientmgmt.Service
	consent   *memConsentStore
}

func TestDeviceSecretProviderTestSuite(t *testing.T) {
	suite.Run(t, new(DeviceSecretProviderTestSuite))
}

type multiClientStubQuerier struct {
	db.Querier
	clients map[string]db.ClientDetail
}

func (q *multiClientStubQuerier) GetActiveClient(_ context.Context, id string) (db.ClientDetail, error) {
	c, ok := q.clients[id]
	if !ok || c.Status != "ACTIVE" {
		return db.ClientDetail{}, sql.ErrNoRows
	}
	return c, nil
}

// memConsentStore is an in-memory stand-in for consentmgmt.Service: the current record per
// (client, user) plus every saved record, which stands for the append-only consent_history rows.
type memConsentStore struct {
	mu      sync.Mutex
	records map[string]*consentmgmt.ConsentRecord
	history []consentmgmt.ConsentRecord
	// beforeUpdate and beforeSave run inside UpdateRecord, before the current record is read and
	// before the result is stored: they stand in for time passing inside the database transaction.
	beforeUpdate func()
	beforeSave   func()
}

func newMemConsentStore() *memConsentStore {
	return &memConsentStore{records: map[string]*consentmgmt.ConsentRecord{}}
}

func consentKey(clientID, user string) string { return clientID + "|" + user }

func (s *memConsentStore) FetchRecord(_ context.Context, clientID, userID string) (*consentmgmt.ConsentRecord, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	rec, ok := s.records[consentKey(clientID, userID)]
	if !ok {
		return nil, nil
	}
	cp := *rec
	return &cp, nil
}

// SaveRecord mirrors the real service: claims and scopes are stored normalized, slices are never
// nil, and every save adds one history entry.
func (s *memConsentStore) SaveRecord(_ context.Context, c *consentmgmt.ConsentRecord) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.save(c)
}

// UpdateRecord mirrors consentmgmt.Service.UpdateRecord: fn sees a copy of the current record
// while the store's lock is held, and a non-nil result is stored like SaveRecord.
func (s *memConsentStore) UpdateRecord(_ context.Context, clientID, userID string, fn consentmgmt.UpdateFunc) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.beforeUpdate != nil {
		s.beforeUpdate()
	}
	var existing *consentmgmt.ConsentRecord
	if rec, ok := s.records[consentKey(clientID, userID)]; ok {
		cp := *rec
		existing = &cp
	}
	record, err := fn(existing)
	if err != nil || record == nil {
		return err
	}
	if s.beforeSave != nil {
		s.beforeSave()
	}
	return s.save(record)
}

// save stores c; the caller holds s.mu.
func (s *memConsentStore) save(c *consentmgmt.ConsentRecord) error {
	claims := consentmgmt.NormalizeClaims(c.Claims)
	scopeMap := consentmgmt.NormalizeAuthorizationScopes(c.AuthorizationScopes)
	hash, err := consentmgmt.HashRequestedConsent(claims, scopeMap)
	if err != nil {
		return err
	}
	scopes := make([]string, 0, len(scopeMap))
	for scope := range scopeMap {
		scopes = append(scopes, scope)
	}
	rec := consentmgmt.ConsentRecord{
		ID:                  c.ID,
		ClientID:            c.ClientID,
		UserID:              c.UserID,
		Claims:              claims,
		AuthorizationScopes: scopes,
		Hash:                hash,
		AcceptedClaims:      append([]string{}, c.AcceptedClaims...),
		PermittedScopes:     append([]string{}, c.PermittedScopes...),
		CreatedAt:           c.CreatedAt,
		ExpiresAt:           c.ExpiresAt,
	}
	s.history = append(s.history, rec)
	s.records[consentKey(c.ClientID, c.UserID)] = &rec
	return nil
}

// failingClientQuerier fails every client lookup with a database error.
type failingClientQuerier struct {
	db.Querier
	err error
}

func (q *failingClientQuerier) GetActiveClient(_ context.Context, _ string) (db.ClientDetail, error) {
	return db.ClientDetail{}, q.err
}

func clientRow(id, rpID, claims, additionalConfig string) db.ClientDetail {
	row := db.ClientDetail{
		ID:           id,
		Name:         `{"@none":"` + id + `"}`,
		RpID:         rpID,
		RedirectUris: `["https://` + id + `.example.com"]`,
		Claims:       claims,
		AcrValues:    `[]`,
		GrantTypes:   `["authorization_code","token_exchange"]`,
		AuthMethods:  `["private_key_jwt"]`,
		Status:       "ACTIVE",
	}
	if additionalConfig != "" {
		row.AdditionalConfig = sql.NullString{String: additionalConfig, Valid: true}
	}
	return row
}

func (ts *DeviceSecretProviderTestSuite) SetupSuite() {
	t := ts.T()
	ctx := context.Background()

	kmQuerier := kmtest.NewStateQuerier()
	ts.km = keymanager.NewServiceWithQuerier(kmQuerier, kmtest.NewFakeKeyStore(), keymanager.Config{
		AsymmetricKeyLength:  2048,
		CertCommonName:       "www.mosip.io",
		CertOrganizationUnit: "thunder-tech-team",
		CertOrganization:     "IIITB",
		CertLocation:         "Bangalore",
		CertState:            "KA",
		CertCountry:          "IN",
	})

	for _, req := range []keymanager.GenerateMasterKeyRequest{
		{ApplicationID: keymanager.AppIDRoot, ObjectType: keymanager.ObjectTypeCertificate, CommonName: "MOSIP Root CA"},
		{ApplicationID: config.OIDCServiceAppID, ReferenceID: keymanager.RefIDRSA2048, ObjectType: keymanager.ObjectTypeCertificate, CommonName: "test esignet master key"},
		{ApplicationID: config.OIDCServiceAppID, ReferenceID: defaultSignReferenceID, ObjectType: keymanager.ObjectTypeCertificate, CommonName: "test esignet sign key"},
	} {
		_, err := ts.km.GenerateMasterKey(ctx, req)
		require.NoError(t, err, req.ReferenceID)
	}
	// The device secret encryption key is a Component Encryption Key, created on first request.
	_, err := ts.km.GetCertificate(ctx, config.OIDCServiceAppID, DeviceSecretEncryptionRefID)
	require.NoError(t, err)

	ts.sigSvc = signature.NewService(ts.km)
	ts.cryptoSvc = cryptomanager.NewService(kmQuerier, ts.km, cryptomanager.LoadConfig())
	ts.appCfg = &config.AppConfig{
		Issuer: "https://esignet.example.com",
		ScopeClaims: map[string][]string{
			"openid":  {},
			"profile": {"name", "picture"},
			"email":   {"email"},
		},
		ResourceServers: []config.ResourceServerConfig{{
			ID: "rs-1", Identifier: "https://api.example.com", Default: true,
			Scopes: map[string]string{"read": "Read access"},
		}},
	}

	clients := map[string]db.ClientDetail{
		// The super app and its sibling share a relying party and are enabled for Native SSO.
		dsSuperClient: clientRow(dsSuperClient, "vendor-family-1", `["name","email"]`,
			`{"allowed_authorization_scopes":["device_sso"],"allowed_child_clients":["cousin-app-id"]}`),
		dsChildClient: clientRow(dsChildClient, "vendor-family-1", `["name","email"]`, dsEnabledConfig),
		// A different relying party explicitly allowed by the super app.
		dsCousinClient: clientRow(dsCousinClient, "vendor-family-3", `["name"]`, dsEnabledConfig),
		// A different vendor, enabled for Native SSO but not related to the super app.
		dsOtherClient: clientRow(dsOtherClient, "vendor-family-2", `["name"]`, dsEnabledConfig),
		// Same vendor, but not registered for device_sso.
		dsLegacyClient: clientRow(dsLegacyClient, "vendor-family-1", `["name"]`, ""),
	}
	ts.clientSvc = clientmgmt.NewServiceWithQuerier(&multiClientStubQuerier{clients: clients}, nil, 0, nil)
}

func (ts *DeviceSecretProviderTestSuite) SetupTest() {
	ts.consent = newMemConsentStore()
}

func (ts *DeviceSecretProviderTestSuite) newProvider() providers.DeviceSecretProvider {
	return NewDeviceSecretProvider(ts.appCfg, ts.clientSvc, ts.consent, ts.sigSvc, ts.cryptoSvc)
}

func (ts *DeviceSecretProviderTestSuite) superParams(validity time.Duration) *providers.DeviceSecretParams {
	return &providers.DeviceSecretParams{
		Sub:            dsTestUser,
		AuthID:         "code-67890",
		SessionID:      dsTestSID,
		ClientID:       dsSuperClient,
		Scope:          "openid profile device_sso",
		ACR:            "mosip:idp:acr:generated-code",
		Claims:         map[string]any{"name": "Alice"},
		CnfJKT:         "dpop-key-a-jkt-thumbprint",
		ExpiryDuration: validity,
	}
}

func (ts *DeviceSecretProviderTestSuite) mintSuperSecret() string {
	res, svcErr := ts.newProvider().GenerateDeviceSecret(context.Background(), ts.superParams(time.Hour))
	ts.Require().Nil(svcErr)
	return res.DeviceSecret
}

// seedSuperConsent stores the consent the user gave the super app.
func (ts *DeviceSecretProviderTestSuite) seedSuperConsent(acceptedClaims, permittedScopes []string, expiresAt sql.NullTime) {
	ts.seedConsent(dsSuperClient, "consent-super", acceptedClaims, permittedScopes, expiresAt)
}

// seedConsent stores a consent the user gave clientID and clears the audit trail.
func (ts *DeviceSecretProviderTestSuite) seedConsent(clientID, id string, acceptedClaims, permittedScopes []string,
	expiresAt sql.NullTime) {
	claims := map[string]any{"userinfo": map[string]any{}}
	for _, c := range acceptedClaims {
		claims["userinfo"].(map[string]any)[c] = nil
	}
	ts.Require().NoError(ts.consent.SaveRecord(context.Background(), &consentmgmt.ConsentRecord{
		ID:                  id,
		ClientID:            clientID,
		UserID:              dsTestUser,
		Claims:              claims,
		AuthorizationScopes: permittedScopes,
		AcceptedClaims:      acceptedClaims,
		PermittedScopes:     permittedScopes,
		CreatedAt:           time.Now().UTC(),
		ExpiresAt:           expiresAt,
	}))
	ts.consent.history = nil
}

// ---- generation ----

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_Success() {
	before := time.Now().UTC()
	res, svcErr := ts.newProvider().GenerateDeviceSecret(context.Background(), ts.superParams(30*24*time.Hour))
	ts.Require().Nil(svcErr)
	ts.Require().NotNil(res)
	ts.Require().NotEmpty(res.DeviceSecret)

	// The device secret is a compact JWE (5 segments): the signed claims are opaque to the client.
	ts.Require().Len(strings.Split(res.DeviceSecret, "."), 5)

	hasher := sha256.New()
	hasher.Write([]byte(res.DeviceSecret))
	ts.Require().Equal(base64.RawURLEncoding.EncodeToString(hasher.Sum(nil)), res.DSHash)
	ts.Require().Equal(DeviceSecretHash(res.DeviceSecret), res.DSHash)

	ts.Require().WithinDuration(before.Add(30*24*time.Hour), res.ExpiresAt, 5*time.Second)
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_EachSecretIsUnique() {
	p := ts.newProvider()
	first, svcErr := p.GenerateDeviceSecret(context.Background(), ts.superParams(time.Hour))
	ts.Require().Nil(svcErr)
	second, svcErr := p.GenerateDeviceSecret(context.Background(), ts.superParams(time.Hour))
	ts.Require().Nil(svcErr)
	ts.Require().NotEqual(first.DeviceSecret, second.DeviceSecret)
	ts.Require().NotEqual(first.DSHash, second.DSHash)
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_MissingSub_Fails() {
	res, svcErr := ts.newProvider().GenerateDeviceSecret(context.Background(), &providers.DeviceSecretParams{
		ClientID: dsSuperClient, SessionID: dsTestSID,
	})
	ts.Require().Nil(res)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("invalid_request", svcErr.Code)
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_MissingSessionID_Fails() {
	params := ts.superParams(time.Hour)
	params.SessionID = ""
	res, svcErr := ts.newProvider().GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(res)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("invalid_request", svcErr.Code)
	ts.Require().Contains(svcErr.ErrorDescription.DefaultValue, "sid")
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_MissingCnfJKT_Fails() {
	params := ts.superParams(time.Hour)
	params.CnfJKT = ""
	res, svcErr := ts.newProvider().GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(res)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("invalid_request", svcErr.Code)
	ts.Require().Contains(svcErr.ErrorDescription.DefaultValue, "cnf.jkt")
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_UnboundSecret_Rejected() {
	// A device secret minted before cnf.jkt was mandatory (signed and encrypted by this server, but
	// with no key binding) is refused rather than accepted as a bearer credential.
	payload := deviceSecretPayload{
		Iss: ts.appCfg.Issuer, Sub: dsTestUser, SID: dsTestSID, ClientID: dsSuperClient,
		Scope: "openid profile device_sso", Iat: time.Now().Unix(), Exp: time.Now().Add(time.Hour).Unix(),
		JTI: "unbound-1", TokenType: DeviceSecretTokenType,
	}
	data, err := json.Marshal(payload)
	ts.Require().NoError(err)
	signResp, err := ts.sigSvc.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: config.OIDCServiceAppID,
		ReferenceID:   defaultSignReferenceID,
		DataToSign:    base64.RawURLEncoding.EncodeToString(data),
		IncludeKeyID:  true,
	})
	ts.Require().NoError(err)
	encResp, err := ts.cryptoSvc.JWTEncrypt(context.Background(), cryptomanager.JWTEncryptRequest{
		ApplicationID: config.OIDCServiceAppID,
		ReferenceID:   DeviceSecretEncryptionRefID,
		Data:          signResp.JWTSignedData,
	})
	ts.Require().NoError(err)

	claims, valErr := ts.newProvider().ValidateDeviceSecret(context.Background(), dsSuperClient, encResp.Data)
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("invalid_grant", valErr.Code)
	ts.Require().Contains(valErr.ErrorDescription.DefaultValue, "not bound to a DPoP key")
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_ClientNotEnabled_Fails() {
	params := ts.superParams(time.Hour)
	params.ClientID = dsLegacyClient
	res, svcErr := ts.newProvider().GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(res)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("invalid_scope", svcErr.Code)
	ts.Require().Contains(svcErr.ErrorDescription.DefaultValue, "device_sso")
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_UnknownClient_Fails() {
	params := ts.superParams(time.Hour)
	params.ClientID = "ghost"
	res, svcErr := ts.newProvider().GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(res)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("invalid_client", svcErr.Code)
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_ValidityBoundedByConfig() {
	cfg := *ts.appCfg
	cfg.NativeSSO.DeviceSecretValiditySeconds = 600
	p := NewDeviceSecretProvider(&cfg, ts.clientSvc, ts.consent, ts.sigSvc, ts.cryptoSvc)

	before := time.Now().UTC()
	res, svcErr := p.GenerateDeviceSecret(context.Background(), ts.superParams(30*24*time.Hour))
	ts.Require().Nil(svcErr)
	ts.Require().WithinDuration(before.Add(10*time.Minute), res.ExpiresAt, 5*time.Second)

	// The configured bound never extends what the engine asked for.
	res, svcErr = p.GenerateDeviceSecret(context.Background(), ts.superParams(time.Minute))
	ts.Require().Nil(svcErr)
	ts.Require().WithinDuration(before.Add(time.Minute), res.ExpiresAt, 5*time.Second)
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_NoEncryption_Fails() {
	p := NewDeviceSecretProvider(ts.appCfg, ts.clientSvc, ts.consent, ts.sigSvc, nil)
	res, svcErr := p.GenerateDeviceSecret(context.Background(), ts.superParams(time.Hour))
	ts.Require().Nil(res)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("server_error", svcErr.Code)
}

// ---- validation ----

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_SameClient_Success() {
	p := ts.newProvider()
	params := ts.superParams(time.Hour)
	genRes, svcErr := p.GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(svcErr)

	claims, valErr := p.ValidateDeviceSecret(context.Background(), dsSuperClient, genRes.DeviceSecret)
	ts.Require().Nil(valErr)
	ts.Require().NotNil(claims)
	ts.Require().Equal(dsTestUser, claims.Sub)
	ts.Require().Equal(dsSuperClient, claims.ClientID)
	ts.Require().Equal("code-67890", claims.AuthID)
	ts.Require().Equal(dsTestSID, claims.SessionID)
	ts.Require().Equal("dpop-key-a-jkt-thumbprint", claims.CnfJKT)
	ts.Require().Equal("openid profile device_sso", claims.Scope)
	ts.Require().Equal("mosip:idp:acr:generated-code", claims.ACR)
	ts.Require().Equal("Alice", claims.Claims["name"])
	ts.Require().Equal("https://esignet.example.com", claims.Issuer)
	ts.Require().NotEmpty(claims.JTI)
	ts.Require().WithinDuration(genRes.ExpiresAt, claims.ExpiresAt, time.Second)
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_ChildClientSameVendor_Success() {
	secret := ts.mintSuperSecret()
	claims, valErr := ts.newProvider().ValidateDeviceSecret(context.Background(), dsChildClient, secret)
	ts.Require().Nil(valErr)
	ts.Require().NotNil(claims)
	ts.Require().Equal(dsSuperClient, claims.ClientID, "the claims name the client the secret was minted for")
	ts.Require().Equal(dsTestSID, claims.SessionID)
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_AllowedChildClient_Success() {
	secret := ts.mintSuperSecret()
	claims, valErr := ts.newProvider().ValidateDeviceSecret(context.Background(), dsCousinClient, secret)
	ts.Require().Nil(valErr)
	ts.Require().NotNil(claims)
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_DifferentVendor_Rejected() {
	secret := ts.mintSuperSecret()
	claims, valErr := ts.newProvider().ValidateDeviceSecret(context.Background(), dsOtherClient, secret)
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("invalid_grant", valErr.Code)
	ts.Require().Contains(valErr.ErrorDescription.DefaultValue, "vendor mismatch")
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_ClientNotEnabled_Rejected() {
	secret := ts.mintSuperSecret()
	claims, valErr := ts.newProvider().ValidateDeviceSecret(context.Background(), dsLegacyClient, secret)
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("unauthorized_client", valErr.Code)
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_UnknownClient_Rejected() {
	secret := ts.mintSuperSecret()
	claims, valErr := ts.newProvider().ValidateDeviceSecret(context.Background(), "ghost", secret)
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("invalid_client", valErr.Code)
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_TamperedToken_Rejected() {
	secret := ts.mintSuperSecret()
	// Flip one bit of the decoded authentication tag: authenticated decryption must reject it.
	parts := strings.Split(secret, ".")
	ts.Require().Len(parts, 5, "a device secret is a compact JWE")
	tag, err := base64.RawURLEncoding.DecodeString(parts[4])
	ts.Require().NoError(err)
	tag[0] ^= 0x01
	parts[4] = base64.RawURLEncoding.EncodeToString(tag)
	tampered := strings.Join(parts, ".")
	claims, valErr := ts.newProvider().ValidateDeviceSecret(context.Background(), dsSuperClient, tampered)
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("invalid_grant", valErr.Code)
	ts.Require().Contains(valErr.ErrorDescription.DefaultValue, "could not be decrypted")
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_PlainJWS_Rejected() {
	// A signed-but-unencrypted token is not a device secret this server issued.
	signResp, err := ts.sigSvc.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: config.OIDCServiceAppID,
		ReferenceID:   defaultSignReferenceID,
		DataToSign:    base64.RawURLEncoding.EncodeToString([]byte(`{"sub":"x"}`)),
		IncludeKeyID:  true,
	})
	ts.Require().NoError(err)
	claims, valErr := ts.newProvider().ValidateDeviceSecret(context.Background(), dsSuperClient, signResp.JWTSignedData)
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("invalid_grant", valErr.Code)
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_ExpiredToken_Rejected() {
	p := ts.newProvider()
	genRes, svcErr := p.GenerateDeviceSecret(context.Background(), ts.superParams(-2*time.Hour))
	ts.Require().Nil(svcErr)

	claims, valErr := p.ValidateDeviceSecret(context.Background(), dsSuperClient, genRes.DeviceSecret)
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("invalid_grant", valErr.Code)
	ts.Require().Contains(valErr.ErrorDescription.DefaultValue, "expired")
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_ForeignIssuer_Rejected() {
	secret := ts.mintSuperSecret()
	cfg := *ts.appCfg
	cfg.Issuer = "https://another-esignet.example.com"
	p := NewDeviceSecretProvider(&cfg, ts.clientSvc, ts.consent, ts.sigSvc, ts.cryptoSvc)

	claims, valErr := p.ValidateDeviceSecret(context.Background(), dsSuperClient, secret)
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("invalid_grant", valErr.Code)
	ts.Require().Contains(valErr.ErrorDescription.DefaultValue, "not issued by this server")
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_MissingInput_Rejected() {
	claims, valErr := ts.newProvider().ValidateDeviceSecret(context.Background(), "", "")
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("invalid_request", valErr.Code)
}

// ---- derived consent (bonus: re-consent in the child app) ----

func (ts *DeviceSecretProviderTestSuite) derivedRequest(scopes ...string) *providers.DerivedConsentRequest {
	return &providers.DerivedConsentRequest{
		Sub:             dsTestUser,
		ClientID:        dsChildClient,
		OriginClientID:  dsSuperClient,
		RequestedScopes: scopes,
		GrantedScopes:   []string{"openid", "profile", "email", "device_sso"},
		AuthID:          "code-67890",
		SessionID:       dsTestSID,
	}
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_CoveredByOriginConsent() {
	ts.seedSuperConsent([]string{"name", "email"}, nil, sql.NullTime{})
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)
	ts.Require().True(res.Allowed)
	ts.Require().Empty(res.MissingClaims)
	ts.Require().Empty(res.MissingScopes)

	// The child app now holds a consent record of its own, with an audit-trail row.
	record, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	ts.Require().NotNil(record)
	ts.Require().Equal([]string{"name"}, record.AcceptedClaims)
	ts.Require().Empty(record.PermittedScopes)
	ts.Require().Len(ts.consent.history, 1)
	ts.Require().Equal(dsChildClient, ts.consent.history[0].ClientID)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_DerivedRecordMatchesInteractiveHash() {
	ts.seedSuperConsent([]string{"name", "email"}, nil, sql.NullTime{})
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)
	_, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)

	// An interactive authorization of the child app with the same scopes hashes to the same value,
	// so the consent screen is skipped on a later browser login.
	record, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	expected, err := requestHash(mergeScopeClaims(map[string]any{}, []string{"name"}), nil)
	ts.Require().NoError(err)
	ts.Require().Equal(expected, record.Hash)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_MissingClaimRequiresInteraction() {
	// The user released only name to the super app; the child app also needs email.
	ts.seedSuperConsent([]string{"name"}, nil, sql.NullTime{})
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile", "email"))
	ts.Require().Nil(svcErr)
	ts.Require().False(res.Allowed)
	ts.Require().Equal([]string{"email"}, res.MissingClaims)

	record, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	ts.Require().Nil(record, "no consent is recorded for the child app when interaction is required")
	ts.Require().Empty(ts.consent.history)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_AuthorizationScopeNotPermitted() {
	ts.seedSuperConsent([]string{"name"}, nil, sql.NullTime{})
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "read"))
	ts.Require().Nil(svcErr)
	ts.Require().False(res.Allowed)
	ts.Require().Equal([]string{"read"}, res.MissingScopes)
	ts.Require().Empty(res.MissingClaims)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_PermittedAuthorizationScope() {
	ts.seedSuperConsent([]string{"name"}, []string{"read"}, sql.NullTime{})
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "read"))
	ts.Require().Nil(svcErr)
	ts.Require().True(res.Allowed)

	record, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	ts.Require().Equal([]string{"read"}, record.PermittedScopes)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_NoOriginConsent() {
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)
	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)
	ts.Require().False(res.Allowed)
	ts.Require().Equal([]string{"name"}, res.MissingClaims)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_ExpiredOriginConsent() {
	expired := sql.NullTime{Time: time.Now().UTC().Add(-time.Hour), Valid: true}
	ts.seedSuperConsent([]string{"name"}, nil, expired)
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)
	ts.Require().False(res.Allowed)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_InheritsOriginExpiry() {
	expiresAt := sql.NullTime{Time: time.Now().UTC().Add(48 * time.Hour).Truncate(time.Second), Valid: true}
	ts.seedSuperConsent([]string{"name"}, nil, expiresAt)
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)
	ts.Require().True(res.Allowed)
	record, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	ts.Require().True(record.ExpiresAt.Valid)
	ts.Require().WithinDuration(expiresAt.Time, record.ExpiresAt.Time, time.Second)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_RepeatedExchangeWritesOnce() {
	ts.seedSuperConsent([]string{"name", "email"}, nil, sql.NullTime{})
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	for i := 0; i < 3; i++ {
		res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
		ts.Require().Nil(svcErr)
		ts.Require().True(res.Allowed)
	}
	ts.Require().Len(ts.consent.history, 1, "one audit row per consent change, not per exchange")
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_ExistingSupersetKept() {
	// The user consented interactively to more for the child app than the exchange derives.
	ts.seedSuperConsent([]string{"name", "email"}, []string{"read"}, sql.NullTime{})
	ts.seedConsent(dsChildClient, "consent-child", []string{"name", "email"}, []string{"read"}, sql.NullTime{})
	before, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)
	ts.Require().True(res.Allowed)

	after, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	ts.Require().Equal(before, after, "a covering consent is not narrowed to the derived one")
	ts.Require().Empty(ts.consent.history)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_ExistingNarrowerWidened() {
	ts.seedSuperConsent([]string{"name", "email"}, nil, sql.NullTime{})
	ts.seedConsent(dsChildClient, "consent-child", []string{"email"}, nil, sql.NullTime{})
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)
	ts.Require().True(res.Allowed)

	record, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	ts.Require().Equal("consent-child", record.ID)
	ts.Require().ElementsMatch([]string{"email", "name"}, record.AcceptedClaims)
	ts.Require().Len(ts.consent.history, 1)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_ExpiredChildConsentReplaced() {
	ts.seedSuperConsent([]string{"name", "email"}, nil, sql.NullTime{})
	expired := sql.NullTime{Time: time.Now().UTC().Add(-time.Hour), Valid: true}
	ts.seedConsent(dsChildClient, "consent-child", []string{"name", "email"}, nil, expired)
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)
	ts.Require().True(res.Allowed)

	record, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	ts.Require().False(record.IsExpired(time.Now().UTC()))
	ts.Require().Equal([]string{"name"}, record.AcceptedClaims)
	ts.Require().Len(ts.consent.history, 1)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_UnknownChild() {
	ts.seedSuperConsent([]string{"name"}, nil, sql.NullTime{})
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)
	req := ts.derivedRequest("openid", "profile")
	req.ClientID = "ghost"

	res, svcErr := p.ResolveDerivedConsent(context.Background(), req)
	ts.Require().Nil(res)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("invalid_client", svcErr.Code)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_MissingInput() {
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)
	res, svcErr := p.ResolveDerivedConsent(context.Background(), &providers.DerivedConsentRequest{})
	ts.Require().Nil(res)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("invalid_request", svcErr.Code)
}

func (ts *DeviceSecretProviderTestSuite) TestDeviceSecretHash() {
	sum := sha256.Sum256([]byte("abc"))
	ts.Require().Equal(base64.RawURLEncoding.EncodeToString(sum[:]), DeviceSecretHash("abc"))
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_UnpacksSignedKycToken() {
	// The mock identity system's KYC token is a signed JWS the engine stores under the raw key.
	payload := base64.RawURLEncoding.EncodeToString([]byte(`{"iss":"mock","aud":"super-app-id","exp":1,"sub":"psut","name":"Alice","email":"alice@example.com"}`))
	kyc := "eyJhbGciOiJQUzI1NiJ9." + payload + ".c2ln"
	params := ts.superParams(time.Hour)
	params.Claims = map[string]any{providers.RawJWTAttributeKey: kyc, "extra": "kept"}

	p := ts.newProvider()
	res, svcErr := p.GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(svcErr)
	claims, valErr := p.ValidateDeviceSecret(context.Background(), dsSuperClient, res.DeviceSecret)
	ts.Require().Nil(valErr)
	ts.Require().Equal("Alice", claims.Claims["name"])
	ts.Require().Equal("alice@example.com", claims.Claims["email"])
	ts.Require().Equal("kept", claims.Claims["extra"])
	ts.Require().NotContains(claims.Claims, providers.RawJWTAttributeKey)
	ts.Require().NotContains(claims.Claims, "aud", "token-level claims are not user attributes")
	ts.Require().NotContains(claims.Claims, "sub")
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_KeepsEncryptedKycTokenOpaque() {
	kyc := "h.k.i.c.t" // a compact JWE has five segments and cannot be unpacked here
	params := ts.superParams(time.Hour)
	params.Claims = map[string]any{providers.RawJWTAttributeKey: kyc}

	p := ts.newProvider()
	res, svcErr := p.GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(svcErr)
	claims, valErr := p.ValidateDeviceSecret(context.Background(), dsSuperClient, res.DeviceSecret)
	ts.Require().Nil(valErr)
	ts.Require().Equal(kyc, claims.Claims[providers.RawJWTAttributeKey])
}

func (ts *DeviceSecretProviderTestSuite) failingClientProvider() providers.DeviceSecretProvider {
	failing := clientmgmt.NewServiceWithQuerier(&failingClientQuerier{err: errors.New("connection reset by peer")}, nil, 0, nil)
	return NewDeviceSecretProvider(ts.appCfg, failing, ts.consent, ts.sigSvc, ts.cryptoSvc)
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_ClientLookupFailure_ServerError() {
	res, svcErr := ts.failingClientProvider().GenerateDeviceSecret(context.Background(), ts.superParams(time.Hour))
	ts.Require().Nil(res)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("server_error", svcErr.Code, "a database failure is not the client's fault")
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_ClientLookupFailure_ServerError() {
	secret := ts.mintSuperSecret()
	claims, svcErr := ts.failingClientProvider().ValidateDeviceSecret(context.Background(), dsSuperClient, secret)
	ts.Require().Nil(claims)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("server_error", svcErr.Code)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_ClientLookupFailure_ServerError() {
	ts.seedSuperConsent([]string{"name"}, nil, sql.NullTime{})
	p := ts.failingClientProvider().(providers.DeviceSSOConsentProvider)
	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(res)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("server_error", svcErr.Code)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_WidenedConsentKeepsChildExpiry() {
	// The child app's own consent expires sooner than the originating one: widening keeps the
	// child's own date, an exchange never extends what the user granted the child app directly.
	childExpiry := sql.NullTime{Time: time.Now().UTC().Add(time.Hour).Truncate(time.Second), Valid: true}
	originExpiry := sql.NullTime{Time: time.Now().UTC().Add(48 * time.Hour).Truncate(time.Second), Valid: true}
	ts.seedSuperConsent([]string{"name", "email"}, nil, originExpiry)
	ts.seedConsent(dsChildClient, "consent-child", []string{"email"}, nil, childExpiry)
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)
	ts.Require().True(res.Allowed)

	record, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	ts.Require().ElementsMatch([]string{"email", "name"}, record.AcceptedClaims)
	ts.Require().True(record.ExpiresAt.Valid)
	ts.Require().WithinDuration(childExpiry.Time, record.ExpiresAt.Time, time.Second)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_WidenedConsentKeepsChildExpiryWhenOriginSooner() {
	// The child app's own consent never expires, the originating one does: widening keeps the
	// child's own lifetime, an exchange never cuts short what the user granted the child app
	// directly. The originating consent bounds the exchange itself, not the stored consent.
	originExpiry := sql.NullTime{Time: time.Now().UTC().Add(48 * time.Hour).Truncate(time.Second), Valid: true}
	ts.seedSuperConsent([]string{"name", "email"}, nil, originExpiry)
	ts.seedConsent(dsChildClient, "consent-child", []string{"email"}, nil, sql.NullTime{})
	p := ts.newProvider().(providers.DeviceSSOConsentProvider)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)
	ts.Require().True(res.Allowed)

	record, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	ts.Require().ElementsMatch([]string{"email", "name"}, record.AcceptedClaims)
	ts.Require().False(record.ExpiresAt.Valid, "the child app's consent keeps its own, unlimited lifetime")
}

// clockedProvider returns a provider that reads the clock from *clock, so a test can move time
// while an exchange is in flight.
func (ts *DeviceSecretProviderTestSuite) clockedProvider(clock *time.Time) providers.DeviceSSOConsentProvider {
	p := ts.newProvider().(*deviceSecretProvider)
	p.now = func() time.Time { return *clock }
	return p
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_ChildExpiresDuringLookup() {
	// The child app's consent is valid when the exchange starts and expires while its record is
	// being read: the clock is read again inside the update, so the stale consent is replaced,
	// not widened and reused.
	start := time.Now().UTC().Truncate(time.Second)
	clock := start
	originExpiry := sql.NullTime{Time: start.Add(48 * time.Hour), Valid: true}
	ts.seedSuperConsent([]string{"name", "email"}, nil, originExpiry)
	ts.seedConsent(dsChildClient, "consent-child", []string{"email"}, nil,
		sql.NullTime{Time: start.Add(time.Second), Valid: true})
	ts.consent.beforeUpdate = func() { clock = start.Add(2 * time.Second) }
	p := ts.clockedProvider(&clock)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)
	ts.Require().True(res.Allowed)

	record, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	ts.Require().NotEqual("consent-child", record.ID, "the expired consent is replaced, not widened")
	ts.Require().Equal([]string{"name"}, record.AcceptedClaims)
	ts.Require().WithinDuration(originExpiry.Time, record.ExpiresAt.Time, time.Second)
	ts.Require().Len(ts.consent.history, 1)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_OriginExpiresDuringSave() {
	// The originating consent is valid when the exchange starts and expires while the derived
	// consent is being recorded: the record is written, but the exchange is not granted.
	start := time.Now().UTC().Truncate(time.Second)
	clock := start
	ts.seedSuperConsent([]string{"name", "email"}, nil, sql.NullTime{Time: start.Add(time.Second), Valid: true})
	ts.consent.beforeSave = func() { clock = start.Add(2 * time.Second) }
	p := ts.clockedProvider(&clock)

	res, svcErr := p.ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
	ts.Require().Nil(svcErr)
	ts.Require().False(res.Allowed, "a consent that expired during the save does not grant the exchange")
	ts.Require().Equal([]string{"name"}, res.MissingClaims)
	ts.Require().Len(ts.consent.history, 1)
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_ConcurrentDifferentScopesBothKept() {
	// Two scope sets exchanged at the same time through two provider instances, as two replicas
	// would run them: each widening sees the other's result, so the record ends up with both.
	ts.seedSuperConsent([]string{"name", "email"}, nil, sql.NullTime{})
	ps := []providers.DeviceSSOConsentProvider{
		ts.newProvider().(providers.DeviceSSOConsentProvider),
		ts.newProvider().(providers.DeviceSSOConsentProvider),
	}

	const n = 8
	var wg sync.WaitGroup
	allowed := make([]bool, n)
	codes := make([]string, n)
	for i := 0; i < n; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			scope := "profile"
			if i%2 == 1 {
				scope = "email"
			}
			res, svcErr := ps[i%2].ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", scope))
			if svcErr != nil {
				codes[i] = svcErr.Code
				return
			}
			allowed[i] = res.Allowed
		}(i)
	}
	wg.Wait()
	for i := 0; i < n; i++ {
		ts.Require().Empty(codes[i])
		ts.Require().True(allowed[i])
	}
	record, err := ts.consent.FetchRecord(context.Background(), dsChildClient, dsTestUser)
	ts.Require().NoError(err)
	ts.Require().ElementsMatch([]string{"email", "name"}, record.AcceptedClaims)
	ts.Require().Len(ts.consent.history, 2, "one audit row per widening, none lost")
}

func (ts *DeviceSecretProviderTestSuite) TestResolveDerivedConsent_ConcurrentExchangesRecordOnce() {
	// Eight identical exchanges at once through two provider instances, as two replicas would run them.
	ts.seedSuperConsent([]string{"name", "email"}, nil, sql.NullTime{})
	ps := []providers.DeviceSSOConsentProvider{
		ts.newProvider().(providers.DeviceSSOConsentProvider),
		ts.newProvider().(providers.DeviceSSOConsentProvider),
	}

	const n = 8
	var wg sync.WaitGroup
	allowed := make([]bool, n)
	codes := make([]string, n)
	for i := 0; i < n; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			res, svcErr := ps[i%2].ResolveDerivedConsent(context.Background(), ts.derivedRequest("openid", "profile"))
			if svcErr != nil {
				codes[i] = svcErr.Code
				return
			}
			allowed[i] = res.Allowed
		}(i)
	}
	wg.Wait()
	for i := 0; i < n; i++ {
		ts.Require().Empty(codes[i])
		ts.Require().True(allowed[i])
	}
	ts.Require().Len(ts.consent.history, 1, "concurrent identical exchanges through two provider instances record the derived consent once")
}
