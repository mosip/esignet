package consent

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"slices"
	"sort"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	applog "github.com/mosip/esignet/internal/log"
)

const (
	// runtimeKeyAuthorizationRequestID and runtimeKeyClientID are the runtimeMetadata keys the
	// engine populates for the consent enforcer.
	runtimeKeyAuthorizationRequestID = "authorization_request_id"
	runtimeKeyClientID               = "current_client_id"

	// purpose-name prefixes the engine uses to classify a consent purpose's namespace.
	attributesPurposePrefix  = "attributes:"
	permissionsPurposePrefix = "permissions:"

	// consent prompt type discriminators read by the UI.
	promptTypeAttributes  = "attributes"
	promptTypePermissions = "permissions"

	// consent namespaces persisted on each element approval record.
	namespaceAttribute  = "attribute"
	namespacePermission = "permission"

	// consent record status for a freshly recorded consent.
	recordStatusActive = "ACTIVE"
)

// consentStore persists and retrieves consent records.
type consentStore interface {
	get(ctx context.Context, clientID, userID string) (*storedConsent, bool, error)
	save(ctx context.Context, c *storedConsent) error
	delete(ctx context.Context, clientID, userID string) error
}

// Service implements the consent-action decision logic of the Java esignet ConsentHelperService:
// it hashes the requested claims + authorize scopes and compares against the stored consent to
// decide between re-prompting (CAPTURE, a non-nil PromptData) and reusing existing consent
// (NOCAPTURE, a nil PromptData). It is the utility the engine's ConsentProvider implementation
// delegates to; it does not implement the provider interface itself.
type Service struct {
	store        consentStore
	runtimeStore providers.RuntimeStoreProvider
	logger       *applog.Logger
}

// NewService creates a consent Service. store persists consent records; runtimeStore is the
// engine's runtime store, from which the authorization request shared with the engine is read.
func NewService(store consentStore, runtimeStore providers.RuntimeStoreProvider) *Service {
	return &Service{
		store:        store,
		runtimeStore: runtimeStore,
		logger:       applog.GetLogger(),
	}
}

// readAuthRequest reads and decodes the consent-relevant view of the authorization request
// identified by authID from the runtime store shared with the ThunderID engine. The engine
// persists each authorization request under NamespaceAuthzReq keyed by the
// authorization_request_id (see the engine's authorizationRequestStore); there is no public API to
// fetch it, so it is read back through the same runtime store. The boolean is false (with a nil
// error) when no request exists for the id.
func (s *Service) readAuthRequest(ctx context.Context, authID string) (*requestedConsent, bool, error) {
	if authID == "" {
		return nil, false, nil
	}
	data, err := s.runtimeStore.Get(ctx, providers.NamespaceAuthzReq, authID)
	if err != nil {
		return nil, false, fmt.Errorf("read authorization request %q: %w", authID, err)
	}
	if data == nil {
		return nil, false, nil
	}
	req, err := decodeStored(data)
	if err != nil {
		return nil, false, err
	}
	return req, true, nil
}

// Resolve decides whether the user must be re-prompted for consent. It returns nil when the
// existing consent still covers the request (NOCAPTURE); otherwise it returns the consent prompt
// data for the claims/permissions that need consent (CAPTURE).
func (s *Service) Resolve(ctx context.Context, in ResolveInput) (*PromptData, error) {
	clientID := consentClientID(in.RuntimeMetadata, in.AppID)
	authID := in.RuntimeMetadata[runtimeKeyAuthorizationRequestID]

	req, found, err := s.readAuthRequest(ctx, authID)
	if err != nil {
		s.logger.Error("consent: read authorization request", applog.Error(err))
		return nil, codedError(ErrCodeAuthRequestRead, err)
	}
	if !found || req == nil {
		req = &requestedConsent{}
	}

	// Nothing requested at all -> no consent needed (Java NOCAPTURE with empty accepted set). Any
	// previously stored consent for this pair is now stale, so delete it (mirrors Java
	// ConsentHelperService.updateUserConsent deleting on NOCAPTURE with no requested claims/scopes).
	if !req.hasRequest() && len(in.EssentialAttributes) == 0 && len(in.OptionalAttributes) == 0 &&
		len(in.AuthorizedPermissions) == 0 {
		if err := s.store.delete(ctx, clientID, in.UserID); err != nil {
			s.logger.Error("consent: delete stale consent", applog.Error(err))
			return nil, codedError(ErrCodeConsentDelete, err)
		}
		return nil, nil
	}

	capture := func() (*PromptData, error) {
		prompt := s.buildPrompt(in.AppID, req, in.EssentialAttributes, in.OptionalAttributes,
			in.AuthorizedPermissions, in.AvailableAttributes)
		return prompt, nil
	}

	// Forced re-prompt or prompt=consent always captures.
	if in.ForceReprompt || promptRequestsConsent(req.Prompt) {
		return capture()
	}

	stored, ok, err := s.store.get(ctx, clientID, in.UserID)
	if err != nil {
		s.logger.Error("consent: lookup stored consent", applog.Error(err))
		return nil, codedError(ErrCodeConsentLookup, err)
	}
	if !ok || stored.isExpired(time.Now().UTC()) {
		return capture()
	}

	hash, err := hashRequestedConsent(req)
	if err != nil {
		s.logger.Error("consent: hash request", applog.Error(err))
		return nil, codedError(ErrCodeHash, err)
	}
	if hash == stored.Hash {
		return nil, nil // NOCAPTURE: request unchanged and consent still valid.
	}
	return capture()
}

