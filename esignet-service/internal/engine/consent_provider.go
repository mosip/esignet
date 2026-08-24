/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"maps"
	"sort"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/consentmgmt"
	applog "github.com/mosip/esignet/internal/log"
)

const (
	runtimeKeyClientID = "initiator_query_client_id"
	runtimeKeyScopes   = "initiator_query_scope"
	runtimeKeyClaims   = "initiator_query_claims"

	// purpose-name prefixes the engine uses to classify a consent purpose's namespace.
	attributesPurpose  = "attributes:"
	permissionsPurpose = "permissions:"

	// consent prompt type discriminators read by the UI.
	promptTypeAttributes  = "attributes"
	promptTypePermissions = "permissions"
)

type consentProvider struct {
	consentSvc *consentmgmt.Service
	clientSvc  *clientmgmt.Service
	config     *config.AppConfig
	logger     *applog.Logger
}

// NewConsentProvider builds a providers.ConsentProvider backed by consentSvc.
func NewConsentProvider(consentSvc *consentmgmt.Service, clientSvc *clientmgmt.Service, config *config.AppConfig) providers.ConsentProvider {

	return &consentProvider{consentSvc: consentSvc,
		config:    config,
		clientSvc: clientSvc,
		logger:    applog.GetLogger().Named("consentProvider"),
	}
}

func (p *consentProvider) ResolveConsent(ctx context.Context, _, appID string, _, userID string,
	essentialAttributes, optionalAttributes, authorizedPermissions []string,
	_ *providers.AttributesResponse, forceReprompt bool,
	runtimeMetadata map[string][]string) (
	*providers.ConsentPromptData, *common.ServiceError) {

	clientID := runtimeMetadata[runtimeKeyClientID][0]
	consentRecord, err := p.consentSvc.FetchRecord(ctx, clientID, userID)
	if err != nil {
		p.logger.Error(ctx, "Failed to read consent record", applog.Error(err))
		return nil, clientError("consent_fetch_failed", err)
	}

	req, err := p.readAuthRequest(ctx, runtimeMetadata)
	if err != nil {
		p.logger.Error(ctx, "Failed to read auth request", applog.Error(err))
		return nil, clientError("consent_record_failed", err)
	}
	if req == nil {
		p.logger.Error(ctx, "Read auth request is nil", applog.Error(err))
		return nil, clientError("consent_record_failed", err)
	}

	if forceReprompt {
		p.logger.Warn(ctx, "Force reprompt consent")
		return p.buildPrompt(appID, req, essentialAttributes, optionalAttributes,
			authorizedPermissions, []string{}), nil
	}

	if consentRecord == nil {
		p.logger.Debug(ctx, "No stored consent found, prompt consent")
		return p.buildPrompt(appID, req, essentialAttributes, optionalAttributes,
			authorizedPermissions, []string{}), nil
	}

	if consentRecord.IsExpired(time.Now().UTC()) {
		p.logger.Warn(ctx, "Expired consent found! reprompt consent")
		return p.buildPrompt(appID, req, essentialAttributes, optionalAttributes,
			authorizedPermissions, []string{}), nil
	}

	client, err := p.clientSvc.GetClient(ctx, clientID)
	if err != nil {
		p.logger.Error(ctx, "Failed to read client record", applog.Error(err))
		return nil, clientError("consent_fetch_failed", err)
	}
	hash := p.getConsentRequestHash(ctx, req, client.Claims)
	if hash == "" {
		return nil, clientError("consent_check_failed", nil)
	}

	if consentRecord.Hash == hash {
		p.logger.Debug(ctx, "Requested consent and stored consent match, skip consent prompt")
		return nil, nil
	}

	return p.buildPrompt(appID, req, essentialAttributes, optionalAttributes,
		authorizedPermissions, []string{}), nil
}

