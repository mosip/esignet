/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ldp

import (
	"fmt"
	"strings"
)

// Kind classifies a verification failure. Kinds are errors, so callers can
// match with errors.Is(err, ldp.ErrSignature) or switch on
// VerificationError.Kind.
type Kind int

// Failure kinds.
const (
	_ Kind = iota
	// ErrInvalidRequest is a caller error (empty challenge or domain), not a
	// property of the presentation.
	ErrInvalidRequest
	// ErrMalformed is input that is not a JSON object, or that JSON-LD
	// processing rejects.
	ErrMalformed
	// ErrLimitExceeded is input over a size, depth, count or complexity limit.
	ErrLimitExceeded
	// ErrMissingField is a required field that is absent; Field names it.
	ErrMissingField
	// ErrInvalidField is a field with an invalid value; Field names it.
	ErrInvalidField
	// ErrUnsupportedProof is a proof suite or algorithm that is not accepted.
	ErrUnsupportedProof
	// ErrUndefinedTerm is a term the document's JSON-LD context does not
	// define, which the signature therefore does not cover.
	ErrUndefinedTerm
	// ErrContext is a JSON-LD context that is not pinned or allowed, or that
	// could not be loaded.
	ErrContext
	// ErrChallenge is a presentation proof without the expected challenge.
	ErrChallenge
	// ErrDomain is a presentation proof without the expected domain.
	ErrDomain
	// ErrProofPurpose is a proof with the wrong proofPurpose.
	ErrProofPurpose
	// ErrProofExpired is a presentation or credential proof past its
	// expires, or a presentation proof older than Config.MaxVPAge.
	ErrProofExpired
	// ErrKeyResolution is a verificationMethod that could not be resolved.
	ErrKeyResolution
	// ErrKeyNotAuthorized is a verificationMethod its controller document
	// does not authorize for the proof purpose.
	ErrKeyNotAuthorized
	// ErrSignature is a signature that does not verify, or a key that does
	// not fit the algorithm.
	ErrSignature
	// ErrUntrustedIssuer is an issuer Config.IssuerTrusted rejects.
	ErrUntrustedIssuer
	// ErrIssuerKeyMismatch is a credential proof key the issuer does not
	// control.
	ErrIssuerKeyMismatch
	// ErrHolderBinding is a presentation proof key the holder does not
	// control, or a credential subject that is not the holder.
	ErrHolderBinding
	// ErrNotYetValid is a credential before its validFrom / issuanceDate;
	// Field names the field.
	ErrNotYetValid
	// ErrExpired is a credential past its validUntil / expirationDate.
	ErrExpired
	// ErrStatusUnavailable is a credential status that could not be checked.
	ErrStatusUnavailable
	// ErrStatusSet is a credential whose status is set for a purpose, e.g.
	// revoked; StatusPurpose names it.
	ErrStatusSet
)

var kindText = [...]string{
	ErrInvalidRequest:    "invalid request",
	ErrMalformed:         "malformed document",
	ErrLimitExceeded:     "limit exceeded",
	ErrMissingField:      "missing field",
	ErrInvalidField:      "invalid field",
	ErrUnsupportedProof:  "unsupported proof",
	ErrUndefinedTerm:     "undefined JSON-LD term",
	ErrContext:           "JSON-LD context not available",
	ErrChallenge:         "challenge mismatch",
	ErrDomain:            "domain mismatch",
	ErrProofPurpose:      "wrong proof purpose",
	ErrProofExpired:      "proof expired",
	ErrKeyResolution:     "key resolution failed",
	ErrKeyNotAuthorized:  "key not authorized for proof purpose",
	ErrSignature:         "signature verification failed",
	ErrUntrustedIssuer:   "untrusted issuer",
	ErrIssuerKeyMismatch: "issuer does not control the proof key",
	ErrHolderBinding:     "holder binding failed",
	ErrNotYetValid:       "credential not yet valid",
	ErrExpired:           "credential expired",
	ErrStatusUnavailable: "credential status unavailable",
	ErrStatusSet:         "credential status set",
}

func (k Kind) Error() string {
	if k > 0 && int(k) < len(kindText) {
		return kindText[k]
	}
	return fmt.Sprintf("ldp.Kind(%d)", int(k))
}

// presentationIndex is VerificationError.Index for failures of the VP itself.
const presentationIndex = -1

// VerificationError is the single error type VerifyPresentation returns.
// Message and Err are meant for logs; the caller decides what, if anything,
// goes back to the wallet.
type VerificationError struct {
	Kind Kind
	// Index is the credential's position in the presentation, or -1 for the
	// presentation itself.
	Index int
	// Field names the offending field for ErrMissingField, ErrInvalidField,
	// ErrUnsupportedProof and ErrNotYetValid, e.g. "credentialSubject.id".
	Field string
	// StatusPurpose names the purpose for ErrStatusSet, e.g. "revocation".
	StatusPurpose string
	Message       string
	Err           error
}

func (e *VerificationError) Error() string {
	var b strings.Builder
	if e.Index >= 0 {
		fmt.Fprintf(&b, "credential %d: ", e.Index)
	} else {
		b.WriteString("presentation: ")
	}
	b.WriteString(e.Kind.Error())
	for _, detail := range []string{e.Field, e.StatusPurpose} {
		if detail != "" {
			b.WriteByte(' ')
			b.WriteString(detail)
		}
	}
	if e.Message != "" {
		b.WriteString(": ")
		b.WriteString(e.Message)
	}
	if e.Err != nil {
		b.WriteString(": ")
		b.WriteString(e.Err.Error())
	}
	return b.String()
}

// Is matches the error's Kind.
func (e *VerificationError) Is(target error) bool {
	k, ok := target.(Kind)
	return ok && k == e.Kind
}

func (e *VerificationError) Unwrap() error { return e.Err }

func newError(kind Kind, message string) *VerificationError {
	return &VerificationError{Kind: kind, Index: presentationIndex, Message: message}
}

func wrapError(kind Kind, message string, err error) *VerificationError {
	return &VerificationError{Kind: kind, Index: presentationIndex, Message: message, Err: err}
}

func fieldError(kind Kind, field, message string) *VerificationError {
	return &VerificationError{Kind: kind, Index: presentationIndex, Field: field, Message: message}
}

func missingField(field string) *VerificationError {
	return fieldError(ErrMissingField, field, "")
}

func invalidField(field, message string) *VerificationError {
	return fieldError(ErrInvalidField, field, message)
}
