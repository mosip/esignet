package consent

import (
	"crypto/sha3"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"sort"
	"strings"
)

// authReqContext mirrors the consent-relevant fields of the engine's stored authorization request.
// The field names match the engine's serialized OAuthParameters (no json tags on the source struct,
// so keys are the Go field names); ClaimsRequest is decoded generically as it is stored in the OIDC
// claims wire shape ({"userinfo":{...},"id_token":{...}}).
type authReqContext struct {
	OAuthParameters struct {
		ClientID         string         `json:"ClientID"`
		Prompt           string         `json:"Prompt"`
		StandardScopes   []string       `json:"StandardScopes"`
		PermissionScopes []string       `json:"PermissionScopes"`
		ClaimsRequest    map[string]any `json:"ClaimsRequest"`
	} `json:"OAuthParameters"`
}

// decodeStored unmarshals the engine's stored authorization-request form into a requestedConsent.
func decodeStored(data []byte) (*requestedConsent, error) {
	var raw authReqContext
	if err := json.Unmarshal(data, &raw); err != nil {
		return nil, fmt.Errorf("unmarshal authorization request: %w", err)
	}
	return &requestedConsent{
		UserInfo:        claimsSection(raw.OAuthParameters.ClaimsRequest, "userinfo"),
		IDToken:         claimsSection(raw.OAuthParameters.ClaimsRequest, "id_token"),
		AuthorizeScopes: raw.OAuthParameters.PermissionScopes,
		Prompt:          raw.OAuthParameters.Prompt,
	}, nil
}

// claimsSection extracts the named section from a decoded claims request, nil when absent.
func claimsSection(claims map[string]any, name string) map[string]any {
	if claims == nil {
		return nil
	}
	section, ok := claims[name].(map[string]any)
	if !ok {
		return nil
	}
	return section
}

// hashRequestedConsent computes a deterministic hash over the requested claims and authorize scopes
// (nil claim constraints normalized to empty objects, everything deep-sorted), used to detect
// whether a stored consent still covers the request. Ports Java ConsentHelperService.hashUserConsent.
func hashRequestedConsent(req *requestedConsent) (string, error) {
	scopes := map[string]any{}
	for _, s := range req.AuthorizeScopes {
		scopes[s] = false
	}

	payload := map[string]any{
		"claims": map[string]any{
			"userinfo": normalizeSection(req.UserInfo),
			"id_token": normalizeSection(req.IDToken),
		},
		"authorizeScopes": scopes,
	}

	encoded, err := json.Marshal(sortObject(payload))
	if err != nil {
		return "", err
	}

	sum := sha3.Sum256(encoded)
	return base64.RawURLEncoding.EncodeToString(sum[:]), nil
}

// normalizeSection replaces each nil claim constraint with an empty object; a nil section becomes
// an empty map.
func normalizeSection(section map[string]any) map[string]any {
	out := make(map[string]any, len(section))
	for name, value := range section {
		if value == nil {
			out[name] = map[string]any{}
			continue
		}
		out[name] = value
	}
	return out
}

// sortObject returns a deterministically ordered copy of value: map keys are sorted by json.Marshal,
// string slices case-insensitively, and other slices keep order with elements sorted recursively.
func sortObject(value any) any {
	switch v := value.(type) {
	case map[string]any:
		out := make(map[string]any, len(v))
		for key, val := range v {
			out[key] = sortObject(val)
		}
		return out
	case []any:
		out := make([]any, len(v))
		allStrings := true
		for i, item := range v {
			out[i] = sortObject(item)
			if _, ok := item.(string); !ok {
				allStrings = false
			}
		}
		if allStrings {
			sort.SliceStable(out, func(i, j int) bool {
				return strings.ToLower(out[i].(string)) < strings.ToLower(out[j].(string))
			})
		}
		return out
	default:
		return v
	}
}
