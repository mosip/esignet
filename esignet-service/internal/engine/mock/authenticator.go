/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mock

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
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
	defaultClaimLocale = "eng"
)

// identifierKeyIndividualID is the identifiers-map key that carries the individual ID.
const identifierKeyIndividualID = "username"

// Credential/identifier keys sent by the esignet flows (see data/flows/flow-esignet.yaml).
const (
	credentialOtp      = "otp"
	credentialPassword = "password"
	credentialPin      = "pin"
	credentialBio      = "biometrics"
)

type mockAuthnProvider struct {
	appConfig *config.AppConfig
	client    *http.Client
	clientSvc *clientmgmt.Service
	cfg       Config
}

// NewMockAuthnProvider creates a new mock authentication provider that talks to the
// MOSIP mock-identity-system over HTTP.
func NewMockAuthnProvider(cfg *config.AppConfig, clientSvc *clientmgmt.Service) (shared.ConsolidatedAuthnProvider, error) {
	return &mockAuthnProvider{
		appConfig: cfg,
		client:    newHTTPClient(),
		clientSvc: clientSvc,
		cfg:       LoadConfig(),
	}, nil
}

func (p *mockAuthnProvider) SendOTP(_ context.Context, identifiers map[string]any,
	metadata *providers.AuthnMetadata) (*shared.SendOTPResult, *common.ServiceError) {

	relyingPartyID, clientID, err := p.getApplicationAndClientID(metadata.RuntimeMetadata)
	if err != nil {
		return nil, shared.ClientNotFoundError
	}

	individualID, ok := identifiers[identifierKeyIndividualID].(string)
	if !ok || individualID == "" {
		return nil, shared.InvalidIndividualIDError
	}

	transactionID, err := generateTransactionID(metadata.RuntimeMetadata)
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

	result, err := p.callSendOtpEndpoint(requestBytes, relyingPartyID, clientID)
	if err != nil {
		return nil, shared.SendOTPFailedError
	}
	return result, nil
}

func (p *mockAuthnProvider) AuthenticateUser(_ context.Context, identifiers, credentials map[string]any,
	_ *providers.RequestedAttributes,
	metadata *providers.AuthnMetadata,
	authUser providers.AuthUser) (providers.AuthUser, providers.AuthenticatedClaims, *common.ServiceError) {

	relyingPartyID, clientID, err := p.getApplicationAndClientID(metadata.RuntimeMetadata)
	if err != nil {
		return authUser, nil, shared.ClientNotFoundError
	}

	individualID, ok := identifiers[identifierKeyIndividualID].(string)
	if !ok || individualID == "" {
		return authUser, nil, shared.InvalidIndividualIDError
	}

	transactionID, err := generateTransactionID(metadata.RuntimeMetadata)
	if err != nil {
		return authUser, nil, shared.InvalidRequestError
	}

	kycAuthRequest := &KycAuthRequestDto{
		TransactionID: transactionID,
		IndividualID:  individualID,
	}
	if !setChallenge(kycAuthRequest, identifiers, credentials) {
		return authUser, nil, shared.InvalidRequestError
	}

	requestBytes, err := json.Marshal(kycAuthRequest)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}

	kycToken, psut, err := p.callKycAuthEndpoint(requestBytes, relyingPartyID, clientID)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}

	authUser.SetAttributeToken(strings.Join([]string{kycToken, individualID}, "||"))
	authUser.SetEntityReferenceToken(psut)
	return authUser, nil, nil
}

func (p *mockAuthnProvider) GetEntityReference(_ context.Context, authUser providers.AuthUser) (
	providers.AuthUser, *providers.EntityReference, *common.ServiceError) {

	psut, ok := authUser.EntityReferenceToken().(string)
	if !ok || psut == "" {
		return authUser, nil, shared.AuthenticationFailedError
	}
	return authUser, &providers.EntityReference{EntityID: psut}, nil
}

func (p *mockAuthnProvider) GetUserAvailableAttributes(_ context.Context,
	_ providers.AuthUser) (*providers.AttributesResponse, *common.ServiceError) {
	return nil, nil
}

