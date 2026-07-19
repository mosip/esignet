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
	"sort"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/consentmgmt"
	applog "github.com/mosip/esignet/internal/log"
)

const (
	// runtimeKeyAuthorizationRequestID and runtimeKeyClientID are the runtimeMetadata keys the
	// engine populates for the consent enforcer.
	runtimeKeyAuthorizationRequestID = "authorization_request_id"
	runtimeKeyClientID               = "current_client_id"

	// purpose-name prefixes the engine uses to classify a consent purpose's namespace.
	attributesPurpose  = "attributes:"
	permissionsPurpose = "permissions:"

	// consent prompt type discriminators read by the UI.
	promptTypeAttributes  = "attributes"
	promptTypePermissions = "permissions"
)

type consentProvider struct {
	consentSvc   *consentmgmt.Service
	config       *config.AppConfig
	runtimeStore providers.RuntimeStoreProvider
	logger       *applog.Logger
}

// NewConsentProvider builds a providers.ConsentProvider backed by consentSvc.
func NewConsentProvider(consentSvc *consentmgmt.Service, config *config.AppConfig,
	runtimeStore providers.RuntimeStoreProvider) providers.ConsentProvider {

	return &consentProvider{consentSvc: consentSvc,
		config:       config,
		runtimeStore: runtimeStore,
		logger:       applog.GetLogger().Named("consentProvider"),
	}
}

func (p *consentProvider) ResolveConsent(ctx context.Context, _, appID string, _, userID string,
	_, _, authorizedPermissions []string,
	_ *providers.AttributesResponse, forceReprompt bool,
	runtimeMetadata map[string]string) (
	*providers.ConsentPromptData, *common.ServiceError) {

	clientID := runtimeMetadata[runtimeKeyClientID]
	consentRecord, err := p.consentSvc.FetchRecord(ctx, clientID, userID)
	if err != nil {
		p.logger.Error("Failed to read consent record", applog.Error(err))
		return nil, clientError("consent_fetch_failed", err)
	}

	req, err := p.readAuthRequest(ctx, runtimeMetadata[runtimeKeyAuthorizationRequestID])
	if err != nil {
		p.logger.Error("Failed to read auth request", applog.Error(err))
		return nil, clientError("consent_record_failed", err)
	}
	if req == nil {
		p.logger.Error("Read auth request is nil", applog.Error(err))
		return nil, clientError("consent_record_failed", err)
	}
	essentialAttributes, optionalAttributes := consentmgmt.ExtractAttributes(req.claimsRequest)

	if forceReprompt {
		p.logger.Warn("Force reprompt consent")
		return p.buildPrompt(appID, req, essentialAttributes, optionalAttributes,
			authorizedPermissions, []string{}), nil
	}

	if consentRecord == nil {
		p.logger.Debug("No stored consent found, prompt consent")
		return p.buildPrompt(appID, req, essentialAttributes, optionalAttributes,
			authorizedPermissions, []string{}), nil
	}

	if consentRecord.IsExpired(time.Now().UTC()) {
		p.logger.Warn("Expired consent found! reprompt consent")
		return p.buildPrompt(appID, req, essentialAttributes, optionalAttributes,
			authorizedPermissions, []string{}), nil
	}

	hash := p.getConsentRequestHash(ctx, req)
	if hash == "" {
		return nil, clientError("consent_check_failed", nil)
	}

	if consentRecord.Hash == hash {
		p.logger.Debug("Requested consent and stored consent match, skip consent prompt")
		return nil, nil
	}

	return p.buildPrompt(appID, req, essentialAttributes, optionalAttributes,
		authorizedPermissions, []string{}), nil
}

