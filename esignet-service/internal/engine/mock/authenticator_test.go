/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mock

import (
	"context"
	"database/sql"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/clientmgmt/db"
	"github.com/mosip/esignet/internal/engine/shared"
)

// stubQuerier is a hand-written fake of db.Querier, reused across the tests in this file to
// avoid standing up a real database. It only implements GetClient since that is all the mock
// authn provider needs.
type stubQuerier struct {
	db.Querier
	client db.ClientDetail
	found  bool
}

func (s *stubQuerier) GetClient(_ context.Context, id string) (db.ClientDetail, error) {
	if !s.found || id != s.client.ID {
		return db.ClientDetail{}, sql.ErrNoRows
	}
	return s.client, nil
}

func testClientRow() db.ClientDetail {
	return db.ClientDetail{
		ID:           "client-1",
		Name:         `{"@none":"Test App"}`,
		RpID:         "rp-1",
		RedirectUris: `["https://example.com/callback"]`,
		Claims:       `["name","email"]`,
		AcrValues:    `["mosip:idp:acr:static-code"]`,
		PublicKey:    `{"kty":"RSA","n":"abc","e":"AQAB"}`,
		GrantTypes:   `["authorization_code"]`,
		AuthMethods:  `["private_key_jwt"]`,
		Status:       "ACTIVE",
		CrDtimes:     time.Now(),
	}
}

func newTestClientSvc() *clientmgmt.Service {
	return clientmgmt.NewServiceWithQuerier(&stubQuerier{client: testClientRow(), found: true}, nil, 0, nil)
}

func metadataWithClientID(clientID string) *providers.AuthnMetadata {
	return &providers.AuthnMetadata{RuntimeMetadata: map[string][]string{runtimeKeyClientID: {clientID}}}
}

func getAttributesMetadataWithClientID(clientID string) *providers.GetAttributesMetadata {
	return &providers.GetAttributesMetadata{RuntimeMetadata: map[string][]string{runtimeKeyClientID: {clientID}}}
}

func newTestProvider(t *testing.T, kycAuthURL, kycExchangeV3URL, sendOtpURL string) *mockAuthnProvider {
	t.Helper()
	return &mockAuthnProvider{
		client:    &http.Client{Timeout: 5 * time.Second},
		clientSvc: newTestClientSvc(),
		cfg: Config{
			KycAuthURL:       kycAuthURL,
			KycExchangeV3URL: kycExchangeV3URL,
			SendOtpURL:       sendOtpURL,
			OtpChannels:      []string{"email", "phone"},
		},
	}
}

