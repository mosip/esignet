/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package sunbird

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

const sunbirdIndividualIDKey = "username"

// errSunbirdKBIAuthFailed signals a registry lookup that did not match exactly one
// entity, i.e. an authentication failure (as opposed to a transport/server error).
var errSunbirdKBIAuthFailed = errors.New("authentication failed")

type sunbirdFieldDetail struct {
	ID     string `json:"id"`
	Type   string `json:"type"`
	Format string `json:"format"`
}

type sunbirdSearchFilter struct {
	Eq string `json:"eq"`
}

type sunbirdSearchRequest struct {
	Filters map[string]sunbirdSearchFilter `json:"filters"`
}

type sunbirdAuthnProvider struct {
	cfg    Config
	client *http.Client
	// kbiFieldIDs are the KBI credential fields (from SUNBIRD_FIELD_DETAILS,
	// excluding IDField) parsed once at construction and reused per request.
	kbiFieldIDs []string
}

// NewSunbirdAuthnProvider creates a SunbirdRC registry-backed host.AuthnProvider.
// It validates the config and parses the KBI field details once, returning an
// error when SearchURL is unset or no KBI field other than IDField is configured.
func NewSunbirdAuthnProvider(httpClient *http.Client) (shared.ConsolidatedAuthnProvider, error) {
	cfg := LoadConfig()
	if err := cfg.Validate(); err != nil {
		return nil, err
	}

	fields, err := parseSunbirdFieldDetails(cfg.FieldDetails)
	if err != nil {
		return nil, err
	}
	kbiFieldIDs := make([]string, 0, len(fields))
	for _, f := range fields {
		if f.ID == cfg.IDField {
			continue
		}
		kbiFieldIDs = append(kbiFieldIDs, f.ID)
	}
	if len(kbiFieldIDs) == 0 {
		return nil, errors.New("SUNBIRD_FIELD_DETAILS must define at least one KBI field other than the individual ID field")
	}

	return &sunbirdAuthnProvider{
		cfg:         cfg,
		client:      httpClient,
		kbiFieldIDs: kbiFieldIDs,
	}, nil
}

