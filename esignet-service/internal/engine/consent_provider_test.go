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
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/consentmgmt"
	"github.com/mosip/esignet/internal/consentmgmt/db"
)

type consentStubQuerier struct {
	db.Querier

	getRow db.ConsentDetail
	getErr error

	upsertErr        error
	insertHistoryErr error

	lastUpsert  db.UpsertConsentParams
	lastHistory db.InsertConsentHistoryParams
}

func (q *consentStubQuerier) GetConsent(_ context.Context, _ db.GetConsentParams) (db.ConsentDetail, error) {
	return q.getRow, q.getErr
}

func (q *consentStubQuerier) InsertConsentHistory(_ context.Context, arg db.InsertConsentHistoryParams) error {
	q.lastHistory = arg
	return q.insertHistoryErr
}

func (q *consentStubQuerier) UpsertConsent(_ context.Context, arg db.UpsertConsentParams) error {
	q.lastUpsert = arg
	return q.upsertErr
}

func newConsentTestProvider(q db.Querier, cfg *config.AppConfig) *consentProvider {
	if cfg == nil {
		cfg = &config.AppConfig{}
	}
	svc := consentmgmt.NewServiceWithQuerier(q)
	return NewConsentProvider(svc, cfg).(*consentProvider)
}

func clientIDMeta(clientID string) map[string][]string {
	return map[string][]string{runtimeKeyClientID: {clientID}}
}

func matchingConsentHash(ts *ConsentProviderTestSuite) string {
	hash, err := consentmgmt.HashRequestedConsent(
		consentmgmt.NormalizeClaims(map[string]any{}),
		consentmgmt.NormalizeAuthorizationScopes([]string{}),
	)
	ts.Require().NoError(err)
	return hash
}

func (ts *ConsentProviderTestSuite) TestNewConsentProvider() {
	p := newConsentTestProvider(&consentStubQuerier{getErr: sql.ErrNoRows}, nil)
	ts.Require().NotNil(p)
}

func (ts *ConsentProviderTestSuite) TestResolveConsent_FetchRecordError() {
	p := newConsentTestProvider(&consentStubQuerier{getErr: errors.New("db down")}, nil)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou-1", "app-1", "App One", "user-1",
		nil, nil, nil, nil, false, clientIDMeta("client-1"))
	ts.Require().Nil(prompt)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("consent_fetch_failed", svcErr.Code)
}

func (ts *ConsentProviderTestSuite) TestResolveConsent_NoStoredConsent_BuildsPrompt() {
	p := newConsentTestProvider(&consentStubQuerier{getErr: sql.ErrNoRows}, nil)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou-1", "app-1", "App One", "user-1",
		[]string{"name"}, []string{"email"}, []string{"perm.write", "perm.read"}, nil, false,
		clientIDMeta("client-1"))
	ts.Require().Nil(svcErr)
	ts.Require().NotNil(prompt)
	ts.Require().Len(prompt.Purposes, 2)

	attrs := prompt.Purposes[0]
	ts.Require().Equal("attributes:app-1", attrs.PurposeName)
	ts.Require().Equal(promptTypeAttributes, attrs.Type)
	ts.Require().Equal([]providers.PromptElement{{Name: "name"}}, attrs.Essential)
	ts.Require().Equal([]providers.PromptElement{{Name: "email"}}, attrs.Optional)

	perms := prompt.Purposes[1]
	ts.Require().Equal("permissions:app-1", perms.PurposeName)
	ts.Require().Equal(promptTypePermissions, perms.Type)
	ts.Require().Equal([]providers.PromptElement{{Name: "perm.read"}, {Name: "perm.write"}}, perms.Essential)
	ts.Require().Empty(perms.Optional)
}

func (ts *ConsentProviderTestSuite) TestResolveConsent_NothingToPrompt_ReturnsNil() {
	p := newConsentTestProvider(&consentStubQuerier{getErr: sql.ErrNoRows}, nil)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou-1", "app-1", "App One", "user-1",
		nil, nil, nil, nil, false, clientIDMeta("client-1"))
	ts.Require().Nil(svcErr)
	ts.Require().Nil(prompt)
}

