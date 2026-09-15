/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

import (
	"crypto/rand"
	"fmt"
	"strings"

	"golang.org/x/text/language"
)

const (
	// TransactionIDKey is the runtime metadata key holding the OIDC transaction id
	// (the flow's execution id) that GenerateTransactionID derives the IDA transaction
	// id from.
	TransactionIDKey = "provider_ext_TransactionID"

	// AllowedAuthorizationScopesKey is the client additional_config key listing the
	// authorization (non-OIDC) scopes a client is allowed to request.
	AllowedAuthorizationScopesKey = "allowed_authorization_scopes"

	// defaultAuthTransactionIDLength is the fixed length MOSIP IDA/mock-identity-system
	// require for a transaction id, used by GenerateTransactionID/DeriveAuthTransactionID
	// when called with a non-positive transactionIDLength (see
	// config.AppConfig.AuthTransactionIDLength, the normal source of that argument).
	defaultAuthTransactionIDLength = 10
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

// GenerateTransactionID fetches the transaction id from the runtimeMetadata already
// established for this runtime context (so SendOTP/AuthenticateUser/GetUserAttributes calls
// for the same flow execution share one transaction id, as mock-identity-system and MOSIP IDA
// require), falling back to a cryptographically random numeric string of transactionIDLength
// digits (or defaultAuthTransactionIDLength, if transactionIDLength <= 0) when no OIDC
// transaction id is available. Callers pass config.AppConfig.AuthTransactionIDLength.
func GenerateTransactionID(runtimeMetadata map[string][]string, transactionIDLength int) (string, error) {
	if ids := runtimeMetadata[TransactionIDKey]; len(ids) > 0 && ids[0] != "" {
		return ids[0], nil
	}

	b := make([]byte, resolveAuthTransactionIDLength(transactionIDLength))
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	for i := range b {
		b[i] = '0' + b[i]%10
	}
	return string(b), nil
}

// DeriveAuthTransactionID derives a fixed-length IDA transaction id from an OIDC transaction id
// (the flow's execution id) by reading its characters from the end, cyclically, until
// transactionIDLength characters (or defaultAuthTransactionIDLength, if transactionIDLength <=
// 0) are collected. Callers pass config.AppConfig.AuthTransactionIDLength. This mirrors the
// legacy esignet-service (Java) derivation so IDA transaction ids stay a stable length
// regardless of the OIDC transaction id's own length or format (e.g. a UUID). Returns an
// error if oidcTransactionID is empty once hyphens/underscores are stripped, since there are
// then no characters left to read from.
func DeriveAuthTransactionID(oidcTransactionID string, transactionIDLength int) (string, error) {
	cleaned := strings.NewReplacer("_", "", "-", "").Replace(oidcTransactionID)
	if cleaned == "" {
		return "", fmt.Errorf("cannot derive auth transaction id from empty execution id")
	}
	length := resolveAuthTransactionIDLength(transactionIDLength)
	b := []byte(cleaned)
	out := make([]byte, length)
	i := len(b) - 1
	for j := 0; j < length; j++ {
		out[j] = b[i]
		i--
		if i < 0 {
			i = len(b) - 1
		}
	}
	return string(out), nil
}

// resolveAuthTransactionIDLength falls back to defaultAuthTransactionIDLength for a
// non-positive length, since config.AppConfig.AuthTransactionIDLength is normally
// already defaulted, but callers (tests, other future callers) may pass 0.
func resolveAuthTransactionIDLength(length int) int {
	if length <= 0 {
		return defaultAuthTransactionIDLength
	}
	return length
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
