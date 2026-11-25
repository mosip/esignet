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

var consentDetailColumns = []string{
	"id", "client_id", "psu_token", "claims", "authorization_scopes",
	"cr_dtimes", "expire_dtimes", "signature", "hash", "accepted_claims", "permitted_scopes",
}

// consentRowValues flattens a ConsentDetail into driver values in consentDetailColumns order,
// unwrapping the sql.Null* fields the way a real driver would return them.
func consentRowValues(row db.ConsentDetail) []driver.Value {
	expire, _ := row.ExpireDtimes.Value()
	signature, _ := row.Signature.Value()
	hash, _ := row.Hash.Value()
	accepted, _ := row.AcceptedClaims.Value()
	permitted, _ := row.PermittedScopes.Value()
	return []driver.Value{
		row.ID, row.ClientID, row.PsuToken, row.Claims, row.AuthorizationScopes,
		row.CrDtimes, expire, signature, hash, accepted, permitted,
	}
}

// expectGetConsent scripts the consent_detail read issued by FetchRecord.
func expectGetConsent(mock sqlmock.Sqlmock, clientID, userID string, row db.ConsentDetail) {
	mock.ExpectQuery("FROM consent_detail WHERE").
		WithArgs(clientID, userID).
		WillReturnRows(sqlmock.NewRows(consentDetailColumns).AddRow(consentRowValues(row)...))
}

func TestFetchRecord_NotFound(t *testing.T) {
	s, mock := newMockService(t)
	mock.ExpectQuery("FROM consent_detail WHERE").
		WithArgs("client-1", "user-1").
		WillReturnError(sql.ErrNoRows)

	rec, err := s.FetchRecord(context.Background(), "client-1", "user-1")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if rec != nil {
		t.Fatalf("expected nil record when no row exists, got %#v", rec)
	}
}

func TestFetchRecord_QueryError(t *testing.T) {
	s, mock := newMockService(t)
	mock.ExpectQuery("FROM consent_detail WHERE").WillReturnError(errors.New("boom"))

	_, err := s.FetchRecord(context.Background(), "client-1", "user-1")
	if err == nil {
		t.Fatal("expected an error to be returned")
	}
}

func TestFetchRecord_DecodesStoredRow(t *testing.T) {
	createdAt := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	expiresAt := createdAt.Add(time.Hour)
	s, mock := newMockService(t)
	expectGetConsent(mock, "client-1", "user-1", db.ConsentDetail{
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
	})

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
	s, mock := newMockService(t)
	expectGetConsent(mock, "client-1", "user-1", db.ConsentDetail{
		ID:                  "consent-1",
		ClientID:            "client-1",
		PsuToken:            "user-1",
		Claims:              `{}`,
		AuthorizationScopes: `{}`,
	})

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
	s, mock := newMockService(t)
	expectGetConsent(mock, "client-1", "user-1", db.ConsentDetail{
		Claims:              `not-json`,
		AuthorizationScopes: `{}`,
	})

	if _, err := s.FetchRecord(context.Background(), "client-1", "user-1"); err == nil {
		t.Fatal("expected an error for malformed claims JSON")
	}
}

// newMockService builds a Service whose *sqlx.DB is backed by go-sqlmock, so the
// SaveRecord transaction (Begin → execs → Commit/Rollback) can be scripted and
// asserted without a real database. The "sqlmock" driver name leaves "?"
// placeholders in the query text, which the regexp statement matchers below ignore.
func newMockService(t *testing.T) (*Service, sqlmock.Sqlmock) {
	return newMockServiceFor(t, "sqlmock")
}

// newMockServiceFor is newMockService with an explicit sqlx driver name, so tests can
// exercise the dialect-specific placeholder and upsert SQL. The pool is capped at one
// connection: a query that bypassed the transaction would block on the pool instead of
// being silently accepted, and the call's context deadline then fails the test.
func newMockServiceFor(t *testing.T, driverName string) (*Service, sqlmock.Sqlmock) {
	t.Helper()
	mockDB, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New: %v", err)
	}
	mockDB.SetMaxOpenConns(1)
	t.Cleanup(func() { _ = mockDB.Close() })
	return NewService(sqlx.NewDb(mockDB, driverName)), mock
}

