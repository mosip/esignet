package consentmgmt

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/mosip/esignet/internal/consentmgmt/db"
)

// fakeQuerier is an in-memory db.Querier stand-in that lets tests script GetConsent's response
// and capture the params passed to the write methods.
type fakeQuerier struct {
	getRow db.ConsentDetail
	getErr error

	insertHistoryParams db.InsertConsentHistoryParams
	insertHistoryErr    error

	upsertParams db.UpsertConsentParams
	upsertErr    error

	deleteParams db.DeleteConsentParams
	deleteErr    error
}

var _ db.Querier = (*fakeQuerier)(nil)

func (f *fakeQuerier) GetConsent(_ context.Context, _ db.GetConsentParams) (db.ConsentDetail, error) {
	return f.getRow, f.getErr
}

func (f *fakeQuerier) InsertConsentHistory(_ context.Context, arg db.InsertConsentHistoryParams) error {
	f.insertHistoryParams = arg
	return f.insertHistoryErr
}

func (f *fakeQuerier) UpsertConsent(_ context.Context, arg db.UpsertConsentParams) error {
	f.upsertParams = arg
	return f.upsertErr
}

func (f *fakeQuerier) DeleteConsent(_ context.Context, arg db.DeleteConsentParams) error {
	f.deleteParams = arg
	return f.deleteErr
}

func TestFetchRecord_NotFound(t *testing.T) {
	q := &fakeQuerier{getErr: sql.ErrNoRows}
	s := NewServiceWithQuerier(q)

	rec, err := s.FetchRecord(context.Background(), "client-1", "user-1")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if rec != nil {
		t.Fatalf("expected nil record when no row exists, got %#v", rec)
	}
}

func TestFetchRecord_QueryError(t *testing.T) {
	q := &fakeQuerier{getErr: errors.New("boom")}
	s := NewServiceWithQuerier(q)

	_, err := s.FetchRecord(context.Background(), "client-1", "user-1")
	if err == nil {
		t.Fatal("expected an error to be returned")
	}
}

func TestFetchRecord_DecodesStoredRow(t *testing.T) {
	createdAt := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	expiresAt := createdAt.Add(time.Hour)
	q := &fakeQuerier{getRow: db.ConsentDetail{
		ID:                  "consent-1",
		ClientID:            "client-1",
		PsuToken:            "user-1",
		Claims:              `{"userinfo":{"email":{}},"id_token":{}}`,
		AuthorizationScopes: `{"resource.read":false}`,
		Hash:                sql.NullString{String: "hash-value", Valid: true},
		AcceptedClaims:      sql.NullString{String: `["email"]`, Valid: true},
		PermittedScopes:     sql.NullString{String: `["resource.read"]`, Valid: true},
		CrDtimes:            createdAt,
		ExpireDtimes:        sql.NullTime{Time: expiresAt, Valid: true},
	}}
	s := NewServiceWithQuerier(q)

	rec, err := s.FetchRecord(context.Background(), "client-1", "user-1")
	if err != nil {
		t.Fatalf("fetch: %v", err)
	}
	if rec == nil {
		t.Fatal("expected a non-nil record")
	}
	if rec.ID != "consent-1" || rec.ClientID != "client-1" || rec.UserID != "user-1" {
		t.Errorf("unexpected identity fields: %#v", rec)
	}
	if rec.Hash != "hash-value" {
		t.Errorf("Hash = %q, want %q", rec.Hash, "hash-value")
	}
	if len(rec.Claims) != 2 {
		t.Errorf("expected claims to decode both sections, got %#v", rec.Claims)
	}
	if len(rec.AuthorizationScopes) != 1 || rec.AuthorizationScopes[0] != "resource.read" {
		t.Errorf("unexpected authorization scopes: %#v", rec.AuthorizationScopes)
	}
	if len(rec.AcceptedClaims) != 1 || rec.AcceptedClaims[0] != "email" {
		t.Errorf("unexpected accepted claims: %#v", rec.AcceptedClaims)
	}
	if len(rec.PermittedScopes) != 1 || rec.PermittedScopes[0] != "resource.read" {
		t.Errorf("unexpected permitted scopes: %#v", rec.PermittedScopes)
	}
	if !rec.ExpiresAt.Valid || !rec.ExpiresAt.Time.Equal(expiresAt) {
		t.Errorf("unexpected expiry: %#v", rec.ExpiresAt)
	}
}

