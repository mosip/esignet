/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"bytes"
	"context"
	"crypto"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"software.sslmate.com/src/go-pkcs12"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

const (
	signatureHeaderName       = "signature"
	authorizationHeader       = "Authorization"
	mosipKycAuthRequestID     = "mosip.identity.kycauth"
	mosipSendOtpRequestID     = "mosip.identity.otp"
	mosipKycExchangeRequestID = "mosip.identity.kycexchange"
	mosipRequestVersion       = "1.0"
	mosipEnvStaging           = "Staging" // default MOSIP_ENV; see config.LoadMosipAuthn
)

type mosipAuthnProvider struct {
	appConfig *config.AppConfig
	client    *http.Client
	clientSvc *clientmgmt.Service
	cfg       Config
}

// NewMosipAuthnProvider creates a MOSIP providers.AuthnProviderManager with OTP send support.
func NewMosipAuthnProvider(cfg *config.AppConfig, clientSvc *clientmgmt.Service, client *http.Client) (shared.ConsolidatedAuthnProvider, error) {
	provider := &mosipAuthnProvider{
		appConfig: cfg,
		client:    client,
		clientSvc: clientSvc,
		cfg:       LoadConfig(),
	}
	return provider, nil
}

func (p *mosipAuthnProvider) AuthenticateUser(_ context.Context, identifiers, credentials map[string]interface{},
	_ *providers.RequestedAttributes,
	metadata *providers.AuthnMetadata,
	authUser providers.AuthUser) (providers.AuthUser, providers.AuthenticatedClaims, *common.ServiceError) {

	clientDtl, err := p.getApplicationAndClientID(metadata.RuntimeMetadata)
	if err != nil {
		return authUser, nil, shared.ClientNotFoundError
	}

	individualID, ok := identifiers["username"].(string)
	if !ok || individualID == "" {
		return authUser, nil, shared.InvalidIndividualIDError
	}

	transactionID, err := shared.GenerateTransactionID(metadata.RuntimeMetadata)
	if err != nil {
		return authUser, nil, shared.InvalidRequestError
	}

	claimsMetadataRequired := false
	requestTime := GetUTCDateTime()
	idaKycAuthRequest := &IdaKycAuthRequest{
		ID:                     mosipKycAuthRequestID,
		Version:                mosipRequestVersion,
		RequestTime:            requestTime,
		DomainURI:              p.cfg.DomainURI,
		Env:                    p.cfg.Env,
		ConsentObtained:        true,
		IndividualID:           individualID,
		TransactionID:          transactionID,
		ClaimsMetadataRequired: &claimsMetadataRequired,
	}

	if len(credentials) == 0 {
		return authUser, nil, shared.InvalidRequestError
	}
	authRequest := &AuthRequest{
		Timestamp: requestTime,
	}
	credentialSet := false

	if otp, ok := credentials["otp"].(string); ok && otp != "" {
		authRequest.OTP = otp
		credentialSet = true
	} else if password, ok := credentials["password"].(string); ok && password != "" {
		authRequest.Password = password
		credentialSet = true
	} else if encodedBiometric, ok := biometricCredential(credentials); ok {
		decodedBiometric, err := B64Decode(encodedBiometric)
		if err != nil {
			return authUser, nil, shared.InvalidRequestError
		}
		var biometrics []Biometric
		if err := json.Unmarshal(decodedBiometric, &biometrics); err != nil {
			return authUser, nil, shared.InvalidRequestError
		}
		if len(biometrics) == 0 {
			return authUser, nil, shared.InvalidRequestError
		}
		authRequest.Biometrics = biometrics
		credentialSet = true
	}
	if !credentialSet {
		return authUser, nil, shared.InvalidRequestError
	}
	authRequestBytes, err := json.Marshal(authRequest)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}

	requestHash, err := GenerateHashWithErr(authRequestBytes)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}
	hexEncodedRequestHash, err := EncodeBytesToHexUpper(requestHash)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}
	symmetricKey, err := GenerateAESKey()
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}
	encryptedRequest, err := SymmetricEncrypt(authRequestBytes, symmetricKey)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}
	encryptedRequestHash, err := SymmetricEncrypt(hexEncodedRequestHash, symmetricKey)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}
	generatedCert, err := p.fetchIDAPartnerCertificate()
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}
	encryptedSessionKey, err := AsymmetricEncrypt(generatedCert.PublicKey.(*rsa.PublicKey), symmetricKey)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}
	certThumbprint, err := GetCertificateThumbprint(generatedCert)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}

	idaKycAuthRequest.RequestSessionKey = B64EncodeBytes(encryptedSessionKey)
	idaKycAuthRequest.Request = B64EncodeBytes(encryptedRequest)
	idaKycAuthRequest.RequestHMAC = B64EncodeBytes(encryptedRequestHash)
	idaKycAuthRequest.Thumbprint = B64EncodeBytes(certThumbprint)

	requestBytes, err := json.Marshal(idaKycAuthRequest)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}

	requestSignature, err := p.getRequestSignature(requestBytes)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}

	psut, kycToken, err := p.callKycAuthEndpoint(requestBytes, requestSignature, clientDtl.RpID, clientDtl.ClientID, claimsMetadataRequired)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}

	authUser.SetAttributeToken(strings.Join([]string{kycToken, individualID, transactionID}, "||"))
	authUser.SetEntityReferenceToken(psut)
	return authUser, nil, nil
}