// Record persists the user's consent decisions and returns the resulting consent record. If the
// user denied an essential claim, the record is still persisted and ErrEssentialConsentDenied is
// returned so the flow can stop.
func (s *Service) Record(ctx context.Context, in RecordInput) (*Record, error) {
	clientID := consentClientID(in.RuntimeMetadata, in.AppID)
	authID := in.RuntimeMetadata[runtimeKeyAuthorizationRequestID]

	req, found, err := s.readAuthRequest(ctx, authID)
	if err != nil {
		s.logger.Error("consent: read authorization request", applog.Error(err))
		return nil, codedError(ErrCodeAuthRequestRead, err)
	}
	if !found || req == nil {
		req = &requestedConsent{}
	}

	essentialSet := essentialClaimSet(req)
	acceptedClaims, permittedScopes, essentialDenied := splitDecisions(in.Decisions, essentialSet)

	hash, err := hashRequestedConsent(req)
	if err != nil {
		s.logger.Error("consent: hash request", applog.Error(err))
		return nil, codedError(ErrCodeHash, err)
	}

	now := time.Now().UTC()
	var expires sql.NullTime
	if in.ValidityPeriod > 0 {
		expires = sql.NullTime{Time: now.Add(time.Duration(in.ValidityPeriod) * time.Second), Valid: true}
	}

	record := &storedConsent{
		ID:                  uuid.NewString(),
		ClientID:            clientID,
		UserID:              in.UserID,
		Claims:              serializeClaims(req),
		AuthorizationScopes: serializeScopes(req.AuthorizeScopes),
		Hash:                hash,
		AcceptedClaims:      acceptedClaims,
		PermittedScopes:     permittedScopes,
		CreatedAt:           now,
		ExpiresAt:           expires,
	}
	if err := s.store.save(ctx, record); err != nil {
		s.logger.Error("consent: persist consent", applog.Error(err))
		return nil, codedError(ErrCodeConsentPersist, err)
	}

	if essentialDenied {
		return nil, ErrEssentialConsentDenied
	}
	return buildRecord(record.ID, in.AppID, in.Decisions), nil
}

