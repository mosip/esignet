package mock

import "encoding/json"

// KycAuthRequestDto is the mock-identity-system kyc-auth request payload.
type KycAuthRequestDto struct {
	TransactionID         string   `json:"transactionId,omitempty"`
	IndividualID          string   `json:"individualId,omitempty"`
	Password              string   `json:"password,omitempty"`
	Otp                   string   `json:"otp,omitempty"`
	Pin                   string   `json:"pin,omitempty"`
	Biometrics            string   `json:"biometrics,omitempty"`
	Kbi                   string   `json:"kbi,omitempty"`
	Tokens                []string `json:"tokens,omitempty"`
	ClaimMetadataRequired bool     `json:"claimMetadataRequired,omitempty"`
}

// KycAuthResponseDtoV2 is the mock-identity-system kyc-auth response payload.
type KycAuthResponseDtoV2 struct {
	AuthStatus               bool                         `json:"authStatus"`
	KycToken                 string                       `json:"kycToken,omitempty"`
	PartnerSpecificUserToken string                       `json:"partnerSpecificUserToken,omitempty"`
	ClaimMetadata            map[string][]json.RawMessage `json:"claimMetadata,omitempty"`
}

// KycExchangeRequestDto is the mock-identity-system kyc-exchange request payload.
type KycExchangeRequestDto struct {
	RequestDateTime string   `json:"requestDateTime,omitempty"`
	TransactionID   string   `json:"transactionId,omitempty"`
	KycToken        string   `json:"kycToken,omitempty"`
	IndividualID    string   `json:"individualId,omitempty"`
	AcceptedClaims  []string `json:"acceptedClaims,omitempty"`
	ClaimLocales    []string `json:"claimLocales,omitempty"`
	RespType        string   `json:"respType,omitempty"`
}

// KycExchangeResponseDto is the mock-identity-system kyc-exchange response payload.
type KycExchangeResponseDto struct {
	Kyc string `json:"kyc,omitempty"`
}

// SendOtpDto is the mock-identity-system send-otp request payload.
type SendOtpDto struct {
	TransactionID string   `json:"transactionId,omitempty"`
	IndividualID  string   `json:"individualId,omitempty"`
	OtpChannels   []string `json:"otpChannels,omitempty"`
}

// SendOtpResult is the mock-identity-system send-otp response payload.
type SendOtpResult struct {
	TransactionID string `json:"transactionId,omitempty"`
	MaskedEmail   string `json:"maskedEmail,omitempty"`
	MaskedMobile  string `json:"maskedMobile,omitempty"`
}

// ResponseWrapper is the common mock-identity-system response envelope.
type ResponseWrapper[T any] struct {
	ID            string  `json:"id,omitempty"`
	Version       string  `json:"version,omitempty"`
	TransactionID string  `json:"transactionID,omitempty"`
	ResponseTime  string  `json:"responseTime,omitempty"`
	Response      *T      `json:"response,omitempty"`
	Errors        []Error `json:"errors,omitempty"`
}

// Error is a mock-identity-system API error entry.
type Error struct {
	ErrorCode string `json:"errorCode,omitempty"`
	Message   string `json:"message,omitempty"`
}