func (p *mosipAuthnProvider) GetEntityReference(_ context.Context, authUser providers.AuthUser) (
	providers.AuthUser, *providers.EntityReference, *common.ServiceError) {

	psut, ok := authUser.EntityReferenceToken().(string)
	if !ok || psut == "" {
		return authUser, nil, shared.AuthenticationFailedError
	}
	return authUser, &providers.EntityReference{EntityID: psut}, nil
}

func (p *mosipAuthnProvider) GetUserAvailableAttributes(_ context.Context,
	_ providers.AuthUser) (*providers.AttributesResponse, *common.ServiceError) {

	return nil, nil
}

func (p *mosipAuthnProvider) GetUserAttributes(_ context.Context,
	requestedAttributes *providers.RequestedAttributes,
	metadata *providers.GetAttributesMetadata,
	authUser providers.AuthUser) (providers.AuthUser, *providers.AttributesResponse, *common.ServiceError) {

	if requestedAttributes == nil {
		return authUser, nil, shared.InvalidRequestError
	}

	clientDtl, err := p.getApplicationAndClientID(metadata.RuntimeMetadata)
	if err != nil {
		return authUser, nil, shared.ClientNotFoundError
	}

	attributeToken := authUser.AttributeToken()
	if attributeToken == nil || attributeToken == "" {
		return authUser, nil, nil
	}

	tokenParts := strings.Split(attributeToken.(string), "||") // Extract KYC token, username and transaction ID from token (format "kycToken||username||transactionID")
	if len(tokenParts) != 3 {
		return authUser, nil, shared.AuthenticationFailedError
	}
	kycToken, username, transactionID := tokenParts[0], tokenParts[1], tokenParts[2]

	// TODO add requested attributes to the request with value and values
	keys := make([]string, 0, len(requestedAttributes.Attributes))
	for k := range requestedAttributes.Attributes {
		keys = append(keys, k)
	}

	if len(keys) == 0 {
		keys = append(keys, "sub")
	}

	responseType, ok := clientDtl.AdditionalConfig["userinfo_response_type"].(string)
	if !ok || responseType == "" {
		responseType = "JWS"
	}

	idaKycExchangeRequest := &IdaKycExchangeRequest{
		ID:              mosipKycExchangeRequestID,
		Version:         mosipRequestVersion,
		RequestTime:     GetUTCDateTime(),
		TransactionID:   transactionID,
		KycToken:        kycToken,
		ConsentObtained: keys,
		Locales:         []string{"eng"},
		RespType:        responseType,
		IndividualID:    username,
	}

	requestBytes, err := json.Marshal(idaKycExchangeRequest)
	if err != nil {
		return authUser, nil, shared.InvalidRequestError
	}

	requestSignature, err := p.getRequestSignature(requestBytes)
	if err != nil {
		return authUser, nil, shared.InvalidRequestError
	}
	attributesResponse, err := p.callKycExchangeEndpoint(requestBytes, requestSignature, clientDtl.RpID, clientDtl.ClientID)
	if err != nil {
		return authUser, nil, shared.AuthenticationFailedError
	}
	return authUser, attributesResponse, nil
}

