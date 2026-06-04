package client

import (
	"encoding/json"
	"time"
)

// errorEntry is one wire-error in the response envelope's errors[] array.
type errorEntry struct {
	ErrorCode    string `json:"errorCode"`
	ErrorMessage string `json:"errorMessage,omitempty"`
}

// createResponse is the typed success payload for POST /v1/esignet/client-mgmt/client.
type createResponse struct {
	ClientID string `json:"clientId"`
	Status   string `json:"status"` // "ACTIVE"
}

// createResponseEnvelope is the wire shape for the create endpoint.
// Response is nil on error; Errors is always present (empty slice on success).
type createResponseEnvelope struct {
	ResponseTime string          `json:"responseTime"`
	Response     *createResponse `json:"response"`
	Errors       []errorEntry    `json:"errors"`
}

// createRequestEnvelope wraps the create request body.
type createRequestEnvelope struct {
	RequestTime time.Time     `json:"requestTime"`
	Request     createRequest `json:"request"`
}

// createRequest carries the OIDC client registration payload. Field-level
// rules live in schemas/create_request.schema.json and run at the HTTP
// boundary, before this struct is populated.
type createRequest struct {
	ClientID          string            `json:"clientId"`
	ClientName        string            `json:"clientName"`
	ClientNameLangMap map[string]string `json:"clientNameLangMap"`
	RelyingPartyID    string            `json:"relyingPartyId"`
	LogoURI           string            `json:"logoUri"`
	RedirectURIs      []string          `json:"redirectUris"`
	AuthContextRefs   []string          `json:"authContextRefs"`
	PublicKey         json.RawMessage   `json:"publicKey"`
	UserClaims        []string          `json:"userClaims"`
	GrantTypes        []string          `json:"grantTypes"`
	ClientAuthMethods []string          `json:"clientAuthMethods"`
	AdditionalConfig  json.RawMessage   `json:"additionalConfig,omitempty"`
}
