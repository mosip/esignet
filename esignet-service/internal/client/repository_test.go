package client

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

// =============================================================================
// Test helpers — fake pgxQuerier and pgx.Row
// =============================================================================

// fakeQuerier records each call so tests can assert on SQL and args.
type fakeQuerier struct {
	queryRowSQL  string
	queryRowArgs []any
	queryRowRow  pgx.Row // row returned to caller; tests pre-populate

	execSQL  string
	execArgs []any
	execErr  error
}

func (f *fakeQuerier) QueryRow(_ context.Context, sql string, args ...any) pgx.Row {
	f.queryRowSQL = sql
	f.queryRowArgs = args
	return f.queryRowRow
}

func (f *fakeQuerier) Exec(_ context.Context, sql string, args ...any) (pgconn.CommandTag, error) {
	f.execSQL = sql
	f.execArgs = args
	return pgconn.CommandTag{}, f.execErr
}

// fakeRow scans a single bool destination — what `SELECT EXISTS(...)` returns.
type fakeRow struct {
	exists  bool
	scanErr error
}

func (r *fakeRow) Scan(dest ...any) error {
	if r.scanErr != nil {
		return r.scanErr
	}
	if len(dest) != 1 {
		return errors.New("fakeRow: expected exactly one Scan destination")
	}
	if d, ok := dest[0].(*bool); ok {
		*d = r.exists
		return nil
	}
	return errors.New("fakeRow: expected *bool destination")
}

func newClientRepoWithQuerier(q pgxQuerier) *Repository {
	return &Repository{pool: q}
}

// =============================================================================
// ExistsByID
// =============================================================================

func TestClientRepo_ExistsByID_QueryShape(t *testing.T) {
	q := &fakeQuerier{queryRowRow: &fakeRow{exists: true}}
	repo := newClientRepoWithQuerier(q)

	got, err := repo.ExistsByID(context.Background(), "abc-123")
	if err != nil {
		t.Fatalf("ExistsByID: %v", err)
	}
	if !got {
		t.Error("expected exists=true, got false")
	}

	if !strings.Contains(q.queryRowSQL, "SELECT EXISTS") {
		t.Errorf("query missing SELECT EXISTS: %q", q.queryRowSQL)
	}
	if !strings.Contains(q.queryRowSQL, "FROM client_detail WHERE id = $1") {
		t.Errorf("query shape unexpected: %q", q.queryRowSQL)
	}
	if len(q.queryRowArgs) != 1 || q.queryRowArgs[0] != "abc-123" {
		t.Errorf("query args: got %v, want [abc-123]", q.queryRowArgs)
	}
}

func TestClientRepo_ExistsByID_FalseWhenAbsent(t *testing.T) {
	q := &fakeQuerier{queryRowRow: &fakeRow{exists: false}}
	repo := newClientRepoWithQuerier(q)

	got, err := repo.ExistsByID(context.Background(), "missing")
	if err != nil {
		t.Fatalf("ExistsByID: %v", err)
	}
	if got {
		t.Error("expected exists=false")
	}
}

func TestClientRepo_ExistsByID_ScanError(t *testing.T) {
	q := &fakeQuerier{queryRowRow: &fakeRow{scanErr: errors.New("conn dead")}}
	repo := newClientRepoWithQuerier(q)

	_, err := repo.ExistsByID(context.Background(), "x")
	if err == nil {
		t.Fatal("expected error on scan failure")
	}
	if !strings.Contains(err.Error(), "exists check") {
		t.Errorf("error should be wrapped with context, got %v", err)
	}
}

// =============================================================================
// Insert — SQL shape + parameter order
// =============================================================================

func sampleRow() *DetailRow {
	addCfg := `{"userinfo_response_type":"JWS"}`
	return &DetailRow{
		ID:               "client-001",
		Name:             `{"eng":"Test Client"}`,
		RpID:             "rp-001",
		LogoURI:          "https://example.com/logo.png",
		RedirectURIs:     `["https://example.com/cb"]`,
		Claims:           `["name","email"]`,
		ACRValues:        `["mosip:idp:acr:biometrics"]`,
		PublicKey:        `{"kty":"RSA","n":"abc","e":"AQAB"}`,
		PublicKeyHash:    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
		GrantTypes:       `["authorization_code"]`,
		AuthMethods:      `["private_key_jwt"]`,
		Status:           "ACTIVE",
		AdditionalConfig: &addCfg,
		CrDtimes:         time.Date(2026, 5, 25, 12, 0, 0, 0, time.UTC),
	}
}

func TestClientRepo_Insert_QueryShape(t *testing.T) {
	q := &fakeQuerier{}
	repo := newClientRepoWithQuerier(q)

	if err := repo.Insert(context.Background(), sampleRow()); err != nil {
		t.Fatalf("Insert: %v", err)
	}

	if !strings.Contains(q.execSQL, "INSERT INTO client_detail") {
		t.Errorf("missing INSERT into client_detail: %q", q.execSQL)
	}
	// Column order must match the VALUES bindings 1:1 — a swap here is a
	// silent data-corruption bug.
	for _, col := range []string{
		"id", "name", "rp_id", "logo_uri", "redirect_uris", "claims", "acr_values",
		"public_key", "public_key_hash", "grant_types", "auth_methods",
		"status", "additional_config", "cr_dtimes",
	} {
		if !strings.Contains(q.execSQL, col) {
			t.Errorf("missing column %q in INSERT: %q", col, q.execSQL)
		}
	}
	// $1..$14 — 14 columns bound positionally.
	for _, ph := range []string{"$1", "$2", "$3", "$4", "$5", "$6", "$7", "$8", "$9", "$10", "$11", "$12", "$13", "$14"} {
		if !strings.Contains(q.execSQL, ph) {
			t.Errorf("missing placeholder %s in INSERT", ph)
		}
	}
	if strings.Contains(q.execSQL, "$15") {
		t.Errorf("unexpected $15 placeholder (only 14 columns): %q", q.execSQL)
	}
}

