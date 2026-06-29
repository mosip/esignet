package clientmgmt

import (
	"encoding/json"
	"strings"

	"golang.org/x/text/language"
)

var allowedClaims = map[string]struct{}{
	"name": {}, "given_name": {}, "middle_name": {}, "preferred_username": {},
	"nickname": {}, "gender": {}, "birthdate": {}, "email": {},
	"phone_number": {}, "picture": {}, "address": {},
}

var allowedACRAll = map[string]struct{}{
	"mosip:idp:acr:static-code":    {},
	"mosip:idp:acr:generated-code": {},
	"mosip:idp:acr:linked-wallet":  {},
	"mosip:idp:acr:biometrics":     {},
	"mosip:idp:acr:id-token":       {},
	"mosip:idp:acr:password":       {},
	"mosip:idp:acr:knowledge":      {},
}

var allowedACROIDCPut = map[string]struct{}{
	"mosip:idp:acr:static-code":    {},
	"mosip:idp:acr:generated-code": {},
	"mosip:idp:acr:linked-wallet":  {},
	"mosip:idp:acr:biometrics":     {},
}

// ValidateCreate validates a create request for the given API profile.
func ValidateCreate(profile Profile, req CreateClientRequest) error {
	if profile == ProfileOIDC {
		if req.ClientNameLangMap != nil {
			return validationErr("invalid_input")
		}
		if len(req.AdditionalConfig) > 0 {
			return validationErr("invalid_input")
		}
	} else {
		if req.ClientNameLangMap == nil {
			return validationErr("invalid_input")
		}
		if profile == ProfileOAuth && len(req.AdditionalConfig) > 0 {
			return validationErr("invalid_input")
		}
	}

	if err := validateClientID(req.ClientID); err != nil {
		return err
	}
	if err := validateClientName(req.ClientName); err != nil {
		return err
	}
	if profile != ProfileOIDC {
		if err := validateClientNameLangMap(req.ClientNameLangMap, false); err != nil {
			return err
		}
	}
	if err := validateRpID(req.RpID); err != nil {
		return err
	}
	if err := validateLogoURI(req.LogoURI); err != nil {
		return err
	}
	if len(req.RedirectURIs) > 0 {
		if err := validateRedirectURIs(req.RedirectURIs, 0, 0); err != nil {
			return err
		}
	}
	if len(req.Claims) == 0 {
		return validationErr("invalid_claim")
	}
	if err := validateClaims(req.Claims, 0, 0); err != nil {
		return err
	}
	if len(req.AcrValues) == 0 {
		return validationErr("invalid_acr")
	}
	if err := validateACRs(req.AcrValues, allowedACRAll, 0, 0); err != nil {
		return err
	}
	if err := validateJWK(req.PublicKey); err != nil {
		return err
	}
	if len(req.GrantTypes) == 0 {
		return validationErr("invalid_grant_type")
	}
	if err := validateGrantTypes(req.GrantTypes, 0, 0); err != nil {
		return err
	}
	if len(req.AuthMethods) == 0 {
		return validationErr("invalid_client_auth")
	}
	if err := validateAuthMethods(req.AuthMethods, 0, 0); err != nil {
		return err
	}
	if profile == ProfileClient && len(req.AdditionalConfig) > 0 {
		if err := validateAdditionalConfig(req.AdditionalConfig); err != nil {
			return err
		}
	}
	if len(req.EncPublicKey) > 0 {
		if err := validateJWK(req.EncPublicKey); err != nil {
			return err
		}
	}
	return nil
}

