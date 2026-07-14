package consent

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/google/uuid"

	"github.com/mosip/esignet/internal/consent/db"
)

// Store persists consent records in the consent_detail Postgres table via the sqlc-generated
// query layer. Each (client_id, psu_token) pair holds at most one record in consent_detail
// (unique constraint), while consent_history keeps an append-only snapshot of every consent taken.
type Store struct {
	db *sql.DB
	q  db.Querier
}

// NewConsentStore creates a Postgres-backed consent store over the given connection.
func NewConsentStore(conn *sql.DB) *Store {
	return &Store{db: conn, q: db.New(conn)}
}

// NewConsentStoreWithQuerier creates a store with an explicit Querier; use in tests to inject a
// mock without a real database connection. With no *sql.DB, save writes run without a transaction.
func NewConsentStoreWithQuerier(q db.Querier) *Store {
	return &Store{q: q}
}

// get returns the stored consent for the (clientID, userID) pair. The boolean is false (with a nil
// error) when no record exists.
func (s *Store) get(ctx context.Context, clientID, userID string) (*storedConsent, bool, error) {
	row, err := s.q.GetConsent(ctx, db.GetConsentParams{ClientID: clientID, PsuToken: userID})
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, false, nil
		}
		return nil, false, fmt.Errorf("query consent: %w", err)
	}

	c := &storedConsent{
		ID:                  row.ID,
		ClientID:            row.ClientID,
		UserID:              row.PsuToken,
		Claims:              row.Claims,
		AuthorizationScopes: row.AuthorizationScopes,
		Hash:                row.Hash.String,
		CreatedAt:           row.CrDtimes,
		ExpiresAt:           row.ExpireDtimes,
	}
	if c.AcceptedClaims, err = decodeStringSlice(row.AcceptedClaims); err != nil {
		return nil, false, fmt.Errorf("decode accepted_claims: %w", err)
	}
	if c.PermittedScopes, err = decodeStringSlice(row.PermittedScopes); err != nil {
		return nil, false, fmt.Errorf("decode permitted_scopes: %w", err)
	}
	return c, true, nil
}

// save persists a new consent for its (client_id, psu_token) pair. Mirroring the Java
// ConsentServiceImpl.saveUserConsent, it appends a snapshot of the new consent to consent_history
// (append-only audit trail) and inserts-or-replaces the current row in consent_detail, both within
// a single transaction so the history snapshot and the current row commit atomically.
func (s *Store) save(ctx context.Context, c *storedConsent) error {
	acceptedClaimsJSON, err := json.Marshal(nonNilSlice(c.AcceptedClaims))
	if err != nil {
		return fmt.Errorf("encode accepted_claims: %w", err)
	}
	permittedScopesJSON, err := json.Marshal(nonNilSlice(c.PermittedScopes))
	if err != nil {
		return fmt.Errorf("encode permitted_scopes: %w", err)
	}

	historyParams := db.InsertConsentHistoryParams{
		ID:                  uuid.NewString(),
		ClientID:            c.ClientID,
		PsuToken:            c.UserID,
		Claims:              c.Claims,
		AuthorizationScopes: c.AuthorizationScopes,
		Hash:                nullString(c.Hash),
		AcceptedClaims:      nullString(string(acceptedClaimsJSON)),
		PermittedScopes:     nullString(string(permittedScopesJSON)),
		CrDtimes:            c.CreatedAt,
		ExpireDtimes:        c.ExpiresAt,
	}
	detailParams := db.UpsertConsentParams{
		ID:                  c.ID,
		ClientID:            c.ClientID,
		PsuToken:            c.UserID,
		Claims:              c.Claims,
		AuthorizationScopes: c.AuthorizationScopes,
		Hash:                nullString(c.Hash),
		AcceptedClaims:      nullString(string(acceptedClaimsJSON)),
		PermittedScopes:     nullString(string(permittedScopesJSON)),
		CrDtimes:            c.CreatedAt,
		ExpireDtimes:        c.ExpiresAt,
	}

	q := s.q
	var tx *sql.Tx
	if s.db != nil {
		if tx, err = s.db.BeginTx(ctx, nil); err != nil {
			return fmt.Errorf("begin consent tx: %w", err)
		}
		defer func() { _ = tx.Rollback() }()
		q = db.New(tx)
	}

	if err := q.InsertConsentHistory(ctx, historyParams); err != nil {
		return fmt.Errorf("insert consent history: %w", err)
	}
	if err := q.UpsertConsent(ctx, detailParams); err != nil {
		return fmt.Errorf("upsert consent: %w", err)
	}

	if tx != nil {
		if err := tx.Commit(); err != nil {
			return fmt.Errorf("commit consent tx: %w", err)
		}
	}
	return nil
}

// delete removes the current consent for the (clientID, userID) pair from consent_detail. It
// mirrors Java ConsentServiceImpl.deleteUserConsent and is a no-op when no row exists. The
// consent_history audit trail is left untouched.
func (s *Store) delete(ctx context.Context, clientID, userID string) error {
	if err := s.q.DeleteConsent(ctx, db.DeleteConsentParams{ClientID: clientID, PsuToken: userID}); err != nil {
		return fmt.Errorf("delete consent: %w", err)
	}
	return nil
}

// decodeStringSlice decodes a nullable JSON-array column into a string slice. A NULL or empty
// value yields a nil slice.
func decodeStringSlice(v sql.NullString) ([]string, error) {
	if !v.Valid || v.String == "" {
		return nil, nil
	}
	var out []string
	if err := json.Unmarshal([]byte(v.String), &out); err != nil {
		return nil, err
	}
	return out, nil
}

// nonNilSlice returns an empty slice instead of nil so JSON encodes "[]" rather than "null".
func nonNilSlice(s []string) []string {
	if s == nil {
		return []string{}
	}
	return s
}

// nullString wraps a string as a valid sql.NullString, treating "" as SQL NULL.
func nullString(s string) sql.NullString {
	return sql.NullString{String: s, Valid: s != ""}
}
