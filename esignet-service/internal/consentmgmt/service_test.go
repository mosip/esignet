/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package consentmgmt

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/jmoiron/sqlx"

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
	if rec == nil { //nolint:staticcheck // SA5011 false positive: t.Fatal below exits before the later dereference
		t.Fatal("expected a non-nil record")
	}
	if rec.ID != "consent-1" || rec.ClientID != "client-1" || rec.UserID != "user-1" { //nolint:staticcheck // SA5011 false positive: t.Fatal above exits before this dereference
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

// newMockService builds a Service whose *sqlx.DB is backed by go-sqlmock, so the
// SaveRecord transaction (Begin → execs → Commit/Rollback) can be scripted and
// asserted without a real database. The "sqlmock" driver name leaves "?"
// placeholders in the query text, which the regexp statement matchers below ignore.
func newMockService(t *testing.T) (*Service, sqlmock.Sqlmock) {
	t.Helper()
	mockDB, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New: %v", err)
	}
	t.Cleanup(func() { _ = mockDB.Close() })
	return NewService(sqlx.NewDb(mockDB, "sqlmock")), mock
}

// jsonArgHasKey is a sqlmock argument matcher that passes when the bound value is
// a JSON object with the given top-level key — used to assert the persisted
// claims were normalized (they keep their "userinfo" section).
type jsonArgHasKey struct{ key string }

func (m jsonArgHasKey) Match(v driver.Value) bool {
	s, ok := v.(string)
	if !ok {
		return false
	}
	var obj map[string]any
	if json.Unmarshal([]byte(s), &obj) != nil {
		return false
	}
	_, ok = obj[m.key]
	return ok
}

func TestSaveRecord_PersistsComputedHashAndNormalizedPayload(t *testing.T) {
	s, mock := newMockService(t)

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
	wantHash, err := HashRequestedConsent(
		NormalizeClaims(rec.Claims),
		NormalizeAuthorizationScopes(rec.AuthorizationScopes),
	)
	if err != nil {
		t.Fatalf("compute expected hash: %v", err)
	}
	hashArg := sql.NullString{String: wantHash, Valid: true}

	mock.ExpectBegin()
	// consent_history snapshot: fresh UUID id, identity, normalized claims, computed hash.
	mock.ExpectExec("INSERT INTO consent_history").
		WithArgs(sqlmock.AnyArg(), "client-1", "user-1", jsonArgHasKey{"userinfo"}, sqlmock.AnyArg(),
			hashArg, sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))
	// consent_detail replace = delete then insert (portability rule D).
	mock.ExpectExec("DELETE FROM consent_detail").
		WithArgs("client-1", "user-1").
		WillReturnResult(sqlmock.NewResult(0, 0))
	mock.ExpectExec("INSERT INTO consent_detail").
		WithArgs("consent-1", "client-1", "user-1", jsonArgHasKey{"userinfo"}, sqlmock.AnyArg(),
			hashArg, sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectCommit()

	if err := s.SaveRecord(context.Background(), rec); err != nil {
		t.Fatalf("save: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Errorf("unmet sqlmock expectations: %v", err)
	}
}

func TestSaveRecord_InsertHistoryError(t *testing.T) {
	// The first write failing aborts the transaction before any consent_detail change.
	s, mock := newMockService(t)

	mock.ExpectBegin()
	mock.ExpectExec("INSERT INTO consent_history").WillReturnError(errors.New("insert failed"))
	mock.ExpectRollback()

	err := s.SaveRecord(context.Background(), &ConsentRecord{ID: "c1", ClientID: "client-1", UserID: "user-1"})
	if err == nil {
		t.Fatal("expected an error when inserting consent history fails")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Errorf("unmet sqlmock expectations (rollback not performed?): %v", err)
	}
}

func TestSaveRecord_UpsertError(t *testing.T) {
	// A mid-transaction failure (the consent_detail insert) must roll back the whole
	// unit, so the history snapshot written moments earlier is never committed — the
	// acceptance criterion that a partial failure leaves no partial data.
	s, mock := newMockService(t)

	mock.ExpectBegin()
	mock.ExpectExec("INSERT INTO consent_history").WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectExec("DELETE FROM consent_detail").WillReturnResult(sqlmock.NewResult(0, 0))
	mock.ExpectExec("INSERT INTO consent_detail").WillReturnError(errors.New("insert failed"))
	mock.ExpectRollback()

	err := s.SaveRecord(context.Background(), &ConsentRecord{ID: "c1", ClientID: "client-1", UserID: "user-1"})
	if err == nil {
		t.Fatal("expected an error when the consent insert fails")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Errorf("unmet sqlmock expectations (rollback not performed?): %v", err)
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