// ValidateUpdate validates a full update request for the given API profile.
func ValidateUpdate(profile Profile, req UpdateClientRequest) error {
	if profile == ProfileOIDC {
		if req.ClientNameLangMap != nil {
			return validationErr("invalid_input")
		}
		if len(req.AdditionalConfig) > 0 {
			return validationErr("invalid_input")
		}
	} else {
		if req.ClientNameLangMap == nil {
			return validationErr("invalid_input")
		}
		if profile == ProfileOAuth && len(req.AdditionalConfig) > 0 {
			return validationErr("invalid_input")
		}
	}

	if err := validateClientName(req.ClientName); err != nil {
		return err
	}
	if profile != ProfileOIDC {
		if err := validateClientNameLangMap(req.ClientNameLangMap, false); err != nil {
			return err
		}
	}
	if _, err := normalizePutStatus(req.Status); err != nil {
		return err
	}
	if err := validateLogoURI(req.LogoURI); err != nil {
		return err
	}
	if len(req.RedirectURIs) == 0 {
		return validationErr("invalid_redirect_uri")
	}
	if err := validateRedirectURIs(req.RedirectURIs, 1, 0); err != nil {
		return err
	}
	if len(req.Claims) == 0 {
		return validationErr("invalid_claim")
	}
	if err := validateClaims(req.Claims, 1, 0); err != nil {
		return err
	}
	acrSet := allowedACRAll
	if profile == ProfileOIDC {
		acrSet = allowedACROIDCPut
	}
	if len(req.AcrValues) == 0 {
		return validationErr("invalid_acr")
	}
	if err := validateACRs(req.AcrValues, acrSet, 1, 0); err != nil {
		return err
	}
	if len(req.GrantTypes) == 0 {
		return validationErr("invalid_grant_type")
	}
	if err := validateGrantTypes(req.GrantTypes, 1, 0); err != nil {
		return err
	}
	if len(req.AuthMethods) == 0 {
		return validationErr("invalid_client_auth")
	}
	if err := validateAuthMethods(req.AuthMethods, 1, 0); err != nil {
		return err
	}
	if profile == ProfileClient && len(req.AdditionalConfig) > 0 {
		if err := validateAdditionalConfig(req.AdditionalConfig); err != nil {
			return err
		}
	}
	return nil
}

// ValidatePatch validates a merged client state after applying PATCH fields.
func ValidatePatch(merged UpdateClientRequest, fields PatchFields) error {
	if fields.ClientName {
		if err := validateClientName(merged.ClientName); err != nil {
			return err
		}
	}
	if fields.ClientNameLangMap {
		if err := validateClientNameLangMap(merged.ClientNameLangMap, true); err != nil {
			return err
		}
	}
	if fields.Status {
		if _, err := normalizePatchStatus(merged.Status); err != nil {
			return err
		}
	}
	if fields.LogoURI {
		if err := validateLogoURI(merged.LogoURI); err != nil {
			return err
		}
	}
	if fields.RedirectURIs {
		if err := validateRedirectURIs(merged.RedirectURIs, 0, 5); err != nil {
			return err
		}
	}
	if fields.Claims {
		if err := validateClaims(merged.Claims, 0, 30); err != nil {
			return err
		}
	}
	if fields.AcrValues {
		if err := validateACRs(merged.AcrValues, allowedACRAll, 0, 30); err != nil {
			return err
		}
	}
	if fields.GrantTypes {
		if err := validateGrantTypes(merged.GrantTypes, 0, 3); err != nil {
			return err
		}
	}
	if fields.AuthMethods {
		if err := validateAuthMethods(merged.AuthMethods, 0, 3); err != nil {
			return err
		}
	}
	if fields.AdditionalConfig && len(merged.AdditionalConfig) > 0 {
		if err := validateAdditionalConfig(merged.AdditionalConfig); err != nil {
			return err
		}
	}
	return nil
}

func validateClientID(id string) error {
	if id == "" || len(id) > 50 {
		return validationErr("invalid_client_id")
	}
	return nil
}

func validateClientName(name string) error {
	if name == "" || len(name) > 256 {
		return validationErr("invalid_client_name")
	}
	return nil
}

func validateRpID(rpID string) error {
	if rpID == "" || len(rpID) > 50 {
		return validationErr("invalid_rp_id")
	}
	return nil
}

func validateLogoURI(uri string) error {
	if !isValidURI(uri) || len(uri) > 1024 {
		return validationErr("invalid_uri")
	}
	return nil
}

func validateRedirectURIs(uris []string, minItems, maxItems int) error {
	if minItems > 0 && len(uris) < minItems {
		return validationErr("invalid_redirect_uri")
	}
	if maxItems > 0 && len(uris) > maxItems {
		return validationErr("invalid_redirect_uri")
	}
	if !hasUniqueStrings(uris) {
		return validationErr("invalid_redirect_uri")
	}
	for _, u := range uris {
		if !isValidURI(u) {
			return validationErr("invalid_redirect_uri")
		}
	}
	return nil
}

func validateClaims(claims []string, minItems, maxItems int) error {
	if minItems > 0 && len(claims) < minItems {
		return validationErr("invalid_claim")
	}
	if maxItems > 0 && len(claims) > maxItems {
		return validationErr("invalid_claim")
	}
	if !containsAll(claims, allowedClaims) {
		return validationErr("invalid_claim")
	}
	return nil
}

func validateACRs(acrs []string, allowed map[string]struct{}, minItems, maxItems int) error {
	if minItems > 0 && len(acrs) < minItems {
		return validationErr("invalid_acr")
	}
	if maxItems > 0 && len(acrs) > maxItems {
		return validationErr("invalid_acr")
	}
	if !hasUniqueStrings(acrs) {
		return validationErr("invalid_acr")
	}
	if !containsAll(acrs, allowed) {
		return validationErr("invalid_acr")
	}
	return nil
}