func (p *sunbirdAuthnProvider) Authenticate(ctx context.Context, identifiers, credentials map[string]interface{},
	_ *providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {

	individualID, ok := identifiers[sunbirdIndividualIDKey].(string)
	if !ok || individualID == "" {
		return nil, shared.InvalidIndividualIDError
	}

	kbiFields := make(map[string]string, len(p.kbiFieldIDs))
	for _, id := range p.kbiFieldIDs {
		val, ok := credentials[id].(string)
		if !ok || val == "" {
			return nil, shared.InvalidRequestError
		}
		kbiFields[id] = val
	}

	entityID, err := p.validateKBI(ctx, individualID, kbiFields)
	if err != nil {
		if errors.Is(err, errSunbirdKBIAuthFailed) {
			return nil, shared.InvalidRequestError
		}
		return nil, shared.AuthenticationFailedError
	}

	return &providers.AuthnResult{
		EntityReferenceToken: entityID,
		AttributeToken:       entityID,
	}, nil
}

func (p *sunbirdAuthnProvider) GetAttributes(ctx context.Context, attributeToken any, consentedAttributes *providers.RequestedAttributes,
	_ *providers.GetAttributesMetadata) (*providers.AttributesResponse, *common.ServiceError) {

	if consentedAttributes == nil {
		return nil, shared.InvalidRequestError
	}

	entityData, err := p.fetchEntityData(ctx, attributeToken.(string))
	if err != nil {
		return nil, shared.InvalidRequestError
	}

	mappedClaims := buildSunbirdMappedClaims(ctx, entityData, p.cfg.ClaimsMapping)

	mappedClaimsMap := make(map[string]*providers.AttributeResponse, len(mappedClaims))
	for claim, value := range mappedClaims {
		mappedClaimsMap[claim] = &providers.AttributeResponse{Value: value}
	}

	return &providers.AttributesResponse{Attributes: mappedClaimsMap}, nil
}

func (p *sunbirdAuthnProvider) GetEntityReference(_ context.Context, entityReferenceToken any) (*providers.EntityReference,
	*common.ServiceError) {
	psut, ok := entityReferenceToken.(string)
	if !ok || psut == "" {
		return nil, shared.AuthenticationFailedError
	}
	return &providers.EntityReference{EntityID: psut}, nil
}

func (p *sunbirdAuthnProvider) InitiateAuthentication(_ context.Context, _ string, _ any,
	_ *providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (p *sunbirdAuthnProvider) InitiateEnrollment(_ context.Context, _ string, _ any,
	_ *providers.AuthnMetadata) (any, *common.ServiceError) {
	return nil, nil
}

func (p *sunbirdAuthnProvider) Enroll(_ context.Context, _, _ map[string]interface{},
	_ *providers.AuthnMetadata) (*providers.AuthnResult, *common.ServiceError) {
	return nil, nil
}

func (p *sunbirdAuthnProvider) SendOTP(_ context.Context, _ map[string]interface{},
	_ *providers.AuthnMetadata) (*shared.SendOTPResult, *common.ServiceError) {
	return nil, shared.NotImplementedError
}

func (p *sunbirdAuthnProvider) validateKBI(ctx context.Context, individualID string, kbiFields map[string]string) (string, error) {

	filters := make(map[string]sunbirdSearchFilter, len(kbiFields)+1)
	filters[p.cfg.IDField] = sunbirdSearchFilter{Eq: individualID}
	for fieldID, fieldValue := range kbiFields {
		filters[fieldID] = sunbirdSearchFilter{Eq: fieldValue}
	}

	reqBody, err := json.Marshal(sunbirdSearchRequest{Filters: filters})
	if err != nil {
		return "", fmt.Errorf("failed to marshal registry search request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, p.cfg.SearchURL, bytes.NewReader(reqBody))
	if err != nil {
		return "", fmt.Errorf("failed to build registry search request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	resp, err := p.client.Do(req)
	if err != nil {
		return "", fmt.Errorf("registry search request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		return "", fmt.Errorf("registry search returned status %d", resp.StatusCode)
	}

	var results []map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&results); err != nil {
		return "", fmt.Errorf("failed to decode registry search response: %w", err)
	}

	if len(results) != 1 {
		applog.GetLogger().Debug(ctx, "sunbird registry search did not match exactly one entity",
			applog.Int("matches", len(results)))
		return "", errSunbirdKBIAuthFailed
	}

	entityIDVal, ok := results[0][p.cfg.EntityIDField]
	if !ok {
		return "", fmt.Errorf("entity_id_field %q not found in registry response", p.cfg.EntityIDField)
	}
	entityID, ok := entityIDVal.(string)
	if !ok || entityID == "" {
		return "", fmt.Errorf("entity_id_field %q has invalid value in registry response", p.cfg.EntityIDField)
	}

	return entityID, nil
}

func (p *sunbirdAuthnProvider) fetchEntityData(ctx context.Context, entityID string) (map[string]interface{}, error) {
	url := fmt.Sprintf("%s/%s", p.cfg.EntityURL, entityID)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to build entity fetch request: %w", err)
	}
	req.Header.Set("Accept", "application/json")

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("entity fetch request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		return nil, fmt.Errorf("entity fetch returned status %d", resp.StatusCode)
	}

	var entityData map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&entityData); err != nil {
		return nil, fmt.Errorf("failed to decode entity fetch response: %w", err)
	}

	return entityData, nil
}

func parseSunbirdFieldDetails(jsonStr string) ([]sunbirdFieldDetail, error) {
	if jsonStr == "" {
		return nil, errors.New("SUNBIRD_FIELD_DETAILS is empty")
	}
	var fields []sunbirdFieldDetail
	if err := json.Unmarshal([]byte(jsonStr), &fields); err != nil {
		return nil, fmt.Errorf("invalid SUNBIRD_FIELD_DETAILS JSON: %w", err)
	}
	return fields, nil
}

func parseSunbirdClaimsMapping(jsonStr string) (map[string]string, error) {
	var mapping map[string]string
	if err := json.Unmarshal([]byte(jsonStr), &mapping); err != nil {
		return nil, fmt.Errorf("invalid SUNBIRD_CLAIMS_MAPPING JSON: %w", err)
	}
	return mapping, nil
}

// buildSunbirdMappedClaims maps registry entity fields to OIDC claims using
// claimsMappingJSON ({oidcClaim: registryField}). It fails closed: when the
// mapping is empty or malformed, no claims are released, so unmapped registry
// fields are never disclosed as OIDC attributes. This mirrors the upstream Java
// SunbirdRC plugin, which only emits claims that have an explicit mapping.
func buildSunbirdMappedClaims(ctx context.Context, entityData map[string]interface{},
	claimsMappingJSON string) map[string]interface{} {

	if claimsMappingJSON == "" {
		return map[string]interface{}{}
	}

	claimsMapping, err := parseSunbirdClaimsMapping(claimsMappingJSON)
	if err != nil {
		applog.GetLogger().Warn(ctx, "failed to parse SUNBIRD_CLAIMS_MAPPING; dropping all claims to avoid disclosing raw registry fields",
			applog.Error(err))
		return map[string]interface{}{}
	}

	mapped := make(map[string]interface{}, len(claimsMapping))
	for oidcClaim, registryField := range claimsMapping {
		if val, ok := entityData[registryField]; ok {
			mapped[oidcClaim] = val
		}
	}
	return mapped
}