func (p *mosipAuthnProvider) SendOTP(_ context.Context, identifiers map[string]interface{},
	metadata *providers.AuthnMetadata) (*shared.SendOTPResult, *common.ServiceError) {

	clientDtl, err := p.getApplicationAndClientID(metadata.RuntimeMetadata)
	if err != nil {
		return nil, shared.ClientNotFoundError
	}

	transactionID, err := shared.GenerateTransactionID(metadata.RuntimeMetadata)
	if err != nil {
		return nil, shared.InvalidRequestError
	}
	individualID, ok := identifiers["username"].(string)
	if !ok || individualID == "" {
		return nil, shared.InvalidRequestError
	}
	req := IdaSendOtpRequest{
		ID:            mosipSendOtpRequestID,
		Version:       mosipRequestVersion,
		IndividualID:  individualID,
		TransactionID: transactionID,
		RequestTime:   GetUTCDateTime(),
		OtpChannel:    []string{"phone", "email"},
	}

	otpRequestBytes, err := json.Marshal(req)
	if err != nil {
		return nil, shared.InvalidRequestError
	}

	requestSignature, err := p.getRequestSignature(otpRequestBytes)
	if err != nil {
		return nil, shared.InvalidRequestError
	}
	sendOTPResult, err := p.callSendOtpEndpoint(otpRequestBytes, requestSignature, clientDtl.RpID, clientDtl.ClientID)
	if err != nil {
		return nil, shared.SendOTPFailedError
	}
	return sendOTPResult, nil
}

func (p *mosipAuthnProvider) getApplicationAndClientID(runtimeMetadata map[string]string) (clientmgmt.ClientResponse, error) {
	if runtimeMetadata == nil {
		return clientmgmt.ClientResponse{}, errors.New("missing runtime metadata")
	}
	if p.clientSvc == nil {
		return clientmgmt.ClientResponse{}, errors.New("client service is not initialized")
	}

	clientID := runtimeMetadata["current_client_id"]
	if clientID == "" {
		return clientmgmt.ClientResponse{}, errors.New("missing current_client_id in runtime metadata")
	}
	client, err := p.clientSvc.GetClient(context.Background(), clientID)
	if err != nil {
		return clientmgmt.ClientResponse{}, fmt.Errorf("failed to resolve client %q: %w", clientID, err)
	}
	return client, nil
}

// ---------------------------------------------------------------------------------------------------------

var (
	// ErrInvalidCertificate is returned when a certificate is nil or empty.
	ErrInvalidCertificate = errors.New("invalid or nil certificate")
	// ErrCertificateParsing is returned when a certificate cannot be parsed.
	ErrCertificateParsing = errors.New("certificate parsing error")
)

// utcTimeFormat is the MOSIP ISO-8601 timestamp layout (millisecond
// precision), shared by the IDA authenticator and the audit-manager client.
const utcTimeFormat = "2006-01-02T15:04:05.000Z"

// GetUTCDateTime returns current time in UTC as string in ISO 8601 format
func GetUTCDateTime() string {
	// Go uses a reference time Mon Jan 2 15:04:05 MST 2006 to define the format pattern
	return time.Now().UTC().Format(utcTimeFormat)
}

// B64EncodeBytes returns base64url-encoded string (no padding)
func B64EncodeBytes(data []byte) string {
	return base64.RawURLEncoding.EncodeToString(data)
}

// B64EncodeString encodes UTF-8 string → base64url (no padding)
func B64EncodeString(s string) string {
	return base64.RawURLEncoding.EncodeToString([]byte(s))
}

