/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

import (
	"crypto/rand"
	"strings"

	"golang.org/x/text/language"
)

const (
	transactionIDKey = "provider_ext_TransactionID"

	// AllowedAuthorizationScopesKey is the client additional_config key listing the
	// authorization (non-OIDC) scopes a client is allowed to request.
	AllowedAuthorizationScopesKey = "allowed_authorization_scopes"
)

// AllowedAuthorizationScopes extracts AllowedAuthorizationScopesKey from a client's decoded
// additional_config. additionalConfig is always decoded from JSON, so the value is []any
// rather than []string.
func AllowedAuthorizationScopes(additionalConfig map[string]any) []string {
	v, ok := additionalConfig[AllowedAuthorizationScopesKey].([]any)
	if !ok {
		return nil
	}
	scopes := make([]string, 0, len(v))
	for _, item := range v {
		if s, ok := item.(string); ok {
			scopes = append(scopes, s)
		}
	}
	return scopes
}

// GenerateTransactionID generates a cryptographically random 10-digit numeric string,
// reusing any transaction id already established for this runtime context (so
// SendOTP/AuthenticateUser/GetUserAttributes calls for the same flow execution share
// one transaction id, as mock-identity-system and MOSIP IDA require).
func GenerateTransactionID(runtimeMetadata map[string][]string) (string, error) {
	if ids := runtimeMetadata[transactionIDKey]; len(ids) > 0 {
		return ids[0], nil
	}

	const digitCount = 10
	b := make([]byte, digitCount)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	for i := range b {
		b[i] = '0' + b[i]%10
	}
	return string(b), nil
}

// NormalizeClaimLocales parses a raw claims_locales value (a space-separated list
// of BCP-47 language tags, per the OIDC spec) into IDA-compatible ISO 639-2/T
// 3-letter codes. 2-letter codes are converted (e.g. "en" -> "eng"); 3-letter
// codes pass through unchanged; codes that fail conversion are dropped. Returns
// a non-nil empty slice when raw is empty or every token is dropped, so callers
// get an empty (not omitted) locales list and let IDA choose the default language.
func NormalizeClaimLocales(raw string) []string {
	locales := make([]string, 0)
	for tok := range strings.FieldsSeq(raw) {
		if len(tok) == 3 { // already ISO 639-2/T (e.g. "eng", "fra")
			locales = append(locales, tok)
			continue
		}
		// Use Parse, not Make: Make guesses "en" for unrecognized input, which
		// would silently coerce garbage codes to English.
		if tag, err := language.Parse(tok); err == nil {
			if base, conf := tag.Base(); conf >= language.High {
				if iso3 := base.ISO3(); iso3 != "" {
					locales = append(locales, iso3)
				}
			}
		}
	}
	return locales
}