func (p *mockAuthnProvider) GetUserAttributes(_ context.Context,
	requestedAttributes *providers.RequestedAttributes,
	metadata *providers.GetAttributesMetadata,
	authUser providers.AuthUser) (providers.AuthUser, *providers.AttributesResponse, *common.ServiceError) {

	relyingPartyID, clientID, err := p.getApplicationAndClientID(metadata.RuntimeMetadata)
	if err != nil {
		return authUser, nil, shared.ClientNotFoundError
	}

	attributeToken := authUser.AttributeToken()
	if attributeToken == nil || attributeToken == "" {
		return authUser, nil, nil
	}

	tokenParts := strings.SplitN(attributeToken.(string), "||", 2)
	if len(tokenParts) != 2 {
		return authUser, nil, shared.AuthenticationFailedError
	}
	kycToken, individualID := tokenParts[0], tokenParts[1]

	transactionID, err := generateTransactionID(metadata.RuntimeMetadata)
	if err != nil {
		return authUser, nil, shared.InvalidRequestError
	}

	acceptedClaims := acceptedClaimsFromRequest(requestedAttributes)
	claimLocales := []string{defaultClaimLocale}
	if metadata.Locale != "" {
		claimLocales = []string{metadata.Locale}
	}

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
		return authUser, nil, shared.InvalidRequestError
	}

	attributesResponse, err := p.callKycExchangeEndpoint(requestBytes, relyingPartyID, clientID)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}
	return authUser, attributesResponse, nil
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

func (p *mockAuthnProvider) getApplicationAndClientID(runtimeMetadata map[string]string) (string, string, error) {
	if runtimeMetadata == nil {
		return "", "", errors.New("missing runtime metadata")
	}
	if p.clientSvc == nil {
		return "", "", errors.New("client service is not initialized")
	}

	clientID := runtimeMetadata["current_client_id"]
	if clientID == "" {
		return "", "", errors.New("missing current_client_id in runtime metadata")
	}
	client, err := p.clientSvc.GetClient(context.Background(), clientID)
	if err != nil {
		return "", "", fmt.Errorf("failed to resolve client %q: %w", clientID, err)
	}
	return client.RpID, clientID, nil
}

func (p *mockAuthnProvider) callKycAuthEndpoint(requestBody []byte, relyingPartyID, clientID string) (string, string, error) {
	endpointURL := buildEndpointURL(p.cfg.KycAuthURL, relyingPartyID, clientID)

	req, err := http.NewRequest(http.MethodPost, endpointURL, bytes.NewReader(requestBody))
	if err != nil {
		return "", "", err
	}
	req.Header.Set("Content-Type", "application/json; charset=utf-8")

	resp, err := p.client.Do(req)
	if err != nil {
		return "", "", err
	}
	defer func() { _ = resp.Body.Close() }()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", "", err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", "", fmt.Errorf("unexpected kyc-auth status: %d - %s", resp.StatusCode, string(bodyBytes))
	}

	var wrapper ResponseWrapper[KycAuthResponseDtoV2]
	if err := json.Unmarshal(bodyBytes, &wrapper); err != nil {
		return "", "", fmt.Errorf("failed to parse kyc-auth response: %w", err)
	}

	if wrapper.Response != nil && wrapper.Response.AuthStatus && wrapper.Response.KycToken != "" {
		return wrapper.Response.KycToken, wrapper.Response.PartnerSpecificUserToken, nil
	}

	applog.GetLogger().Error("mock-identity-system kyc-auth error response",
		applog.Any("response", wrapper.Response),
		applog.Any("errors", wrapper.Errors))

	if len(wrapper.Errors) == 0 {
		return "", "", errors.New("authentication failed")
	}
	firstErr := wrapper.Errors[0]
	return "", "", fmt.Errorf("%s: %s", firstErr.ErrorCode, firstErr.Message)
}

func (p *mockAuthnProvider) callKycExchangeEndpoint(requestBody []byte, relyingPartyID, clientID string) (*providers.AttributesResponse, error) {
	endpointURL := buildEndpointURL(p.cfg.KycExchangeV3URL, relyingPartyID, clientID)

	req, err := http.NewRequest(http.MethodPost, endpointURL, bytes.NewReader(requestBody))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json; charset=utf-8")

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("kyc-exchange request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read kyc-exchange response: %w", err)
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("unexpected kyc-exchange status: %d - %s", resp.StatusCode, string(bodyBytes))
	}

	var wrapper ResponseWrapper[KycExchangeResponseDto]
	if err := json.Unmarshal(bodyBytes, &wrapper); err != nil {
		return nil, fmt.Errorf("failed to parse kyc-exchange response: %w", err)
	}

	if wrapper.Response != nil && wrapper.Response.Kyc != "" {
		claims, err := decodeJWTUnsafe(wrapper.Response.Kyc)
		if err != nil {
			return nil, fmt.Errorf("failed to decode kyc JWT: %w", err)
		}
		attributes := make(map[string]*providers.AttributeResponse, len(claims))
		for claim, value := range claims {
			attributes[claim] = &providers.AttributeResponse{Value: value}
		}
		return &providers.AttributesResponse{Attributes: attributes}, nil
	}

	applog.GetLogger().Error("mock-identity-system kyc-exchange error response",
		applog.Any("response", wrapper.Response),
		applog.Any("errors", wrapper.Errors))

	if len(wrapper.Errors) == 0 {
		return nil, errors.New("kyc exchange failed")
	}
	firstErr := wrapper.Errors[0]
	return nil, fmt.Errorf("%s: %s", firstErr.ErrorCode, firstErr.Message)
}

