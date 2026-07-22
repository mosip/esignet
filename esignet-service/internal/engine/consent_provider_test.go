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

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/consentmgmt"
	"github.com/mosip/esignet/internal/consentmgmt/db"
	applog "github.com/mosip/esignet/internal/log"
)

// fakeQuerier is a minimal db.Querier stand-in so consentmgmt.Service can be exercised without a
// real Postgres connection.
type fakeQuerier struct {
	getRow db.ConsentDetail
	getErr error

	upsertParams        db.UpsertConsentParams
	insertHistoryParams db.InsertConsentHistoryParams
	saveErr             error
}

var _ db.Querier = (*fakeQuerier)(nil)

func (f *fakeQuerier) GetConsent(context.Context, db.GetConsentParams) (db.ConsentDetail, error) {
	return f.getRow, f.getErr
}
func (f *fakeQuerier) InsertConsentHistory(_ context.Context, arg db.InsertConsentHistoryParams) error {
	f.insertHistoryParams = arg
	return f.saveErr
}
func (f *fakeQuerier) UpsertConsent(_ context.Context, arg db.UpsertConsentParams) error {
	f.upsertParams = arg
	return f.saveErr
}
func (f *fakeQuerier) DeleteConsent(context.Context, db.DeleteConsentParams) error { return nil }

// fakeRuntimeStore is a minimal RuntimeStoreProvider whose Get returns preset data/err; the other
// methods are unused by the consent provider.
type fakeRuntimeStore struct {
	data map[string][]byte
	err  error
}

func (f *fakeRuntimeStore) Put(context.Context, providers.RuntimeStoreNamespace, string, []byte, int64) error {
	return nil
}
func (f *fakeRuntimeStore) Get(_ context.Context, ns providers.RuntimeStoreNamespace, key string) ([]byte, error) {
	if f.err != nil {
		return nil, f.err
	}
	return f.data[string(ns)+":"+key], nil
}
func (f *fakeRuntimeStore) Update(context.Context, providers.RuntimeStoreNamespace, string, []byte) error {
	return nil
}
func (f *fakeRuntimeStore) Delete(context.Context, providers.RuntimeStoreNamespace, string) error {
	return nil
}
func (f *fakeRuntimeStore) Take(context.Context, providers.RuntimeStoreNamespace, string) ([]byte, error) {
	return nil, nil
}
func (f *fakeRuntimeStore) ExtendTTL(context.Context, providers.RuntimeStoreNamespace, string, int64) error {
	return nil
}

var _ providers.RuntimeStoreProvider = (*fakeRuntimeStore)(nil)

const (
	testAuthID   = "authreq-1"
	testClientID = "client-1"
	testUserID   = "user-1"
)

var testMetadata = map[string]string{
	runtimeKeyAuthorizationRequestID: testAuthID,
	runtimeKeyClientID:               testClientID,
}

// storeWithAuthRequest builds a fakeRuntimeStore that returns the given claims/scopes under
// testAuthID, in the engine's stored authorization-request wire form.
func storeWithAuthRequest(claimsRequest map[string]any, authorizeScopes []string) *fakeRuntimeStore {
	return storeWithAuthRequestScopes(claimsRequest, authorizeScopes, nil)
}

// storeWithAuthRequestScopes is storeWithAuthRequest extended with standard OIDC scopes.
func storeWithAuthRequestScopes(claimsRequest map[string]any, authorizeScopes,
	standardScopes []string) *fakeRuntimeStore {
	data, _ := json.Marshal(map[string]any{"OAuthParameters": map[string]any{
		"ClientID":         testClientID,
		"PermissionScopes": authorizeScopes,
		"StandardScopes":   standardScopes,
		"ClaimsRequest":    claimsRequest,
	}})
	return &fakeRuntimeStore{data: map[string][]byte{
		string(providers.NamespaceAuthzReq) + ":" + testAuthID: data,
	}}
}

func newProvider(q db.Querier, rs providers.RuntimeStoreProvider) *consentProvider {
	return newProviderWithConfig(q, rs, &config.AppConfig{})
}