// B64Decode decodes base64 in unpadded URL-safe, standard, or padded URL-safe form.
// Biometric payloads from the OIDC UI use standard base64 (btoa); internal MOSIP
// payloads use base64url.
func B64Decode(s string) ([]byte, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, errors.New("empty base64 input")
	}

	var lastErr error
	for _, enc := range []*base64.Encoding{
		base64.RawURLEncoding,
		base64.StdEncoding,
		base64.URLEncoding,
	} {
		decoded, err := enc.DecodeString(s)
		if err == nil {
			return decoded, nil
		}
		lastErr = err
	}
	return nil, lastErr
}

func biometricCredential(credentials map[string]interface{}) (string, bool) {
	if val, ok := credentials["biometric"].(string); ok && val != "" {
		return val, true
	}
	return "", false
}

// GenerateHashWithErr returns the SHA-256 hash of data.
func GenerateHashWithErr(data []byte) ([]byte, error) {
	hash := sha256.Sum256(data)
	return hash[:], nil
}

// EncodeBytesToHexUpper returns an upper-case hex encoding of bytes.
func EncodeBytesToHexUpper(bytes []byte) ([]byte, error) {
	s := hex.EncodeToString(bytes)
	return []byte(strings.ToUpper(s)), nil
}

// GenerateAESKey returns a random 256-bit AES key.
func GenerateAESKey() ([]byte, error) {
	key := make([]byte, 32) // 256 bits
	if _, err := rand.Read(key); err != nil {
		return nil, fmt.Errorf("failed to generate random key: %w", err)
	}
	return key, nil
}

// SymmetricEncrypt encrypts data using a fresh random AES-256-GCM key.
// Returns: ciphertext || IV (IV is appended at the end, matching the Java behavior)
// Also returns the raw AES key bytes so the caller can encrypt it separately (e.g. with RSA)
func SymmetricEncrypt(plaintext []byte, key []byte) (encrypted []byte, err error) {
	if len(key) != 32 {
		return nil, errors.New("AES key must be 32 bytes (AES-256)")
	}
	if len(plaintext) == 0 {
		return nil, errors.New("plaintext cannot be empty")
	}

	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("failed to create AES cipher: %w", err)
	}

	nonceSize := 16                                          // 16 bytes nonce (IV) for GCM
	gcm, err := cipher.NewGCMWithNonceSize(block, nonceSize) // 16-byte nonce (IV)
	if err != nil {
		return nil, fmt.Errorf("failed to create GCM AEAD: %w", err)
	}

	// Generate 16-byte nonce (standard for GCM)
	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return nil, fmt.Errorf("failed to generate nonce: %w", err)
	}

	// Encrypt (no real AAD during encryption — we add dummy prefix later)
	ciphertext := gcm.Seal(nil, nonce, plaintext, nil) // nil = no AAD used in crypto operation

	// Final output: ciphertext+tag || nonce (IV) appended at the end (matches Java's behavior of appending IV)
	ciphertext = append(ciphertext, nonce...)

	return ciphertext, nil
}

