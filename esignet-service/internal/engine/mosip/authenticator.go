/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"bytes"
	"context"
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
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/keystore"
	"github.com/mosip/esignet/internal/keymanager/signature"
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
	runtimeKeyClientID        = "initiator_query_client_id"

	// mosipRequestSignAlgorithm is the JWS algorithm the "signature" header
	// on outbound IDA requests is signed with — MOSIP IDA's partner request
	// signing contract expects RS256 specifically (RSA PKCS#1 v1.5), not the
	// PS256 that signature.AlgorithmForRefID would otherwise default an
	// RSA_2048 key to.
	mosipRequestSignAlgorithm = "RS256"

	// idaPartnerCertExpiryBuffer is how far ahead of its NotAfter a cached
	// IDA partner certificate is treated as expired, so a refetch has time
	// to land before IDA actually rejects encryption under the old cert.
	idaPartnerCertExpiryBuffer = 5 * time.Minute
)

type mosipAuthnProvider struct {
	appConfig *config.AppConfig
	client    *http.Client
	clientSvc *clientmgmt.Service
	svc       *keymanager.Service
	sigSvc    *signature.Service
	cfg       Config

	// certMu guards cachedCert, the last IDA partner certificate fetched by
	// fetchIDAPartnerCertificate. Cached until it nears its own NotAfter, so
	// Authenticate stops paying an HTTP round-trip per call.
	certMu     sync.RWMutex
	cachedCert *x509.Certificate
}

// NewMosipAuthnProvider creates a MOSIP providers.AuthnProviderManager with
// OTP send support. Outbound IDA requests are signed with the keymanager's
// OIDC_PARTNER / RSA_2048 component master key via svc/sigSvc (see
// getRequestSignature).
func NewMosipAuthnProvider(cfg *config.AppConfig, clientSvc *clientmgmt.Service, client *http.Client,
	svc *keymanager.Service, sigSvc *signature.Service) (shared.ConsolidatedAuthnProvider, error) {
	provider := &mosipAuthnProvider{
		appConfig: cfg,
		client:    client,
		clientSvc: clientSvc,
		svc:       svc,
		sigSvc:    sigSvc,
		cfg:       LoadConfig(),
	}
	return provider, nil
}

func (p *mosipAuthnProvider) Authenticate(ctx context.Context, identifiers, credentials map[string]interface{},
	metadata *providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {

	clientDtl, err := p.getApplicationAndClientID(ctx, metadata.RuntimeMetadata)
	if err != nil {
		return nil, shared.ClientNotFoundError
	}

	individualID, ok := identifiers["username"].(string)
	if !ok || individualID == "" {
		return nil, shared.InvalidIndividualIDError
	}

	transactionID, err := shared.GenerateTransactionID(metadata.RuntimeMetadata)
	if err != nil {
		return nil, shared.InvalidRequestError
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
		return nil, shared.InvalidRequestError
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
			applog.GetLogger().Error(ctx, "Biometric decode failed", applog.Error(err))
			return nil, shared.InvalidRequestError
		}
		var biometrics []Biometric
		if err := json.Unmarshal(decodedBiometric, &biometrics); err != nil {
			applog.GetLogger().Error(ctx, "Biometric unmarshal failed", applog.Error(err))
			return nil, shared.InvalidRequestError
		}
		if len(biometrics) == 0 {
			return nil, shared.InvalidRequestError
		}
		authRequest.Biometrics = biometrics
		credentialSet = true
	}
	if !credentialSet {
		return nil, shared.InvalidRequestError
	}
	authRequestBytes, err := json.Marshal(authRequest)
	if err != nil {
		applog.GetLogger().Error(ctx, "Auth request marshal failed", applog.Error(err))
		return nil, shared.AuthenticationFailedError
	}

	requestHash, err := GenerateHashWithErr(authRequestBytes)
	if err != nil {
		applog.GetLogger().Error(ctx, "Auth request hash generation failed", applog.Error(err))
		return nil, shared.AuthenticationFailedError
	}
	hexEncodedRequestHash, err := EncodeBytesToHexUpper(requestHash)
	if err != nil {
		applog.GetLogger().Error(ctx, "Auth request hash encoding failed", applog.Error(err))
		return nil, shared.AuthenticationFailedError
	}

	symmetricKey, err := GenerateAESKey()
	if err != nil {
		applog.GetLogger().Error(ctx, "Auth request symmetric key generation failed", applog.Error(err))
		return nil, shared.AuthenticationFailedError
	}

	applog.GetLogger().Debug(ctx, "kyc-auth symmetric key generated successfully")

	encryptedRequest, err := SymmetricEncrypt(authRequestBytes, symmetricKey)
	if err != nil {
		applog.GetLogger().Error(ctx, "Auth request encryption failed", applog.Error(err))
		return nil, shared.AuthenticationFailedError
	}

	encryptedRequestHash, err := SymmetricEncrypt(hexEncodedRequestHash, symmetricKey)
	if err != nil {
		applog.GetLogger().Error(ctx, "Auth request hash encryption failed", applog.Error(err))
		return nil, shared.AuthenticationFailedError
	}
	generatedCert, err := p.fetchIDAPartnerCertificate(ctx)
	if err != nil {
		applog.GetLogger().Error(ctx, "IDA partner certificate fetch failed", applog.Error(err))
		return nil, shared.AuthenticationFailedError
	}

	psut, kycToken, err := p.performKycAuth(ctx, idaKycAuthRequest, generatedCert, encryptedRequest, encryptedRequestHash,
		symmetricKey, clientDtl.RpID, clientDtl.ClientID, claimsMetadataRequired)
	if err != nil {
		applog.GetLogger().Error(ctx, "KYC auth endpoint call failed", applog.Error(err))
		// The cached certificate may be stale (e.g. IDA rotated it
		// out-of-band); drop it so the next Authenticate call fetches a
		// fresh one instead of repeatedly failing against the same cert.
		p.invalidateCachedIDAPartnerCertificate(ctx)
		return nil, shared.AuthenticationFailedError
	}

	return &providers.AuthnResult{
		EntityReferenceToken: psut,
		AttributeToken:       strings.Join([]string{kycToken, individualID, transactionID}, "||"),
	}, nil
}