func (ts *ConsentProviderTestSuite) TestResolveConsent_ForceReprompt() {
	hash := matchingConsentHash(ts)
	q := &consentStubQuerier{getRow: db.ConsentDetail{
		ID: "consent-1", ClientID: "client-1", PsuToken: "user-1",
		Claims: "{}", AuthorizationScopes: "{}",
		Hash: sql.NullString{String: hash, Valid: true}, CrDtimes: time.Now().UTC(),
	}}
	p := newConsentTestProvider(q, nil)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou-1", "app-1", "App One", "user-1",
		[]string{"name"}, nil, nil, nil, true, clientIDMeta("client-1"))
	ts.Require().Nil(svcErr)
	ts.Require().NotNil(prompt, "force reprompt should re-prompt even when stored consent matches")
}

func (ts *ConsentProviderTestSuite) TestResolveConsent_ExpiredConsent_Reprompts() {
	hash := matchingConsentHash(ts)
	q := &consentStubQuerier{getRow: db.ConsentDetail{
		ID: "consent-1", ClientID: "client-1", PsuToken: "user-1",
		Claims: "{}", AuthorizationScopes: "{}",
		Hash:         sql.NullString{String: hash, Valid: true},
		CrDtimes:     time.Now().UTC().Add(-2 * time.Hour),
		ExpireDtimes: sql.NullTime{Time: time.Now().UTC().Add(-time.Hour), Valid: true},
	}}
	p := newConsentTestProvider(q, nil)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou-1", "app-1", "App One", "user-1",
		[]string{"name"}, nil, nil, nil, false, clientIDMeta("client-1"))
	ts.Require().Nil(svcErr)
	ts.Require().NotNil(prompt, "expired consent should be re-prompted")
}

func (ts *ConsentProviderTestSuite) TestResolveConsent_HashMatches_SkipsPrompt() {
	hash := matchingConsentHash(ts)
	q := &consentStubQuerier{getRow: db.ConsentDetail{
		ID: "consent-1", ClientID: "client-1", PsuToken: "user-1",
		Claims: "{}", AuthorizationScopes: "{}",
		Hash:     sql.NullString{String: hash, Valid: true},
		CrDtimes: time.Now().UTC(),
	}}
	p := newConsentTestProvider(q, nil)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou-1", "app-1", "App One", "user-1",
		nil, nil, nil, nil, false, clientIDMeta("client-1"))
	ts.Require().Nil(svcErr)
	ts.Require().Nil(prompt)
}

func (ts *ConsentProviderTestSuite) TestResolveConsent_HashMismatch_ReturnsPrompt() {
	q := &consentStubQuerier{getRow: db.ConsentDetail{
		ID: "consent-1", ClientID: "client-1", PsuToken: "user-1",
		Claims: "{}", AuthorizationScopes: "{}",
		Hash:     sql.NullString{String: "stale-hash", Valid: true},
		CrDtimes: time.Now().UTC(),
	}}
	p := newConsentTestProvider(q, nil)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou-1", "app-1", "App One", "user-1",
		[]string{"name"}, nil, nil, nil, false, clientIDMeta("client-1"))
	ts.Require().Nil(svcErr)
	ts.Require().NotNil(prompt)
}

