package mosip

// IdaKycAuthRequest represents the top-level KYC authentication request
type IdaKycAuthRequest struct {
	ID                     string                 `json:"id,omitempty"`
	Version                string                 `json:"version,omitempty"`
	IndividualID           string                 `json:"individualId,omitempty"`
	IndividualIDType       string                 `json:"individualIdType,omitempty"`
	TransactionID          string                 `json:"transactionID,omitempty"`
	RequestTime            string                 `json:"requestTime,omitempty"` // usually ISO8601
	SpecVersion            string                 `json:"specVersion,omitempty"`
	Thumbprint             string                 `json:"thumbprint,omitempty"`
	DomainURI              string                 `json:"domainUri,omitempty"`
	Env                    string                 `json:"env,omitempty"`
	ConsentObtained        bool                   `json:"consentObtained"`
	Request                string                 `json:"request,omitempty"` // usually base64 encoded encrypted payload
	RequestHMAC            string                 `json:"requestHMAC,omitempty"`
	RequestSessionKey      string                 `json:"requestSessionKey,omitempty"`
	Metadata               map[string]interface{} `json:"metadata,omitempty"`
	AllowedKycAttributes   []string               `json:"allowedKycAttributes,omitempty"`
	ClaimsMetadataRequired *bool                  `json:"claimsMetadataRequired,omitempty"` // nullable boolean
}

// AuthRequest is the decrypted / inner authentication payload
type AuthRequest struct {
	OTP             string           `json:"otp,omitempty"`
	StaticPin       string           `json:"staticPin,omitempty"`
	Timestamp       string           `json:"timestamp,omitempty"` // or time.Time if you prefer
	Biometrics      []Biometric      `json:"biometrics,omitempty"`
	KeyBindedTokens []KeyBindedToken `json:"keyBindedTokens,omitempty"`
	Password        string           `json:"password,omitempty"`
}

// Biometric represents one biometric record (usually encoded & encrypted)
type Biometric struct {
	Data        string `json:"data,omitempty"` // base64(FMR / Iris / Face ...)
	Hash        string `json:"hash,omitempty"`
	SessionKey  string `json:"sessionKey,omitempty"`
	SpecVersion string `json:"specVersion,omitempty"`
	Thumbprint  string `json:"thumbprint,omitempty"`
}

// KeyBindedToken represents a key-bound authentication token.
type KeyBindedToken struct {
	Type   string `json:"type,omitempty"`
	Token  string `json:"token,omitempty"`
	Format string `json:"format,omitempty"`
}

// IdaKycAuthResponse represents the core KYC authentication response data
type IdaKycAuthResponse struct {
	KycToken               string `json:"kycToken,omitempty"`
	AuthToken              string `json:"authToken,omitempty"`
	KycStatus              bool   `json:"kycStatus"`
	VerifiedClaimsMetadata string `json:"verifiedClaimsMetadata,omitempty"`
}

// IdaResponseWrapper is the top-level response structure
// (commonly used in MOSIP/IDA style APIs)
type IdaResponseWrapper struct {
	ID            string              `json:"id,omitempty"`
	Version       string              `json:"version,omitempty"`
	TransactionID string              `json:"transactionID,omitempty"`
	ResponseTime  string              `json:"responseTime,omitempty"` // usually ISO8601 / RFC3339
	Response      *IdaKycAuthResponse `json:"response,omitempty"`
	Errors        []IdaError          `json:"errors,omitempty"`
}

// IdaError represents a single error entry in the response
type IdaError struct {
	ActionMessage string `json:"actionMessage,omitempty"`
	ErrorCode     string `json:"errorCode,omitempty"`
	ErrorMessage  string `json:"errorMessage,omitempty"`
}

// SendOTPResult represents the result of an generate and notify OTP attempt.
type SendOTPResult struct {
	MaskedEmail  string `json:"maskedEmail,omitempty"`
	MaskedMobile string `json:"maskedMobile,omitempty"`
}

// IdaSendOtpRequest is the MOSIP IDA send-OTP request payload.
type IdaSendOtpRequest struct {
	ID               string   `json:"id,omitempty"`
	Version          string   `json:"version,omitempty"`
	IndividualID     string   `json:"individualId,omitempty"`
	IndividualIDType string   `json:"individualIdType,omitempty"`
	TransactionID    string   `json:"transactionID,omitempty"`
	RequestTime      string   `json:"requestTime,omitempty"`
	OtpChannel       []string `json:"otpChannel,omitempty"`
}

// IdaOtpResponse is the successful send-OTP response body.
type IdaOtpResponse struct {
	MaskedEmail  string `json:"maskedEmail,omitempty"`
	MaskedMobile string `json:"maskedMobile,omitempty"`
}

// IdaSendOtpResponse is the top-level MOSIP IDA send-OTP response wrapper.
type IdaSendOtpResponse struct {
	ID            string          `json:"id,omitempty"`
	Version       string          `json:"version,omitempty"`
	TransactionID string          `json:"transactionID,omitempty"`
	ResponseTime  string          `json:"responseTime,omitempty"`
	Errors        []Error         `json:"errors,omitempty"`
	Response      *IdaOtpResponse `json:"response,omitempty"`
}

// Error is a MOSIP IDA API error entry.
type Error struct {
	ErrorCode    string `json:"errorCode,omitempty"`
	ErrorMessage string `json:"errorMessage,omitempty"`
}

// IdaKycExchangeRequest is the MOSIP IDA KYC exchange request payload.
type IdaKycExchangeRequest struct {
	ID                        string                   `json:"id,omitempty"`
	Version                   string                   `json:"version,omitempty"`
	RequestTime               string                   `json:"requestTime,omitempty"`
	TransactionID             string                   `json:"transactionID,omitempty"`
	KycToken                  string                   `json:"kycToken,omitempty"`
	ConsentObtained           []string                 `json:"consentObtained,omitempty"`
	Locales                   []string                 `json:"locales,omitempty"`
	RespType                  string                   `json:"respType,omitempty"`
	IndividualID              string                   `json:"individualId,omitempty"`
	Metadata                  map[string]interface{}   `json:"metadata,omitempty"`
	VerifiedConsentedClaims   []map[string]interface{} `json:"verifiedConsentedClaims,omitempty"`
	UnVerifiedConsentedClaims map[string]interface{}   `json:"unVerifiedConsentedClaims,omitempty"`
}

// IdaKycExchangeResponse is the successful KYC exchange response body.
type IdaKycExchangeResponse struct {
	EncryptedKyc string `json:"encryptedKyc,omitempty"`
}

// IdaKycExchangeResponseWrapper is the top-level MOSIP IDA KYC exchange response wrapper.
type IdaKycExchangeResponseWrapper struct {
	ID            string                  `json:"id,omitempty"`
	Version       string                  `json:"version,omitempty"`
	TransactionID string                  `json:"transactionID,omitempty"`
	ResponseTime  string                  `json:"responseTime,omitempty"`
	Response      *IdaKycExchangeResponse `json:"response,omitempty"`
	Errors        []IdaError              `json:"errors,omitempty"`
}