// RecordConsent records the user's consent decisions and returns the persisted consent record.
// If the user denied any essential attribute, ErrorEssentialConsentDenied is returned.
func (p *consentProvider) RecordConsent(ctx context.Context, _, appID, userID string,
	decisions *providers.ConsentDecisions, _ string, validityPeriod int64,
	runtimeMetadata map[string][]string) (
	*providers.Consent, *common.ServiceError) {
	clientID := runtimeMetadata[runtimeKeyClientID][0]

	req, err := p.readAuthRequest(ctx, runtimeMetadata)
	if err != nil {
		p.logger.Error(ctx, "Failed to read auth request", applog.Error(err))
		return nil, clientError("consent_record_failed", err)
	}
	if req == nil {
		p.logger.Error(ctx, "Read auth request is nil", applog.Error(err))
		return nil, clientError("consent_record_failed", err)
	}

	essentialClaims := map[string]bool{}
	allowedClaims := map[string]bool{}
	for name, v := range requestedClaims(req) {
		allowedClaims[name] = true
		if isEssentialClaim(v) {
			essentialClaims[name] = true
		}
	}

	client, err := p.clientSvc.GetClient(ctx, clientID)
	if err != nil {
		p.logger.Error(ctx, "Failed to read client record", applog.Error(err))
		return nil, clientError("consent_record_failed", err)
	}
	claimsFromScopes := p.claimsFromScopes(req.standardScopes, client.Claims)
	for _, name := range claimsFromScopes {
		allowedClaims[name] = true
	}
	allowedScopes := map[string]bool{}
	for _, s := range req.authorizeScopes {
		allowedScopes[s] = true
	}

	// filteredDecisions drops any purpose element the caller approved/denied that was never part
	// of the original authorize request's claims/scopes, so a consent-decision submission cannot
	// grant claims or permissions beyond what was actually requested.
	var acceptedClaims, permittedScopes []string
	var filteredDecisions *providers.ConsentDecisions
	if decisions != nil {
		filteredPurposes := make([]providers.PurposeDecision, 0, len(decisions.Purposes))
		for _, purpose := range decisions.Purposes {
			isAttributesPurpose := strings.HasPrefix(purpose.PurposeName, attributesPurpose)
			isPermissionsPurpose := strings.HasPrefix(purpose.PurposeName, permissionsPurpose)

			elements := purpose.Elements
			if isAttributesPurpose || isPermissionsPurpose {
				filteredElements := make([]providers.ElementDecision, 0, len(purpose.Elements))
				for _, element := range purpose.Elements {
					if isAttributesPurpose && !allowedClaims[element.Name] {
						p.logger.Warn(ctx, "Ignoring consent decision for unrequested claim",
							applog.String("claim", element.Name))
						continue
					}
					if isPermissionsPurpose && !allowedScopes[element.Name] {
						p.logger.Warn(ctx, "Ignoring consent decision for unrequested scope",
							applog.String("scope", element.Name))
						continue
					}
					filteredElements = append(filteredElements, element)
				}
				elements = filteredElements
			}

			for _, element := range elements {
				if !element.Approved {
					if isAttributesPurpose && essentialClaims[element.Name] {
						p.logger.Warn(ctx, "Essential attribute consent denied", applog.String("attribute", element.Name))
						return nil, clientError("essential_consent_denied", nil)
					}
					continue
				}
				if isAttributesPurpose {
					acceptedClaims = append(acceptedClaims, element.Name)
				}
				if isPermissionsPurpose {
					permittedScopes = append(permittedScopes, element.Name)
				}
			}

			filteredPurposes = append(filteredPurposes, providers.PurposeDecision{
				PurposeName: purpose.PurposeName,
				Approved:    purpose.Approved,
				Elements:    elements,
			})
		}
		filteredDecisions = &providers.ConsentDecisions{
			Approved: decisions.Approved,
			Reason:   decisions.Reason,
			Purposes: filteredPurposes,
		}
	}

	now := time.Now().UTC()
	var expiresAt sql.NullTime
	if validityPeriod > 0 {
		expiresAt = sql.NullTime{Time: now.Add(time.Duration(validityPeriod) * time.Second), Valid: true}
	}

	consentRecord := &consentmgmt.ConsentRecord{
		ID:                  uuid.NewString(),
		ClientID:            clientID,
		UserID:              userID,
		Claims:              mergeScopeClaims(req.claimsRequest, claimsFromScopes),
		AuthorizationScopes: req.authorizeScopes,
		AcceptedClaims:      acceptedClaims,
		PermittedScopes:     permittedScopes,
		CreatedAt:           now,
		ExpiresAt:           expiresAt,
	}
	if err := p.consentSvc.SaveRecord(ctx, consentRecord); err != nil {
		p.logger.Error(ctx, "Failed to save consent record", applog.Error(err))
		return nil, clientError("consent_persist_failed", err)
	}

	return buildRecord(consentRecord.ID, appID, filteredDecisions), nil
}