// newProviderWithConfig is newProvider with an explicit AppConfig, for tests that need
// config.ScopeClaims (e.g. standard-scope-to-claim expansion) populated.
func newProviderWithConfig(q db.Querier, rs providers.RuntimeStoreProvider, cfg *config.AppConfig) *consentProvider {
	return &consentProvider{
		consentSvc:   consentmgmt.NewServiceWithQuerier(q),
		config:       cfg,
		runtimeStore: rs,
		logger:       applog.GetLogger(),
	}
}

func TestResolveConsent_NoStoredConsent_PromptsWithoutPanic(t *testing.T) {
	q := &fakeQuerier{getErr: sql.ErrNoRows}
	rs := storeWithAuthRequest(map[string]any{"userinfo": map[string]any{"email": nil}}, []string{"resource.read"})
	p := newProvider(q, rs)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou1", "app1", "App One", testUserID,
		[]string{"email"}, nil, []string{"resource.read"}, nil, false, testMetadata)

	if svcErr != nil {
		t.Fatalf("unexpected service error: %+v", svcErr)
	}
	if prompt == nil {
		t.Fatal("expected a consent prompt when no consent is on file")
	}
}

func TestResolveConsent_ForceReprompt(t *testing.T) {
	q := &fakeQuerier{getRow: db.ConsentDetail{
		Hash:   sql.NullString{String: "irrelevant", Valid: true},
		Claims: `{}`, AuthorizationScopes: `{}`,
	}}
	rs := storeWithAuthRequest(map[string]any{"userinfo": map[string]any{"email": nil}}, []string{"resource.read"})
	p := newProvider(q, rs)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou1", "app1", "App One", testUserID,
		[]string{"email"}, nil, []string{"resource.read"}, nil, true, testMetadata)

	if svcErr != nil {
		t.Fatalf("unexpected service error: %+v", svcErr)
	}
	if prompt == nil {
		t.Fatal("expected a consent prompt when forceReprompt is set")
	}
}

func TestResolveConsent_ExpiredConsent_Prompts(t *testing.T) {
	q := &fakeQuerier{getRow: db.ConsentDetail{
		ExpireDtimes: sql.NullTime{Time: time.Now().UTC().Add(-time.Hour), Valid: true},
		Claims:       `{}`, AuthorizationScopes: `{}`,
	}}
	rs := storeWithAuthRequest(map[string]any{"userinfo": map[string]any{"email": nil}}, []string{"resource.read"})
	p := newProvider(q, rs)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou1", "app1", "App One", testUserID,
		[]string{"email"}, nil, []string{"resource.read"}, nil, false, testMetadata)

	if svcErr != nil {
		t.Fatalf("unexpected service error: %+v", svcErr)
	}
	if prompt == nil {
		t.Fatal("expected a consent prompt for an expired consent")
	}
}

func TestResolveConsent_MatchingHash_SkipsPrompt(t *testing.T) {
	claimsRequest := map[string]any{"userinfo": map[string]any{"email": nil}}
	scopes := []string{"resource.read"}
	hash, err := requestHash(claimsRequest, scopes)
	if err != nil {
		t.Fatalf("compute hash: %v", err)
	}

	q := &fakeQuerier{getRow: db.ConsentDetail{
		Hash:                sql.NullString{String: hash, Valid: true},
		Claims:              `{}`,
		AuthorizationScopes: `{}`,
	}}
	rs := storeWithAuthRequest(claimsRequest, scopes)
	p := newProvider(q, rs)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou1", "app1", "App One", testUserID,
		[]string{"email"}, nil, scopes, nil, false, testMetadata)

	if svcErr != nil {
		t.Fatalf("unexpected service error: %+v", svcErr)
	}
	if prompt != nil {
		t.Fatalf("expected no prompt when the stored hash matches, got %#v", prompt)
	}
}

func TestResolveConsent_AdditionalStandardScope_Reprompts(t *testing.T) {
	cfg := &config.AppConfig{ScopeClaims: map[string][]string{"email": {"email", "email_verified"}}}
	claimsRequest := map[string]any{"userinfo": map[string]any{}}
	scopes := []string{"resource.read"}

	// The stored consent's hash was computed for a request that carried no claim-producing standard
	// scope (e.g. just "openid").
	priorHash, err := requestHash(claimsRequest, scopes)
	if err != nil {
		t.Fatalf("compute prior hash: %v", err)
	}
	q := &fakeQuerier{getRow: db.ConsentDetail{
		Hash:                sql.NullString{String: priorHash, Valid: true},
		Claims:              `{}`,
		AuthorizationScopes: `{}`,
	}}
	// The current request adds the "email" standard scope, which the ScopeClaims config expands
	// into claims. Same explicit claims param and same authorize scopes as before.
	rs := storeWithAuthRequestScopes(claimsRequest, scopes, []string{"openid", "email"})
	p := newProviderWithConfig(q, rs, cfg)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou1", "app1", "App One", testUserID,
		nil, []string{"email"}, scopes, nil, false, testMetadata)

	if svcErr != nil {
		t.Fatalf("unexpected service error: %+v", svcErr)
	}
	if prompt == nil {
		t.Fatal("expected a reprompt: the added standard scope expands to claims not covered by the stored consent")
	}
}