func (p *mosipAuthnProvider) GetEntityReference(_ context.Context, entityReferenceToken any) (*providers.EntityReference,
	*common.ServiceError) {
	psut, ok := entityReferenceToken.(string)
	if !ok || psut == "" {
		return nil, shared.AuthenticationFailedError
	}
	return &providers.EntityReference{EntityID: psut}, nil
}

func (p *mosipAuthnProvider) GetAttributes(ctx context.Context, attributeToken any, consentedAttributes *providers.RequestedAttributes,
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

	tokenParts := strings.Split(attributeToken.(string), "||") // Extract KYC token, username and transaction ID from token (format "kycToken||username||transactionID")
	if len(tokenParts) != 3 {
		return nil, shared.AuthenticationFailedError
	}
	kycToken, username, transactionID := tokenParts[0], tokenParts[1], tokenParts[2]

	// TODO add requested attributes to the request with value and values
	keys := make([]string, 0, len(consentedAttributes.Attributes))
	for k := range consentedAttributes.Attributes {
		keys = append(keys, k)
	}

	if len(keys) == 0 {
		keys = append(keys, "sub")
	}

	idaKycExchangeRequest := &IdaKycExchangeRequest{
		ID:              mosipKycExchangeRequestID,
		Version:         mosipRequestVersion,
		RequestTime:     GetUTCDateTime(),
		TransactionID:   transactionID,
		KycToken:        kycToken,
		ConsentObtained: keys,
		Locales:         shared.NormalizeClaimLocales(metadata.Locale),
		RespType:        "JWS",
		IndividualID:    username,
	}

	requestBytes, err := json.Marshal(idaKycExchangeRequest)
	if err != nil {
		return nil, shared.InvalidRequestError
	}

	requestSignature, err := p.getRequestSignature(ctx, requestBytes)
	if err != nil {
		return nil, shared.InvalidRequestError
	}
	attributesResponse, err := p.callKycExchangeEndpoint(ctx, requestBytes, requestSignature, clientDtl.RpID, clientDtl.ClientID)
	if err != nil {
		return nil, shared.AuthenticationFailedError
	}
	return attributesResponse, nil
}