// buildPrompt constructs the consent prompt for the requested attributes and permissions. It
// returns nil when, after filtering, there is nothing to prompt for.
func (p *consentProvider) buildPrompt(appID string, req *requestedConsent, essentialAttributes,
	optionalAttributes, authorizedPermissions, availableAttributes []string) *providers.ConsentPromptData {
	purposes := make([]providers.ConsentPurposePrompt, 0, 2)

	essential, optional := classifyAttributes(req, essentialAttributes, optionalAttributes, availableAttributes)
	if len(essential) > 0 || len(optional) > 0 {
		purposes = append(purposes, providers.ConsentPurposePrompt{
			PurposeName: attributesPurpose + appID,
			Type:        promptTypeAttributes,
			Essential:   toPromptElements(essential),
			Optional:    toPromptElements(optional),
		})
	}

	if len(authorizedPermissions) > 0 {
		perms := append([]string(nil), authorizedPermissions...)
		sortFold(perms)
		purposes = append(purposes, providers.ConsentPurposePrompt{
			PurposeName: permissionsPurpose + appID,
			Type:        promptTypePermissions,
			Essential:   toPromptElements(perms),
			Optional:    []providers.PromptElement{},
		})
	}

	if len(purposes) == 0 {
		return nil
	}
	return &providers.ConsentPromptData{Purposes: purposes}
}

// toPromptElements maps element names to prompt elements.
// toPromptElements maps element names to prompt elements.
func toPromptElements(names []string) []providers.PromptElement {
	elements := make([]providers.PromptElement, 0, len(names))
	for _, n := range names {
		elements = append(elements, providers.PromptElement{Name: n})
	}
	return elements
}

// classifyAttributes computes the essential and optional attribute names to prompt for, drawing
// from the engine-resolved attribute lists and the claims-request essential flags, filtered to the
// attributes present in the user's profile (when availableAttributes is non-empty).
func classifyAttributes(req *requestedConsent, essentialAttributes, optionalAttributes,
	availableAttributes []string) (essential, optional []string) {
	essentialSet := map[string]bool{}
	for _, a := range essentialAttributes {
		essentialSet[a] = true
	}
	for name, v := range requestedClaims(req) {
		if isEssentialClaim(v) {
			essentialSet[name] = true
		}
	}

	optionalSet := map[string]bool{}
	add := func(name string) {
		if name == "" || essentialSet[name] {
			return
		}
		optionalSet[name] = true
	}
	for _, a := range optionalAttributes {
		add(a)
	}
	for name := range requestedClaims(req) {
		add(name)
	}

	present := profileFilter(availableAttributes)
	essential = filterSorted(essentialSet, present)
	optional = filterSorted(optionalSet, present)
	return essential, optional
}

// isEssentialClaim reports whether a claim constraint marks the claim essential ({"essential":true}).
func isEssentialClaim(v any) bool {
	m, ok := v.(map[string]any)
	if !ok {
		return false
	}
	essential, ok := m["essential"].(bool)
	return ok && essential
}