func (ts *AuthenticatorTestSuite) TestAuthenticate() {
	t := ts.T()

	t.Run("missing runtime metadata", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		result, svcErr := p.Authenticate(context.Background(), map[string]interface{}{}, map[string]interface{}{}, &providers.AuthnMetadata{})
		require.Nil(t, result)
		require.Same(t, shared.ClientNotFoundError, svcErr)
	})

	t.Run("unknown client", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		result, svcErr := p.Authenticate(context.Background(), map[string]interface{}{}, map[string]interface{}{}, metadataWithClientID("no-such-client"))
		require.Nil(t, result)
		require.Same(t, shared.ClientNotFoundError, svcErr)
	})

	t.Run("missing individual id", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		result, svcErr := p.Authenticate(context.Background(), map[string]interface{}{}, map[string]interface{}{}, metadataWithClientID("client-1"))
		require.Nil(t, result)
		require.Same(t, shared.InvalidIndividualIDError, svcErr)
	})

	t.Run("empty individual id", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		identifiers := map[string]interface{}{"username": ""}
		result, svcErr := p.Authenticate(context.Background(), identifiers, map[string]interface{}{}, metadataWithClientID("client-1"))
		require.Nil(t, result)
		require.Same(t, shared.InvalidIndividualIDError, svcErr)
	})

	t.Run("no supported challenge", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		identifiers := map[string]interface{}{"username": "ind-1"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, map[string]interface{}{}, metadataWithClientID("client-1"))
		require.Nil(t, result)
		require.Same(t, shared.InvalidRequestError, svcErr)
	})

	t.Run("successful otp authentication", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			require.Equal(t, http.MethodPost, r.Method)
			require.Contains(t, r.URL.Path, "/rp-1/client-1")
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"response":{"authStatus":true,"kycToken":"kyc-token-1","partnerSpecificUserToken":"psut-1"}}`))
		}))
		defer server.Close()

		p := newTestProvider(t, server.URL, "http://unused", "http://unused")
		identifiers := map[string]interface{}{"username": "ind-1"}
		credentials := map[string]interface{}{credentialOtp: "111111"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, metadataWithClientID("client-1"))
		require.Nil(t, svcErr)
		require.NotNil(t, result)
		require.Equal(t, "psut-1", result.EntityReferenceToken)
		require.Contains(t, result.AttributeToken, "kyc-token-1||ind-1||")
	})

	t.Run("password challenge accepted", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"response":{"authStatus":true,"kycToken":"kyc-token-2","partnerSpecificUserToken":"psut-2"}}`))
		}))
		defer server.Close()

		p := newTestProvider(t, server.URL, "http://unused", "http://unused")
		identifiers := map[string]interface{}{"username": "ind-1"}
		credentials := map[string]interface{}{credentialPassword: "secret"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, metadataWithClientID("client-1"))
		require.Nil(t, svcErr)
		require.Equal(t, "psut-2", result.EntityReferenceToken)
	})

	t.Run("kyc-auth authStatus false returns authentication failed", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"response":{"authStatus":false},"errors":[{"errorCode":"IDA-001","message":"auth failed"}]}`))
		}))
		defer server.Close()

		p := newTestProvider(t, server.URL, "http://unused", "http://unused")
		identifiers := map[string]interface{}{"username": "ind-1"}
		credentials := map[string]interface{}{credentialOtp: "111111"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, metadataWithClientID("client-1"))
		require.Nil(t, result)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})

	t.Run("kyc-auth http error returns authentication failed", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer server.Close()

		p := newTestProvider(t, server.URL, "http://unused", "http://unused")
		identifiers := map[string]interface{}{"username": "ind-1"}
		credentials := map[string]interface{}{credentialOtp: "111111"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, metadataWithClientID("client-1"))
		require.Nil(t, result)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})

	t.Run("unreachable kyc-auth endpoint returns authentication failed", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(_ http.ResponseWriter, _ *http.Request) {}))
		server.Close()

		p := newTestProvider(t, server.URL, "http://unused", "http://unused")
		identifiers := map[string]interface{}{"username": "ind-1"}
		credentials := map[string]interface{}{credentialOtp: "111111"}
		result, svcErr := p.Authenticate(context.Background(), identifiers, credentials, metadataWithClientID("client-1"))
		require.Nil(t, result)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})
}

func (ts *AuthenticatorTestSuite) TestGetEntityReference() {
	t := ts.T()
	p := newTestProvider(t, "http://unused", "http://unused", "http://unused")

	t.Run("valid token", func(t *testing.T) {
		ref, svcErr := p.GetEntityReference(context.Background(), "psut-1")
		require.Nil(t, svcErr)
		require.Equal(t, "psut-1", ref.EntityID)
	})

	t.Run("empty token", func(t *testing.T) {
		ref, svcErr := p.GetEntityReference(context.Background(), "")
		require.Nil(t, ref)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})

	t.Run("non string token", func(t *testing.T) {
		ref, svcErr := p.GetEntityReference(context.Background(), 42)
		require.Nil(t, ref)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})
}

func (ts *AuthenticatorTestSuite) TestGetAttributes() {
	t := ts.T()

	t.Run("nil consented attributes", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		attrs, svcErr := p.GetAttributes(context.Background(), "any", nil, getAttributesMetadataWithClientID("client-1"))
		require.Nil(t, attrs)
		require.Same(t, shared.InvalidRequestError, svcErr)
	})

	t.Run("unknown client", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		attrs, svcErr := p.GetAttributes(context.Background(), "any", &providers.RequestedAttributes{}, getAttributesMetadataWithClientID("no-such-client"))
		require.Nil(t, attrs)
		require.Same(t, shared.ClientNotFoundError, svcErr)
	})

	t.Run("nil attribute token returns nil result", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		attrs, svcErr := p.GetAttributes(context.Background(), nil, &providers.RequestedAttributes{}, getAttributesMetadataWithClientID("client-1"))
		require.Nil(t, attrs)
		require.Nil(t, svcErr)
	})

	t.Run("malformed attribute token", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		attrs, svcErr := p.GetAttributes(context.Background(), "not-enough-parts", &providers.RequestedAttributes{}, getAttributesMetadataWithClientID("client-1"))
		require.Nil(t, attrs)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})

	t.Run("successful kyc exchange passes through raw jwt", func(t *testing.T) {
		claims := jwt.MapClaims{"sub": "ind-1", "name": "Jane Doe"}
		signed, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString([]byte("test-secret"))
		require.NoError(t, err)

		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			require.Equal(t, http.MethodPost, r.Method)
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"response":{"kyc":"` + signed + `"}}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", server.URL, "http://unused")
		attrs, svcErr := p.GetAttributes(context.Background(), "kyc-token||ind-1||txn-1",
			&providers.RequestedAttributes{}, getAttributesMetadataWithClientID("client-1"))
		require.Nil(t, svcErr)
		require.NotNil(t, attrs)
		require.Len(t, attrs.Attributes, 1)
		require.Equal(t, signed, attrs.Attributes[providers.RawJWTAttributeKey].Value)
	})

	t.Run("kyc exchange error", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", server.URL, "http://unused")
		attrs, svcErr := p.GetAttributes(context.Background(), "kyc-token||ind-1||txn-1",
			&providers.RequestedAttributes{}, getAttributesMetadataWithClientID("client-1"))
		require.Nil(t, attrs)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})

	t.Run("kyc response missing kyc field", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"response":{},"errors":[{"errorCode":"IDA-002","message":"kyc failed"}]}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", server.URL, "http://unused")
		attrs, svcErr := p.GetAttributes(context.Background(), "kyc-token||ind-1||txn-1",
			&providers.RequestedAttributes{}, getAttributesMetadataWithClientID("client-1"))
		require.Nil(t, attrs)
		require.Same(t, shared.AuthenticationFailedError, svcErr)
	})

	t.Run("uses requested claims_locales", func(t *testing.T) {
		claims := jwt.MapClaims{"sub": "ind-1"}
		signed, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString([]byte("test-secret"))
		require.NoError(t, err)

		var capturedBody []byte
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			capturedBody, _ = io.ReadAll(r.Body)
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"response":{"kyc":"` + signed + `"}}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", server.URL, "http://unused")
		metadata := getAttributesMetadataWithClientID("client-1")
		metadata.Locale = "en fr"
		_, svcErr := p.GetAttributes(context.Background(), "kyc-token||ind-1||txn-1",
			&providers.RequestedAttributes{}, metadata)
		require.Nil(t, svcErr)

		var req KycExchangeRequestDto
		require.NoError(t, json.Unmarshal(capturedBody, &req))
		require.Equal(t, []string{"eng", "fra"}, req.ClaimLocales)
	})

	t.Run("sends empty locales when claims_locales not requested", func(t *testing.T) {
		claims := jwt.MapClaims{"sub": "ind-1"}
		signed, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString([]byte("test-secret"))
		require.NoError(t, err)

		var capturedBody []byte
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			capturedBody, _ = io.ReadAll(r.Body)
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"response":{"kyc":"` + signed + `"}}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", server.URL, "http://unused")

		// getAttributesMetadataWithClientID leaves Locale unset, i.e. the RP did
		// not send claims_locales. eSignet must not inject an "eng" default here
		// — an empty locales list lets IDA choose.
		_, svcErr := p.GetAttributes(context.Background(), "kyc-token||ind-1||txn-1",
			&providers.RequestedAttributes{}, getAttributesMetadataWithClientID("client-1"))
		require.Nil(t, svcErr)

		var req KycExchangeRequestDto
		require.NoError(t, json.Unmarshal(capturedBody, &req))
		require.NotNil(t, req.ClaimLocales)
		require.Empty(t, req.ClaimLocales)
	})
}

func (ts *AuthenticatorTestSuite) TestSendOTP() {
	t := ts.T()

	t.Run("unknown client", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		result, svcErr := p.SendOTP(context.Background(), map[string]any{"username": "ind-1"}, metadataWithClientID("no-such-client"))
		require.Nil(t, result)
		require.Same(t, shared.ClientNotFoundError, svcErr)
	})

	t.Run("missing individual id", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		result, svcErr := p.SendOTP(context.Background(), map[string]any{}, metadataWithClientID("client-1"))
		require.Nil(t, result)
		require.Same(t, shared.InvalidIndividualIDError, svcErr)
	})

	t.Run("success", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			require.Equal(t, http.MethodPost, r.Method)
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"response":{"maskedEmail":"j***@example.com","maskedMobile":"9***789"}}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", server.URL)
		result, svcErr := p.SendOTP(context.Background(), map[string]any{"username": "ind-1"}, metadataWithClientID("client-1"))
		require.Nil(t, svcErr)
		require.NotNil(t, result)
		require.Equal(t, "j***@example.com", result.MaskedEmail)
		require.Equal(t, "9***789", result.MaskedMobile)
		require.NotEmpty(t, result.TransactionID)
	})

	t.Run("endpoint error returns send otp failed", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", server.URL)
		result, svcErr := p.SendOTP(context.Background(), map[string]any{"username": "ind-1"}, metadataWithClientID("client-1"))
		require.Nil(t, result)
		require.Same(t, shared.SendOTPFailedError, svcErr)
	})

	t.Run("endpoint errors array is forwarded", func(t *testing.T) {
		// The mock-identity-system returns invalid_individual_id for an unknown ID;
		// the code is forwarded to the client as ServiceError code + i18n key.
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"errors":[{"errorCode":"invalid_individual_id","message":"Invalid Individual ID"}]}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", server.URL)
		result, svcErr := p.SendOTP(context.Background(), map[string]any{"username": "ind-1"}, metadataWithClientID("client-1"))
		require.Nil(t, result)
		require.NotNil(t, svcErr)
		require.Equal(t, "invalid_individual_id", svcErr.Code)
		require.Equal(t, "invalid_individual_id", svcErr.Error.Key)
	})

	t.Run("endpoint errors array without code falls back", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"errors":[{"message":"boom"}]}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", server.URL)
		result, svcErr := p.SendOTP(context.Background(), map[string]any{"username": "ind-1"}, metadataWithClientID("client-1"))
		require.Nil(t, result)
		require.Same(t, shared.SendOTPFailedError, svcErr)
	})

	t.Run("blank leading error code does not mask a valid later one", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"errors":[{"errorCode":"","message":"blank"},{"errorCode":"invalid_individual_id","message":"Invalid Individual ID"}]}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", server.URL)
		result, svcErr := p.SendOTP(context.Background(), map[string]any{"username": "ind-1"}, metadataWithClientID("client-1"))
		require.Nil(t, result)
		require.NotNil(t, svcErr)
		require.Equal(t, "invalid_individual_id", svcErr.Code)
	})
}

func (ts *AuthenticatorTestSuite) TestGetSigningCertificates() {
	t := ts.T()

	t.Run("success", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			require.Equal(t, http.MethodGet, r.Method)
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"response":{"allCertificates":[
				{"keyId":"key-1","certificateData":"cert-1"},
				{"keyId":"key-2","certificateData":"cert-2"}
			]}}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		p.cfg.CertificateURL = server.URL
		certs, svcErr := p.GetSigningCertificates(context.Background())
		require.Nil(t, svcErr)
		require.Equal(t, []shared.CertificateData{
			{KeyID: "key-1", Certificate: "cert-1"},
			{KeyID: "key-2", Certificate: "cert-2"},
		}, certs)
	})

	t.Run("request creation error", func(t *testing.T) {
		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		p.cfg.CertificateURL = "://bad-url"
		certs, svcErr := p.GetSigningCertificates(context.Background())
		require.Nil(t, certs)
		require.Same(t, shared.CertificateFetchFailed, svcErr)
	})

	t.Run("connection error", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(_ http.ResponseWriter, _ *http.Request) {}))
		server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		p.cfg.CertificateURL = server.URL
		certs, svcErr := p.GetSigningCertificates(context.Background())
		require.Nil(t, certs)
		require.Same(t, shared.CertificateFetchFailed, svcErr)
	})

	t.Run("non-2xx status", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		p.cfg.CertificateURL = server.URL
		certs, svcErr := p.GetSigningCertificates(context.Background())
		require.Nil(t, certs)
		require.Same(t, shared.CertificateFetchFailed, svcErr)
	})

	t.Run("invalid json body", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			_, _ = w.Write([]byte("not json"))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		p.cfg.CertificateURL = server.URL
		certs, svcErr := p.GetSigningCertificates(context.Background())
		require.Nil(t, certs)
		require.Same(t, shared.CertificateFetchFailed, svcErr)
	})

	t.Run("error response with no certificates", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"errors":[{"errorCode":"IDA-003","message":"fetch failed"}]}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		p.cfg.CertificateURL = server.URL
		certs, svcErr := p.GetSigningCertificates(context.Background())
		require.Nil(t, certs)
		require.Same(t, shared.CertificateFetchFailed, svcErr)
	})

	t.Run("subsequent calls are served from cache", func(t *testing.T) {
		var requestCount int
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			requestCount++
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"response":{"allCertificates":[{"keyId":"key-1","certificateData":"cert-1"}]}}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		p.cfg.CertificateURL = server.URL

		first, svcErr := p.GetSigningCertificates(context.Background())
		require.Nil(t, svcErr)
		second, svcErr := p.GetSigningCertificates(context.Background())
		require.Nil(t, svcErr)

		require.Equal(t, 1, requestCount, "second call should be served from cache, not hit the server again")
		require.Equal(t, first, second)
	})

	t.Run("refetches once the cache expires", func(t *testing.T) {
		var requestCount int
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			requestCount++
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"response":{"allCertificates":[{"keyId":"key-1","certificateData":"cert-1"}]}}`))
		}))
		defer server.Close()

		p := newTestProvider(t, "http://unused", "http://unused", "http://unused")
		p.cfg.CertificateURL = server.URL

		_, svcErr := p.GetSigningCertificates(context.Background())
		require.Nil(t, svcErr)

		p.certsMu.Lock()
		p.certsExpiry = time.Now().Add(-time.Second)
		p.certsMu.Unlock()

		_, svcErr = p.GetSigningCertificates(context.Background())
		require.Nil(t, svcErr)

		require.Equal(t, 2, requestCount, "expired cache should trigger a fresh fetch")
	})
}