func TestClientRepo_Insert_ParameterOrder(t *testing.T) {
	q := &fakeQuerier{}
	repo := newClientRepoWithQuerier(q)
	row := sampleRow()

	if err := repo.Insert(context.Background(), row); err != nil {
		t.Fatalf("Insert: %v", err)
	}

	if len(q.execArgs) != 14 {
		t.Fatalf("expected 14 args, got %d", len(q.execArgs))
	}
	// Positional bindings must match the column list, in order. A swap
	// would write fields into the wrong columns with no compile-time signal.
	want := []any{
		row.ID, row.Name, row.RpID, row.LogoURI, row.RedirectURIs, row.Claims, row.ACRValues,
		row.PublicKey, row.PublicKeyHash, row.GrantTypes, row.AuthMethods,
		row.Status, row.AdditionalConfig, row.CrDtimes,
	}
	for i := range want {
		if !equalArg(q.execArgs[i], want[i]) {
			t.Errorf("arg[%d]: got %v (%T), want %v (%T)", i, q.execArgs[i], q.execArgs[i], want[i], want[i])
		}
	}
}

// equalArg compares two args, handling *string (AdditionalConfig) and
// time.Time (CrDtimes) cases that don't compare with ==.
func equalArg(got, want any) bool {
	if g, ok := got.(*string); ok {
		if w, ok2 := want.(*string); ok2 {
			if g == nil && w == nil {
				return true
			}
			if g == nil || w == nil {
				return false
			}
			return *g == *w
		}
	}
	if g, ok := got.(time.Time); ok {
		if w, ok2 := want.(time.Time); ok2 {
			return g.Equal(w)
		}
	}
	return got == want
}

// =============================================================================
// Insert — unique-violation mapping by constraint name
// =============================================================================

func TestClientRepo_Insert_DuplicateClientID(t *testing.T) {
	q := &fakeQuerier{execErr: &pgconn.PgError{Code: "23505", ConstraintName: "pk_clntdtl_id"}}
	repo := newClientRepoWithQuerier(q)

	err := repo.Insert(context.Background(), sampleRow())
	if err == nil {
		t.Fatal("expected error")
	}
	if !errors.Is(err, ErrDuplicateClientID) {
		t.Errorf("expected ErrDuplicateClientID, got %v", err)
	}
	if errors.Is(err, ErrDuplicatePublicKey) {
		t.Error("must not also match ErrDuplicatePublicKey")
	}
}

func TestClientRepo_Insert_DuplicatePublicKey(t *testing.T) {
	q := &fakeQuerier{execErr: &pgconn.PgError{Code: "23505", ConstraintName: "uk_clntdtl_public_key_hash"}}
	repo := newClientRepoWithQuerier(q)

	err := repo.Insert(context.Background(), sampleRow())
	if err == nil {
		t.Fatal("expected error")
	}
	if !errors.Is(err, ErrDuplicatePublicKey) {
		t.Errorf("expected ErrDuplicatePublicKey, got %v", err)
	}
	if errors.Is(err, ErrDuplicateClientID) {
		t.Error("must not also match ErrDuplicateClientID")
	}
}

func TestClientRepo_Insert_UnknownConstraint(t *testing.T) {
	// 23505 on an unknown constraint → wrap the raw error; don't map to
	// a duplicate sentinel.
	q := &fakeQuerier{execErr: &pgconn.PgError{Code: "23505", ConstraintName: "uk_some_future_constraint"}}
	repo := newClientRepoWithQuerier(q)

	err := repo.Insert(context.Background(), sampleRow())
	if err == nil {
		t.Fatal("expected error")
	}
	if errors.Is(err, ErrDuplicateClientID) || errors.Is(err, ErrDuplicatePublicKey) {
		t.Errorf("must not match either duplicate sentinel: %v", err)
	}
}

func TestClientRepo_Insert_NonUniqueError(t *testing.T) {
	// Non-23505 errors are surfaced as-is, not silently mapped to a duplicate.
	q := &fakeQuerier{execErr: &pgconn.PgError{Code: "08006", Message: "connection failure"}}
	repo := newClientRepoWithQuerier(q)

	err := repo.Insert(context.Background(), sampleRow())
	if err == nil {
		t.Fatal("expected error")
	}
	if errors.Is(err, ErrDuplicateClientID) || errors.Is(err, ErrDuplicatePublicKey) {
		t.Errorf("non-unique error must not map to duplicate sentinel: %v", err)
	}
}

// Compile-time interface checks for the test fakes.
var _ pgx.Row = (*fakeRow)(nil)
var _ pgxQuerier = (*fakeQuerier)(nil)
