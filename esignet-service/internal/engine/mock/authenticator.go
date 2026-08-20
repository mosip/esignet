/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mock

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

const (
	utcDateTimeFormat  = "2006-01-02T15:04:05.000Z"
	runtimeKeyClientID = "initiator_query_client_id"

	// Credential/identifier keys sent by the esignet flows (see data/flows/flow-esignet.yaml).
	credentialOtp      = "otp"
	credentialPassword = "password"
	credentialPin      = "pin"
	credentialBio      = "biometrics"

	// identifierKeyIndividualID is the identifiers-map key that carries the individual ID.
	identifierKeyIndividualID = "username"

	// signingCertsCacheTTL is how long a fetched signing-certificate list is
	// served from cache before GetSigningCertificates fetches a fresh one.
	// Unlike the mosip provider, mock's certificate endpoint isn't gated by
	// an auth token with its own expiry, so a fixed TTL is used instead.
	signingCertsCacheTTL = 5 * time.Minute
)

type mockAuthnProvider struct {
	appConfig *config.AppConfig
	client    *http.Client
	clientSvc *clientmgmt.Service
	cfg       Config

	// certsMu guards cachedCerts/certsExpiry, the last signing-certificate
	// list fetched by GetSigningCertificates. Cached for signingCertsCacheTTL
	// so callers (e.g. JWKS/discovery) stop paying an HTTP round-trip on
	// every request.
	certsMu     sync.RWMutex
	cachedCerts []shared.CertificateData
	certsExpiry time.Time
}

// NewMockAuthnProvider creates a new mock authentication provider that talks to the
// MOSIP mock-identity-system over HTTP.
func NewMockAuthnProvider(cfg *config.AppConfig, clientSvc *clientmgmt.Service, httpClient *http.Client) (shared.ConsolidatedAuthnProvider, error) {
	return &mockAuthnProvider{
		appConfig: cfg,
		client:    httpClient,
		clientSvc: clientSvc,
		cfg:       LoadConfig(),
	}, nil
}

