/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package consentmgmt

import (
	"crypto/sha3"
	"encoding/base64"
	"encoding/json"
	"sort"
	"strings"
)

// NormalizeClaims normalizes a decoded OIDC claims request into a stable shape for hashing:
// both the userinfo and id_token sections are always present, with nil claim constraints
// replaced by empty objects.
func NormalizeClaims(claims map[string]any) map[string]any {
	return map[string]any{
		"userinfo": normalizeSection(claimsSection(claims, "userinfo")),
		"id_token": normalizeSection(claimsSection(claims, "id_token")),
	}
}

// ExtractAttributes parses a decoded OIDC claims request parameter (the userinfo/id_token shape
// defined at https://openid.net/specs/openid-connect-core-1_0.html#Claims, including the nested
// verified_claims claim added by the Identity Assurance extension,
// https://openid.net/specs/openid-connect-4-identity-assurance-1_0-05.html#name-requesting-verified-claims)
// into deduplicated, alphabetically sorted essential and optional attribute name lists. A claim is
// essential if any section or verified_claims entry requests it with {"essential": true}; the same
// claim requested as essential in one place and optional in another resolves to essential.
func ExtractAttributes(claims map[string]any) (essential []string, optional []string) {
	essentialSet := map[string]bool{}
	optionalSet := map[string]bool{}

	collectClaims(claimsSection(claims, "userinfo"), essentialSet, optionalSet)
	collectClaims(claimsSection(claims, "id_token"), essentialSet, optionalSet)

	for name := range essentialSet {
		essential = append(essential, name)
	}
	for name := range optionalSet {
		if essentialSet[name] {
			continue
		}
		optional = append(optional, name)
	}
	sort.Strings(essential)
	sort.Strings(optional)
	return essential, optional
}

// collectClaims records each claim name in section as essential or optional. verified_claims is
// unpacked rather than recorded by name: its nested "claims" object is merged in using the same
// rules, whether verified_claims is a single object or (to target multiple verification sources)
// an array of objects.
func collectClaims(section map[string]any, essentialSet, optionalSet map[string]bool) {
	for name, constraint := range section {
		if name == "verified_claims" {
			collectVerifiedClaims(constraint, essentialSet, optionalSet)
			continue
		}
		if isEssentialConstraint(constraint) {
			essentialSet[name] = true
		} else {
			optionalSet[name] = true
		}
	}
}

// collectVerifiedClaims merges the claim names nested under a verified_claims request's "claims"
// object(s) into essentialSet/optionalSet.
func collectVerifiedClaims(constraint any, essentialSet, optionalSet map[string]bool) {
	switch v := constraint.(type) {
	case map[string]any:
		collectVerifiedClaimsEntry(v, essentialSet, optionalSet)
	case []any:
		for _, entry := range v {
			if m, ok := entry.(map[string]any); ok {
				collectVerifiedClaimsEntry(m, essentialSet, optionalSet)
			}
		}
	}
}

// collectVerifiedClaimsEntry merges a single verified_claims entry's "claims" object.
func collectVerifiedClaimsEntry(entry map[string]any, essentialSet, optionalSet map[string]bool) {
	if nested, ok := entry["claims"].(map[string]any); ok {
		collectClaims(nested, essentialSet, optionalSet)
	}
}

// isEssentialConstraint reports whether a claim constraint marks the claim essential per the OIDC
// spec: the value is an object with "essential": true.
func isEssentialConstraint(constraint any) bool {
	m, ok := constraint.(map[string]any)
	if !ok {
		return false
	}
	v, _ := m["essential"].(bool)
	return v
}

// NormalizeAuthorizationScopes converts the requested authorize scopes into the map shape used
// for hashing, mapping each scope name to false.
func NormalizeAuthorizationScopes(authorizeScopes []string) map[string]any {
	scopes := map[string]any{}
	for _, s := range authorizeScopes {
		scopes[s] = false
	}
	return scopes
}

// HashRequestedConsent computes a deterministic hash over the requested claims and authorize scopes
// (nil claim constraints normalized to empty objects, everything deep-sorted), used to detect
// whether a stored consent still covers the request. Ports Java ConsentHelperService.hashUserConsent.
func HashRequestedConsent(normalizedClaims map[string]any,
	normalizedAuthorizeScopes map[string]any) (string, error) {
	payload := map[string]any{
		"claims":          normalizedClaims,
		"authorizeScopes": normalizedAuthorizeScopes,
	}

	encoded, err := json.Marshal(sortObject(payload))
	if err != nil {
		return "", err
	}

	sum := sha3.Sum256(encoded)
	return base64.RawURLEncoding.EncodeToString(sum[:]), nil
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