// RecordConsent records the user's consent decisions and returns the persisted consent record.
// If the user denied any essential attribute, ErrorEssentialConsentDenied is returned.
func (p *consentProvider) RecordConsent(ctx context.Context, _, appID, userID string,
	decisions *providers.ConsentDecisions, _ string, validityPeriod int64,
	runtimeMetadata map[string]string) (
	*providers.Consent, *common.ServiceError) {
	clientID := runtimeMetadata[runtimeKeyClientID]

	req, err := p.readAuthRequest(ctx, runtimeMetadata[runtimeKeyAuthorizationRequestID])
	if err != nil {
		p.logger.Error("Failed to read auth request", applog.Error(err))
		return nil, clientError("consent_record_failed", err)
	}
	if req == nil {
		p.logger.Error("Read auth request is nil", applog.Error(err))
		return nil, clientError("consent_record_failed", err)
	}

	// TODO check if any essential attribute is denied, ErrorEssentialConsentDenied should be returned.
	var acceptedClaims, permittedScopes []string
	if decisions != nil {
		for _, purpose := range decisions.Purposes {
			for _, element := range purpose.Elements {
				if !element.Approved {
					continue
				}
				if strings.HasPrefix(purpose.PurposeName, attributesPurpose) {
					acceptedClaims = append(acceptedClaims, element.Name)
				}
				if strings.HasPrefix(purpose.PurposeName, permissionsPurpose) {
					permittedScopes = append(permittedScopes, element.Name)
				}
			}
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
		Claims:              req.claimsRequest,
		AuthorizationScopes: req.authorizeScopes,
		AcceptedClaims:      acceptedClaims,
		PermittedScopes:     permittedScopes,
		CreatedAt:           now,
		ExpiresAt:           expiresAt,
	}
	if err := p.consentSvc.SaveRecord(ctx, consentRecord); err != nil {
		p.logger.Error("Failed to save consent record", applog.Error(err))
		return nil, clientError("consent_persist_failed", err)
	}

	return buildRecord(consentRecord.ID, appID, decisions), nil
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

func (p *consentProvider) getConsentRequestHash(_ context.Context, req *requestedConsent) string {
	hash, err := requestHash(req.claimsRequest, req.authorizeScopes)
	if err != nil {
		p.logger.Error("Failed to hash the claims request", applog.Error(err))
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

// readAuthRequest reads and decodes the consent-relevant view of the authorization request
// identified by authID from the runtime store shared with the ThunderID engine. The engine
// persists each authorization request under NamespaceAuthzReq keyed by the
// authorization_request_id (see the engine's authorizationRequestStore); there is no public API to
// fetch it, so it is read back through the same runtime store.
func (p *consentProvider) readAuthRequest(ctx context.Context, authID string) (*requestedConsent, error) {
	if authID == "" {
		return nil, nil
	}
	data, err := p.runtimeStore.Get(ctx, providers.NamespaceAuthzReq, authID)
	if err != nil {
		return nil, fmt.Errorf("read authorization request %q: %w", authID, err)
	}
	if data == nil {
		return nil, nil
	}
	req, err := decodeStored(data)
	if err != nil {
		return nil, err
	}
	return req, nil
}

// decodeStored unmarshals the engine's stored authorization-request form into a requestedConsent.
func decodeStored(data []byte) (*requestedConsent, error) {
	var raw authReqContext
	if err := json.Unmarshal(data, &raw); err != nil {
		return nil, fmt.Errorf("unmarshal authorization request: %w", err)
	}
	return &requestedConsent{
		claimsRequest:   raw.OAuthParameters.ClaimsRequest,
		authorizeScopes: raw.OAuthParameters.PermissionScopes,
		prompt:          raw.OAuthParameters.Prompt,
	}, nil
}

// authReqContext mirrors the consent-relevant fields of the engine's stored authorization request.
// The field names match the engine's serialized OAuthParameters (no json tags on the source struct,
// so keys are the Go field names); ClaimsRequest is decoded generically as it is stored in the OIDC
// claims wire shape ({"userinfo":{...},"id_token":{...}}).
type authReqContext struct {
	OAuthParameters struct {
		ClientID         string         `json:"ClientID"`
		Prompt           string         `json:"Prompt"`
		StandardScopes   []string       `json:"StandardScopes"`
		PermissionScopes []string       `json:"PermissionScopes"`
		ClaimsRequest    map[string]any `json:"ClaimsRequest"`
	} `json:"OAuthParameters"`
}

// requestedConsent is the consent-relevant view of an authorization request, read from the
// runtime store by authorization_request_id.
type requestedConsent struct {
	claimsRequest map[string]any
	// AuthorizeScopes is the set of resource/authorize scopes that require consent.
	authorizeScopes []string
	// Prompt is the raw OIDC prompt parameter (space-delimited values).
	prompt string
}
