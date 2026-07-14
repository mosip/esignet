// Package consent implements the esignet consent-action decision logic: it ports the
// consent-action decision of the Java esignet ConsentHelperService, hashing the requested OIDC
// claims (read from the authorization request) and authorize scopes and comparing against a stored
// consent record to decide whether to re-prompt the user (CAPTURE) or reuse existing consent
// (NOCAPTURE). It exposes a Service the engine's ConsentProvider implementation delegates to; the
// package provides the decision logic and utilities but does not implement the provider interface.
package consent

import (
	"database/sql"
	"errors"
	"fmt"
	"time"
)

// -----------------------------------------------------------------------------
// Public API types: the domain input/output types the engine adapter translates
// to and from, decoupled from the ThunderID provider types.
// -----------------------------------------------------------------------------

// ResolveInput carries everything Service.Resolve needs, decoupled from the engine provider types.
type ResolveInput struct {
	OUID                  string
	AppID                 string
	AppName               string
	UserID                string
	EssentialAttributes   []string
	OptionalAttributes    []string
	AuthorizedPermissions []string
	// AvailableAttributes is the set of attribute names present in the user's profile. When empty,
	// no profile-based filtering is applied.
	AvailableAttributes []string
	ForceReprompt       bool
	RuntimeMetadata     map[string]string
}

// PromptData is the consent prompt to present to the user (CAPTURE). Service.Resolve returns a nil
// *PromptData to signal NOCAPTURE (existing consent still covers the request).
type PromptData struct {
	Purposes []PromptPurpose
}

// PromptPurpose is one consent purpose (attributes or permissions) with the element names that need
// user consent.
type PromptPurpose struct {
	PurposeName string
	Type        string
	Essential   []string
	Optional    []string
}

// RecordInput carries the user's consent decisions to persist.
type RecordInput struct {
	OUID            string
	AppID           string
	UserID          string
	Decisions       *Decisions
	SessionToken    string
	ValidityPeriod  int64
	RuntimeMetadata map[string]string
}

// Decisions holds the user's per-purpose consent decisions.
type Decisions struct {
	Purposes []PurposeDecision
}

// PurposeDecision holds the element decisions for a single consent purpose.
type PurposeDecision struct {
	PurposeName string
	Approved    bool
	Elements    []ElementDecision
}

// ElementDecision holds the user's approval decision for a single consent element.
type ElementDecision struct {
	Name     string
	Approved bool
}

// Record is the persisted consent, returned so the engine can translate it into a provider Consent.
type Record struct {
	ID       string
	GroupID  string
	Status   string
	Purposes []RecordPurpose
}

// RecordPurpose is a persisted purpose with its element approval records.
type RecordPurpose struct {
	Name     string
	Elements []RecordElement
}

// RecordElement is a persisted element approval. Namespace is "attribute" or "permission".
type RecordElement struct {
	Name           string
	Namespace      string
	IsUserApproved bool
}

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

// ErrEssentialConsentDenied signals that the user denied consent for an essential attribute. The
// consent record is still persisted before this is returned; the engine maps it to the
// essential-consent-denied error the flow executor matches on.
var ErrEssentialConsentDenied = errors.New("essential consent denied")

// Error is a consent service failure carrying a stable esignet error code. The engine adapter maps
// Code onto the server-side ServiceError returned to the engine.
type Error struct {
	Code string
	Err  error
}

func (e *Error) Error() string {
	if e.Err == nil {
		return e.Code
	}
	return fmt.Sprintf("%s: %v", e.Code, e.Err)
}

func (e *Error) Unwrap() error { return e.Err }

// codedError wraps err with a consent error code.
func codedError(code string, err error) error {
	return &Error{Code: code, Err: err}
}

// -----------------------------------------------------------------------------
// Internal models
// -----------------------------------------------------------------------------

// requestedConsent is the consent-relevant view of an authorization request, read from the
// runtime store by authorization_request_id.
type requestedConsent struct {
	// UserInfo is the "userinfo" section of the OIDC claims request parameter (claim name -> raw
	// constraint, which may be nil). The "verified_claims" member, when present, is retained here.
	UserInfo map[string]any
	// IDToken is the "id_token" section of the OIDC claims request parameter.
	IDToken map[string]any
	// AuthorizeScopes is the set of resource/authorize scopes that require consent.
	AuthorizeScopes []string
	// Prompt is the raw OIDC prompt parameter (space-delimited values).
	Prompt string
}

// hasRequest reports whether the request carries any claims or authorize scopes.
func (r *requestedConsent) hasRequest() bool {
	if r == nil {
		return false
	}
	return len(r.UserInfo) > 0 || len(r.IDToken) > 0 || len(r.AuthorizeScopes) > 0
}

// storedConsent mirrors a row of the consent_detail table.
type storedConsent struct {
	ID                  string
	ClientID            string
	UserID              string
	Claims              string // serialized OIDC claims request (audit/debug)
	AuthorizationScopes string // serialized scope->essential map (audit/debug)
	Hash                string
	AcceptedClaims      []string
	PermittedScopes     []string
	CreatedAt           time.Time
	ExpiresAt           sql.NullTime
}

// isExpired reports whether the stored consent has a past expiry. A null expiry never expires
// (mirrors Java ConsentHelperService.isOlderDate).
func (c *storedConsent) isExpired(now time.Time) bool {
	return c.ExpiresAt.Valid && c.ExpiresAt.Time.Before(now)
}
