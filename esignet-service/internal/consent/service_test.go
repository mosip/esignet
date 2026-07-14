package consent

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	applog "github.com/mosip/esignet/internal/log"
)

type fakeStore struct {
	stored    *storedConsent
	getErr    error
	saved     *storedConsent
	saveErr   error
	deleted   bool
	deleteErr error
}

func (f *fakeStore) get(_ context.Context, _, _ string) (*storedConsent, bool, error) {
	if f.getErr != nil {
		return nil, false, f.getErr
	}
	if f.stored == nil {
		return nil, false, nil
	}
	return f.stored, true, nil
}

func (f *fakeStore) save(_ context.Context, c *storedConsent) error {
	f.saved = c
	return f.saveErr
}

func (f *fakeStore) delete(_ context.Context, _, _ string) error {
	f.deleted = true
	return f.deleteErr
}

func newTestService(store consentStore, runtimeStore providers.RuntimeStoreProvider) *Service {
	return &Service{store: store, runtimeStore: runtimeStore, logger: applog.GetLogger()}
}

// fakeStoreForRequest builds a runtime store that returns req (serialized in the engine's stored
// authorization-request wire form) under the test authorization_request_id. When found is false it
// returns an empty store, so readAuthRequest reports the request as absent.
func fakeStoreForRequest(req *requestedConsent, found bool) *fakeRuntimeStore {
	if !found || req == nil {
		return &fakeRuntimeStore{}
	}
	data, _ := json.Marshal(map[string]any{"OAuthParameters": map[string]any{
		"PermissionScopes": req.AuthorizeScopes,
		"Prompt":           req.Prompt,
		"ClaimsRequest": map[string]any{
			"userinfo": req.UserInfo,
			"id_token": req.IDToken,
		},
	}})
	authID := testMetadata[runtimeKeyAuthorizationRequestID]
	return &fakeRuntimeStore{data: map[string][]byte{
		string(providers.NamespaceAuthzReq) + ":" + authID: data,
	}}
}

const testAppID = "app1"

var testMetadata = map[string]string{
	runtimeKeyAuthorizationRequestID: "authreq-1",
	runtimeKeyClientID:               "client-1",
}

func emailEssentialRequest() *requestedConsent {
	return &requestedConsent{
		UserInfo:        map[string]any{"email": map[string]any{"essential": true}, "name": nil},
		AuthorizeScopes: []string{"resource.read"},
	}
}

func resolve(s *Service, req *requestedConsent, found, force bool, store consentStore) (*PromptData, error) {
	s.runtimeStore = fakeStoreForRequest(req, found)
	if store != nil {
		s.store = store
	}
	return s.Resolve(context.Background(), ResolveInput{
		OUID:            "default",
		AppID:           testAppID,
		AppName:         "App One",
		UserID:          "user-1",
		ForceReprompt:   force,
		RuntimeMetadata: testMetadata,
	})
}

