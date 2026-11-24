/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ldp

import (
	"context"
	"crypto"
	"crypto/subtle"
	"errors"
	"fmt"
	"maps"
	"slices"
	"strings"
	"time"

	"golang.org/x/sync/errgroup"

	"github.com/mosip/esignet/internal/vc/ldp/didkeys"
	"github.com/mosip/esignet/internal/vc/ldp/jsonval"
	"github.com/mosip/esignet/internal/vc/ldp/proof"
)

const fieldVerifiableCreds = "verifiableCredential"

// Holder binding messages.
const (
	msgHolderMissing      = "The Verifiable Presentation is missing a mandatory Holder ID"
	msgSubjectIDMissing   = "The Verifiable Credential is missing a mandatory Credential Subject ID"
	msgHolderMismatch     = "Holder mismatch in VP: VP holder: %s, VC subject: %s"
	msgFailedToDecode     = "Fail to decode input to extract public key"
	msgInvalidHolderProof = "VP proof verificationMethod is not controlled by vp.holder"
)

// presentation is a structurally valid presentation.
type presentation struct {
	raw         map[string]any
	holder      string
	proof       map[string]any
	credentials []map[string]any // at least one
}

// VerifyPresentation verifies the VP proof, its binding to challenge and
// domain (the OpenID4VP nonce and client_id), holder binding, and every
// embedded VC. Any failure returns a *VerificationError; success means
// everything passed.
func (v *Verifier) VerifyPresentation(ctx context.Context, raw []byte, challenge, domain string) (*PresentationResult, error) {
	if challenge == "" || domain == "" {
		return nil, newError(ErrInvalidRequest, "challenge and domain are required")
	}
	now := v.cfg.Now()
	doc, err := v.decode(raw)
	if err != nil {
		return nil, err
	}
	p, err := v.parsePresentation(doc)
	if err != nil {
		return nil, err
	}
	if err := v.checkProofBinding(p.proof, challenge, domain, now); err != nil {
		return nil, err
	}
	parsed, err := proof.Parse(p.proof, v.cfg.VPSuites)
	if err != nil {
		return nil, proofError(err)
	}
	if err := v.loader.CheckSafe(ctx, unsecuredPresentation(p)); err != nil {
		return nil, jsonldError(err)
	}
	// The embedded credentials' proofs are part of what the holder signed;
	// their terms are checked below under their own contexts.
	canonical, err := v.loader.Canonicalize(ctx, withoutProof(doc))
	if err != nil {
		return nil, jsonldError(err)
	}
	m, err := v.resolve(ctx, parsed.VerificationMethod, didkeys.Authentication)
	if err != nil {
		return nil, err
	}
	if err := parsed.Verify(ctx, v.loader, doc[fieldContext], canonical, m.Key); err != nil {
		return nil, proofError(err)
	}
	h, err := bindHolder(p.holder, m)
	if err != nil {
		return nil, err
	}
	thumbprint, err := didkeys.Thumbprint(h.key)
	if err != nil {
		return nil, wrapError(ErrKeyResolution, "cannot compute the holder key thumbprint", err)
	}
	claims, err := v.verifyCredentials(ctx, p.credentials, h, now)
	if err != nil {
		return nil, err
	}
	return &PresentationResult{
		Format:                  FormatLDPVC,
		Claims:                  claims,
		HolderBindingThumbprint: thumbprint,
		Created:                 proofTime(p.proof["created"]),
	}, nil
}

// verifyCredentials verifies the embedded credentials in parallel, binds
// each to the holder and merges their subjects' claims. On failure it
// returns the error of the first failing credential in document order.
func (v *Verifier) verifyCredentials(ctx context.Context, vcs []map[string]any, h *holder, now time.Time) (map[string]any, error) {
	subjects := make([][]map[string]any, len(vcs))
	errs := make([]error, len(vcs))
	var g errgroup.Group
	g.SetLimit(v.cfg.CredentialConcurrency)
	for i, vc := range vcs {
		g.Go(func() error {
			if _, err := v.verifyCredential(ctx, vc, now, i); err != nil {
				errs[i] = err
				return nil
			}
			s, _ := jsonval.Objects(vc[fieldCredentialSubject]) // validated
			if err := h.bindSubjects(s); err != nil {
				errs[i] = withIndex(err, i)
				return nil
			}
			subjects[i] = s
			return nil
		})
	}
	_ = g.Wait() // the workers report through errs
	for _, err := range errs {
		if err != nil {
			return nil, err
		}
	}
	return mergeClaims(subjects), nil
}