func TestFetchRecord_NullAcceptedAndPermitted(t *testing.T) {
	q := &fakeQuerier{getRow: db.ConsentDetail{
		ID:                  "consent-1",
		ClientID:            "client-1",
		PsuToken:            "user-1",
		Claims:              `{}`,
		AuthorizationScopes: `{}`,
	}}
	s := NewServiceWithQuerier(q)

	rec, err := s.FetchRecord(context.Background(), "client-1", "user-1")
	if err != nil {
		t.Fatalf("fetch: %v", err)
	}
	if rec.AcceptedClaims != nil {
		t.Errorf("expected nil AcceptedClaims for a NULL column, got %#v", rec.AcceptedClaims)
	}
	if rec.PermittedScopes != nil {
		t.Errorf("expected nil PermittedScopes for a NULL column, got %#v", rec.PermittedScopes)
	}
}

func TestFetchRecord_MalformedClaimsJSON(t *testing.T) {
	q := &fakeQuerier{getRow: db.ConsentDetail{
		Claims:              `not-json`,
		AuthorizationScopes: `{}`,
	}}
	s := NewServiceWithQuerier(q)

	if _, err := s.FetchRecord(context.Background(), "client-1", "user-1"); err == nil {
		t.Fatal("expected an error for malformed claims JSON")
	}
}

func TestSaveRecord_PersistsComputedHashAndNormalizedPayload(t *testing.T) {
	q := &fakeQuerier{}
	s := NewServiceWithQuerier(q)

	createdAt := time.Now().UTC()
	rec := &ConsentRecord{
		ID:                  "consent-1",
		ClientID:            "client-1",
		UserID:              "user-1",
		Claims:              map[string]any{"userinfo": map[string]any{"email": nil}},
		AuthorizationScopes: []string{"resource.read"},
		AcceptedClaims:      []string{"email"},
		PermittedScopes:     []string{"resource.read"},
		CreatedAt:           createdAt,
	}

	if err := s.SaveRecord(context.Background(), rec); err != nil {
		t.Fatalf("save: %v", err)
	}

	wantHash, err := HashRequestedConsent(
		NormalizeClaims(rec.Claims),
		NormalizeAuthorizationScopes(rec.AuthorizationScopes),
	)
	if err != nil {
		t.Fatalf("compute expected hash: %v", err)
	}

	if !q.upsertParams.Hash.Valid || q.upsertParams.Hash.String != wantHash {
		t.Errorf("UpsertConsent Hash = %#v, want %q", q.upsertParams.Hash, wantHash)
	}
	if !q.insertHistoryParams.Hash.Valid || q.insertHistoryParams.Hash.String != wantHash {
		t.Errorf("InsertConsentHistory Hash = %#v, want %q", q.insertHistoryParams.Hash, wantHash)
	}
	if q.upsertParams.ID != rec.ID || q.upsertParams.ClientID != rec.ClientID || q.upsertParams.PsuToken != rec.UserID {
		t.Errorf("unexpected upsert identity fields: %#v", q.upsertParams)
	}

	var gotClaims map[string]any
	if err := json.Unmarshal([]byte(q.upsertParams.Claims), &gotClaims); err != nil {
		t.Fatalf("decode persisted claims: %v", err)
	}
	if _, ok := gotClaims["userinfo"]; !ok {
		t.Errorf("expected normalized claims to include userinfo, got %#v", gotClaims)
	}
}

func TestSaveRecord_InsertHistoryError(t *testing.T) {
	q := &fakeQuerier{insertHistoryErr: errors.New("insert failed")}
	s := NewServiceWithQuerier(q)

	err := s.SaveRecord(context.Background(), &ConsentRecord{ID: "c1", ClientID: "client-1", UserID: "user-1"})
	if err == nil {
		t.Fatal("expected an error when inserting consent history fails")
	}
}

func TestSaveRecord_UpsertError(t *testing.T) {
	q := &fakeQuerier{upsertErr: errors.New("upsert failed")}
	s := NewServiceWithQuerier(q)

	err := s.SaveRecord(context.Background(), &ConsentRecord{ID: "c1", ClientID: "client-1", UserID: "user-1"})
	if err == nil {
		t.Fatal("expected an error when upserting consent fails")
	}
}

func TestDeleteRecord(t *testing.T) {
	q := &fakeQuerier{}
	s := NewServiceWithQuerier(q)

	if err := s.DeleteRecord(context.Background(), "client-1", "user-1"); err != nil {
		t.Fatalf("delete: %v", err)
	}
	if q.deleteParams.ClientID != "client-1" || q.deleteParams.PsuToken != "user-1" {
		t.Errorf("unexpected delete params: %#v", q.deleteParams)
	}
}

func TestDeleteRecord_Error(t *testing.T) {
	q := &fakeQuerier{deleteErr: errors.New("delete failed")}
	s := NewServiceWithQuerier(q)

	if err := s.DeleteRecord(context.Background(), "client-1", "user-1"); err == nil {
		t.Fatal("expected an error when delete fails")
	}
}