func (p *mockAuthnProvider) callSendOtpEndpoint(requestBody []byte, relyingPartyID, clientID string) (*shared.SendOTPResult, error) {
	endpointURL := buildEndpointURL(p.cfg.SendOtpURL, relyingPartyID, clientID)

	req, err := http.NewRequest(http.MethodPost, endpointURL, bytes.NewReader(requestBody))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json; charset=utf-8")

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("send-otp request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read send-otp response: %w", err)
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("unexpected send-otp status: %d - %s", resp.StatusCode, string(bodyBytes))
	}

	var wrapper ResponseWrapper[SendOtpResult]
	if err := json.Unmarshal(bodyBytes, &wrapper); err != nil {
		return nil, fmt.Errorf("failed to parse send-otp response: %w", err)
	}

	if wrapper.Response != nil {
		return &shared.SendOTPResult{
			MaskedEmail:  wrapper.Response.MaskedEmail,
			MaskedMobile: wrapper.Response.MaskedMobile,
		}, nil
	}

	applog.GetLogger().Error("mock-identity-system send-otp error response",
		applog.Any("errors", wrapper.Errors))

	if len(wrapper.Errors) == 0 {
		return nil, errors.New("send otp failed")
	}
	firstErr := wrapper.Errors[0]
	return nil, fmt.Errorf("%s: %s", firstErr.ErrorCode, firstErr.Message)
}

// ---------------------------------------------------------------------------------------------------------

func newHTTPClient() *http.Client {
	return &http.Client{
		Timeout: 30 * time.Second,
		Transport: &http.Transport{
			DialContext: (&net.Dialer{
				Timeout:   5 * time.Second,
				KeepAlive: 30 * time.Second,
			}).DialContext,
			TLSHandshakeTimeout:   10 * time.Second,
			ResponseHeaderTimeout: 10 * time.Second,
			IdleConnTimeout:       90 * time.Second,
		},
	}
}

func buildEndpointURL(baseURL, relyingPartyID, clientID string) string {
	return strings.TrimRight(baseURL, "/") + "/" +
		url.PathEscape(relyingPartyID) + "/" + url.PathEscape(clientID)
}

// getUTCDateTime returns current time in UTC as string in ISO 8601 format.
func getUTCDateTime() string {
	return time.Now().UTC().Format(utcDateTimeFormat)
}

// generateTransactionID generates a cryptographically random 10-digit numeric string,
// reusing any transaction id already established for this runtime context (so
// SendOTP/AuthenticateUser/GetUserAttributes calls for the same flow execution share
// one transaction id, as mock-identity-system requires).
func generateTransactionID(runtimeMetadata map[string]string) (string, error) {
	if runtimeMetadata != nil && runtimeMetadata["ext_TransactionID"] != "" {
		return runtimeMetadata["ext_TransactionID"], nil
	}

	const digitCount = 10
	b := make([]byte, digitCount)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	for i := range b {
		b[i] = '0' + b[i]%10
	}
	return string(b), nil
}

// decodeJWTUnsafe decodes a JWT's payload without verifying its signature. The mock
// provider trusts mock-identity-system's response as-is, matching the trust boundary
// already used by the mosip provider for the same purpose.
func decodeJWTUnsafe(token string) (map[string]any, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return nil, fmt.Errorf("invalid JWT format")
	}

	payload := parts[1]
	payload += strings.Repeat("=", (4-len(payload)%4)%4)

	decoded, err := base64.URLEncoding.DecodeString(payload)
	if err != nil {
		return nil, err
	}

	var claims map[string]any
	if err := json.Unmarshal(decoded, &claims); err != nil {
		return nil, err
	}
	return claims, nil
}