// buildPrompt constructs the consent prompt for the requested attributes and permissions. It
// returns nil when, after filtering, there is nothing to prompt for.
func (s *Service) buildPrompt(
	appID string, req *requestedConsent,
	essentialAttributes, optionalAttributes, authorizedPermissions, availableAttributes []string,
) *PromptData {
	purposes := make([]PromptPurpose, 0, 2)

	essential, optional := classifyAttributes(req, essentialAttributes, optionalAttributes, availableAttributes)
	if len(essential) > 0 || len(optional) > 0 {
		purposes = append(purposes, PromptPurpose{
			PurposeName: attributesPurposePrefix + appID,
			Type:        promptTypeAttributes,
			Essential:   essential,
			Optional:    optional,
		})
	}

	if len(authorizedPermissions) > 0 {
		perms := append([]string(nil), authorizedPermissions...)
		sortFold(perms)
		purposes = append(purposes, PromptPurpose{
			PurposeName: permissionsPurposePrefix + appID,
			Type:        promptTypePermissions,
			Essential:   []string{},
			Optional:    perms,
		})
	}

	if len(purposes) == 0 {
		return nil
	}
	return &PromptData{Purposes: purposes}
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

// requestedClaims merges the userinfo and id_token claim entries, skipping the verified_claims
// member which is not an individual claim.
func requestedClaims(req *requestedConsent) map[string]any {
	out := make(map[string]any, len(req.UserInfo)+len(req.IDToken))
	for _, section := range []map[string]any{req.UserInfo, req.IDToken} {
		for name, v := range section {
			if name == "verified_claims" {
				continue
			}
			out[name] = v
		}
	}
	return out
}

// essentialClaimSet returns the set of claim names explicitly marked essential in the request.
func essentialClaimSet(req *requestedConsent) map[string]bool {
	set := map[string]bool{}
	for name, v := range requestedClaims(req) {
		if isEssentialClaim(v) {
			set[name] = true
		}
	}
	return set
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

// splitDecisions separates the user's element decisions into approved claim names and approved
// permission names (by purpose namespace) and reports whether any essential claim was denied.
func splitDecisions(decisions *Decisions,
	essentialSet map[string]bool) (acceptedClaims, permittedScopes []string, essentialDenied bool) {
	if decisions == nil {
		return nil, nil, false
	}
	for _, pd := range decisions.Purposes {
		isPermission := namespaceFromPurposeName(pd.PurposeName) == namespacePermission
		for _, ed := range pd.Elements {
			if ed.Approved {
				if isPermission {
					permittedScopes = append(permittedScopes, ed.Name)
				} else {
					acceptedClaims = append(acceptedClaims, ed.Name)
				}
			} else if essentialSet[ed.Name] {
				essentialDenied = true
			}
		}
	}
	sortFold(acceptedClaims)
	sortFold(permittedScopes)
	return acceptedClaims, permittedScopes, essentialDenied
}

// buildRecord constructs the consent Record from the persisted id and the user's decisions. Purpose
// names carry the namespace prefix the flow executor uses to derive consented attributes/permissions.
func buildRecord(id, appID string, decisions *Decisions) *Record {
	purposes := make([]RecordPurpose, 0)
	if decisions != nil {
		for _, pd := range decisions.Purposes {
			ns := namespaceFromPurposeName(pd.PurposeName)
			elements := make([]RecordElement, 0, len(pd.Elements))
			for _, ed := range pd.Elements {
				elements = append(elements, RecordElement{
					Name:           ed.Name,
					Namespace:      ns,
					IsUserApproved: ed.Approved,
				})
			}
			purposes = append(purposes, RecordPurpose{Name: pd.PurposeName, Elements: elements})
		}
	}
	return &Record{
		ID:       id,
		GroupID:  appID,
		Status:   recordStatusActive,
		Purposes: purposes,
	}
}

// namespaceFromPurposeName derives the consent namespace from a purpose name's prefix, mirroring
// the engine's convention.
func namespaceFromPurposeName(name string) string {
	switch {
	case strings.HasPrefix(name, permissionsPurposePrefix):
		return namespacePermission
	case strings.HasPrefix(name, attributesPurposePrefix):
		return namespaceAttribute
	default:
		return ""
	}
}

// consentClientID returns the client id used to key consent records, preferring the current client
// id from runtime metadata and falling back to the application id.
func consentClientID(runtimeMetadata map[string]string, appID string) string {
	if id := runtimeMetadata[runtimeKeyClientID]; id != "" {
		return id
	}
	return appID
}

// promptRequestsConsent reports whether the OIDC prompt parameter requests re-consent.
func promptRequestsConsent(prompt string) bool {
	return slices.Contains(strings.Fields(prompt), "consent")
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

// serializeClaims renders the requested claims sections for storage (audit/debug only).
func serializeClaims(req *requestedConsent) string {
	payload := map[string]any{
		"userinfo": orEmptyMap(req.UserInfo),
		"id_token": orEmptyMap(req.IDToken),
	}
	data, err := json.Marshal(payload)
	if err != nil {
		return "{}"
	}
	return string(data)
}

// serializeScopes renders the authorize scopes as a scope->essential(false) map for storage.
func serializeScopes(scopes []string) string {
	m := map[string]bool{}
	for _, s := range scopes {
		m[s] = false
	}
	data, err := json.Marshal(m)
	if err != nil {
		return "{}"
	}
	return string(data)
}

func orEmptyMap(m map[string]any) map[string]any {
	if m == nil {
		return map[string]any{}
	}
	return m
}
