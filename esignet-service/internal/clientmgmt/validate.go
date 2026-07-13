/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package clientmgmt

import (
	"encoding/json"
	"net/url"
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
	if len(req.RedirectURIs) == 0 {
		return validationErr("invalid_redirect_uri")
	}
	if err := validateRedirectURIs(req.RedirectURIs, 1, 0); err != nil {
		return err
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
	if _, err := normalizeStatus(req.Status); err != nil {
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
func ValidatePatch(profile Profile, merged UpdateClientRequest, fields PatchFields, encPublicKey NullableJWK) error {
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
		if _, err := normalizeStatus(merged.Status); err != nil {
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
	if fields.EncPublicKey && !encPublicKey.IsNull {
		if err := validateJWK(encPublicKey.Value); err != nil {
			return err
		}
	}

	return ValidateUpdate(profile, merged)
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

func isValidURI(uri string) bool {
	if uri == "" || len(uri) > 1024 {
		return false
	}
	u, err := url.Parse(uri)
	if err != nil || u.Scheme == "" {
		return false
	}
	if u.Scheme == "http" || u.Scheme == "https" {
		return u.Host != ""
	}
	return u.Host != "" || u.Opaque != ""
}

// isValidRedirectURI validates a redirect URI.
//
// It accepts standard HTTP(S) URIs as well as app native redirect URIs that
// use a custom scheme (e.g. "io.mosip.residentapp://oauth"), matching the
// relying party onboarding guidance at
// https://docs.esignet.io/esignet-authentication/develop/integration/relying-party/relying-party-onboarding#id-4.-define-callback-urls-redirect-uris
//
// Wildcards ("*" and "**") are permitted, but only as a standalone path
// segment: "*" matches exactly one path segment and "**" matches the
// remainder of the path. Wildcards are never permitted in the scheme or
// host, and can't be mixed with other characters within a segment, e.g.
// "http*", "https://*", "https://domain*" and "residentapp://*" are all
// rejected while "http://localhost:5000/*", "http://localhost:5000/**" and
// "my.phone.app://oauth/*" are accepted.
func isValidRedirectURI(uri string) bool {
	if uri == "" || len(uri) > 1024 {
		return false
	}
	// Reject control characters, whitespace and backslashes outright.
	if strings.ContainsAny(uri, " \t\r\n\\") {
		return false
	}
	u, err := url.Parse(uri)
	if err != nil || u.Scheme == "" || u.Host == "" {
		return false
	}
	// Wildcards are only allowed within path segments, never in the scheme
	// or host/authority.
	if strings.Contains(u.Scheme, "*") || strings.Contains(u.Host, "*") {
		return false
	}
	for segment := range strings.SplitSeq(u.Path, "/") {
		if segment == "" || segment == "*" || segment == "**" {
			continue
		}
		if strings.Contains(segment, "*") {
			return false
		}
	}
	return true
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
		if !isValidRedirectURI(u) {
			return validationErr("invalid_redirect_uri")
		}
	}
	return nil
}

func hasUniqueStrings(items []string) bool {
	seen := make(map[string]struct{}, len(items))
	for _, item := range items {
		if _, ok := seen[item]; ok {
			return false
		}
		seen[item] = struct{}{}
	}
	return true
}

func containsAll(items []string, allowed map[string]struct{}) bool {
	for _, item := range items {
		if _, ok := allowed[item]; !ok {
			return false
		}
	}
	return true
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

// normalizeStatus validates a client status value case-insensitively and
// returns its canonical uppercase form ("ACTIVE"/"INACTIVE"), matching the
// representation stored in the database and returned in API responses.
func normalizeStatus(status string) (string, error) {
	switch strings.ToUpper(status) {
	case "ACTIVE":
		return "ACTIVE", nil
	case "INACTIVE":
		return "INACTIVE", nil
	default:
		return "", validationErr("invalid_input")
	}
}
