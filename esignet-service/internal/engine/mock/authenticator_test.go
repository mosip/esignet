/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mock_test

import (
	"context"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/clientmgmt/db"
	"github.com/mosip/esignet/internal/engine/mock"
	"github.com/mosip/esignet/internal/engine/shared"
)

// --- test scaffolding ---------------------------------------------------------------------

type stubQuerier struct {
	db.Querier
	client db.ClientDetail
}

func (s *stubQuerier) GetClient(_ context.Context, id string) (db.ClientDetail, error) {
	if id != s.client.ID {
		return db.ClientDetail{}, sql.ErrNoRows
	}
	return s.client, nil
}

func newClientService(t *testing.T, clientID, rpID string) *clientmgmt.Service {
	t.Helper()
	return clientmgmt.NewServiceWithQuerier(&stubQuerier{
		client: db.ClientDetail{
			ID:           clientID,
			Name:         `{"@none":"Test App"}`,
			RpID:         rpID,
			LogoUri:      "https://example.com/logo.png",
			RedirectUris: `["https://example.com/callback"]`,
			Claims:       `["name","email"]`,
			AcrValues:    `["mosip:idp:acr:static-code"]`,
			PublicKey:    `{"kty":"RSA","n":"abc","e":"AQAB"}`,
			GrantTypes:   `["authorization_code"]`,
			AuthMethods:  `["private_key_jwt"]`,
			Status:       "ACTIVE",
			CrDtimes:     time.Now(),
		},
	}, nil, 0)
}

func newAuthnMetadata() *providers.AuthnMetadata {
	return &providers.AuthnMetadata{
		RuntimeMetadata: map[string]string{
			"current_client_id": "client-001",
			"ext_TransactionID": "1234567890",
		},
	}
}

func newGetAttributesMetadata() *providers.GetAttributesMetadata {
	return &providers.GetAttributesMetadata{
		RuntimeMetadata: map[string]string{
			"current_client_id": "client-001",
			"ext_TransactionID": "1234567890",
		},
	}
}

func setMockEnv(t *testing.T, server *httptest.Server) {
	t.Helper()
	t.Setenv("MOSIP_ESIGNET_MOCK_KYC_AUTH_URL", server.URL+"/kyc-auth")
	t.Setenv("MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_V3_URL", server.URL+"/kyc-exchange")
	t.Setenv("MOSIP_ESIGNET_MOCK_SEND_OTP_URL", server.URL+"/send-otp")
}

// unsignedJWS builds a "header.payload.signature" token carrying the given claims as
// its payload, with an arbitrary signature segment (decodeJWTUnsafe does not verify it).
func unsignedJWS(t *testing.T, claims map[string]any) string {
	t.Helper()
	payload, err := json.Marshal(claims)
	require.NoError(t, err)
	return "header." + base64.RawURLEncoding.EncodeToString(payload) + ".signature"
}