func (p *mosipAuthnProvider) InitiateAuthentication(_ context.Context, _ string, _ any,
	_ *providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (p *mosipAuthnProvider) InitiateEnrollment(_ context.Context, _ string, _ any,
	_ *providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (p *mosipAuthnProvider) Enroll(_ context.Context, _, _ map[string]interface{},
	_ *providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {
	return nil, nil
}

func (p *mosipAuthnProvider) SendOTP(ctx context.Context, identifiers map[string]interface{},
	metadata *providers.AuthnMetadata) (*shared.SendOTPResult, *common.ServiceError) {

	clientDtl, err := p.getApplicationAndClientID(ctx, metadata.RuntimeMetadata)
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

	requestSignature, err := p.getRequestSignature(ctx, otpRequestBytes)
	if err != nil {
		return nil, shared.InvalidRequestError
	}
	sendOTPResult, err := p.callSendOtpEndpoint(ctx, otpRequestBytes, requestSignature, clientDtl.RpID, clientDtl.ClientID)
	if err != nil {
		return nil, shared.SendOTPFailedError
	}

	sendOTPResult.TransactionID = transactionID
	return sendOTPResult, nil
}

func (p *mosipAuthnProvider) getApplicationAndClientID(ctx context.Context, runtimeMetadata map[string][]string) (clientmgmt.ClientResponse, error) {
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

// fetchIDAPartnerCertificate returns the IDA partner encryption certificate,
// serving it from cache while it remains valid.
func (p *mosipAuthnProvider) fetchIDAPartnerCertificate(ctx context.Context) (*x509.Certificate, error) {
	p.certMu.RLock()
	cached := p.cachedCert
	p.certMu.RUnlock()
	if cached != nil && time.Now().Before(cached.NotAfter.Add(-idaPartnerCertExpiryBuffer)) {
		return cached, nil
	}

	cert, err := p.doFetchIDAPartnerCertificate(ctx)
	if err != nil {
		return nil, err
	}

	p.certMu.Lock()
	p.cachedCert = cert
	p.certMu.Unlock()

	return cert, nil
}

// invalidateCachedIDAPartnerCertificate drops the cached IDA partner
// certificate, forcing the next fetchIDAPartnerCertificate call to fetch a
// fresh one instead of reusing one IDA has just rejected.
func (p *mosipAuthnProvider) invalidateCachedIDAPartnerCertificate(ctx context.Context) {
	applog.GetLogger().Warn(ctx, "clearing cached IDA partner certificate after KYC auth rejection")
	p.certMu.Lock()
	p.cachedCert = nil
	p.certMu.Unlock()
}

// doFetchIDAPartnerCertificate performs the actual HTTP GET + PEM/X.509
// parse against p.cfg.IDAPartnerCertificateURL, unconditionally.
func (p *mosipAuthnProvider) doFetchIDAPartnerCertificate(ctx context.Context) (*x509.Certificate, error) {
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
		applog.GetLogger().Error(ctx, "failed to parse certificate: no valid PEM block found")
		return nil, fmt.Errorf("%w: no valid PEM block", ErrCertificateParsing)
	}

	// The block.Bytes is the DER-encoded certificate
	cert, err := keystore.ParseCertificateTolerant(block.Bytes)
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

// buildX5CHeader builds the base64url-encoded JWS protected header used by
// getRequestSignature — mosipRequestSignAlgorithm plus a single-entry x5c
// carrying cert, matching MOSIP IDA's expected "signature" header shape.
func buildX5CHeader(cert *x509.Certificate) (string, error) {
	header := map[string]interface{}{
		"alg": mosipRequestSignAlgorithm,
		"typ": "JWT",
		"x5c": []string{base64.StdEncoding.EncodeToString(cert.Raw)}, // standard base64, not url-safe
	}
	headerJSON, err := json.Marshal(header)
	if err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(headerJSON), nil
}

// errorCodes extracts just the ErrorCode field from a MOSIP IDA error list —
// a fixed enum value, safe to log — leaving ErrorMessage and any other
// echoed request context out of logs/error strings.
func errorCodes(errs []Error) []string {
	codes := make([]string, len(errs))
	for i, e := range errs {
		codes[i] = e.ErrorCode
	}
	return codes
}

func (p *mosipAuthnProvider) callSendOtpEndpoint(
	ctx context.Context,
	requestBody []byte, // already marshaled JSON of IdaKycAuthRequest
	signature string, // from helperService.getRequestSignature(requestBody)
	relyingPartyID string,
	clientID string,
) (*shared.SendOTPResult, error) {
	endpointURL, err := buildIDAEndpointURL(ctx, p.cfg.SendOTPBaseURL, relyingPartyID, clientID)
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

	// Check status. The response body is never logged or included in the
	// returned error here: MOSIP IDA error payloads for the OTP flow echo
	// request context (individualId, masked email/mobile) — personal
	// identifiers that must not reach application logs or error strings
	// that themselves might get logged upstream. Only the status code and
	// the IDA errorCode values (a fixed enum, not caller data) are
	// surfaced, best-effort — a non-2xx body isn't guaranteed to parse as
	// IdaSendOtpResponse at all.
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		var errWrapper IdaSendOtpResponse
		_ = json.Unmarshal(bodyBytes, &errWrapper)
		applog.GetLogger().Error(ctx, "unexpected send OTP status",
			applog.Int("status", resp.StatusCode),
			applog.Any("errorCodes", errorCodes(errWrapper.Errors)))
		return nil, fmt.Errorf("unexpected send OTP status: %d (codes: %v)", resp.StatusCode, errorCodes(errWrapper.Errors))
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

	applog.GetLogger().Error(ctx, "IDA OTP error response",
		applog.Any("errorCodes", errorCodes(wrapper.Errors)))

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

// performKycAuth encrypts the session key and computes the thumbprint under
// cert, finishes assembling idaKycAuthRequest, signs it, and sends it to the
// KYC auth endpoint. Split out from Authenticate so the cached-certificate
// rejection fallback can rebuild and resend the request under a freshly
// fetched certificate without duplicating the assembly steps.
func (p *mosipAuthnProvider) performKycAuth(ctx context.Context, idaKycAuthRequest *IdaKycAuthRequest, cert *x509.Certificate,
	encryptedRequest, encryptedRequestHash, symmetricKey []byte, rpID, clientID string, claimsMetadataRequired bool) (string, string, error) {
	encryptedSessionKey, err := AsymmetricEncrypt(cert.PublicKey.(*rsa.PublicKey), symmetricKey)
	if err != nil {
		return "", "", fmt.Errorf("session key encryption failed: %w", err)
	}
	certThumbprint, err := GetCertificateThumbprint(cert)
	if err != nil {
		return "", "", fmt.Errorf("certificate thumbprint generation failed: %w", err)
	}

	idaKycAuthRequest.RequestSessionKey = B64EncodeBytes(encryptedSessionKey)
	idaKycAuthRequest.Request = B64EncodeBytes(encryptedRequest)
	idaKycAuthRequest.RequestHMAC = B64EncodeBytes(encryptedRequestHash)
	idaKycAuthRequest.Thumbprint = B64EncodeBytes(certThumbprint)

	requestBytes, err := json.Marshal(idaKycAuthRequest)
	if err != nil {
		return "", "", fmt.Errorf("request marshal failed: %w", err)
	}

	requestSignature, err := p.getRequestSignature(ctx, requestBytes)
	if err != nil {
		return "", "", fmt.Errorf("request signature generation failed: %w", err)
	}

	return p.callKycAuthEndpoint(ctx, requestBytes, requestSignature, rpID, clientID, claimsMetadataRequired)
}

// PerformKycAuth sends the KYC auth request to IDA and processes the response
func (p *mosipAuthnProvider) callKycAuthEndpoint(
	ctx context.Context,
	requestBody []byte, // already marshaled JSON of IdaKycAuthRequest
	signature string, // from helperService.getRequestSignature(requestBody)
	relyingPartyID string,
	clientID string,
	_ bool,
) (string, string, error) {
	endpointURL, err := buildIDAEndpointURL(ctx, p.cfg.KYCAuthBaseURL, relyingPartyID, clientID)
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
		return "", "", fmt.Errorf("unexpected KYC auth status: %d - %s", resp.StatusCode, string(bodyBytes))
	}

	// Parse response
	var wrapper IdaResponseWrapper
	if err := json.Unmarshal(bodyBytes, &wrapper); err != nil {
		return "", "", fmt.Errorf("failed to parse IdaResponseWrapper: %w", err)
	}

	// Success path
	if wrapper.Response != nil && wrapper.Response.KycStatus && wrapper.Response.KycToken != "" {
		return wrapper.Response.AuthToken, wrapper.Response.KycToken, nil
	}

	// Error path
	if wrapper.Response == nil {
		return "", "", errors.New("response object is missing in wrapper")
	}

	applog.GetLogger().Error(ctx, "IDA KYC auth error response",
		applog.Bool("kycStatus", wrapper.Response.KycStatus),
		applog.Any("errors", wrapper.Errors))

	if len(wrapper.Errors) == 0 {
		return "", "", errors.New("no errors in response wrapper")
	}

	// Take first error (common pattern)
	firstErr := wrapper.Errors[0]
	return "", "", fmt.Errorf("%s: %s", firstErr.ErrorMessage, firstErr.ActionMessage)
}

// PerformKycExchange sends the KYC exchange request to IDA and processes the response
func (p *mosipAuthnProvider) callKycExchangeEndpoint(
	ctx context.Context,
	requestBody []byte,
	signature string,
	relyingPartyID string,
	clientID string,
) (*providers.AttributesResponse, error) {
	endpointURL, err := buildIDAEndpointURL(ctx, p.cfg.KYCExchangeBaseURL, relyingPartyID, clientID)
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

	// Success path, currently parses the payload and returns the claims
	// This should instead return signed JWT as is, but this can be done only when
	// thunderID SDK supports "JWT" key in the Attributes map.
	if wrapper.Response != nil && wrapper.Response.EncryptedKyc != "" {
		claims := jwt.MapClaims{}
		if _, _, err := jwt.NewParser().ParseUnverified(wrapper.Response.EncryptedKyc, claims); err != nil {
			return nil, fmt.Errorf("failed to parse KYC JWT payload: %w", err)
		}

		attributes := make(map[string]*providers.AttributeResponse, len(claims))
		for k, v := range claims {
			attributes[k] = &providers.AttributeResponse{Value: v}
		}

		return &providers.AttributesResponse{Attributes: attributes}, nil
	}

	// Error path
	if wrapper.Response == nil {
		return nil, errors.New("response object is missing in wrapper")
	}

	applog.GetLogger().Error(ctx, "IDA KYC exchange error response",
		applog.Any("response", wrapper.Response),
		applog.Any("errors", wrapper.Errors))

	if len(wrapper.Errors) == 0 {
		return nil, errors.New("no errors in response wrapper")
	}

	// Take first error (common pattern)
	firstErr := wrapper.Errors[0]
	return nil, fmt.Errorf("%s: %s", firstErr.ErrorMessage, firstErr.ActionMessage)
}

func buildIDAEndpointURL(ctx context.Context, baseURL, relyingPartyID, clientID string) (string, error) {
	u, err := url.Parse(baseURL)
	if err != nil {
		return "", err
	}
	u.Path = strings.TrimRight(u.Path, "/") + "/" + url.PathEscape(relyingPartyID) + "/" + url.PathEscape(clientID)

	applog.GetLogger().Debug(ctx, "buildIDAEndpointURL",
		applog.String("baseURL", baseURL),
		applog.String("relyingPartyID", relyingPartyID),
		applog.String("clientID", clientID),
		applog.String("resultURL", u.String()))

	return u.String(), nil
}

// getRequestSignature builds the detached-payload JWS ("header..signature")
// MOSIP IDA expects in the outbound "signature" header, signing requestBody
// with the keymanager's OIDC_PARTNER / RSA_2048 component master key.
func (p *mosipAuthnProvider) getRequestSignature(ctx context.Context, requestBody []byte) (string, error) {
	// The certificate embedded in the x5c header and the signature must come
	// from the same key resolution: resolving the certificate and signing
	// independently each perform their own lazy-rotation lookup, and a
	// rotation landing between the two calls would attach the old
	// certificate to a signature produced by the new key, which MOSIP IDA
	// rejects. ResolveSigner+SignWithKey resolve once and reuse the result.
	cert, signer, err := p.sigSvc.ResolveSigner(ctx, config.OIDCPartnerAppID, keymanager.RefIDRSA2048)
	if err != nil {
		return "", fmt.Errorf("failed to resolve %s signing certificate: %w", config.OIDCPartnerAppID, err)
	}

	headerB64, err := buildX5CHeader(cert)
	if err != nil {
		return "", fmt.Errorf("failed to build JWT header: %w", err)
	}

	payloadB64 := B64EncodeBytes(requestBody)
	signingInput := headerB64 + "." + payloadB64

	sigBytes, err := p.sigSvc.SignWithKey(signer, cert.PublicKey, mosipRequestSignAlgorithm, []byte(signingInput))
	if err != nil {
		return "", fmt.Errorf("failed to sign request: %w", err)
	}

	// Detached-payload JWS: header..signature (the payload is not embedded in the return value)
	return headerB64 + ".." + base64.RawURLEncoding.EncodeToString(sigBytes), nil
}