func TestGetConsentRequestHash_SameStandardScopes_SameHash(t *testing.T) {
	cfg := &config.AppConfig{ScopeClaims: map[string][]string{"email": {"email", "email_verified"}}}
	p := newProviderWithConfig(&fakeQuerier{}, &fakeRuntimeStore{}, cfg)
	req := &requestedConsent{
		claimsRequest:   map[string]any{"userinfo": map[string]any{}},
		authorizeScopes: []string{"resource.read"},
		standardScopes:  []string{"openid", "email"},
	}

	h1 := p.getConsentRequestHash(context.Background(), req)
	h2 := p.getConsentRequestHash(context.Background(), req)
	if h1 == "" || h1 != h2 {
		t.Fatalf("expected identical, non-empty hashes for identical requests, got %q vs %q", h1, h2)
	}
}

func TestMergeScopeClaims_SkipsAlreadyRequestedClaim(t *testing.T) {
	claimsRequest := map[string]any{"userinfo": map[string]any{"email": map[string]any{"essential": true}}}

	merged := mergeScopeClaims(claimsRequest, []string{"email"})

	userinfo, ok := merged["userinfo"].(map[string]any)
	if !ok {
		t.Fatalf("expected merged userinfo section, got %#v", merged["userinfo"])
	}
	constraint, ok := userinfo["email"].(map[string]any)
	if !ok {
		t.Fatalf("expected the explicit email constraint to be preserved as a map, got %#v", userinfo["email"])
	}
	if essential, _ := constraint["essential"].(bool); !essential {
		t.Errorf("expected the explicit essential constraint to survive, not be overwritten by the scope-derived claim")
	}
}

func TestMergeScopeClaims_DoesNotMutateInput(t *testing.T) {
	original := map[string]any{"userinfo": map[string]any{"name": nil}}
	originalUserinfo := original["userinfo"].(map[string]any)

	_ = mergeScopeClaims(original, []string{"email"})

	if _, ok := originalUserinfo["email"]; ok {
		t.Fatal("mergeScopeClaims must not mutate the input claims request")
	}
	if len(originalUserinfo) != 1 {
		t.Fatalf("expected the input userinfo section to be untouched, got %#v", originalUserinfo)
	}
}

func TestResolveConsent_MismatchedHash_Prompts(t *testing.T) {
	q := &fakeQuerier{getRow: db.ConsentDetail{
		Hash:                sql.NullString{String: "stale-hash", Valid: true},
		Claims:              `{}`,
		AuthorizationScopes: `{}`,
	}}
	rs := storeWithAuthRequest(map[string]any{"userinfo": map[string]any{"email": nil}}, []string{"resource.read"})
	p := newProvider(q, rs)

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou1", "app1", "App One", testUserID,
		[]string{"email"}, nil, []string{"resource.read"}, nil, false, testMetadata)

	if svcErr != nil {
		t.Fatalf("unexpected service error: %+v", svcErr)
	}
	if prompt == nil {
		t.Fatal("expected a consent prompt when the stored hash no longer matches")
	}
}

func TestResolveConsent_FetchError(t *testing.T) {
	q := &fakeQuerier{getErr: errors.New("db down")}
	p := newProvider(q, &fakeRuntimeStore{})

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou1", "app1", "App One", testUserID,
		[]string{"email"}, nil, nil, nil, false, testMetadata)

	if svcErr == nil {
		t.Fatal("expected a service error when fetching consent fails")
	}
	if prompt != nil {
		t.Fatal("expected a nil prompt on error")
	}
}