// --- AuthenticateUser ---------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestAuthenticateUser_OTP_Success() {
	t := ts.T()
	var gotBody map[string]any
	mux := http.NewServeMux()
	mux.HandleFunc("POST /kyc-auth/{rp}/{client}", func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "rp-001", r.PathValue("rp"))
		assert.Equal(t, "client-001", r.PathValue("client"))
		require.NoError(t, json.NewDecoder(r.Body).Decode(&gotBody))
		_ = json.NewEncoder(w).Encode(map[string]any{
			"response": map[string]any{
				"authStatus":               true,
				"kycToken":                 "kyc-token-xyz",
				"partnerSpecificUserToken": "psut-xyz",
			},
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()
	setMockEnv(t, server)

	provider, err := mock.NewMockAuthnProvider(nil, newClientService(t, "client-001", "rp-001"))
	require.NoError(t, err)

	var authUser providers.AuthUser
	identifiers := map[string]any{"username": "2760459465"}
	credentials := map[string]any{"otp": "111111"}

	resultUser, claims, svcErr := provider.AuthenticateUser(context.Background(), identifiers, credentials,
		nil, newAuthnMetadata(), authUser)

	require.Nil(t, svcErr)
	assert.Nil(t, claims)
	assert.Equal(t, "kyc-token-xyz||2760459465", resultUser.AttributeToken())
	assert.Equal(t, "psut-xyz", resultUser.EntityReferenceToken())
	assert.Equal(t, "111111", gotBody["otp"])
	assert.Equal(t, "2760459465", gotBody["individualId"])
}

func (ts *AuthenticatorTestSuite) TestAuthenticateUser_Password_Success() {
	t := ts.T()
	mux := http.NewServeMux()
	mux.HandleFunc("POST /kyc-auth/{rp}/{client}", func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		require.NoError(t, json.NewDecoder(r.Body).Decode(&body))
		assert.Equal(t, "s3cr3t", body["password"])
		_ = json.NewEncoder(w).Encode(map[string]any{
			"response": map[string]any{
				"authStatus":               true,
				"kycToken":                 "kyc-token-pwd",
				"partnerSpecificUserToken": "psut-pwd",
			},
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()
	setMockEnv(t, server)

	provider, err := mock.NewMockAuthnProvider(nil, newClientService(t, "client-001", "rp-001"))
	require.NoError(t, err)

	var authUser providers.AuthUser
	identifiers := map[string]any{"username": "2760459465"}
	credentials := map[string]any{"password": "s3cr3t"}

	resultUser, _, svcErr := provider.AuthenticateUser(context.Background(), identifiers, credentials,
		nil, newAuthnMetadata(), authUser)

	require.Nil(t, svcErr)
	assert.True(t, resultUser.IsAuthenticated())
}

func (ts *AuthenticatorTestSuite) TestAuthenticateUser_Pin_Success() {
	t := ts.T()
	mux := http.NewServeMux()
	mux.HandleFunc("POST /kyc-auth/{rp}/{client}", func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		require.NoError(t, json.NewDecoder(r.Body).Decode(&body))
		assert.Equal(t, "1234", body["pin"])
		_ = json.NewEncoder(w).Encode(map[string]any{
			"response": map[string]any{
				"authStatus":               true,
				"kycToken":                 "kyc-token-pin",
				"partnerSpecificUserToken": "psut-pin",
			},
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()
	setMockEnv(t, server)

	provider, err := mock.NewMockAuthnProvider(nil, newClientService(t, "client-001", "rp-001"))
	require.NoError(t, err)

	var authUser providers.AuthUser
	identifiers := map[string]any{"username": "2760459465", "pin": "1234"}

	resultUser, _, svcErr := provider.AuthenticateUser(context.Background(), identifiers, map[string]any{},
		nil, newAuthnMetadata(), authUser)

	require.Nil(t, svcErr)
	assert.True(t, resultUser.IsAuthenticated())
}

func (ts *AuthenticatorTestSuite) TestAuthenticateUser_KBI_Success() {
	t := ts.T()
	var gotKbi string
	mux := http.NewServeMux()
	mux.HandleFunc("POST /kyc-auth/{rp}/{client}", func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		require.NoError(t, json.NewDecoder(r.Body).Decode(&body))
		gotKbi, _ = body["kbi"].(string)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"response": map[string]any{
				"authStatus":               true,
				"kycToken":                 "kyc-token-kbi",
				"partnerSpecificUserToken": "psut-kbi",
			},
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()
	setMockEnv(t, server)

	provider, err := mock.NewMockAuthnProvider(nil, newClientService(t, "client-001", "rp-001"))
	require.NoError(t, err)

	var authUser providers.AuthUser
	identifiers := map[string]any{"username": "2760459465"}
	credentials := map[string]any{"fullname": "Jane Doe", "date_of_birth": "1990-01-01"}

	_, _, svcErr := provider.AuthenticateUser(context.Background(), identifiers, credentials,
		nil, newAuthnMetadata(), authUser)

	require.Nil(t, svcErr)
	decoded, err := base64.RawURLEncoding.DecodeString(gotKbi)
	require.NoError(t, err)
	var kbiFields map[string]string
	require.NoError(t, json.Unmarshal(decoded, &kbiFields))
	assert.Equal(t, "Jane Doe", kbiFields["fullname"])
	assert.Equal(t, "1990-01-01", kbiFields["date_of_birth"])
}

func (ts *AuthenticatorTestSuite) TestAuthenticateUser_NoChallenge_ReturnsInvalidRequest() {
	t := ts.T()
	server := httptest.NewServer(http.NotFoundHandler())
	defer server.Close()
	setMockEnv(t, server)

	provider, err := mock.NewMockAuthnProvider(nil, newClientService(t, "client-001", "rp-001"))
	require.NoError(t, err)

	var authUser providers.AuthUser
	identifiers := map[string]any{"username": "2760459465"}

	_, _, svcErr := provider.AuthenticateUser(context.Background(), identifiers, map[string]any{},
		nil, newAuthnMetadata(), authUser)

	require.NotNil(t, svcErr)
	assert.Equal(t, shared.InvalidRequestError.Code, svcErr.Code)
}

func (ts *AuthenticatorTestSuite) TestAuthenticateUser_ServiceRejects_ReturnsAuthenticationFailed() {
	t := ts.T()
	mux := http.NewServeMux()
	mux.HandleFunc("POST /kyc-auth/{rp}/{client}", func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{
			"response": map[string]any{"authStatus": false},
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()
	setMockEnv(t, server)

	provider, err := mock.NewMockAuthnProvider(nil, newClientService(t, "client-001", "rp-001"))
	require.NoError(t, err)

	var authUser providers.AuthUser
	identifiers := map[string]any{"username": "2760459465"}
	credentials := map[string]any{"otp": "000000"}

	_, _, svcErr := provider.AuthenticateUser(context.Background(), identifiers, credentials,
		nil, newAuthnMetadata(), authUser)

	require.NotNil(t, svcErr)
	assert.Equal(t, shared.AuthenticationFailedError.Code, svcErr.Code)
}

func (ts *AuthenticatorTestSuite) TestAuthenticateUser_UnknownClient_ReturnsClientNotFound() {
	t := ts.T()
	server := httptest.NewServer(http.NotFoundHandler())
	defer server.Close()
	setMockEnv(t, server)

	provider, err := mock.NewMockAuthnProvider(nil, newClientService(t, "client-001", "rp-001"))
	require.NoError(t, err)

	var authUser providers.AuthUser
	identifiers := map[string]any{"username": "2760459465"}
	credentials := map[string]any{"otp": "111111"}
	metadata := &providers.AuthnMetadata{RuntimeMetadata: map[string]string{"current_client_id": "unknown-client"}}

	_, _, svcErr := provider.AuthenticateUser(context.Background(), identifiers, credentials,
		nil, metadata, authUser)

	require.NotNil(t, svcErr)
	assert.Equal(t, shared.ClientNotFoundError.Code, svcErr.Code)
}

// --- GetUserAttributes ---------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestGetUserAttributes_Success() {
	t := ts.T()
	var gotBody map[string]any
	mux := http.NewServeMux()
	mux.HandleFunc("POST /kyc-exchange/{rp}/{client}", func(w http.ResponseWriter, r *http.Request) {
		require.NoError(t, json.NewDecoder(r.Body).Decode(&gotBody))
		kyc := unsignedJWS(t, map[string]any{"sub": "psut-xyz", "name": "Jane Doe"})
		_ = json.NewEncoder(w).Encode(map[string]any{
			"response": map[string]any{"kyc": kyc},
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()
	setMockEnv(t, server)

	provider, err := mock.NewMockAuthnProvider(nil, newClientService(t, "client-001", "rp-001"))
	require.NoError(t, err)

	var authUser providers.AuthUser
	authUser.SetAttributeToken("kyc-token-xyz||2760459465")

	_, attrs, svcErr := provider.GetUserAttributes(context.Background(), nil, newGetAttributesMetadata(), authUser)

	require.Nil(t, svcErr)
	require.NotNil(t, attrs)
	assert.Equal(t, "Jane Doe", attrs.Attributes["name"].Value)
	assert.Equal(t, "kyc-token-xyz", gotBody["kycToken"])
	assert.Equal(t, "2760459465", gotBody["individualId"])
}

func (ts *AuthenticatorTestSuite) TestGetUserAttributes_NoAttributeToken_ReturnsNil() {
	t := ts.T()
	provider, err := mock.NewMockAuthnProvider(nil, newClientService(t, "client-001", "rp-001"))
	require.NoError(t, err)

	var authUser providers.AuthUser
	_, attrs, svcErr := provider.GetUserAttributes(context.Background(), nil, newGetAttributesMetadata(), authUser)

	require.Nil(t, svcErr)
	assert.Nil(t, attrs)
}

// --- SendOTP ---------------------------------------------------------------------

func (ts *AuthenticatorTestSuite) TestSendOTP_Success() {
	t := ts.T()
	mux := http.NewServeMux()
	mux.HandleFunc("POST /send-otp/{rp}/{client}", func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		require.NoError(t, json.NewDecoder(r.Body).Decode(&body))
		assert.Equal(t, "2760459465", body["individualId"])
		_ = json.NewEncoder(w).Encode(map[string]any{
			"response": map[string]any{
				"maskedEmail":  "j***@x.io",
				"maskedMobile": "XXXXX9999",
			},
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()
	setMockEnv(t, server)

	provider, err := mock.NewMockAuthnProvider(nil, newClientService(t, "client-001", "rp-001"))
	require.NoError(t, err)

	identifiers := map[string]any{"username": "2760459465"}
	result, svcErr := provider.SendOTP(context.Background(), identifiers, newAuthnMetadata())

	require.Nil(t, svcErr)
	require.NotNil(t, result)
	assert.Equal(t, "j***@x.io", result.MaskedEmail)
	assert.Equal(t, "XXXXX9999", result.MaskedMobile)
}

func (ts *AuthenticatorTestSuite) TestSendOTP_ServiceError_ReturnsSendOTPFailed() {
	t := ts.T()
	mux := http.NewServeMux()
	mux.HandleFunc("POST /send-otp/{rp}/{client}", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte(`{"errors":[{"errorCode":"no_email_mobile_found","message":"no channel"}]}`))
	})
	server := httptest.NewServer(mux)
	defer server.Close()
	setMockEnv(t, server)

	provider, err := mock.NewMockAuthnProvider(nil, newClientService(t, "client-001", "rp-001"))
	require.NoError(t, err)

	identifiers := map[string]any{"username": "2760459465"}
	result, svcErr := provider.SendOTP(context.Background(), identifiers, newAuthnMetadata())

	require.NotNil(t, svcErr)
	assert.Nil(t, result)
	assert.Equal(t, shared.SendOTPFailedError.Code, svcErr.Code)
}

type AuthenticatorTestSuite struct {
	suite.Suite
}

func TestAuthenticatorTestSuite(t *testing.T) {
	suite.Run(t, new(AuthenticatorTestSuite))
}
