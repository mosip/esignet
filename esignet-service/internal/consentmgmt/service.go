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

	"github.com/mosip/esignet/internal/consentmgmt/db"
	applog "github.com/mosip/esignet/internal/log"
)

// Service persists consent records in the consent_detail Postgres table via the sqlc-generated
// query layer. Each (client_id, psu_token) pair holds at most one record in consent_detail
// (unique constraint), while consent_history keeps an append-only snapshot of every consent taken.
type Service struct {
	db     *sql.DB
	q      db.Querier
	logger *applog.Logger
}

// NewService creates a Service backed by the given database connection.
func NewService(conn *sql.DB) *Service {
	return &Service{q: db.New(conn), logger: applog.GetLogger().Named("consentmgmt")}
}

// NewServiceWithQuerier creates a Service with an explicit Querier; use in tests
// to inject a mock without a real database connection.
func NewServiceWithQuerier(q db.Querier) *Service {
	return &Service{q: q, logger: applog.GetLogger().Named("consentmgmt")}
}

// FetchRecord returns the stored consent for the (clientID, userID) pair. It returns a nil
// ConsentRecord (with a nil error) when no record exists.
func (s *Service) FetchRecord(ctx context.Context, clientID, userID string) (*ConsentRecord, error) {
	row, err := s.q.GetConsent(ctx, db.GetConsentParams{ClientID: clientID, PsuToken: userID})
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			s.logger.Debug("no stored consent record", applog.String("clientId", clientID))
			return nil, nil
		}
		return nil, fmt.Errorf("query consent: %w", err)
	}

	var claims map[string]any
	err = json.Unmarshal([]byte(row.Claims), &claims)
	if err != nil {
		return nil, fmt.Errorf("query consent: %w", err)
	}

	var scopeMap map[string]any
	err = json.Unmarshal([]byte(row.AuthorizationScopes), &scopeMap)
	if err != nil {
		return nil, fmt.Errorf("query consent: %w", err)
	}
	scopes := make([]string, 0, len(scopeMap))
	if len(scopeMap) > 0 {
		for scope := range scopeMap {
			scopes = append(scopes, scope)
		}
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
	if c.AcceptedClaims, err = decodeStringSlice(row.AcceptedClaims); err != nil {
		return nil, fmt.Errorf("decode accepted_claims: %w", err)
	}
	if c.PermittedScopes, err = decodeStringSlice(row.PermittedScopes); err != nil {
		return nil, fmt.Errorf("decode permitted_scopes: %w", err)
	}
	return c, nil
}

// SaveRecord persists a new consent for its (client_id, psu_token) pair. Mirroring the Java
// ConsentServiceImpl.saveUserConsent, it appends a snapshot of the new consent to consent_history
// (append-only audit trail) and inserts-or-replaces the current row in consent_detail, both within
// a single transaction so the history snapshot and the current row commit atomically.
func (s *Service) SaveRecord(ctx context.Context, c *ConsentRecord) error {
	normalizedClaims := NormalizeClaims(c.Claims)
	claimsRequestJSON, err := json.Marshal(normalizedClaims)
	if err != nil {
		return fmt.Errorf("encode claims request: %w", err)
	}
	normalizedScopes := NormalizeAuthorizationScopes(c.AuthorizationScopes)
	scopesJSON, err := json.Marshal(normalizedScopes)
	if err != nil {
		return fmt.Errorf("encode requested scopes: %w", err)
	}
	hash, err := HashRequestedConsent(normalizedClaims, normalizedScopes)
	if err != nil {
		return fmt.Errorf("hashing request consent: %w", err)
	}

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
		Claims:              string(claimsRequestJSON),
		AuthorizationScopes: string(scopesJSON),
		Hash:                nullString(hash),
		AcceptedClaims:      nullString(string(acceptedClaimsJSON)),
		PermittedScopes:     nullString(string(permittedScopesJSON)),
		CrDtimes:            c.CreatedAt,
		ExpireDtimes:        c.ExpiresAt,
	}
	detailParams := db.UpsertConsentParams{
		ID:                  c.ID,
		ClientID:            c.ClientID,
		PsuToken:            c.UserID,
		Claims:              string(claimsRequestJSON),
		AuthorizationScopes: string(scopesJSON),
		Hash:                nullString(hash),
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
	s.logger.Debug("consent record saved",
		applog.String("clientId", c.ClientID),
		applog.Int("claimCount", len(normalizedClaims)),
		applog.Int("scopeCount", len(normalizedScopes)))
	return nil
}

// DeleteRecord removes the current consent for the (clientID, userID) pair from consent_detail. It
// mirrors Java ConsentServiceImpl.deleteUserConsent and is a no-op when no row exists. The
// consent_history audit trail is left untouched.
func (s *Service) DeleteRecord(ctx context.Context, clientID, userID string) error {
	if err := s.q.DeleteConsent(ctx, db.DeleteConsentParams{ClientID: clientID, PsuToken: userID}); err != nil {
		return fmt.Errorf("delete consent: %w", err)
	}
	s.logger.Debug("consent record deleted", applog.String("clientId", clientID))
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
