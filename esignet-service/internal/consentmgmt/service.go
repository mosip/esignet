/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package consentmgmt implements the esignet consent-action decision logic
package consentmgmt

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"

	"github.com/mosip/esignet/internal/consentmgmt/db"
	applog "github.com/mosip/esignet/internal/log"
)

// Service persists consent records in the consent_detail table via a hand-written sqlx
// query layer. Each (client_id, psu_token) pair holds at most one record in consent_detail
// (unique constraint), while consent_history keeps an append-only snapshot of every consent taken.
type Service struct {
	db     *sqlx.DB
	q      *db.Queries
	logger *applog.Logger
}

// NewService creates a Service backed by the given database connection. The driver name is
// derived from the connection (conn.DriverName()) and threaded into db.New so queries are
// rebound to the active dialect's placeholder style.
func NewService(conn *sqlx.DB) *Service {
	return &Service{
		db:     conn,
		q:      db.New(conn, conn.DriverName()),
		logger: applog.GetLogger().Named("consentmgmt"),
	}
}

// FetchRecord returns the stored consent for the (clientID, userID) pair. It returns a nil
// ConsentRecord (with a nil error) when no record exists.
func (s *Service) FetchRecord(ctx context.Context, clientID, userID string) (*ConsentRecord, error) {
	return s.fetch(ctx, s.q, clientID, userID, false)
}

// fetch reads the current consent of the pair through q, a connection or a transaction, and
// decodes it; a nil record with a nil error means no row exists. With forUpdate the row stays
// locked until q's transaction ends, which serialises a read-merge-write of the pair across
// connections and replicas.
func (s *Service) fetch(ctx context.Context, q db.Querier, clientID, userID string, forUpdate bool) (*ConsentRecord, error) {
	params := db.GetConsentParams{ClientID: clientID, PsuToken: userID}
	var (
		row db.ConsentDetail
		err error
	)
	if forUpdate {
		row, err = q.GetConsentForUpdate(ctx, params)
	} else {
		row, err = q.GetConsent(ctx, params)
	}
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			s.logger.Debug(ctx, "no stored consent record", applog.String("clientId", clientID))
			return nil, nil
		}
		return nil, fmt.Errorf("query consent: %w", err)
	}
	return decodeRecord(row)
}

// decodeRecord turns a consent_detail row into a ConsentRecord.
func decodeRecord(row db.ConsentDetail) (*ConsentRecord, error) {
	var claims map[string]any
	if err := json.Unmarshal([]byte(row.Claims), &claims); err != nil {
		return nil, fmt.Errorf("query consent: %w", err)
	}

	var scopeMap map[string]any
	if err := json.Unmarshal([]byte(row.AuthorizationScopes), &scopeMap); err != nil {
		return nil, fmt.Errorf("query consent: %w", err)
	}
	scopes := make([]string, 0, len(scopeMap))
	for scope := range scopeMap {
		scopes = append(scopes, scope)
	}

	c := &ConsentRecord{
		ID:                  row.ID,
		ClientID:            row.ClientID,
		UserID:              row.PsuToken,
		Claims:              claims,
		AuthorizationScopes: scopes,
		Hash:                row.Hash.String,
		CreatedAt:           row.CrDtimes,
		ExpiresAt:           row.ExpireDtimes,
	}
	var err error
	if c.AcceptedClaims, err = decodeStringSlice(row.AcceptedClaims); err != nil {
		return nil, fmt.Errorf("decode accepted_claims: %w", err)
	}
	if c.PermittedScopes, err = decodeStringSlice(row.PermittedScopes); err != nil {
		return nil, fmt.Errorf("decode permitted_scopes: %w", err)
	}
	return c, nil
}

// encodedConsent is a record ready to be written: the history snapshot parameters (a fresh
// snapshot id; detail gives the consent_detail variant) and the normalized counts for logging.
type encodedConsent struct {
	history    db.InsertConsentHistoryParams
	claimCount int
	scopeCount int
}