func (p *mosipAuthnProvider) fetchIDAPartnerCertificate() (*x509.Certificate, error) {
	req, err := http.NewRequest(http.MethodGet, p.cfg.IDAPartnerCertificateURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/text")

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("got %d instead of 200 OK", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	// Clean up the input (remove extra whitespace/newlines if any)
	certData := strings.TrimSpace(string(body))

	// Decode PEM
	block, _ := pem.Decode([]byte(certData))
	if block == nil {
		applog.GetLogger().Error("failed to parse certificate: no valid PEM block found")
		return nil, fmt.Errorf("%w: no valid PEM block", ErrCertificateParsing)
	}

	// The block.Bytes is the DER-encoded certificate
	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		// Append original error message (similar to your Java concatenation)
		return nil, fmt.Errorf("%w: %w", ErrCertificateParsing, err)
	}

	return cert, nil
}

// GetCertificateThumbprint returns the SHA-256 thumbprint of a certificate.
func GetCertificateThumbprint(cert *x509.Certificate) ([]byte, error) {
	if cert == nil {
		return nil, ErrInvalidCertificate
	}

	if len(cert.Raw) == 0 {
		return nil, ErrInvalidCertificate
	}

	hash := sha256.Sum256(cert.Raw)
	return hash[:], nil
}

// AsymmetricEncrypt encrypts data using RSA-OAEP with SHA-256 + MGF1-SHA256
// (equivalent to Java's OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSpecified.DEFAULT))
//
// Parameters:
//   - pubKey: *rsa.PublicKey (parsed from X.509 certificate or JWK)
//   - data:   plaintext to encrypt (must be shorter than key size minus padding overhead)
//
// Returns encrypted ciphertext or error
func AsymmetricEncrypt(pubKey *rsa.PublicKey, data []byte) ([]byte, error) {
	if pubKey == nil {
		return nil, errors.New("invalid key: public key is nil")
	}

	if len(data) == 0 {
		return nil, errors.New("invalid data: empty input")
	}

	// MOSIP typically uses SHA-256 for OAEP (label digest) and MGF1
	hash := sha256.New()

	// EncryptOAEP uses the same hash for OAEP digest and MGF1 by default — matches most secure configs
	// (Java's explicit MGF1-SHA256 is equivalent here when hash=SHA-256)
	ciphertext, err := rsa.EncryptOAEP(
		hash,        // OAEP digest (SHA-256) + MGF1 digest (SHA-256)
		rand.Reader, // secure random source
		pubKey,
		data,
		nil, // label = nil (empty, matches PSpecified.DEFAULT)
	)
	if err != nil {
		return nil, fmt.Errorf("RSA-OAEP encryption failed: %w", err)
	}

	return ciphertext, nil
}

// LoadRSAPrivateKeyAndCertFromP12 loads an RSA private key and certificate from a PKCS#12 file.
func LoadRSAPrivateKeyAndCertFromP12(
	p12Path string,
	password string,
) (*rsa.PrivateKey, *x509.Certificate, error) {
	pfxData, err := os.ReadFile(p12Path)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to read .p12 file: %w", err)
	}

	// Decode → gets private key + the (single) certificate
	privateKeyAny, cert, err := pkcs12.Decode(pfxData, password)
	if err != nil {
		return nil, nil, fmt.Errorf("decode .p12 (check password and file format): %w", err)
	}

	rsaKey, ok := privateKeyAny.(*rsa.PrivateKey)
	if !ok {
		return nil, nil, errors.New("private key is not RSA")
	}

	if cert == nil {
		return nil, nil, errors.New("no certificate found in .p12")
	}

	return rsaKey, cert, nil
}

// CreateAndSignJWTWithX5C builds and signs JWT with x5c header
func CreateAndSignJWTWithX5C(
	base64Payload string, // pre-encoded base64url payload
	privateKey *rsa.PrivateKey,
	signedCertificate *x509.Certificate, // signed certificate (the leaf)
	kid string,
) (string, error) {
	// Prepare x5c values: base64(der) for each certificate
	x5c := make([]string, 1)
	der := signedCertificate.Raw
	x5c[0] = base64.StdEncoding.EncodeToString(der) // **standard** base64, **not** url-safe

	// Header with x5c
	header := map[string]interface{}{
		"alg": "RS256",
		"typ": "JWT",
		"x5c": x5c,
	}
	if kid != "" {
		header["kid"] = kid
	}

	headerJSON, err := json.Marshal(header)
	if err != nil {
		return "", err
	}
	headerB64 := base64.RawURLEncoding.EncodeToString(headerJSON)

	payloadB64 := base64Payload

	input := headerB64 + "." + payloadB64

	// RS256 signature
	hash := sha256.Sum256([]byte(input))
	signature, err := rsa.SignPKCS1v15(nil, privateKey, crypto.SHA256, hash[:])
	if err != nil {
		return "", fmt.Errorf("rsa sign failed: %w", err)
	}

	sigB64 := base64.RawURLEncoding.EncodeToString(signature)

	// Final JWT: header.payload.signature
	// Note: the payload is not add in the JWT on return
	return headerB64 + ".." + sigB64, nil
}