func (ts *AuthenticatorTestSuite) TestNoOpMethods() {
	t := ts.T()
	p := newTestProvider(t, "http://unused", "http://unused", "http://unused")

	result, svcErr := p.InitiateAuthentication(context.Background(), "otp", nil, nil)
	require.Nil(t, result)
	require.Nil(t, svcErr)

	result, svcErr = p.InitiateEnrollment(context.Background(), "otp", nil, nil)
	require.Nil(t, result)
	require.Nil(t, svcErr)

	authnResult, svcErr := p.Enroll(context.Background(), nil, nil, nil)
	require.Nil(t, authnResult)
	require.Nil(t, svcErr)
}

func (ts *AuthenticatorTestSuite) TestSetChallenge() {
	t := ts.T()

	t.Run("otp", func(t *testing.T) {
		req := &KycAuthRequestDto{}
		ok := setChallenge(req, nil, map[string]interface{}{credentialOtp: "111111"})
		require.True(t, ok)
		require.Equal(t, "111111", req.Otp)
	})

	t.Run("password", func(t *testing.T) {
		req := &KycAuthRequestDto{}
		ok := setChallenge(req, nil, map[string]interface{}{credentialPassword: "secret"})
		require.True(t, ok)
		require.Equal(t, "secret", req.Password)
	})

	t.Run("pin", func(t *testing.T) {
		req := &KycAuthRequestDto{}
		ok := setChallenge(req, map[string]interface{}{credentialPin: "1234"}, nil)
		require.True(t, ok)
		require.Equal(t, "1234", req.Pin)
	})

	t.Run("biometrics", func(t *testing.T) {
		req := &KycAuthRequestDto{}
		ok := setChallenge(req, map[string]interface{}{credentialBio: "bio-data"}, nil)
		require.True(t, ok)
		require.Equal(t, "bio-data", req.Biometrics)
	})

	t.Run("kbi fallback", func(t *testing.T) {
		req := &KycAuthRequestDto{}
		ok := setChallenge(req, nil, map[string]interface{}{"fullName": "Jane"})
		require.True(t, ok)
		require.NotEmpty(t, req.Kbi)
	})

	t.Run("no challenge found", func(t *testing.T) {
		req := &KycAuthRequestDto{}
		ok := setChallenge(req, map[string]interface{}{}, map[string]interface{}{})
		require.False(t, ok)
	})

	t.Run("empty otp value falls back to kbi since credentials map is non-empty", func(t *testing.T) {
		req := &KycAuthRequestDto{}
		ok := setChallenge(req, nil, map[string]interface{}{credentialOtp: ""})
		require.True(t, ok)
		require.Empty(t, req.Otp)
		require.NotEmpty(t, req.Kbi)
	})
}

