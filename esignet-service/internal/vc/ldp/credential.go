/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ldp

import (
	"context"
	"errors"
	"fmt"
	"maps"
	"time"

	"github.com/mosip/esignet/internal/vc/ldp/didkeys"
	"github.com/mosip/esignet/internal/vc/ldp/jsonld"
	"github.com/mosip/esignet/internal/vc/ldp/proof"
	"github.com/mosip/esignet/internal/vc/ldp/sig"
	"github.com/mosip/esignet/internal/vc/ldp/status"
)

// verifyCredential verifies one embedded VC: structure, validity window,
// issuer trust, proof and, if configured, status. Errors carry index.
func (v *Verifier) verifyCredential(ctx context.Context, vc map[string]any, now time.Time, index int) (*credentialResult, error) {
	c, err := parseCredential(vc, now, v.cfg.ClockSkew)
	if err != nil {
		return nil, withIndex(err, index)
	}
	if !v.cfg.IssuerTrusted(c.issuer) {
		return nil, withIndex(newError(ErrUntrustedIssuer, "issuer "+c.issuer+" is not trusted"), index)
	}
	if err := v.verifyCredentialProof(ctx, c, now); err != nil {
		return nil, withIndex(err, index)
	}
	res := &credentialResult{
		Issuer:  c.issuer,
		Types:   c.types,
		Subject: vc[fieldCredentialSubject],
	}
	if len(v.cfg.StatusPurposes) == 0 {
		return res, nil
	}
	statuses, err := v.status.Check(ctx, vc, status.Request{
		Purposes: v.cfg.StatusPurposes,
		Verify:   v.verifyStatusList,
		Issuer:   c.issuer,
		Now:      now,
	})
	if err != nil {
		return nil, withIndex(wrapError(ErrStatusUnavailable, "", err), index)
	}
	for purpose, s := range statuses {
		if !s.Valid {
			ve := newError(ErrStatusSet, fmt.Sprintf("status value %d", s.Value))
			ve.StatusPurpose, ve.Index = purpose, index
			return nil, ve
		}
	}
	res.Status = statuses
	return res, nil
}

// verifyCredentialProof verifies a parsed credential's proof, including its
// expiry, issuer/key control and safe mode.
func (v *Verifier) verifyCredentialProof(ctx context.Context, c *credential, now time.Time) error {
	if c.proof["proofPurpose"] != didkeys.AssertionMethod {
		return newError(ErrProofPurpose, "credential proofPurpose must be "+didkeys.AssertionMethod)
	}
	if err := v.checkProofExpiry(c.proof, now); err != nil {
		return err
	}
	parsed, err := proof.Parse(c.proof, v.cfg.VCSuites)
	if err != nil {
		return proofError(err)
	}
	// Safe mode and canonicalization share one expansion.
	canonical, err := v.loader.CanonicalizeSafe(ctx, withoutProof(c.raw))
	if err != nil {
		return jsonldError(err)
	}
	m, err := v.resolve(ctx, parsed.VerificationMethod, didkeys.AssertionMethod)
	if err != nil {
		return err
	}
	if m.Controller != c.issuer {
		return newError(ErrIssuerKeyMismatch,
			fmt.Sprintf("verificationMethod %s is controlled by %q, not the issuer %s", m.ID, m.Controller, c.issuer))
	}
	if err := parsed.Verify(ctx, v.loader, c.raw[fieldContext], canonical, m.Key); err != nil {
		return proofError(err)
	}
	return nil
}

// resolve resolves a proof's verificationMethod and requires its controller
// to authorize it for purpose.
func (v *Verifier) resolve(ctx context.Context, vm, purpose string) (*didkeys.Method, error) {
	m, err := v.keys.Resolve(ctx, vm)
	if err != nil {
		return nil, wrapError(ErrKeyResolution, "cannot resolve verificationMethod "+vm, err)
	}
	if !m.Authorizes(purpose) {
		return nil, newError(ErrKeyNotAuthorized,
			fmt.Sprintf("the controller document of %s does not list it under %s", vm, purpose))
	}
	return m, nil
}

// withoutProof is doc without its proof, whose options are checked
// separately under their own context.
func withoutProof(doc map[string]any) map[string]any {
	out := maps.Clone(doc)
	delete(out, fieldProof)
	return out
}

// verifyStatusList verifies a status list credential for the status
// checker. Issuer trust is not checked here: the checker requires the list
// issuer to be the credential's issuer.
func (v *Verifier) verifyStatusList(ctx context.Context, listVC map[string]any, now time.Time) (string, error) {
	c, err := parseCredential(listVC, now, v.cfg.ClockSkew)
	if err != nil {
		return "", err
	}
	if err := v.verifyCredentialProof(ctx, c, now); err != nil {
		return "", err
	}
	return c.issuer, nil
}

// proofError maps proof and signature failures to kinds.
func proofError(err error) *VerificationError {
	switch {
	case errors.Is(err, proof.ErrUnsupportedSuite):
		return fieldError(ErrUnsupportedProof, fieldProof+".type", err.Error())
	case errors.Is(err, proof.ErrUnsupportedAlg):
		return fieldError(ErrUnsupportedProof, fieldProof+".alg", err.Error())
	case errors.Is(err, proof.ErrMalformed):
		ve := invalidField(fieldProof, "")
		ve.Err = err
		return ve
	case errors.Is(err, sig.ErrInvalidSignature), errors.Is(err, sig.ErrKeyMismatch), errors.Is(err, sig.ErrUnsupportedAlg):
		return wrapError(ErrSignature, "", err)
	default:
		return jsonldError(err)
	}
}

// jsonldError maps JSON-LD processing failures to kinds.
func jsonldError(err error) *VerificationError {
	switch {
	case errors.Is(err, jsonld.ErrUndefinedTerm):
		return wrapError(ErrUndefinedTerm, "", err)
	case errors.Is(err, jsonld.ErrContextNotAllowed), errors.Is(err, jsonld.ErrContext):
		return wrapError(ErrContext, "", err)
	case errors.Is(err, jsonld.ErrTooComplex):
		return wrapError(ErrLimitExceeded, "document is too complex to canonicalize", err)
	default:
		return wrapError(ErrMalformed, "JSON-LD processing failed", err)
	}
}