// mergeClaims merges the claims of every credential's subjects in document
// order; a later value overrides an earlier one.
func mergeClaims(subjects [][]map[string]any) map[string]any {
	claims := map[string]any{}
	for _, ss := range subjects {
		for _, s := range ss {
			maps.Copy(claims, s)
		}
	}
	return claims
}

func (v *Verifier) decode(raw []byte) (map[string]any, error) {
	if len(raw) > v.cfg.MaxPresentationBytes {
		return nil, newError(ErrLimitExceeded, fmt.Sprintf("document exceeds %d bytes", v.cfg.MaxPresentationBytes))
	}
	doc, err := decodeObject(raw, v.cfg.MaxJSONDepth)
	if err != nil {
		return nil, wrapError(ErrMalformed, "document is not a supported JSON object", err)
	}
	return doc, nil
}

// parsePresentation checks the presentation's structure: context, type, a
// single proof, and credentials as an object or an array of objects, none
// of them enveloped.
func (v *Verifier) parsePresentation(vp map[string]any) (*presentation, error) {
	if vp[fieldContext] == nil {
		return nil, missingField(fieldContext)
	}
	if c := firstContext(vp[fieldContext]); c != contextV1 && c != contextV2 {
		return nil, invalidField(fieldContext, contextV1+" or "+contextV2+" must be the first context")
	}
	if !slices.Contains(jsonval.Strings(vp["type"]), typePresentation) {
		return nil, invalidField("type", "must include "+typePresentation)
	}
	pr, err := singleProof(vp)
	if err != nil {
		return nil, err
	}
	list := jsonval.List(vp[fieldVerifiableCreds])
	if len(list) == 0 {
		return nil, missingField(fieldVerifiableCreds)
	}
	if len(list) > v.cfg.MaxCredentials {
		return nil, newError(ErrLimitExceeded, fmt.Sprintf("more than %d credentials", v.cfg.MaxCredentials))
	}
	p := &presentation{raw: vp, holder: jsonval.ID(vp["holder"]), proof: pr}
	for i, e := range list {
		vc, ok := e.(map[string]any)
		if !ok {
			return nil, withIndex(invalidField(fieldVerifiableCreds, "credential is not a JSON object"), i)
		}
		if slices.Contains(jsonval.Strings(vc["type"]), typeEnvelopedVC) {
			return nil, withIndex(invalidField(fieldVerifiableCreds, "enveloped credentials are not supported"), i)
		}
		p.credentials = append(p.credentials, vc)
	}
	return p, nil
}

// singleProof returns the one proof object; proof sets are not supported.
func singleProof(doc map[string]any) (map[string]any, error) {
	switch p := doc[fieldProof].(type) {
	case nil:
		return nil, missingField(fieldProof)
	case map[string]any:
		return p, nil
	default:
		return nil, invalidField(fieldProof, "must be a single object; multiple proofs are not supported")
	}
}

// checkProofBinding enforces proofPurpose, challenge, domain and the proof
// time window on the VP proof.
func (v *Verifier) checkProofBinding(pr map[string]any, challenge, domain string, now time.Time) error {
	if pr["proofPurpose"] != didkeys.Authentication {
		return newError(ErrProofPurpose, "presentation proofPurpose must be "+didkeys.Authentication)
	}
	got := jsonval.String(pr["challenge"])
	if subtle.ConstantTimeCompare([]byte(got), []byte(challenge)) != 1 {
		return newError(ErrChallenge, "")
	}
	if !domainContains(pr["domain"], domain) {
		return newError(ErrDomain, "")
	}
	return v.checkProofTimes(pr, now)
}

// domainContains accepts domain as a string or an array of strings.
func domainContains(domain any, expected string) bool {
	values := jsonval.List(domain)
	for _, d := range values {
		if _, ok := d.(string); !ok {
			return false
		}
	}
	return slices.Contains(jsonval.Strings(values), expected)
}