func (p *mockAuthnProvider) Authenticate(ctx context.Context, identifiers, credentials map[string]interface{},
	metadata *providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {

	clientDtl, err := p.getApplicationAndClientID(ctx, metadata.RuntimeMetadata)
	if err != nil {
		return nil, shared.ClientNotFoundError
	}

	individualID, ok := identifiers[identifierKeyIndividualID].(string)
	if !ok || individualID == "" {
		return nil, shared.InvalidIndividualIDError
	}

	transactionID, err := shared.GenerateTransactionID(metadata.RuntimeMetadata)
	if err != nil {
		return nil, shared.InvalidRequestError
	}

	kycAuthRequest := &KycAuthRequestDto{
		TransactionID: transactionID,
		IndividualID:  individualID,
	}
	if !setChallenge(kycAuthRequest, identifiers, credentials) {
		return nil, shared.InvalidRequestError
	}

	requestBytes, err := json.Marshal(kycAuthRequest)
	if err != nil {
		return nil, shared.AuthenticationFailedError
	}

	kycToken, psut, err := p.callKycAuthEndpoint(ctx, requestBytes, clientDtl.RpID, clientDtl.ClientID)
	if err != nil {
		return nil, shared.AuthenticationFailedError
	}

	return &providers.AuthnResult{
		EntityReferenceToken: psut,
		AttributeToken:       strings.Join([]string{kycToken, individualID, transactionID}, "||"),
	}, nil
}

func (p *mockAuthnProvider) GetEntityReference(_ context.Context, entityReferenceToken any) (*providers.EntityReference,
	*common.ServiceError) {
	psut, ok := entityReferenceToken.(string)
	if !ok || psut == "" {
		return nil, shared.AuthenticationFailedError
	}
	return &providers.EntityReference{EntityID: psut}, nil
}

func (p *mockAuthnProvider) GetAttributes(ctx context.Context, attributeToken any, consentedAttributes *providers.RequestedAttributes,
	metadata *providers.GetAttributesMetadata) (*providers.AttributesResponse, *common.ServiceError) {

	if consentedAttributes == nil {
		return nil, shared.InvalidRequestError
	}

	clientDtl, err := p.getApplicationAndClientID(ctx, metadata.RuntimeMetadata)
	if err != nil {
		return nil, shared.ClientNotFoundError
	}

	if attributeToken == nil || attributeToken == "" {
		return nil, nil
	}

	tokenParts := strings.SplitN(attributeToken.(string), "||", 3)
	if len(tokenParts) != 3 {
		return nil, shared.AuthenticationFailedError
	}
	kycToken, individualID, transactionID := tokenParts[0], tokenParts[1], tokenParts[2]

	acceptedClaims := acceptedClaimsFromRequest(consentedAttributes)
	claimLocales := shared.NormalizeClaimLocales(metadata.Locale)

	kycExchangeRequest := &KycExchangeRequestDto{
		RequestDateTime: getUTCDateTime(),
		TransactionID:   transactionID,
		KycToken:        kycToken,
		IndividualID:    individualID,
		AcceptedClaims:  acceptedClaims,
		ClaimLocales:    claimLocales,
		RespType:        "JWS",
	}

	requestBytes, err := json.Marshal(kycExchangeRequest)
	if err != nil {
		return nil, shared.InvalidRequestError
	}

	attributesResponse, err := p.callKycExchangeEndpoint(ctx, requestBytes, clientDtl.RpID, clientDtl.ClientID)
	if err != nil {
		return nil, shared.AuthenticationFailedError
	}
	return attributesResponse, nil
}

func (p *mockAuthnProvider) InitiateAuthentication(_ context.Context, _ string, _ any,
	_ *providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (p *mockAuthnProvider) InitiateEnrollment(_ context.Context, _ string, _ any,
	_ *providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (p *mockAuthnProvider) Enroll(_ context.Context, _, _ map[string]interface{},
	_ *providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {
	return nil, nil
}

func (p *mockAuthnProvider) SendOTP(ctx context.Context, identifiers map[string]any,
	metadata *providers.AuthnMetadata) (*shared.SendOTPResult, *common.ServiceError) {

	clientDtl, err := p.getApplicationAndClientID(ctx, metadata.RuntimeMetadata)
	if err != nil {
		return nil, shared.ClientNotFoundError
	}

	individualID, ok := identifiers[identifierKeyIndividualID].(string)
	if !ok || individualID == "" {
		return nil, shared.InvalidIndividualIDError
	}

	transactionID, err := shared.GenerateTransactionID(metadata.RuntimeMetadata)
	if err != nil {
		return nil, shared.InvalidRequestError
	}

	req := SendOtpDto{
		TransactionID: transactionID,
		IndividualID:  individualID,
		OtpChannels:   p.cfg.OtpChannels,
	}

	requestBytes, err := json.Marshal(req)
	if err != nil {
		return nil, shared.InvalidRequestError
	}

	result, err := p.callSendOtpEndpoint(ctx, requestBytes, clientDtl.RpID, clientDtl.ClientID)
	if err != nil {
		return nil, mapSendOTPError(err)
	}
	result.TransactionID = transactionID
	return result, nil
}

type mockOTPError struct{ code string }

func (e *mockOTPError) Error() string { return "mock send-otp error: " + e.code }

func mapSendOTPError(err error) *common.ServiceError {
	var otpErr *mockOTPError
	if !errors.As(err, &otpErr) || otpErr.code == "" {
		return shared.SendOTPFailedError
	}
	svcErr := *shared.SendOTPFailedError // re-key the base error to the mock code
	svcErr.Code = otpErr.code
	svcErr.Error.Key = otpErr.code
	svcErr.ErrorDescription.Key = otpErr.code + "_description"
	return &svcErr
}

// firstNonEmptyErrorCode returns the first non-empty ErrorCode in errs, or "" if
// there is none, so a blank leading code does not mask a valid later one.
func firstNonEmptyErrorCode(errs []Error) string {
	for _, e := range errs {
		if e.ErrorCode != "" {
			return e.ErrorCode
		}
	}
	return ""
}

func (p *mockAuthnProvider) GetSigningCertificates(ctx context.Context) ([]shared.CertificateData, *common.ServiceError) {
	p.certsMu.RLock()
	cached, expiry := p.cachedCerts, p.certsExpiry
	p.certsMu.RUnlock()
	if cached != nil && time.Now().Before(expiry) {
		return cached, nil
	}

	certs, svcErr := p.fetchSigningCertificates(ctx)
	if svcErr != nil {
		return nil, svcErr
	}

	p.certsMu.Lock()
	p.cachedCerts = certs
	p.certsExpiry = time.Now().Add(signingCertsCacheTTL)
	p.certsMu.Unlock()

	return certs, nil
}

// fetchSigningCertificates performs the actual HTTP GET + JSON parse against
// p.cfg.CertificateURL, unconditionally (no cache check).
func (p *mockAuthnProvider) fetchSigningCertificates(ctx context.Context) ([]shared.CertificateData, *common.ServiceError) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, p.cfg.CertificateURL, nil)
	if err != nil {
		applog.GetLogger().Error(ctx, "Failed to certificates create request", applog.Error(err))
		return nil, shared.CertificateFetchFailed
	}
	req.Header.Set("Content-Type", "application/json; charset=utf-8")

	resp, err := p.client.Do(req)
	if err != nil {
		applog.GetLogger().Error(ctx, "Failed to fetch certificates", applog.Error(err))
		return nil, shared.CertificateFetchFailed
	}
	defer func() { _ = resp.Body.Close() }()

	// check the response status code before parsing the body
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		applog.GetLogger().Error(ctx, "Failed to parse certificate response",
			applog.Any("statusCode", resp.StatusCode))
		return nil, shared.CertificateFetchFailed
	}

	// Parse response
	var wrapper CertificateResponseWrapper
	if err := json.NewDecoder(resp.Body).Decode(&wrapper); err != nil {
		applog.GetLogger().Error(ctx, "Failed to parse certificate response", applog.Error(err))
		return nil, shared.CertificateFetchFailed
	}

	// Success path
	if wrapper.Response != nil {
		certs := make([]shared.CertificateData, 0, len(wrapper.Response.AllCertificates))
		for _, certData := range wrapper.Response.AllCertificates {
			certs = append(certs, shared.CertificateData{
				KeyID:       certData.KeyID,
				Certificate: certData.CertificateData})
		}
		return certs, nil
	}

	return nil, shared.CertificateFetchFailed
}

// setChallenge inspects identifiers and credentials for a supported auth factor and
// populates the corresponding field on the kyc-auth request. Only otp/password (which
// arrive as sensitive PASSWORD_INPUT/OTP_INPUT flow inputs) reach the credentials map;
// pin and biometrics arrive via identifiers (see credentials_auth_executor.go). KBI has
// no fixed field set - if otp/password/pin/biometrics are absent, whatever remains in
// credentials is forwarded as the KBI challenge as-is.
// Returns false when no supported challenge was found.
func setChallenge(req *KycAuthRequestDto, identifiers, credentials map[string]any) bool {
	if otp, ok := credentials[credentialOtp].(string); ok && otp != "" {
		req.Otp = otp
		return true
	}
	if password, ok := credentials[credentialPassword].(string); ok && password != "" {
		req.Password = password
		return true
	}
	if pin, ok := identifiers[credentialPin].(string); ok && pin != "" {
		req.Pin = pin
		return true
	}
	if bio, ok := identifiers[credentialBio].(string); ok && bio != "" {
		req.Biometrics = bio
		return true
	}
	if kbi, ok := kbiChallenge(credentials); ok {
		req.Kbi = kbi
		return true
	}
	return false
}

// kbiChallenge base64url-encodes the entire credentials map as the KBI challenge
// payload, matching the format mock-identity-system decodes on the server side. KBI
// questions are flow-defined, so no particular fields are picked out here.
func kbiChallenge(credentials map[string]any) (string, bool) {
	if len(credentials) == 0 {
		return "", false
	}
	encoded, err := json.Marshal(credentials)
	if err != nil {
		return "", false
	}
	return base64.RawURLEncoding.EncodeToString(encoded), true
}

func acceptedClaimsFromRequest(requestedAttributes *providers.RequestedAttributes) []string {
	if requestedAttributes == nil || len(requestedAttributes.Attributes) == 0 {
		return []string{"sub", "name"}
	}
	claims := make([]string, 0, len(requestedAttributes.Attributes))
	for claim := range requestedAttributes.Attributes {
		claims = append(claims, claim)
	}
	return claims
}

func (p *mockAuthnProvider) getApplicationAndClientID(ctx context.Context, runtimeMetadata map[string][]string) (clientmgmt.ClientResponse, error) {
	if runtimeMetadata == nil {
		return clientmgmt.ClientResponse{}, errors.New("missing runtime metadata")
	}
	if p.clientSvc == nil {
		return clientmgmt.ClientResponse{}, errors.New("client service is not initialized")
	}

	values := runtimeMetadata[runtimeKeyClientID]
	if len(values) == 0 {
		return clientmgmt.ClientResponse{}, errors.New("missing client_id in runtime metadata")
	}
	client, err := p.clientSvc.GetClient(ctx, values[0])
	if err != nil {
		return clientmgmt.ClientResponse{}, fmt.Errorf("failed to resolve client %q: %w", values[0], err)
	}
	return client, nil
}

func (p *mockAuthnProvider) callKycAuthEndpoint(ctx context.Context, requestBody []byte, relyingPartyID, clientID string) (string, string, error) {
	endpointURL := buildEndpointURL(p.cfg.KycAuthURL, relyingPartyID, clientID)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpointURL, bytes.NewReader(requestBody))
	if err != nil {
		return "", "", err
	}
	req.Header.Set("Content-Type", "application/json; charset=utf-8")

	resp, err := p.client.Do(req)
	if err != nil {
		return "", "", err
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return "", "", fmt.Errorf("unexpected kyc-auth status: %d - %s", resp.StatusCode, string(bodyBytes))
	}

	var wrapper ResponseWrapper[KycAuthResponseDtoV2]
	if err := json.NewDecoder(resp.Body).Decode(&wrapper); err != nil {
		return "", "", fmt.Errorf("failed to parse kyc-auth response: %w", err)
	}

	if wrapper.Response != nil && wrapper.Response.AuthStatus && wrapper.Response.KycToken != "" {
		return wrapper.Response.KycToken, wrapper.Response.PartnerSpecificUserToken, nil
	}

	applog.GetLogger().Error(ctx, "mock-identity-system kyc-auth error response",
		applog.Any("response", wrapper.Response),
		applog.Any("errors", wrapper.Errors))

	if len(wrapper.Errors) == 0 {
		return "", "", errors.New("authentication failed")
	}
	firstErr := wrapper.Errors[0]
	return "", "", fmt.Errorf("%s: %s", firstErr.ErrorCode, firstErr.Message)
}