func (ts *ConsentProviderTestSuite) TestRecordConsent_Success() {
	q := &consentStubQuerier{}
	cfg := &config.AppConfig{ResourceServers: []config.ResourceServerConfig{
		{ID: "rs-1", Identifier: "https://rs.example.com", Scopes: map[string]string{"perm.read": "read"}},
	}}
	p := newConsentTestProvider(q, cfg)

	meta := clientIDMeta("client-1")
	meta[runtimeKeyClaims] = []string{`{"userinfo":{"email":null,"phone":null}}`}
	meta[runtimeKeyScopes] = []string{"openid perm.read"}

	decisions := &providers.ConsentDecisions{
		Purposes: []providers.PurposeDecision{
			{
				PurposeName: "attributes:app-1",
				Approved:    true,
				Elements: []providers.ElementDecision{
					{Name: "email", Approved: true},
					{Name: "phone", Approved: false},
				},
			},
			{
				PurposeName: "permissions:app-1",
				Approved:    true,
				Elements: []providers.ElementDecision{
					{Name: "perm.read", Approved: true},
				},
			},
		},
	}

	consent, svcErr := p.RecordConsent(context.Background(), "ou-1", "app-1", "user-1",
		decisions, "session-token", 3600, meta)
	ts.Require().Nil(svcErr)
	ts.Require().NotNil(consent)
	ts.Require().Equal("app-1", consent.GroupID)
	ts.Require().Equal(providers.ConsentStatusActive, consent.Status)
	ts.Require().Len(consent.Purposes, 2)

	ts.Require().Equal("attributes:app-1", consent.Purposes[0].Name)
	ts.Require().ElementsMatch(consent.Purposes[0].Elements, []providers.ConsentElementApproval{
		{Name: "email", Namespace: providers.NamespaceAttribute, IsUserApproved: true},
		{Name: "phone", Namespace: providers.NamespaceAttribute, IsUserApproved: false},
	})

	ts.Require().Equal("permissions:app-1", consent.Purposes[1].Name)
	ts.Require().ElementsMatch(consent.Purposes[1].Elements, []providers.ConsentElementApproval{
		{Name: "perm.read", Namespace: providers.NamespacePermission, IsUserApproved: true},
	})

	ts.Require().Equal("client-1", q.lastUpsert.ClientID)
	ts.Require().Equal("user-1", q.lastUpsert.PsuToken)
	ts.Require().True(q.lastUpsert.ExpireDtimes.Valid)
}

// TestRecordConsent_FiltersUnrequestedDecisions reproduces the reported bug: a consent decision
// approving claims/scopes that were never part of the original authorize request must not be
// persisted or returned, even though the caller marked them Approved.
func (ts *ConsentProviderTestSuite) TestRecordConsent_FiltersUnrequestedDecisions() {
	q := &consentStubQuerier{}
	cfg := &config.AppConfig{ResourceServers: []config.ResourceServerConfig{
		{ID: "rs-1", Identifier: "https://rs.example.com", Scopes: map[string]string{"mosip_identity_vc_ldp": "vc"}},
	}}
	p := newConsentTestProvider(q, cfg)

	meta := clientIDMeta("client-1")
	meta[runtimeKeyScopes] = []string{"openid mosip_identity_vc_ldp"}

	decisions := &providers.ConsentDecisions{
		Purposes: []providers.PurposeDecision{
			{
				PurposeName: "attributes:app-1",
				Approved:    true,
				Elements: []providers.ElementDecision{
					{Name: "email", Approved: true},
					{Name: "phone_number", Approved: true},
					{Name: "gender", Approved: true},
				},
			},
			{
				PurposeName: "permissions:app-1",
				Approved:    true,
				Elements: []providers.ElementDecision{
					{Name: "mosip_identity_vc_ldp", Approved: true},
					{Name: "unrequested_scope", Approved: true},
				},
			},
		},
	}

	consent, svcErr := p.RecordConsent(context.Background(), "ou-1", "app-1", "user-1",
		decisions, "session-token", 3600, meta)
	ts.Require().Nil(svcErr)
	ts.Require().NotNil(consent)

	ts.Require().Equal("attributes:app-1", consent.Purposes[0].Name)
	ts.Require().Empty(consent.Purposes[0].Elements, "no attribute was requested, so none should be consented")

	ts.Require().Equal("permissions:app-1", consent.Purposes[1].Name)
	ts.Require().Equal([]providers.ConsentElementApproval{
		{Name: "mosip_identity_vc_ldp", Namespace: providers.NamespacePermission, IsUserApproved: true},
	}, consent.Purposes[1].Elements)

	var accepted, permitted []string
	ts.Require().NoError(json.Unmarshal([]byte(q.lastUpsert.AcceptedClaims.String), &accepted))
	ts.Require().NoError(json.Unmarshal([]byte(q.lastUpsert.PermittedScopes.String), &permitted))
	ts.Require().Empty(accepted, "unrequested claims must not be persisted as accepted")
	ts.Require().Equal([]string{"mosip_identity_vc_ldp"}, permitted)
}