func (p *mosipAuthnProvider) callSendOtpEndpoint(
	requestBody []byte, // already marshaled JSON of IdaKycAuthRequest
	signature string, // from helperService.getRequestSignature(requestBody)
	relyingPartyID string,
	clientID string,
) (*shared.SendOTPResult, error) {
	endpointURL, err := buildIDAEndpointURL(p.cfg.SendOTPBaseURL, relyingPartyID, clientID)
	if err != nil {
		return nil, fmt.Errorf("invalid send OTP URL: %w", err)
	}

	req, err := http.NewRequest(http.MethodPost, endpointURL, bytes.NewReader(requestBody))
	if err != nil {
		return nil, fmt.Errorf("failed to create send OTP request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json; charset=utf-8")
	req.Header.Set(signatureHeaderName, signature)
	req.Header.Set(authorizationHeader, authorizationHeader)

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("send OTP request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	// Read body once
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read send OTP response: %w", err)
	}

	// Check status
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("unexpected send OTP status: %d - %s", resp.StatusCode, string(bodyBytes))
	}

	// Parse response
	var wrapper IdaSendOtpResponse
	if err := json.Unmarshal(bodyBytes, &wrapper); err != nil {
		return nil, fmt.Errorf("failed to parse IdaSendOtpResponse: %w", err)
	}

	// Success path
	if wrapper.Response != nil {
		return &shared.SendOTPResult{
			MaskedEmail:  wrapper.Response.MaskedEmail,
			MaskedMobile: wrapper.Response.MaskedMobile,
		}, nil
	}

	applog.GetLogger().Error("IDA OTP error response",
		applog.Any("response", wrapper.Response),
		applog.Any("errors", wrapper.Errors))

	// Error path
	if wrapper.Response == nil {
		return nil, errors.New("response object is missing in wrapper")
	}

	if len(wrapper.Errors) == 0 {
		return nil, errors.New("no errors in response wrapper")
	}

	// Take first error (common pattern)
	firstErr := wrapper.Errors[0]
	return nil, fmt.Errorf("%s: %s", firstErr.ErrorCode, firstErr.ErrorMessage)
}

// PerformKycAuth sends the KYC auth request to IDA and processes the response
func (p *mosipAuthnProvider) callKycAuthEndpoint(
	requestBody []byte, // already marshaled JSON of IdaKycAuthRequest
	signature string, // from helperService.getRequestSignature(requestBody)
	relyingPartyID string,
	clientID string,
	_ bool,
) (string, string, error) {
	endpointURL, err := buildIDAEndpointURL(p.cfg.KYCAuthBaseURL, relyingPartyID, clientID)
	if err != nil {
		return "", "", err
	}

	req, err := http.NewRequest(http.MethodPost, endpointURL, bytes.NewReader(requestBody))
	if err != nil {
		return "", "", err
	}

	req.Header.Set("Content-Type", "application/json; charset=utf-8")
	req.Header.Set(signatureHeaderName, signature)
	req.Header.Set(authorizationHeader, authorizationHeader) // e.g. "Bearer xxx" or whatever you set

	resp, err := p.client.Do(req)
	if err != nil {
		return "", "", err
	}
	defer func() { _ = resp.Body.Close() }()

	// Read body once
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", "", err
	}

	// Check status
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", "", err
	}

	// Parse response
	var wrapper IdaResponseWrapper
	if err := json.Unmarshal(bodyBytes, &wrapper); err != nil {
		return "", "", err
	}

	// Success path
	if wrapper.Response != nil && wrapper.Response.KycStatus && wrapper.Response.KycToken != "" {
		return wrapper.Response.AuthToken, wrapper.Response.KycToken, nil
	}

	// Error path
	if wrapper.Response == nil {
		return "", "", err
	}

	applog.GetLogger().Error("IDA KYC auth error response",
		applog.Bool("kycStatus", wrapper.Response.KycStatus),
		applog.Any("errors", wrapper.Errors))

	if len(wrapper.Errors) == 0 {
		return "", "", err
	}

	// Take first error (common pattern)
	firstErr := wrapper.Errors[0]
	return "", "", fmt.Errorf("%s: %s", firstErr.ErrorMessage, firstErr.ActionMessage)
}