// detail returns the consent_detail parameters: the snapshot columns under the record's own id.
func (e encodedConsent) detail(id string) db.UpsertConsentParams {
	d := e.history
	d.ID = id
	return d
}

// encodeRecord encodes a record the way it is stored: claims and scopes normalized, the consent
// hash computed from them, and the accepted claims and permitted scopes as never-null JSON arrays.
func encodeRecord(c *ConsentRecord) (encodedConsent, error) {
	normalizedClaims := NormalizeClaims(c.Claims)
	claimsRequestJSON, err := json.Marshal(normalizedClaims)
	if err != nil {
		return encodedConsent{}, fmt.Errorf("encode claims request: %w", err)
	}
	normalizedScopes := NormalizeAuthorizationScopes(c.AuthorizationScopes)
	scopesJSON, err := json.Marshal(normalizedScopes)
	if err != nil {
		return encodedConsent{}, fmt.Errorf("encode requested scopes: %w", err)
	}
	hash, err := HashRequestedConsent(normalizedClaims, normalizedScopes)
	if err != nil {
		return encodedConsent{}, fmt.Errorf("hashing request consent: %w", err)
	}

	acceptedClaimsJSON, err := json.Marshal(nonNilSlice(c.AcceptedClaims))
	if err != nil {
		return encodedConsent{}, fmt.Errorf("encode accepted_claims: %w", err)
	}
	permittedScopesJSON, err := json.Marshal(nonNilSlice(c.PermittedScopes))
	if err != nil {
		return encodedConsent{}, fmt.Errorf("encode permitted_scopes: %w", err)
	}

	return encodedConsent{
		history: db.InsertConsentHistoryParams{
			ID:                  uuid.NewString(),
			ClientID:            c.ClientID,
			PsuToken:            c.UserID,
			Claims:              string(claimsRequestJSON),
			AuthorizationScopes: string(scopesJSON),
			Hash:                nullString(hash),
			AcceptedClaims:      nullString(string(acceptedClaimsJSON)),
			PermittedScopes:     nullString(string(permittedScopesJSON)),
			CrDtimes:            c.CreatedAt,
			ExpireDtimes:        c.ExpiresAt,
		},
		claimCount: len(normalizedClaims),
		scopeCount: len(normalizedScopes),
	}, nil
}

// writeRecord appends the history snapshot and upserts the consent_detail row with id through q.
func writeRecord(ctx context.Context, q db.Querier, id string, enc encodedConsent) error {
	if err := q.InsertConsentHistory(ctx, enc.history); err != nil {
		return fmt.Errorf("insert consent history: %w", err)
	}
	if err := q.UpsertConsent(ctx, enc.detail(id)); err != nil {
		return fmt.Errorf("upsert consent: %w", err)
	}
	return nil
}

// SaveRecord persists a new consent for its (client_id, psu_token) pair. Mirroring the Java
// ConsentServiceImpl.saveUserConsent, it appends a snapshot of the new consent to consent_history
// (append-only audit trail) and upserts the current row in consent_detail,
// both within a single transaction so the history snapshot and the current
// row commit atomically — a partial failure rolls back and leaves no partial data.
func (s *Service) SaveRecord(ctx context.Context, c *ConsentRecord) error {
	enc, err := encodeRecord(c)
	if err != nil {
		return err
	}

	// The history snapshot and the consent upsert must be all-or-nothing, so they run in one
	// transaction; the tx-bound Queries reuses the query strings already built in NewService.
	tx, err := s.db.BeginTxx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin consent tx: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	if err := writeRecord(ctx, s.q.WithTx(tx), c.ID, enc); err != nil {
		return err
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit consent tx: %w", err)
	}
	s.logger.Debug(ctx, "consent record saved",
		applog.String("clientId", c.ClientID),
		applog.Int("claimCount", enc.claimCount),
		applog.Int("scopeCount", enc.scopeCount))
	return nil
}