func (ts *ConsentProviderTestSuite) TestRecordConsent_NilDecisions() {
	q := &consentStubQuerier{}
	p := newConsentTestProvider(q, nil)

	consent, svcErr := p.RecordConsent(context.Background(), "ou-1", "app-1", "user-1",
		nil, "", 0, clientIDMeta("client-1"))
	ts.Require().Nil(svcErr)
	ts.Require().NotNil(consent)
	ts.Require().Empty(consent.Purposes)
	ts.Require().False(q.lastUpsert.ExpireDtimes.Valid, "zero validity period should not set an expiry")
}

func (ts *ConsentProviderTestSuite) TestRecordConsent_SaveError() {
	q := &consentStubQuerier{upsertErr: errors.New("upsert failed")}
	p := newConsentTestProvider(q, nil)

	consent, svcErr := p.RecordConsent(context.Background(), "ou-1", "app-1", "user-1",
		nil, "", 0, clientIDMeta("client-1"))
	ts.Require().Nil(consent)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("consent_persist_failed", svcErr.Code)
}

func (ts *ConsentProviderTestSuite) TestRecordConsent_EssentialAttributeDenied() {
	q := &consentStubQuerier{}
	p := newConsentTestProvider(q, nil)

	meta := clientIDMeta("client-1")
	meta[runtimeKeyClaims] = []string{`{"userinfo":{"email":{"essential":true}}}`}

	decisions := &providers.ConsentDecisions{
		Purposes: []providers.PurposeDecision{
			{
				PurposeName: "attributes:app-1",
				Approved:    true,
				Elements: []providers.ElementDecision{
					{Name: "email", Approved: false},
				},
			},
		},
	}

	consent, svcErr := p.RecordConsent(context.Background(), "ou-1", "app-1", "user-1",
		decisions, "session-token", 3600, meta)
	ts.Require().Nil(consent)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("essential_consent_denied", svcErr.Code)
	ts.Require().Empty(q.lastUpsert.ClientID, "consent record must not be persisted when essential consent is denied")
}

func (ts *ConsentProviderTestSuite) TestBuildPrompt_EmptyReturnsNil() {
	p := newConsentTestProvider(&consentStubQuerier{}, nil)
	req := &requestedConsent{claimsRequest: map[string]any{}}
	prompt := p.buildPrompt("app-1", req, nil, nil, nil, nil)
	ts.Require().Nil(prompt)
}

func (ts *ConsentProviderTestSuite) TestClassifyAttributes() {
	req := &requestedConsent{claimsRequest: map[string]any{
		"userinfo": map[string]any{
			"email": map[string]any{"essential": true},
			"phone": nil,
		},
		"id_token": map[string]any{},
	}}

	essential, optional := classifyAttributes(req, []string{"name"}, []string{"phone", "address"}, nil)
	ts.Require().ElementsMatch([]string{"email", "name"}, essential)
	ts.Require().ElementsMatch([]string{"phone", "address"}, optional)
}

func (ts *ConsentProviderTestSuite) TestClassifyAttributes_FiltersToAvailable() {
	req := &requestedConsent{claimsRequest: map[string]any{}}
	essential, optional := classifyAttributes(req, []string{"name", "email"}, []string{"phone"},
		[]string{"name"})
	ts.Require().Equal([]string{"name"}, essential)
	ts.Require().Empty(optional)
}

func (ts *ConsentProviderTestSuite) TestIsEssentialClaim() {
	ts.Require().True(isEssentialClaim(map[string]any{"essential": true}))
	ts.Require().False(isEssentialClaim(map[string]any{"essential": false}))
	ts.Require().False(isEssentialClaim(nil))
	ts.Require().False(isEssentialClaim("not-a-map"))
}