func TestResolveConsent_MissingAuthRequest_ChecksFailed(t *testing.T) {
	q := &fakeQuerier{getRow: db.ConsentDetail{Claims: `{}`, AuthorizationScopes: `{}`}}
	p := newProvider(q, &fakeRuntimeStore{})

	prompt, svcErr := p.ResolveConsent(context.Background(), "ou1", "app1", "App One", testUserID,
		[]string{"email"}, nil, []string{"resource.read"}, nil, false, testMetadata)

	if svcErr == nil {
		t.Fatal("expected a service error when the authorization request cannot be resolved")
	}
	if prompt != nil {
		t.Fatal("expected a nil prompt on error")
	}
}

func TestRecordConsent_PersistsAcceptedClaimsAndScopes(t *testing.T) {
	q := &fakeQuerier{}
	rs := storeWithAuthRequest(map[string]any{"userinfo": map[string]any{"email": nil}}, []string{"resource.read"})
	p := newProvider(q, rs)

	decisions := &providers.ConsentDecisions{Purposes: []providers.PurposeDecision{
		{
			PurposeName: attributesPurpose,
			Approved:    true,
			Elements:    []providers.ElementDecision{{Name: "email", Approved: true}, {Name: "phone", Approved: false}},
		},
		{
			PurposeName: permissionsPurpose,
			Approved:    true,
			Elements:    []providers.ElementDecision{{Name: "resource.read", Approved: true}},
		},
	}}

	consent, svcErr := p.RecordConsent(context.Background(), "ou1", "app1", testUserID,
		decisions, "session-token", 3600, testMetadata)

	if svcErr != nil {
		t.Fatalf("unexpected service error: %+v", svcErr)
	}
	if consent == nil || consent.GroupID != "app1" {
		t.Fatalf("unexpected consent result: %#v", consent)
	}

	var accepted []string
	if err := json.Unmarshal([]byte(q.upsertParams.AcceptedClaims.String), &accepted); err != nil {
		t.Fatalf("decode accepted claims: %v", err)
	}
	if len(accepted) != 1 || accepted[0] != "email" {
		t.Errorf("unexpected accepted claims: %#v", accepted)
	}

	var permitted []string
	if err := json.Unmarshal([]byte(q.upsertParams.PermittedScopes.String), &permitted); err != nil {
		t.Fatalf("decode permitted scopes: %v", err)
	}
	if len(permitted) != 1 || permitted[0] != "resource.read" {
		t.Errorf("unexpected permitted scopes: %#v", permitted)
	}
	if !q.upsertParams.ExpireDtimes.Valid {
		t.Errorf("expected an expiry to be set for a positive validity period")
	}
}

func TestRecordConsent_NilDecisions(t *testing.T) {
	q := &fakeQuerier{}
	rs := storeWithAuthRequest(map[string]any{"userinfo": map[string]any{"email": nil}}, []string{"resource.read"})
	p := newProvider(q, rs)

	consent, svcErr := p.RecordConsent(context.Background(), "ou1", "app1", testUserID,
		nil, "session-token", 0, testMetadata)

	if svcErr != nil {
		t.Fatalf("unexpected service error: %+v", svcErr)
	}
	if consent == nil {
		t.Fatal("expected a consent result even with no decisions")
	}
	if q.upsertParams.ExpireDtimes.Valid {
		t.Errorf("expected no expiry for a zero validity period")
	}
}

func TestRecordConsent_MissingAuthRequest(t *testing.T) {
	q := &fakeQuerier{}
	p := newProvider(q, &fakeRuntimeStore{})

	consent, svcErr := p.RecordConsent(context.Background(), "ou1", "app1", testUserID,
		nil, "session-token", 0, testMetadata)

	if svcErr == nil {
		t.Fatal("expected a service error when the authorization request is missing")
	}
	if consent != nil {
		t.Fatal("expected a nil consent on error")
	}
}

func TestRecordConsent_ReadAuthRequestError(t *testing.T) {
	q := &fakeQuerier{}
	p := newProvider(q, &fakeRuntimeStore{err: errors.New("store down")})

	consent, svcErr := p.RecordConsent(context.Background(), "ou1", "app1", testUserID,
		nil, "session-token", 0, testMetadata)

	if svcErr == nil {
		t.Fatal("expected a service error when the runtime store fails")
	}
	if consent != nil {
		t.Fatal("expected a nil consent on error")
	}
}
