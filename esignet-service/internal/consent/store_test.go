package consent

import (
	"context"
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/mosip/esignet/internal/consent/db"
)

// mockQuerier records the calls made by ConsentStore so ordering and payloads can be asserted
// without a real database.
type mockQuerier struct {
	getRow     db.ConsentDetail
	getErr     error
	history    []db.InsertConsentHistoryParams
	historyErr error
	upserts    []db.UpsertConsentParams
	upsertErr  error
	deletes    []db.DeleteConsentParams
	deleteErr  error
	calls      []string
}

func (m *mockQuerier) GetConsent(_ context.Context, _ db.GetConsentParams) (db.ConsentDetail, error) {
	m.calls = append(m.calls, "get")
	return m.getRow, m.getErr
}

func (m *mockQuerier) InsertConsentHistory(_ context.Context, arg db.InsertConsentHistoryParams) error {
	m.calls = append(m.calls, "history")
	m.history = append(m.history, arg)
	return m.historyErr
}

func (m *mockQuerier) UpsertConsent(_ context.Context, arg db.UpsertConsentParams) error {
	m.calls = append(m.calls, "upsert")
	m.upserts = append(m.upserts, arg)
	return m.upsertErr
}

func (m *mockQuerier) DeleteConsent(_ context.Context, arg db.DeleteConsentParams) error {
	m.calls = append(m.calls, "delete")
	m.deletes = append(m.deletes, arg)
	return m.deleteErr
}

func testRecord() *storedConsent {
	return &storedConsent{
		ID:                  "detail-id",
		ClientID:            "client-1",
		UserID:              "user-1",
		Claims:              `{"userinfo":{},"id_token":{}}`,
		AuthorizationScopes: `{"resource.read":false}`,
		Hash:                "hash-1",
		AcceptedClaims:      []string{"email", "name"},
		PermittedScopes:     []string{"resource.read"},
		CreatedAt:           time.Now().UTC(),
	}
}

func TestStoreSave_WritesHistoryThenDetail(t *testing.T) {
	m := &mockQuerier{}
	s := NewConsentStoreWithQuerier(m)

	if err := s.save(context.Background(), testRecord()); err != nil {
		t.Fatalf("save: %v", err)
	}

	if want := []string{"history", "upsert"}; !equalStrings(m.calls, want) {
		t.Fatalf("call order = %v, want %v", m.calls, want)
	}
	if len(m.history) != 1 {
		t.Fatalf("expected one history row, got %d", len(m.history))
	}
	// History gets its own id, distinct from the current-detail row's id.
	if m.history[0].ID == "" || m.history[0].ID == "detail-id" {
		t.Fatalf("history id should be a fresh uuid, got %q", m.history[0].ID)
	}
	if m.upserts[0].ID != "detail-id" {
		t.Fatalf("detail id = %q, want detail-id", m.upserts[0].ID)
	}
	// History snapshot carries the same consent payload as the current row.
	if m.history[0].Hash != m.upserts[0].Hash ||
		m.history[0].AcceptedClaims.String != m.upserts[0].AcceptedClaims.String ||
		m.history[0].PermittedScopes.String != m.upserts[0].PermittedScopes.String {
		t.Fatal("history snapshot should match the persisted consent payload")
	}

	var accepted []string
	if err := json.Unmarshal([]byte(m.history[0].AcceptedClaims.String), &accepted); err != nil {
		t.Fatalf("accepted_claims not valid json: %v", err)
	}
	if len(accepted) != 2 {
		t.Fatalf("accepted_claims = %v, want two entries", accepted)
	}
}

func TestStoreSave_HistoryFailureStopsWrite(t *testing.T) {
	m := &mockQuerier{historyErr: errors.New("boom")}
	s := NewConsentStoreWithQuerier(m)

	if err := s.save(context.Background(), testRecord()); err == nil {
		t.Fatal("expected save to fail when history insert fails")
	}
	if len(m.upserts) != 0 {
		t.Fatal("detail must not be written when history insert fails")
	}
}

func TestStoreDelete_RemovesDetail(t *testing.T) {
	m := &mockQuerier{}
	s := NewConsentStoreWithQuerier(m)

	if err := s.delete(context.Background(), "client-1", "user-1"); err != nil {
		t.Fatalf("delete: %v", err)
	}
	if len(m.deletes) != 1 {
		t.Fatalf("expected one delete, got %d", len(m.deletes))
	}
	if m.deletes[0].ClientID != "client-1" || m.deletes[0].PsuToken != "user-1" {
		t.Fatalf("delete params = %+v", m.deletes[0])
	}
}

func equalStrings(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