func (ts *ConsentProviderTestSuite) TestProfileFilter() {
	ts.Require().Nil(profileFilter(nil))
	ts.Require().Nil(profileFilter([]string{}))
	got := profileFilter([]string{"a", "b"})
	ts.Require().Equal(map[string]bool{"a": true, "b": true}, got)
}

func (ts *ConsentProviderTestSuite) TestFilterSorted() {
	set := map[string]bool{"Banana": true, "apple": true, "Cherry": true}
	ts.Require().Equal([]string{"apple", "Banana", "Cherry"}, filterSorted(set, nil))
	ts.Require().Equal([]string{"apple"}, filterSorted(set, map[string]bool{"apple": true}))
}

func (ts *ConsentProviderTestSuite) TestSortFold() {
	s := []string{"Charlie", "alpha", "Bravo"}
	sortFold(s)
	ts.Require().Equal([]string{"alpha", "Bravo", "Charlie"}, s)
}

func (ts *ConsentProviderTestSuite) TestRequestedClaims_SkipsVerifiedClaims() {
	req := &requestedConsent{claimsRequest: map[string]any{
		"userinfo": map[string]any{"email": nil, "verified_claims": map[string]any{}},
		"id_token": map[string]any{"acr": nil},
	}}
	claims := requestedClaims(req)
	_, hasEmail := claims["email"]
	_, hasVerified := claims["verified_claims"]
	_, hasAcr := claims["acr"]
	ts.Require().True(hasEmail)
	ts.Require().False(hasVerified)
	ts.Require().True(hasAcr)
}

func (ts *ConsentProviderTestSuite) TestBuildRecord() {
	ts.Run("nil decisions yields empty purposes", func() {
		record := buildRecord("consent-1", "app-1", nil)
		ts.Require().Equal("consent-1", record.ID)
		ts.Require().Equal("app-1", record.GroupID)
		ts.Require().Equal(providers.ConsentStatusActive, record.Status)
		ts.Require().Empty(record.Purposes)
	})

	ts.Run("maps purposes and elements with namespace", func() {
		decisions := &providers.ConsentDecisions{Purposes: []providers.PurposeDecision{
			{PurposeName: "attributes:app-1", Elements: []providers.ElementDecision{{Name: "email", Approved: true}}},
			{PurposeName: "permissions:app-1", Elements: []providers.ElementDecision{{Name: "perm.read", Approved: false}}},
			{PurposeName: "unknown:app-1", Elements: []providers.ElementDecision{{Name: "x", Approved: true}}},
		}}
		record := buildRecord("consent-1", "app-1", decisions)
		ts.Require().Len(record.Purposes, 3)
		ts.Require().Equal(providers.NamespaceAttribute, record.Purposes[0].Elements[0].Namespace)
		ts.Require().Equal(providers.NamespacePermission, record.Purposes[1].Elements[0].Namespace)
		ts.Require().Equal(providers.Namespace(""), record.Purposes[2].Elements[0].Namespace)
	})
}

func (ts *ConsentProviderTestSuite) TestNamespaceFromPurposeName() {
	ts.Require().Equal(providers.NamespacePermission, namespaceFromPurposeName("permissions:app-1"))
	ts.Require().Equal(providers.NamespaceAttribute, namespaceFromPurposeName("attributes:app-1"))
	ts.Require().Equal(providers.Namespace(""), namespaceFromPurposeName("other:app-1"))
}

func (ts *ConsentProviderTestSuite) TestClientError() {
	svcErr := clientError("some_code", nil)
	ts.Require().Equal("some_code", svcErr.Code)
	ts.Require().Equal(common.ClientErrorType, svcErr.Type)
	ts.Require().Empty(svcErr.Error.DefaultValue)

	svcErr = clientError("some_code", errors.New("boom"))
	ts.Require().Equal("boom", svcErr.Error.DefaultValue)
	ts.Require().Equal("boom", svcErr.ErrorDescription.DefaultValue)
}

