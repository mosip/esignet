package clientmgmt

import (
	"encoding/json"
	"fmt"

	"github.com/mosip/esignet/internal/common"
)

// Profile identifies which client-mgmt API variant is being invoked.
type Profile string

// Supported client-mgmt API profiles.
const (
	ProfileOIDC   Profile = "oidc-client"
	ProfileOAuth  Profile = "oauth-client"
	ProfileClient Profile = "client"
)

// ValidationError carries a documented eSignet client-mgmt error code.
type ValidationError struct {
	Code    string
	Message string
}

func (e *ValidationError) Error() string {
	if e.Message != "" {
		return e.Message
	}
	return e.Code
}

func validationErr(code string) error {
	return &ValidationError{Code: code}
}

// CreateRequestWrapper is the MOSIP envelope for client create requests.
type CreateRequestWrapper struct {
	common.RequestWrapper
	Request CreateClientRequest `json:"request"`
}

// UpdateRequestWrapper is the MOSIP envelope for client update requests.
type UpdateRequestWrapper struct {
	common.RequestWrapper
	Request UpdateClientRequest `json:"request"`
}

// PatchRequestWrapper is the MOSIP envelope for client patch requests.
type PatchRequestWrapper struct {
	common.RequestWrapper
	Request PatchClientRequest `json:"request"`
}

// ResponseWrapper is the MOSIP envelope for client-mgmt API responses.
type ResponseWrapper struct {
	common.ResponseWrapper
	Response *ClientResponse `json:"response"`
}

// CreateClientRequest is the payload for registering a new OIDC client.
type CreateClientRequest struct {
	ClientID          string            `json:"clientId"`
	ClientName        string            `json:"clientName"`
	ClientNameLangMap map[string]string `json:"clientNameLangMap,omitempty"`
	RpID              string            `json:"relyingPartyId"`
	LogoURI           string            `json:"logoUri"`
	RedirectURIs      []string          `json:"redirectUris"`
	Claims            []string          `json:"userClaims"`
	AcrValues         []string          `json:"authContextRefs"`
	PublicKey         map[string]string `json:"publicKey"`
	EncPublicKey      map[string]string `json:"encPublicKey,omitempty"`
	EncPublicKeyCert  string            `json:"encPublicKeyCert,omitempty"`
	GrantTypes        []string          `json:"grantTypes"`
	AuthMethods       []string          `json:"clientAuthMethods"`
	AdditionalConfig  json.RawMessage   `json:"additionalConfig,omitempty"`
}

// UpdateClientRequest is the payload for updating an existing OIDC client.
type UpdateClientRequest struct {
	ClientName        string            `json:"clientName"`
	ClientNameLangMap map[string]string `json:"clientNameLangMap,omitempty"`
	Status            string            `json:"status"`
	LogoURI           string            `json:"logoUri"`
	RedirectURIs      []string          `json:"redirectUris"`
	Claims            []string          `json:"userClaims"`
	AcrValues         []string          `json:"authContextRefs"`
	GrantTypes        []string          `json:"grantTypes"`
	AuthMethods       []string          `json:"clientAuthMethods"`
	AdditionalConfig  json.RawMessage   `json:"additionalConfig,omitempty"`
}

// PatchClientRequest is the payload for patching an existing OIDC client.
type PatchClientRequest struct {
	ClientName        string            `json:"clientName,omitempty"`
	ClientNameLangMap map[string]string `json:"clientNameLangMap,omitempty"`
	Status            string            `json:"status,omitempty"`
	LogoURI           string            `json:"logoUri,omitempty"`
	RedirectURIs      []string          `json:"redirectUris,omitempty"`
	Claims            []string          `json:"userClaims,omitempty"`
	AcrValues         []string          `json:"authContextRefs,omitempty"`
	GrantTypes        []string          `json:"grantTypes,omitempty"`
	AuthMethods       []string          `json:"clientAuthMethods,omitempty"`
	AdditionalConfig  json.RawMessage   `json:"additionalConfig,omitempty"`
	EncPublicKey      NullableJWK       `json:"encPublicKey,omitempty"`
}

// NullableJWK supports omitted, null, or JWK object for PATCH encPublicKey.
type NullableJWK struct {
	Defined bool
	IsNull  bool
	Value   map[string]string
}

// UnmarshalJSON decodes omitted, null, or JWK object encPublicKey values.
func (n *NullableJWK) UnmarshalJSON(data []byte) error {
	n.Defined = true
	if string(data) == "null" {
		n.IsNull = true
		n.Value = nil
		return nil
	}
	n.IsNull = false
	var m map[string]string
	if err := json.Unmarshal(data, &m); err != nil {
		return err
	}
	n.Value = m
	return nil
}