// profileFilter returns the set of attribute names present in the user's profile, or nil when no
// profile information is available (meaning no filtering is applied).
func profileFilter(available []string) map[string]bool {
	if len(available) == 0 {
		return nil
	}
	set := make(map[string]bool, len(available))
	for _, name := range available {
		set[name] = true
	}
	return set
}

// filterSorted returns the keys of set (optionally restricted to those present in allow) sorted
// case-insensitively.
func filterSorted(set, allow map[string]bool) []string {
	out := make([]string, 0, len(set))
	for name := range set {
		if allow != nil && !allow[name] {
			continue
		}
		out = append(out, name)
	}
	sortFold(out)
	return out
}

func sortFold(s []string) {
	sort.SliceStable(s, func(i, j int) bool {
		return strings.ToLower(s[i]) < strings.ToLower(s[j])
	})
}

// requestedClaims merges the userinfo and id_token claim entries, skipping the verified_claims
// member which is not an individual claim.
func requestedClaims(req *requestedConsent) map[string]any {
	normalizedClaims := consentmgmt.NormalizeClaims(req.claimsRequest)
	out := make(map[string]any, 20)
	for _, section := range []map[string]any{normalizedClaims["userinfo"].(map[string]any),
		normalizedClaims["id_token"].(map[string]any)} {
		for name, v := range section {
			if name == "verified_claims" {
				// TODO
				continue
			}
			out[name] = v
		}
	}
	return out
}

// buildRecord constructs the consent Record from the persisted id and the user's decisions. Purpose
// names carry the namespace prefix the flow executor uses to derive consented attributes/permissions.
func buildRecord(id, appID string, decisions *providers.ConsentDecisions) *providers.Consent {
	purposes := make([]providers.ConsentPurposeItem, 0)
	if decisions != nil {
		for _, pd := range decisions.Purposes {
			ns := namespaceFromPurposeName(pd.PurposeName)
			elements := make([]providers.ConsentElementApproval, 0, len(pd.Elements))
			for _, ed := range pd.Elements {
				elements = append(elements, providers.ConsentElementApproval{
					Name:           ed.Name,
					Namespace:      ns,
					IsUserApproved: ed.Approved,
				})
			}
			purposes = append(purposes, providers.ConsentPurposeItem{Name: pd.PurposeName, Elements: elements})
		}
	}
	return &providers.Consent{
		ID:       id,
		GroupID:  appID,
		Status:   providers.ConsentStatusActive,
		Purposes: purposes,
	}
}

// namespaceFromPurposeName derives the consent namespace from a purpose name's prefix, mirroring
// the engine's convention.
func namespaceFromPurposeName(name string) providers.Namespace {
	switch {
	case strings.HasPrefix(name, permissionsPurpose):
		return providers.NamespacePermission
	case strings.HasPrefix(name, attributesPurpose):
		return providers.NamespaceAttribute
	default:
		return ""
	}
}

func clientError(errorCode string, err error) *common.ServiceError {
	serviceError := &common.ServiceError{
		Code: errorCode,
		Type: common.ClientErrorType,
	}

	if err != nil {
		serviceError.Error = common.I18nMessage{
			Key:          errorCode,
			DefaultValue: err.Error(),
		}
		serviceError.ErrorDescription = common.I18nMessage{
			Key:          errorCode,
			DefaultValue: err.Error(),
		}
	}
	return serviceError
}

func (p *consentProvider) getConsentRequestHash(ctx context.Context, req *requestedConsent, registeredClaims []string) string {
	effectiveClaims := mergeScopeClaims(req.claimsRequest, p.claimsFromScopes(req.standardScopes, registeredClaims))
	hash, err := requestHash(effectiveClaims, req.authorizeScopes)
	if err != nil {
		p.logger.Error(ctx, "Failed to hash the claims request", applog.Error(err))
		return ""
	}
	return hash
}