func (v *Verifier) checkProofTimes(pr map[string]any, now time.Time) error {
	if err := v.checkProofExpiry(pr, now); err != nil {
		return err
	}
	skew := v.cfg.ClockSkew
	if v.cfg.MaxVPAge <= 0 {
		return nil
	}
	created, ok := jsonval.Time(pr["created"])
	if !ok {
		return invalidField(fieldProof+".created", "missing or not a valid date-time")
	}
	if isFuture(created, now, skew) {
		return invalidField(fieldProof+".created", "in the future")
	}
	if now.Sub(created) > v.cfg.MaxVPAge+skew {
		return newError(ErrProofExpired, fmt.Sprintf("proof is older than %s", v.cfg.MaxVPAge))
	}
	return nil
}

// checkProofExpiry fails a proof whose expires has passed. A proof without
// expires does not expire.
func (v *Verifier) checkProofExpiry(pr map[string]any, now time.Time) error {
	raw, present := pr["expires"]
	if !present {
		return nil
	}
	expires, ok := jsonval.Time(raw)
	if !ok {
		return invalidField(fieldProof+".expires", "not a valid date-time")
	}
	if isExpired(expires, now, v.cfg.ClockSkew) {
		return newError(ErrProofExpired, "proof.expires has passed")
	}
	return nil
}

// unsecuredPresentation is the presentation without its proof and without
// the proofs of the credentials it embeds. Each credential proof's options
// are checked on their own, under the context their signature verified with.
func unsecuredPresentation(p *presentation) map[string]any {
	out := withoutProof(p.raw)
	vcs := make([]any, 0, len(p.credentials))
	for _, vc := range p.credentials {
		vcs = append(vcs, withoutProof(vc))
	}
	out[fieldVerifiableCreds] = vcs
	return out
}

// holder is the VP holder after proof of possession.
type holder struct {
	did string
	key crypto.PublicKey
}

// bindHolder checks that the VP proof method belongs to the holder. did:key
// and did:jwk holders are compared by key; other holders (did:web, HTTPS)
// by the method's controller.
func bindHolder(did string, m *didkeys.Method) (*holder, error) {
	if did == "" {
		return nil, holderError(msgHolderMissing, nil)
	}
	key, err := didkeys.KeyFromDID(did)
	switch {
	case err == nil:
		if !didkeys.Equal(key, m.Key) {
			return nil, holderError(msgInvalidHolderProof, nil)
		}
	case errors.Is(err, didkeys.ErrUnsupportedMethod) || !strings.HasPrefix(did, "did:"):
		if m.Controller != did {
			return nil, holderError(msgInvalidHolderProof, nil)
		}
		key = m.Key
	default:
		return nil, holderError(msgFailedToDecode, err)
	}
	return &holder{did: did, key: key}, nil
}

// bindSubjects requires every credential subject to be the holder: the same
// identifier, or a did:key / did:jwk of the holder's key.
func (h *holder) bindSubjects(subjects []map[string]any) error {
	if len(subjects) == 0 {
		return holderError(msgSubjectIDMissing, nil)
	}
	for _, s := range subjects {
		id := jsonval.String(s["id"])
		switch id {
		case "":
			return holderError(msgSubjectIDMissing, nil)
		case h.did:
			continue
		}
		key, err := didkeys.KeyFromDID(id)
		switch {
		case err == nil:
			if !didkeys.Equal(h.key, key) {
				return holderError(fmt.Sprintf(msgHolderMismatch, h.did, id), nil)
			}
		case errors.Is(err, didkeys.ErrUnsupportedMethod) || !strings.HasPrefix(id, "did:"):
			return holderError(fmt.Sprintf(msgHolderMismatch, h.did, id), err)
		default:
			return holderError(msgFailedToDecode, err)
		}
	}
	return nil
}

func holderError(message string, err error) *VerificationError {
	return wrapError(ErrHolderBinding, message, err)
}

// proofTime parses a proof timestamp for results.
func proofTime(v any) *time.Time {
	t, ok := jsonval.Time(v)
	if !ok {
		return nil
	}
	return &t
}