func (ts *AuthenticatorTestSuite) TestKbiChallenge() {
	t := ts.T()

	t.Run("empty credentials", func(t *testing.T) {
		_, ok := kbiChallenge(map[string]interface{}{})
		require.False(t, ok)
	})

	t.Run("non empty credentials", func(t *testing.T) {
		encoded, ok := kbiChallenge(map[string]interface{}{"fullName": "Jane"})
		require.True(t, ok)
		require.NotEmpty(t, encoded)
	})
}

func (ts *AuthenticatorTestSuite) TestAcceptedClaimsFromRequest() {
	t := ts.T()

	t.Run("nil requested attributes defaults to sub", func(t *testing.T) {
		claims := acceptedClaimsFromRequest(nil)
		require.ElementsMatch(t, []string{"sub"}, claims)
	})

	t.Run("empty attributes defaults to sub", func(t *testing.T) {
		claims := acceptedClaimsFromRequest(&providers.RequestedAttributes{})
		require.ElementsMatch(t, []string{"sub"}, claims)
	})

	t.Run("explicit attributes are used", func(t *testing.T) {
		req := &providers.RequestedAttributes{
			Attributes: map[string]*providers.AttributeMetadataRequest{"email": {}, "phone_number": {}},
		}
		claims := acceptedClaimsFromRequest(req)
		require.ElementsMatch(t, []string{"email", "phone_number"}, claims)
	})
}

