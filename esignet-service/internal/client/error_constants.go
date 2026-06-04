package client

// Wire error codes. Stable contract; do not rename.

const (
	errInvalidInput            = "invalid_input"
	errInvalidClientID         = "invalid_client_id"
	errInvalidClientName       = "invalid_client_name"
	errInvalidClientNameValue  = "invalid_client_name_value"
	errInvalidLanguageCode     = "invalid_language_code"
	errInvalidClaim            = "invalid_claim"
	errInvalidACR              = "invalid_acr"
	errInvalidURI              = "invalid_uri"
	errInvalidRedirectURI      = "invalid_redirect_uri"
	errUnsupportedGrantType    = "unsupported_grant_type"
	errInvalidClientAuth       = "invalid_client_auth"
	errInvalidPublicKey        = "invalid_public_key"
	errInvalidAdditionalConfig = "invalid_additional_config"
	errInvalidRPID             = "invalid_rp_id"
	errDuplicateClientID       = "duplicate_client_id"
	errDuplicatePublicKey      = "duplicate_public_key"
	errInvalidClientNameLength = "invalid_client_name_length"
	errUnknownError            = "unknown_error"
)

// errorMessages maps each wire error code to its human-readable message.
var errorMessages = map[string]string{
	errInvalidInput:            "Invalid request",
	errInvalidClientID:         "Invalid client id",
	errInvalidClientName:       "Invalid client name",
	errInvalidClientNameValue:  "Invalid client name value",
	errInvalidLanguageCode:     "Invalid language code",
	errInvalidClaim:            "Invalid claim",
	errInvalidACR:              "Invalid acr",
	errInvalidURI:              "Invalid uri",
	errInvalidRedirectURI:      "Invalid redirect uri",
	errUnsupportedGrantType:    "Unsupported grant type",
	errInvalidClientAuth:       "Invalid client auth method",
	errInvalidPublicKey:        "Invalid public key",
	errInvalidAdditionalConfig: "Invalid additional config",
	errInvalidRPID:             "Invalid relying party id",
	errDuplicateClientID:       "Duplicate client id",
	errDuplicatePublicKey:      "Duplicate public key",
	errInvalidClientNameLength: "Invalid client name length",
	errUnknownError:            "Unknown error",
}

// messageFor returns the message for a wire error code, falling back to
// the code itself when no message is registered.
func messageFor(code string) string {
	if m, ok := errorMessages[code]; ok {
		return m
	}
	return code
}