// PerformKycExchange sends the KYC exchange request to IDA and processes the response
func (p *mosipAuthnProvider) callKycExchangeEndpoint(
	requestBody []byte,
	signature string,
	relyingPartyID string,
	clientID string,
) (*providers.AttributesResponse, error) {
	endpointURL, err := buildIDAEndpointURL(p.cfg.KYCExchangeBaseURL, relyingPartyID, clientID)
	if err != nil {
		return nil, fmt.Errorf("invalid KYC exchange URL: %w", err)
	}

	req, err := http.NewRequest(http.MethodPost, endpointURL, bytes.NewReader(requestBody))
	if err != nil {
		return nil, fmt.Errorf("failed to create KYC exchange request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json; charset=utf-8")
	req.Header.Set(signatureHeaderName, signature)
	req.Header.Set(authorizationHeader, authorizationHeader)

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("KYC exchange request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	// Read body once
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read KYC exchange response: %w", err)
	}

	// Check status
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("unexpected KYC exchange status: %d - %s", resp.StatusCode, string(bodyBytes))
	}

	// Parse response
	var wrapper IdaKycExchangeResponseWrapper
	if err := json.Unmarshal(bodyBytes, &wrapper); err != nil {
		return nil, fmt.Errorf("failed to parse IdaKycExchangeResponseWrapper: %w", err)
	}

	// Success path
	if wrapper.Response != nil && wrapper.Response.EncryptedKyc != "" {
		return &providers.AttributesResponse{
			Attributes: map[string]*providers.AttributeResponse{
				"jwt": {
					Value: wrapper.Response.EncryptedKyc,
				},
			},
		}, nil
	}

	// Error path
	if wrapper.Response == nil {
		return nil, errors.New("response object is missing in wrapper")
	}

	applog.GetLogger().Error("IDA KYC exchange error response",
		applog.Any("response", wrapper.Response),
		applog.Any("errors", wrapper.Errors))

	if len(wrapper.Errors) == 0 {
		return nil, errors.New("no errors in response wrapper")
	}

	// Take first error (common pattern)
	firstErr := wrapper.Errors[0]
	return nil, fmt.Errorf("%s: %s", firstErr.ErrorMessage, firstErr.ActionMessage)
}

func buildIDAEndpointURL(baseURL, relyingPartyID, clientID string) (string, error) {
	u, err := url.Parse(baseURL)
	if err != nil {
		return "", err
	}
	u.Path = strings.TrimRight(u.Path, "/") + "/" + url.PathEscape(relyingPartyID) + "/" + url.PathEscape(clientID)

	applog.GetLogger().Debug("buildIDAEndpointURL",
		applog.String("baseURL", baseURL),
		applog.String("relyingPartyID", relyingPartyID),
		applog.String("clientID", clientID),
		applog.String("resultURL", u.String()))

	return u.String(), nil
}

func (p *mosipAuthnProvider) getRequestSignature(requestBody []byte) (string, error) {
	if p.cfg.P12Path == "" {
		return "", errors.New("MOSIP_P12_PATH is not configured")
	}
	if p.cfg.P12Password == "" {
		return "", errors.New("MOSIP_P12_PASSWORD is not configured")
	}

	encodedRequestBody := B64EncodeBytes(requestBody)

	privateKey, signedCertificate, err := LoadRSAPrivateKeyAndCertFromP12(p.cfg.P12Path, p.cfg.P12Password)
	if err != nil {
		return "", fmt.Errorf("failed to load RSA private key and certificate from P12: %w", err)
	}

	jwtWithoutPayload, err := CreateAndSignJWTWithX5C(encodedRequestBody, privateKey, signedCertificate, "")
	if err != nil {
		return "", fmt.Errorf("failed to create and sign JWT: %w", err)
	}
	return jwtWithoutPayload, nil
}