func (p *mockAuthnProvider) callKycExchangeEndpoint(ctx context.Context, requestBody []byte, relyingPartyID, clientID string) (*providers.AttributesResponse, error) {
	endpointURL := buildEndpointURL(p.cfg.KycExchangeV3URL, relyingPartyID, clientID)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpointURL, bytes.NewReader(requestBody))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json; charset=utf-8")

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("kyc-exchange request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("unexpected kyc-exchange status: %d - %s", resp.StatusCode, string(bodyBytes))
	}

	var wrapper ResponseWrapper[KycExchangeResponseDto]
	if err := json.NewDecoder(resp.Body).Decode(&wrapper); err != nil {
		return nil, fmt.Errorf("failed to parse kyc-exchange response: %w", err)
	}

	// Success path: the signed JWT is passed through as-is, undecoded, under
	// providers.RawJWTAttributeKey.
	if wrapper.Response != nil && wrapper.Response.Kyc != "" {
		attributes := map[string]*providers.AttributeResponse{
			providers.RawJWTAttributeKey: {Value: wrapper.Response.Kyc},
		}
		return &providers.AttributesResponse{Attributes: attributes}, nil
	}

	applog.GetLogger().Error(ctx, "mock-identity-system kyc-exchange error response",
		applog.Any("response", wrapper.Response),
		applog.Any("errors", wrapper.Errors))

	if len(wrapper.Errors) == 0 {
		return nil, errors.New("kyc exchange failed")
	}
	firstErr := wrapper.Errors[0]
	return nil, fmt.Errorf("%s: %s", firstErr.ErrorCode, firstErr.Message)
}

