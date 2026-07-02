package shared

// SendOTPResult represents the result of an generate and notify OTP attempt.
type SendOTPResult struct {
	MaskedEmail  string `json:"maskedEmail,omitempty"`
	MaskedMobile string `json:"maskedMobile,omitempty"`
}