// requestHash normalizes the requested claims and authorize scopes and computes the deterministic
// consent hash used to detect whether a stored consent still covers the current request.
func requestHash(claimsRequest map[string]any, authorizeScopes []string) (string, error) {
	return consentmgmt.HashRequestedConsent(
		consentmgmt.NormalizeClaims(claimsRequest),
		consentmgmt.NormalizeAuthorizationScopes(authorizeScopes),
	)
}

// claimsFromScopes flattens the claim names mapped to scopes by the registered client claims,
// deduplicated.
func (p *consentProvider) claimsFromScopes(scopes []string, registeredClaims []string) []string {
	registered := make(map[string]bool, len(registeredClaims))
	for _, claim := range registeredClaims {
		registered[claim] = true
	}

	seen := make(map[string]bool, len(registeredClaims))
	var claims []string
	for _, scope := range scopes {
		for _, scopeClaim := range p.config.ScopeClaims[scope] {
			if !registered[scopeClaim] || seen[scopeClaim] {
				continue
			}
			seen[scopeClaim] = true
			claims = append(claims, scopeClaim)
		}
	}
	return claims
}

// mergeScopeClaims returns a copy of claimsRequest with each name in scopeClaimNames added to the
// userinfo section as an optional claim (nil constraint), skipping any name already present in
// either the userinfo or id_token section. The input is never mutated: callers (e.g. the consent
// prompt path via requestedClaims) read the original claimsRequest independently.
func mergeScopeClaims(claimsRequest map[string]any, scopeClaimNames []string) map[string]any {
	if len(scopeClaimNames) == 0 {
		return claimsRequest
	}

	normalized := consentmgmt.NormalizeClaims(claimsRequest)
	userinfo, _ := normalized["userinfo"].(map[string]any)

	merged := make(map[string]any, len(claimsRequest))
	maps.Copy(merged, claimsRequest)
	mergedUserinfo := make(map[string]any, len(userinfo)+len(scopeClaimNames))
	maps.Copy(mergedUserinfo, userinfo)
	for _, name := range scopeClaimNames {
		if _, ok := userinfo[name]; ok {
			continue
		}
		mergedUserinfo[name] = nil
	}
	merged["userinfo"] = mergedUserinfo
	return merged
}

func (p *consentProvider) readAuthRequest(_ context.Context, runtimeMetadata map[string][]string) (*requestedConsent, error) {
	req := &requestedConsent{
		claimsRequest: map[string]any{},
		prompt:        "consent",
	}

	if claims := firstValue(runtimeMetadata[runtimeKeyClaims]); claims != "" {
		var claimsRequest map[string]any
		if err := json.Unmarshal([]byte(claims), &claimsRequest); err != nil {
			return nil, fmt.Errorf("failed to parse claims request parameter: %w", err)
		}
		req.claimsRequest = claimsRequest
	}

	for _, scope := range strings.Fields(firstValue(runtimeMetadata[runtimeKeyScopes])) {
		if p.config.IsAuthorizationScope(scope) {
			req.authorizeScopes = append(req.authorizeScopes, scope)
			continue
		}
		req.standardScopes = append(req.standardScopes, scope)
	}

	return req, nil
}

// firstValue returns the first element of values, or "" when values is empty.
func firstValue(values []string) string {
	if len(values) == 0 {
		return ""
	}
	return values[0]
}

// requestedConsent is the consent-relevant view of an authorization request, read from the
// runtime store by authorization_request_id.
type requestedConsent struct {
	claimsRequest map[string]any
	// AuthorizeScopes is the set of resource/authorize scopes that require consent.
	authorizeScopes []string
	// standardScopes is the set of standard OIDC scopes (e.g. "profile", "email") requested; the
	// claims they expand to (via the configured ScopeClaims mapping) are folded into the consent
	// hash and stored consent record alongside the explicit claims request.
	standardScopes []string
	// Prompt is the raw OIDC prompt parameter (space-delimited values).
	prompt string
}