func (p *mockAuthnProvider) callSendOtpEndpoint(ctx context.Context, requestBody []byte, relyingPartyID, clientID string) (*shared.SendOTPResult, error) {
	endpointURL := buildEndpointURL(p.cfg.SendOtpURL, relyingPartyID, clientID)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpointURL, bytes.NewReader(requestBody))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json; charset=utf-8")

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("send-otp request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("unexpected send-otp status: %d - %s", resp.StatusCode, string(bodyBytes))
	}

	var wrapper ResponseWrapper[SendOtpResult]
	if err := json.NewDecoder(resp.Body).Decode(&wrapper); err != nil {
		return nil, fmt.Errorf("failed to parse send-otp response: %w", err)
	}

	if wrapper.Response != nil {
		return &shared.SendOTPResult{
			MaskedEmail:  wrapper.Response.MaskedEmail,
			MaskedMobile: wrapper.Response.MaskedMobile,
		}, nil
	}

	applog.GetLogger().Error(ctx, "mock-identity-system send-otp error response",
		applog.Any("errors", wrapper.Errors))

	if len(wrapper.Errors) == 0 {
		return nil, errors.New("send otp failed")
	}
	return nil, &mockOTPError{code: firstNonEmptyErrorCode(wrapper.Errors)}
}

// ---------------------------------------------------------------------------------------------------------

func buildEndpointURL(baseURL, relyingPartyID, clientID string) string {
	return strings.TrimRight(baseURL, "/") + "/" +
		url.PathEscape(relyingPartyID) + "/" + url.PathEscape(clientID)
}

// getUTCDateTime returns current time in UTC as string in ISO 8601 format.
func getUTCDateTime() string {
	return time.Now().UTC().Format(utcDateTimeFormat)
}