func testContext(t *testing.T) context.Context {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	t.Cleanup(cancel)
	return ctx
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
	// consent_detail is a single atomic upsert (no separate delete).
	mock.ExpectExec("INSERT INTO consent_detail").
		WithArgs("consent-1", "client-1", "user-1", jsonArgHasKey{"userinfo"}, sqlmock.AnyArg(),
			hashArg, sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectCommit()

	if err := s.SaveRecord(testContext(t), rec); err != nil {
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
	mock.ExpectExec("INSERT INTO consent_detail").WillReturnError(errors.New("insert failed"))
	mock.ExpectRollback()

	err := s.SaveRecord(testContext(t), &ConsentRecord{ID: "c1", ClientID: "client-1", UserID: "user-1"})
	if err == nil {
		t.Fatal("expected an error when the consent upsert fails")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Errorf("unmet sqlmock expectations (rollback not performed?): %v", err)
	}
}

func TestSaveRecord_BeginError(t *testing.T) {
	s, mock := newMockService(t)
	mock.ExpectBegin().WillReturnError(errors.New("begin failed"))

	if err := s.SaveRecord(testContext(t), &ConsentRecord{ID: "c1", ClientID: "client-1", UserID: "user-1"}); err == nil {
		t.Fatal("expected an error when the transaction cannot begin")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Errorf("unmet sqlmock expectations: %v", err)
	}
}

func TestSaveRecord_CommitError(t *testing.T) {
	s, mock := newMockService(t)
	mock.ExpectBegin()
	mock.ExpectExec("INSERT INTO consent_history").WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectExec("INSERT INTO consent_detail").WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectCommit().WillReturnError(errors.New("commit failed"))

	if err := s.SaveRecord(testContext(t), &ConsentRecord{ID: "c1", ClientID: "client-1", UserID: "user-1"}); err == nil {
		t.Fatal("expected an error when the commit fails")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Errorf("unmet sqlmock expectations: %v", err)
	}
}

// TestSaveRecord_UpsertDialect verifies the consent_detail write is one atomic upsert in each
// dialect's syntax, with the placeholder style the driver expects.
func TestSaveRecord_UpsertDialect(t *testing.T) {
	tests := []struct {
		driver string
		upsert string
	}{
		{"postgres", `VALUES \(\$1, \$2.*ON CONFLICT \(client_id, psu_token\) DO UPDATE SET`},
		{"mysql", `VALUES \(\?, \?.*ON DUPLICATE KEY UPDATE.*VALUES\(claims\)`},
	}
	for _, tc := range tests {
		t.Run(tc.driver, func(t *testing.T) {
			s, mock := newMockServiceFor(t, tc.driver)
			mock.ExpectBegin()
			mock.ExpectExec("INSERT INTO consent_history").WillReturnResult(sqlmock.NewResult(0, 1))
			mock.ExpectExec("(?s)INSERT INTO consent_detail.*" + tc.upsert).WillReturnResult(sqlmock.NewResult(0, 1))
			mock.ExpectCommit()

			if err := s.SaveRecord(testContext(t), &ConsentRecord{ID: "c1", ClientID: "client-1", UserID: "user-1"}); err != nil {
				t.Fatalf("save: %v", err)
			}
			if err := mock.ExpectationsWereMet(); err != nil {
				t.Errorf("unmet sqlmock expectations: %v", err)
			}
		})
	}
}

func TestDeleteRecord(t *testing.T) {
	s, mock := newMockService(t)
	mock.ExpectExec("DELETE FROM consent_detail").
		WithArgs("client-1", "user-1").
		WillReturnResult(sqlmock.NewResult(0, 1))

	if err := s.DeleteRecord(testContext(t), "client-1", "user-1"); err != nil {
		t.Fatalf("delete: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Errorf("unmet sqlmock expectations: %v", err)
	}
}

func TestDeleteRecord_Error(t *testing.T) {
	s, mock := newMockService(t)
	mock.ExpectExec("DELETE FROM consent_detail").WillReturnError(errors.New("delete failed"))

	if err := s.DeleteRecord(testContext(t), "client-1", "user-1"); err == nil {
		t.Fatal("expected an error when delete fails")
	}
}