// ClientResponse is returned from create/update/get operations.
type ClientResponse struct {
	ClientID          string            `json:"clientId"`
	Status            string            `json:"status"`
	ClientName        string            `json:"-"`
	ClientNameLangMap map[string]string `json:"-"`
	RpID              string            `json:"-"`
	LogoURI           string            `json:"-"`
	RedirectURIs      []string          `json:"-"`
	Claims            []string          `json:"-"`
	AcrValues         []string          `json:"-"`
	PublicKey         string            `json:"-"`
	EncPublicKey      string            `json:"-"`
	GrantTypes        []string          `json:"-"`
	AuthMethods       []string          `json:"-"`
	AdditionalConfig  map[string]any    `json:"-"`
}

// APIResponse returns the minimal response object for HTTP envelopes.
func (c ClientResponse) APIResponse() *ClientResponse {
	return &ClientResponse{ClientID: c.ClientID, Status: c.Status}
}

// PatchFields tracks which optional PATCH fields were explicitly provided.
type PatchFields struct {
	ClientName        bool
	ClientNameLangMap bool
	Status            bool
	LogoURI           bool
	RedirectURIs      bool
	Claims            bool
	AcrValues         bool
	GrantTypes        bool
	AuthMethods       bool
	AdditionalConfig  bool
	EncPublicKey      bool
}

// DecodePatchRequest unmarshals a PATCH body and records which fields were set.
func DecodePatchRequest(data []byte) (PatchClientRequest, PatchFields, error) {
	var raw map[string]json.RawMessage
	if err := json.Unmarshal(data, &raw); err != nil {
		return PatchClientRequest{}, PatchFields{}, err
	}
	reqObj, ok := raw["request"]
	if !ok {
		return PatchClientRequest{}, PatchFields{}, fmt.Errorf("missing request")
	}
	var reqMap map[string]json.RawMessage
	if err := json.Unmarshal(reqObj, &reqMap); err != nil {
		return PatchClientRequest{}, PatchFields{}, err
	}
	if reqMap == nil {
		return PatchClientRequest{}, PatchFields{}, fmt.Errorf("invalid request")
	}
	var req PatchClientRequest
	var fields PatchFields
	for k, v := range reqMap {
		switch k {
		case "clientName":
			fields.ClientName = true
			if err := json.Unmarshal(v, &req.ClientName); err != nil {
				return PatchClientRequest{}, PatchFields{}, err
			}
		case "clientNameLangMap":
			fields.ClientNameLangMap = true
			if err := json.Unmarshal(v, &req.ClientNameLangMap); err != nil {
				return PatchClientRequest{}, PatchFields{}, err
			}
		case "status":
			fields.Status = true
			if err := json.Unmarshal(v, &req.Status); err != nil {
				return PatchClientRequest{}, PatchFields{}, err
			}
		case "logoUri":
			fields.LogoURI = true
			if err := json.Unmarshal(v, &req.LogoURI); err != nil {
				return PatchClientRequest{}, PatchFields{}, err
			}
		case "redirectUris":
			fields.RedirectURIs = true
			if err := json.Unmarshal(v, &req.RedirectURIs); err != nil {
				return PatchClientRequest{}, PatchFields{}, err
			}
		case "userClaims":
			fields.Claims = true
			if err := json.Unmarshal(v, &req.Claims); err != nil {
				return PatchClientRequest{}, PatchFields{}, err
			}
		case "authContextRefs":
			fields.AcrValues = true
			if err := json.Unmarshal(v, &req.AcrValues); err != nil {
				return PatchClientRequest{}, PatchFields{}, err
			}
		case "grantTypes":
			fields.GrantTypes = true
			if err := json.Unmarshal(v, &req.GrantTypes); err != nil {
				return PatchClientRequest{}, PatchFields{}, err
			}
		case "clientAuthMethods":
			fields.AuthMethods = true
			if err := json.Unmarshal(v, &req.AuthMethods); err != nil {
				return PatchClientRequest{}, PatchFields{}, err
			}
		case "additionalConfig":
			fields.AdditionalConfig = true
			req.AdditionalConfig = v
		case "encPublicKey":
			fields.EncPublicKey = true
			if string(v) == "null" {
				req.EncPublicKey = NullableJWK{Defined: true, IsNull: true}
			} else {
				var jwk map[string]string
				if err := json.Unmarshal(v, &jwk); err != nil {
					return PatchClientRequest{}, PatchFields{}, err
				}
				req.EncPublicKey = NullableJWK{Defined: true, Value: jwk}
			}
		}
	}
	return req, fields, nil
}
