/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"errors"
	"testing"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/consent"
)

func TestAttributeNames(t *testing.T) {
	if names := attributeNames(nil); names != nil {
		t.Fatalf("nil response should yield nil, got %v", names)
	}
	if names := attributeNames(&providers.AttributesResponse{}); names != nil {
		t.Fatalf("empty response should yield nil, got %v", names)
	}
	got := attributeNames(&providers.AttributesResponse{
		Attributes: map[string]*providers.AttributeResponse{"email": {}, "name": {}},
	})
	if len(got) != 2 {
		t.Fatalf("expected 2 names, got %v", got)
	}
}

func TestToPromptData(t *testing.T) {
	prompt := toPromptData(&consent.PromptData{Purposes: []consent.PromptPurpose{{
		PurposeName: "attributes:app1",
		Type:        "attributes",
		Essential:   []string{"email"},
		Optional:    []string{"name"},
	}}})

	if len(prompt.Purposes) != 1 {
		t.Fatalf("expected one purpose, got %+v", prompt.Purposes)
	}
	p := prompt.Purposes[0]
	if p.PurposeName != "attributes:app1" || p.Type != "attributes" {
		t.Fatalf("unexpected purpose: %+v", p)
	}
	if len(p.Essential) != 1 || p.Essential[0].Name != "email" {
		t.Fatalf("unexpected essential elements: %+v", p.Essential)
	}
	if len(p.Optional) != 1 || p.Optional[0].Name != "name" {
		t.Fatalf("unexpected optional elements: %+v", p.Optional)
	}
}

func TestToDomainDecisions(t *testing.T) {
	if d := toDomainDecisions(nil); d != nil {
		t.Fatalf("nil decisions should stay nil, got %+v", d)
	}

	d := toDomainDecisions(&providers.ConsentDecisions{Purposes: []providers.PurposeDecision{{
		PurposeName: "attributes:app1",
		Approved:    true,
		Elements:    []providers.ElementDecision{{Name: "email", Approved: true}},
	}}})
	if d == nil || len(d.Purposes) != 1 {
		t.Fatalf("expected one purpose, got %+v", d)
	}
	if d.Purposes[0].PurposeName != "attributes:app1" || !d.Purposes[0].Approved {
		t.Fatalf("unexpected purpose: %+v", d.Purposes[0])
	}
	el := d.Purposes[0].Elements
	if len(el) != 1 || el[0].Name != "email" || !el[0].Approved {
		t.Fatalf("unexpected elements: %+v", el)
	}
}

func TestToConsent(t *testing.T) {
	record := toConsent(&consent.Record{
		ID:      "consent-1",
		GroupID: "app1",
		Status:  "ACTIVE",
		Purposes: []consent.RecordPurpose{{
			Name: "attributes:app1",
			Elements: []consent.RecordElement{
				{Name: "email", Namespace: "attribute", IsUserApproved: true},
			},
		}},
	})

	if record.ID != "consent-1" || record.GroupID != "app1" || record.Status != providers.ConsentStatusActive {
		t.Fatalf("unexpected consent: %+v", record)
	}
	if len(record.Purposes) != 1 || record.Purposes[0].Name != "attributes:app1" {
		t.Fatalf("unexpected purposes: %+v", record.Purposes)
	}
	el := record.Purposes[0].Elements
	if len(el) != 1 || el[0].Name != "email" || el[0].Namespace != providers.NamespaceAttribute || !el[0].IsUserApproved {
		t.Fatalf("unexpected element approval: %+v", el)
	}
}

func TestToServiceError(t *testing.T) {
	if svcErr := toServiceError(nil); svcErr != nil {
		t.Fatalf("nil error should map to nil, got %+v", svcErr)
	}

	svcErr := toServiceError(consent.ErrEssentialConsentDenied)
	if svcErr == nil || svcErr.Type != common.ClientErrorType || svcErr.Code != consentEssentialDeniedCode {
		t.Fatalf("essential denied should map to client error %q, got %+v", consentEssentialDeniedCode, svcErr)
	}

	svcErr = toServiceError(&consent.Error{Code: consent.ErrCodeConsentLookup})
	if svcErr == nil || svcErr.Type != common.ServerErrorType || svcErr.Code != consent.ErrCodeConsentLookup {
		t.Fatalf("coded error should map to server error %q, got %+v", consent.ErrCodeConsentLookup, svcErr)
	}

	svcErr = toServiceError(errors.New("boom"))
	if svcErr == nil || svcErr.Type != common.ServerErrorType {
		t.Fatalf("generic error should map to a server error, got %+v", svcErr)
	}
}