func (ts *ConsentProviderTestSuite) TestGetConsentRequestHash() {
	p := newConsentTestProvider(&consentStubQuerier{}, &config.AppConfig{})
	req := &requestedConsent{claimsRequest: map[string]any{}, standardScopes: []string{"openid"}}
	hash := p.getConsentRequestHash(context.Background(), req)
	ts.Require().NotEmpty(hash)
}

func (ts *ConsentProviderTestSuite) TestClaimsFromScopes() {
	cfg := &config.AppConfig{ScopeClaims: map[string][]string{
		"profile": {"name", "family_name"},
		"email":   {"email", "name"},
		"openid":  nil,
	}}
	p := newConsentTestProvider(&consentStubQuerier{}, cfg)

	claims := p.claimsFromScopes([]string{"profile", "email", "openid"})
	ts.Require().ElementsMatch([]string{"name", "family_name", "email"}, claims)
}

func (ts *ConsentProviderTestSuite) TestMergeScopeClaims() {
	ts.Run("no scope claim names returns original", func() {
		original := map[string]any{"userinfo": map[string]any{"email": nil}}
		got := mergeScopeClaims(original, nil)
		ts.Require().Equal(original, got)
	})

	ts.Run("adds missing claims without mutating input", func() {
		original := map[string]any{"userinfo": map[string]any{"email": nil}}
		got := mergeScopeClaims(original, []string{"name", "email"})

		userinfo, ok := got["userinfo"].(map[string]any)
		ts.Require().True(ok)
		_, hasName := userinfo["name"]
		ts.Require().True(hasName)

		originalUserinfo := original["userinfo"].(map[string]any)
		ts.Require().Len(originalUserinfo, 1, "original claimsRequest must not be mutated")
	})
}

func (ts *ConsentProviderTestSuite) TestToPromptElements() {
	got := toPromptElements([]string{"a", "b"})
	ts.Require().Equal([]providers.PromptElement{{Name: "a"}, {Name: "b"}}, got)
	ts.Require().Empty(toPromptElements(nil))
}

func (ts *ConsentProviderTestSuite) TestReadAuthRequest() {
	cfg := &config.AppConfig{ResourceServers: []config.ResourceServerConfig{
		{ID: "rs-1", Identifier: "https://rs.example.com", Scopes: map[string]string{"perm.read": "read"}},
	}}
	p := newConsentTestProvider(&consentStubQuerier{}, cfg)
	req, err := p.readAuthRequest(context.Background(), map[string][]string{
		"initiator_query_claims": {`{"userinfo":{"name":{"essential":true}}}`},
		"initiator_query_scope":  {"openid profile perm.read"},
	})
	ts.Require().NoError(err)
	ts.Require().NotNil(req)
	ts.Require().Equal("consent", req.prompt)
	ts.Require().Equal(map[string]any{"name": map[string]any{"essential": true}},
		req.claimsRequest["userinfo"])
	ts.Require().Equal([]string{"openid", "profile"}, req.standardScopes)
	ts.Require().Equal([]string{"perm.read"}, req.authorizeScopes)
}

func (ts *ConsentProviderTestSuite) TestReadAuthRequest_Empty() {
	p := newConsentTestProvider(&consentStubQuerier{}, nil)
	req, err := p.readAuthRequest(context.Background(), map[string][]string{})
	ts.Require().NoError(err)
	ts.Require().NotNil(req)
	ts.Require().Equal(map[string]any{}, req.claimsRequest)
	ts.Require().Empty(req.standardScopes)
	ts.Require().Empty(req.authorizeScopes)
}

func (ts *ConsentProviderTestSuite) TestReadAuthRequest_InvalidClaimsJSON() {
	p := newConsentTestProvider(&consentStubQuerier{}, nil)
	req, err := p.readAuthRequest(context.Background(), map[string][]string{
		"initiator_query_claims": {"not-json"},
	})
	ts.Require().Error(err)
	ts.Require().Nil(req)
}

type ConsentProviderTestSuite struct {
	suite.Suite
}

func TestConsentProviderTestSuite(t *testing.T) {
	suite.Run(t, new(ConsentProviderTestSuite))
}
