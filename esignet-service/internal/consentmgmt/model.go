/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package consentmgmt implements the esignet consent-action decision logic: it ports the
// consent-action decision of the Java esignet ConsentHelperService, hashing the requested OIDC
// claims (read from the authorization request) and authorize scopes and comparing against a stored
// consent record to decide whether to re-prompt the user (CAPTURE) or reuse existing consent
// (NOCAPTURE). It exposes a Service the engine's ConsentProvider implementation delegates to; the
// package provides the decision logic and utilities but does not implement the provider interface.
package consentmgmt

import (
	"database/sql"
	"time"
)

// -----------------------------------------------------------------------------
// Errors
// -----------------------------------------------------------------------------

// esignet consent error codes, surfaced to the caller as the Code of a server-side ServiceError.
const (
	ErrCodeAuthRequestRead = "ESIGNET-CES-5001"
	ErrCodeHash            = "ESIGNET-CES-5002"
	ErrCodeConsentLookup   = "ESIGNET-CES-5003"
	ErrCodeConsentPersist  = "ESIGNET-CES-5004"
	ErrCodeBuildRecord     = "ESIGNET-CES-5005"
	ErrCodeBuildPrompt     = "ESIGNET-CES-5006"
	ErrCodeConsentDelete   = "ESIGNET-CES-5007"
)

// ConsentRecord mirrors a row of the consent_detail table.
type ConsentRecord struct {
	ID                  string
	ClientID            string
	UserID              string
	Claims              map[string]any
	AuthorizationScopes []string
	Hash                string
	AcceptedClaims      []string
	PermittedScopes     []string
	CreatedAt           time.Time
	ExpiresAt           sql.NullTime
}

// IsExpired reports whether the stored consent has a past expiry. A null expiry never expires
// (mirrors Java ConsentHelperService.isOlderDate).
func (c *ConsentRecord) IsExpired(now time.Time) bool {
	return c.ExpiresAt.Valid && c.ExpiresAt.Time.Before(now)
}
