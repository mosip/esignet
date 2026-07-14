/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"database/sql"
	"errors"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/consent"
)

// consentEssentialDeniedCode is the engine's error code that maps to "essential consent denied";
// the flow executor matches on this exact value.
const consentEssentialDeniedCode = "AUTH-CES-1006"

// consentEnforcer implements providers.ConsentProvider. It is a thin adapter: it translates the
// engine's provider types to and from the consent package's domain types and delegates the consent
// decision logic to the consent service.
type consentEnforcer struct {
	svc *consent.Service
}

// NewConsentEnforcer builds the esignet consent enforcer. It persists consent records in Postgres
// and reads authorization requests (for the requested OIDC claims) from the engine's runtime store,
// which must be the same RuntimeStoreProvider configured on the engine so the authorization-request
// keys resolve.
func NewConsentEnforcer(db *sql.DB, runtimeStore providers.RuntimeStoreProvider) providers.ConsentProvider {
	return &consentEnforcer{svc: consent.NewService(consent.NewConsentStore(db), runtimeStore)}
}

// ResolveConsent decides whether the user must be re-prompted for consent, returning nil when the
// existing consent still covers the request (NOCAPTURE).
func (e *consentEnforcer) ResolveConsent(
	ctx context.Context, ouID, appID, appName, userID string,
	essentialAttributes, optionalAttributes, authorizedPermissions []string,
	availableAttributes *providers.AttributesResponse, forceReprompt bool,
	runtimeMetadata map[string]string,
) (*providers.ConsentPromptData, *common.ServiceError) {
	prompt, err := e.svc.Resolve(ctx, consent.ResolveInput{
		OUID:                  ouID,
		AppID:                 appID,
		AppName:               appName,
		UserID:                userID,
		EssentialAttributes:   essentialAttributes,
		OptionalAttributes:    optionalAttributes,
		AuthorizedPermissions: authorizedPermissions,
		AvailableAttributes:   attributeNames(availableAttributes),
		ForceReprompt:         forceReprompt,
		RuntimeMetadata:       runtimeMetadata,
	})
	if err != nil {
		return nil, toServiceError(err)
	}
	if prompt == nil {
		return nil, nil
	}
	return toPromptData(prompt), nil
}

// RecordConsent persists the user's consent decisions and returns the resulting consent record. If
// the user denied an essential attribute, the essential-consent-denied error is returned.
func (e *consentEnforcer) RecordConsent(
	ctx context.Context, ouID, appID, userID string,
	decisions *providers.ConsentDecisions, sessionToken string, validityPeriod int64,
	runtimeMetadata map[string]string,
) (*providers.Consent, *common.ServiceError) {
	record, err := e.svc.Record(ctx, consent.RecordInput{
		OUID:            ouID,
		AppID:           appID,
		UserID:          userID,
		Decisions:       toDomainDecisions(decisions),
		SessionToken:    sessionToken,
		ValidityPeriod:  validityPeriod,
		RuntimeMetadata: runtimeMetadata,
	})
	if err != nil {
		return nil, toServiceError(err)
	}
	return toConsent(record), nil
}

// attributeNames returns the attribute names present in the response, or nil when none.
func attributeNames(available *providers.AttributesResponse) []string {
	if available == nil || len(available.Attributes) == 0 {
		return nil
	}
	names := make([]string, 0, len(available.Attributes))
	for name := range available.Attributes {
		names = append(names, name)
	}
	return names
}

// toPromptData translates the domain prompt into the engine's ConsentPromptData.
func toPromptData(p *consent.PromptData) *providers.ConsentPromptData {
	purposes := make([]providers.ConsentPurposePrompt, 0, len(p.Purposes))
	for _, pp := range p.Purposes {
		purposes = append(purposes, providers.ConsentPurposePrompt{
			PurposeName: pp.PurposeName,
			Type:        pp.Type,
			Essential:   toPromptElements(pp.Essential),
			Optional:    toPromptElements(pp.Optional),
		})
	}
	return &providers.ConsentPromptData{Purposes: purposes}
}

// toPromptElements maps element names to prompt elements.
func toPromptElements(names []string) []providers.PromptElement {
	elements := make([]providers.PromptElement, 0, len(names))
	for _, n := range names {
		elements = append(elements, providers.PromptElement{Name: n})
	}
	return elements
}

// toDomainDecisions translates the engine's consent decisions into the domain type. It returns nil
// when decisions is nil so the service applies its nil-decisions behaviour.
func toDomainDecisions(decisions *providers.ConsentDecisions) *consent.Decisions {
	if decisions == nil {
		return nil
	}
	purposes := make([]consent.PurposeDecision, 0, len(decisions.Purposes))
	for _, pd := range decisions.Purposes {
		elements := make([]consent.ElementDecision, 0, len(pd.Elements))
		for _, ed := range pd.Elements {
			elements = append(elements, consent.ElementDecision{Name: ed.Name, Approved: ed.Approved})
		}
		purposes = append(purposes, consent.PurposeDecision{
			PurposeName: pd.PurposeName,
			Approved:    pd.Approved,
			Elements:    elements,
		})
	}
	return &consent.Decisions{Purposes: purposes}
}

// toConsent translates the persisted consent record into the engine's Consent.
func toConsent(r *consent.Record) *providers.Consent {
	purposes := make([]providers.ConsentPurposeItem, 0, len(r.Purposes))
	for _, rp := range r.Purposes {
		elements := make([]providers.ConsentElementApproval, 0, len(rp.Elements))
		for _, re := range rp.Elements {
			elements = append(elements, providers.ConsentElementApproval{
				Name:           re.Name,
				Namespace:      providers.Namespace(re.Namespace),
				IsUserApproved: re.IsUserApproved,
			})
		}
		purposes = append(purposes, providers.ConsentPurposeItem{Name: rp.Name, Elements: elements})
	}
	return &providers.Consent{
		ID:       r.ID,
		GroupID:  r.GroupID,
		Status:   providers.ConsentStatus(r.Status),
		Purposes: purposes,
	}
}

// toServiceError maps a consent service error onto the engine's ServiceError. The essential-denied
// sentinel becomes a client error with the engine's contract code; a coded consent error carries
// its esignet error code as a server error; anything else is a generic server error.
func toServiceError(err error) *common.ServiceError {
	if err == nil {
		return nil
	}
	if errors.Is(err, consent.ErrEssentialConsentDenied) {
		return &common.ServiceError{Type: common.ClientErrorType, Code: consentEssentialDeniedCode}
	}
	var cErr *consent.Error
	if errors.As(err, &cErr) {
		return &common.ServiceError{Type: common.ServerErrorType, Code: cErr.Code}
	}
	return &common.ServiceError{Type: common.ServerErrorType, Code: consent.ErrCodeConsentPersist}
}