func validateGrantTypes(grants []string, minItems, maxItems int) error {
	if minItems > 0 && len(grants) < minItems {
		return validationErr("invalid_grant_type")
	}
	if maxItems > 0 && len(grants) > maxItems {
		return validationErr("invalid_grant_type")
	}
	if !hasUniqueStrings(grants) {
		return validationErr("invalid_grant_type")
	}
	for _, g := range grants {
		if g != "authorization_code" {
			return validationErr("invalid_grant_type")
		}
	}
	return nil
}

func validateAuthMethods(methods []string, minItems, maxItems int) error {
	if minItems > 0 && len(methods) < minItems {
		return validationErr("invalid_client_auth")
	}
	if maxItems > 0 && len(methods) > maxItems {
		return validationErr("invalid_client_auth")
	}
	if !hasUniqueStrings(methods) {
		return validationErr("invalid_client_auth")
	}
	for _, m := range methods {
		if m != "private_key_jwt" {
			return validationErr("invalid_client_auth")
		}
	}
	return nil
}

func validateClientNameLangMap(langMap map[string]string, patchValueBounds bool) error {
	if langMap == nil {
		return validationErr("invalid_input")
	}
	for code, value := range langMap {
		if !isValidLanguageCode(code) {
			return validationErr("invalid_language_code")
		}
		if strings.TrimSpace(value) == "" {
			return validationErr("invalid_client_name_value")
		}
		if patchValueBounds && (len(value) < 1 || len(value) > 50) {
			return validationErr("invalid_client_name_value")
		}
	}
	return nil
}

func isValidLanguageCode(code string) bool {
	if len(code) != 3 || !isAlpha(code) {
		return false
	}
	_, err := language.Parse(code)
	return err == nil
}

func isAlpha(s string) bool {
	for _, r := range s {
		if (r < 'a' || r > 'z') && (r < 'A' || r > 'Z') {
			return false
		}
	}
	return true
}

func validateAdditionalConfig(raw json.RawMessage) error {
	var cfg map[string]json.RawMessage
	if err := json.Unmarshal(raw, &cfg); err != nil {
		return validationErr("invalid_additional_config")
	}
	if v, ok := cfg["userinfo_response_type"]; ok {
		var rt string
		if err := json.Unmarshal(v, &rt); err != nil || (rt != "JWS" && rt != "JWE") {
			return validationErr("invalid_additional_config")
		}
	}
	if v, ok := cfg["consent_expire_in_mins"]; ok {
		var mins float64
		if err := json.Unmarshal(v, &mins); err != nil || mins < 10 {
			return validationErr("invalid_additional_config")
		}
	}
	for _, key := range []string{"signup_banner_required", "forgot_pwd_link_required", "require_pushed_authorization_requests", "dpop_bound_access_tokens"} {
		if v, ok := cfg[key]; ok {
			var b bool
			if err := json.Unmarshal(v, &b); err != nil {
				return validationErr("invalid_additional_config")
			}
		}
	}
	if v, ok := cfg["purpose"]; ok {
		if err := validatePurpose(v); err != nil {
			return err
		}
	}
	return nil
}

func validatePurpose(raw json.RawMessage) error {
	var purpose map[string]json.RawMessage
	if err := json.Unmarshal(raw, &purpose); err != nil {
		return validationErr("invalid_additional_config")
	}
	typeVal, ok := purpose["type"]
	if !ok {
		return validationErr("invalid_additional_config")
	}
	var ptype string
	if err := json.Unmarshal(typeVal, &ptype); err != nil || ptype == "" {
		return validationErr("invalid_additional_config")
	}
	for _, key := range []string{"title", "subTitle"} {
		if v, ok := purpose[key]; ok {
			var langMap map[string]string
			if err := json.Unmarshal(v, &langMap); err != nil {
				return validationErr("invalid_additional_config")
			}
			if _, ok := langMap["@none"]; !ok {
				return validationErr("invalid_additional_config")
			}
		}
	}
	return nil
}

func normalizePutStatus(status string) (string, error) {
	switch status {
	case "active":
		return "ACTIVE", nil
	case "inactive":
		return "INACTIVE", nil
	default:
		return "", validationErr("invalid_input")
	}
}

func normalizePatchStatus(status string) (string, error) {
	switch status {
	case "ACTIVE", "INACTIVE":
		return status, nil
	default:
		return "", validationErr("invalid_input")
	}
}