func TestResolve_NothingRequested_NoPrompt(t *testing.T) {
	s := newTestService(&fakeStore{}, nil)
	prompt, err := resolve(s, &requestedConsent{}, false, false, &fakeStore{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if prompt != nil {
		t.Fatalf("expected no prompt, got %+v", prompt)
	}
}

func TestResolve_NothingRequested_DeletesStaleConsent(t *testing.T) {
	store := &fakeStore{}
	s := newTestService(store, nil)
	prompt, err := resolve(s, &requestedConsent{}, false, false, store)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if prompt != nil {
		t.Fatalf("expected no prompt, got %+v", prompt)
	}
	if !store.deleted {
		t.Fatal("expected stale consent to be deleted when nothing is requested")
	}
}

func TestResolve_NoStoredConsent_Captures(t *testing.T) {
	s := newTestService(&fakeStore{}, nil)
	prompt, err := resolve(s, emailEssentialRequest(), true, false, &fakeStore{stored: nil})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if prompt == nil || len(prompt.Purposes) == 0 {
		t.Fatal("expected a consent prompt")
	}
	if prompt.Purposes[0].PurposeName != attributesPurposePrefix+testAppID {
		t.Fatalf("unexpected purpose name %q", prompt.Purposes[0].PurposeName)
	}
}

func TestResolve_HashMatches_NoPrompt(t *testing.T) {
	req := emailEssentialRequest()
	hash, err := hashRequestedConsent(req)
	if err != nil {
		t.Fatalf("hash: %v", err)
	}
	s := newTestService(nil, nil)
	prompt, svcErr := resolve(s, req, true, false, &fakeStore{stored: &storedConsent{Hash: hash}})
	if svcErr != nil {
		t.Fatalf("unexpected error: %v", svcErr)
	}
	if prompt != nil {
		t.Fatalf("expected NOCAPTURE (nil), got %+v", prompt)
	}
}

func TestResolve_HashDiffers_Captures(t *testing.T) {
	s := newTestService(nil, nil)
	prompt, err := resolve(s, emailEssentialRequest(), true, false,
		&fakeStore{stored: &storedConsent{Hash: "stale-hash"}})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if prompt == nil {
		t.Fatal("expected CAPTURE when hash differs")
	}
}

func TestResolve_Expired_Captures(t *testing.T) {
	req := emailEssentialRequest()
	hash, _ := hashRequestedConsent(req)
	stored := &storedConsent{
		Hash:      hash,
		ExpiresAt: sql.NullTime{Time: time.Now().UTC().Add(-time.Hour), Valid: true},
	}
	s := newTestService(nil, nil)
	prompt, err := resolve(s, req, true, false, &fakeStore{stored: stored})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if prompt == nil {
		t.Fatal("expected CAPTURE when consent expired")
	}
}

func TestResolve_PromptConsent_Captures(t *testing.T) {
	req := emailEssentialRequest()
	req.Prompt = "login consent"
	hash, _ := hashRequestedConsent(req)
	s := newTestService(nil, nil)
	prompt, err := resolve(s, req, true, false, &fakeStore{stored: &storedConsent{Hash: hash}})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if prompt == nil {
		t.Fatal("expected CAPTURE when prompt=consent")
	}
}

func TestResolve_ForceReprompt_Captures(t *testing.T) {
	req := emailEssentialRequest()
	hash, _ := hashRequestedConsent(req)
	s := newTestService(nil, nil)
	prompt, err := resolve(s, req, true, true, &fakeStore{stored: &storedConsent{Hash: hash}})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if prompt == nil {
		t.Fatal("expected CAPTURE when forceReprompt is set")
	}
}

func TestResolve_ReadError_ReturnsCodedError(t *testing.T) {
	s := newTestService(&fakeStore{}, &fakeRuntimeStore{err: errors.New("boom")})
	_, err := s.Resolve(context.Background(), ResolveInput{
		AppID: testAppID, UserID: "user-1", RuntimeMetadata: testMetadata,
	})
	var cErr *Error
	if !errors.As(err, &cErr) || cErr.Code != ErrCodeAuthRequestRead {
		t.Fatalf("expected coded error %q, got %v", ErrCodeAuthRequestRead, err)
	}
}

func buildDecisions(purposes ...PurposeDecision) *Decisions {
	return &Decisions{Purposes: purposes}
}

func TestRecord_PersistsAndReturnsRecord(t *testing.T) {
	store := &fakeStore{}
	s := newTestService(store, fakeStoreForRequest(emailEssentialRequest(), true))

	decisions := buildDecisions(PurposeDecision{
		PurposeName: attributesPurposePrefix + testAppID,
		Approved:    true,
		Elements: []ElementDecision{
			{Name: "email", Approved: true},
			{Name: "name", Approved: true},
		},
	})

	record, err := s.Record(context.Background(), RecordInput{
		OUID: "default", AppID: testAppID, UserID: "user-1",
		Decisions: decisions, ValidityPeriod: 3600, RuntimeMetadata: testMetadata,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if record == nil || record.ID == "" {
		t.Fatal("expected a persisted consent record with an ID")
	}
	if store.saved == nil {
		t.Fatal("expected consent to be persisted")
	}
	if !store.saved.ExpiresAt.Valid {
		t.Fatal("expected expiry to be set for a positive validity period")
	}
	if len(record.Purposes) == 0 {
		t.Fatal("expected purposes on the returned record")
	}
	if record.Status != recordStatusActive {
		t.Fatalf("unexpected status %q", record.Status)
	}
	if record.Purposes[0].Elements[0].Namespace != namespaceAttribute {
		t.Fatalf("unexpected namespace %q", record.Purposes[0].Elements[0].Namespace)
	}
}

func TestRecord_EssentialDenied(t *testing.T) {
	store := &fakeStore{}
	s := newTestService(store, fakeStoreForRequest(emailEssentialRequest(), true))

	decisions := buildDecisions(PurposeDecision{
		PurposeName: attributesPurposePrefix + testAppID,
		Approved:    false,
		Elements: []ElementDecision{
			{Name: "email", Approved: false},
		},
	})

	record, err := s.Record(context.Background(), RecordInput{
		OUID: "default", AppID: testAppID, UserID: "user-1",
		Decisions: decisions, ValidityPeriod: 0, RuntimeMetadata: testMetadata,
	})
	if !errors.Is(err, ErrEssentialConsentDenied) {
		t.Fatalf("expected ErrEssentialConsentDenied, got %v", err)
	}
	if record != nil {
		t.Fatal("expected nil record on essential denial")
	}
	if store.saved == nil {
		t.Fatal("expected consent to be persisted even on essential denial")
	}
}