// UpdateFunc computes the consent to store for a (client, user) pair from the one currently
// stored, nil when there is none. A nil result leaves the stored consent as it is: nothing is
// written, not even a history row.
type UpdateFunc func(existing *ConsentRecord) (*ConsentRecord, error)

// UpdateRecord applies fn to the current consent of the (clientID, userID) pair and stores the
// result as SaveRecord would, history snapshot and consent_detail row in one transaction. The pair
// is read under a row lock the transaction holds until it commits, so concurrent updates from any
// connection or replica run one after the other and each sees the previous result: a widening is
// never overwritten by another. When no row exists yet the insert is conditional, and a caller
// that loses that race to a concurrent first insert re-reads the committed row under the lock and
// applies fn to that instead.
func (s *Service) UpdateRecord(ctx context.Context, clientID, userID string, fn UpdateFunc) error {
	tx, err := s.db.BeginTxx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin consent tx: %w", err)
	}
	defer func() { _ = tx.Rollback() }()
	q := s.q.WithTx(tx)

	// The row is looked up before it is locked: locking the key of a row that does not exist yet
	// would let two first inserts on MySQL deadlock on each other's gap lock. A missing row is
	// created with the conditional insert below instead.
	existing, err := s.fetch(ctx, q, clientID, userID, false)
	if err != nil {
		return err
	}
	if existing != nil {
		if existing, err = s.fetch(ctx, q, clientID, userID, true); err != nil {
			return err
		}
	}
	record, err := fn(existing)
	if err != nil {
		return err
	}
	if record == nil {
		return s.commit(ctx, tx, clientID, "consent record unchanged")
	}

	if existing == nil {
		enc, err := encodeRecord(record)
		if err != nil {
			return err
		}
		inserted, err := q.InsertConsentIfAbsent(ctx, enc.detail(record.ID))
		if err != nil {
			return fmt.Errorf("insert consent: %w", err)
		}
		if inserted {
			if err := q.InsertConsentHistory(ctx, enc.history); err != nil {
				return fmt.Errorf("insert consent history: %w", err)
			}
			return s.commit(ctx, tx, clientID, "consent record saved")
		}
		// A concurrent transaction created the row first and has committed it: read it under the
		// lock and apply fn to it, so what that transaction stored is kept.
		if existing, err = s.fetch(ctx, q, clientID, userID, true); err != nil {
			return err
		}
		if existing == nil {
			return errors.New("update consent: the row was created and removed concurrently")
		}
		if record, err = fn(existing); err != nil {
			return err
		}
		if record == nil {
			return s.commit(ctx, tx, clientID, "consent record unchanged")
		}
	}

	enc, err := encodeRecord(record)
	if err != nil {
		return err
	}
	if err := writeRecord(ctx, q, record.ID, enc); err != nil {
		return err
	}
	return s.commit(ctx, tx, clientID, "consent record saved")
}

// commit ends tx and logs msg for clientID.
func (s *Service) commit(ctx context.Context, tx *sqlx.Tx, clientID, msg string) error {
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit consent tx: %w", err)
	}
	s.logger.Debug(ctx, msg, applog.String("clientId", clientID))
	return nil
}

// DeleteRecord removes the current consent for the (clientID, userID) pair from consent_detail. It
// mirrors Java ConsentServiceImpl.deleteUserConsent and is a no-op when no row exists. The
// consent_history audit trail is left untouched.
func (s *Service) DeleteRecord(ctx context.Context, clientID, userID string) error {
	if err := s.q.DeleteConsent(ctx, db.DeleteConsentParams{ClientID: clientID, PsuToken: userID}); err != nil {
		return fmt.Errorf("delete consent: %w", err)
	}
	s.logger.Debug(ctx, "consent record deleted", applog.String("clientId", clientID))
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