func (ts *AuthenticatorTestSuite) TestGetApplicationAndClientID() {
	t := ts.T()

	t.Run("nil runtime metadata", func(t *testing.T) {
		p := newTestProvider(t, "", "", "")
		_, err := p.getApplicationAndClientID(context.Background(), nil)
		require.Error(t, err)
	})

	t.Run("nil client service", func(t *testing.T) {
		p := newTestProvider(t, "", "", "")
		p.clientSvc = nil
		_, err := p.getApplicationAndClientID(context.Background(), map[string][]string{runtimeKeyClientID: {"client-1"}})
		require.Error(t, err)
	})

	t.Run("missing client id in metadata", func(t *testing.T) {
		p := newTestProvider(t, "", "", "")
		_, err := p.getApplicationAndClientID(context.Background(), map[string][]string{})
		require.Error(t, err)
	})

	t.Run("client not found", func(t *testing.T) {
		p := newTestProvider(t, "", "", "")
		_, err := p.getApplicationAndClientID(context.Background(), map[string][]string{runtimeKeyClientID: {"missing"}})
		require.Error(t, err)
	})

	t.Run("success", func(t *testing.T) {
		p := newTestProvider(t, "", "", "")
		clientDtl, err := p.getApplicationAndClientID(context.Background(), map[string][]string{runtimeKeyClientID: {"client-1"}})
		require.NoError(t, err)
		require.Equal(t, "client-1", clientDtl.ClientID)
		require.Equal(t, "rp-1", clientDtl.RpID)
	})
}

func (ts *AuthenticatorTestSuite) TestBuildEndpointURL() {
	t := ts.T()
	got := buildEndpointURL("http://example.com/v1/kyc-auth/", "rp 1", "client/1")
	require.Equal(t, "http://example.com/v1/kyc-auth/rp%201/client%2F1", got)
}

func (ts *AuthenticatorTestSuite) TestGetUTCDateTime() {
	t := ts.T()
	got := getUTCDateTime()
	_, err := time.Parse(utcDateTimeFormat, got)
	require.NoError(t, err)
}

func (ts *AuthenticatorTestSuite) TestNewMockAuthnProvider() {
	t := ts.T()
	provider, err := NewMockAuthnProvider(nil, newTestClientSvc(), &http.Client{Timeout: 30 * time.Second})
	require.NoError(t, err)
	require.NotNil(t, provider)
}

type AuthenticatorTestSuite struct {
	suite.Suite
}

func TestAuthenticatorTestSuite(t *testing.T) {
	suite.Run(t, new(AuthenticatorTestSuite))
}
