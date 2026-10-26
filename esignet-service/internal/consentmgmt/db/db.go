/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package db implements the consentmgmt persistence layer for the consent_detail
// and consent_history tables using jmoiron/sqlx. Queries are written with "?"
// placeholders and rebound once per driver in New (see dbutil.Rebind), so the
// same query text runs on PostgreSQL and MySQL. (Originally sqlc-generated; now
// hand-written — the public surface, Querier/ConsentDetail/ConsentHistory/*Params,
// is unchanged.)
package db

import (
	"context"
	"database/sql"
	"time"

	"github.com/jmoiron/sqlx"

	"github.com/mosip/esignet/internal/dbutil"
)

// TX is satisfied by *sqlx.DB and *sqlx.Tx, allowing Queries to run against
// either a plain connection or a transaction.
type TX interface {
	sqlx.ExtContext
	GetContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error
	SelectContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error
}

// consentColumns is the full consent_detail column list, in ConsentDetail field
// order, shared by every SELECT so sqlx.GetContext maps all columns by db tag.
const consentColumns = `id, client_id, psu_token, claims, authorization_scopes,
	cr_dtimes, expire_dtimes, signature, hash, accepted_claims, permitted_scopes`

// Queries is the sqlx-backed Querier implementation. Query strings are built
// once in New, rebound to the driver's placeholder style.
type Queries struct {
	db TX

	getConsent           string
	insertConsentHistory string
	deleteConsent        string
	insertConsent        string
}

var _ Querier = (*Queries)(nil)

// New returns a Queries backed by conn (a *sqlx.DB or *sqlx.Tx). driverName
// selects the placeholder style via dbutil.Rebind and must be the sql/sqlx
// driver name ("postgres"/"pgx" or "mysql"). Queries are written with "?" and
// rebound here once.
func New(conn TX, driverName string) *Queries {
	q := &Queries{db: conn}

	q.getConsent = dbutil.Rebind(driverName,
		`SELECT `+consentColumns+` FROM consent_detail WHERE client_id = ? AND psu_token = ? LIMIT 1`)

	q.insertConsentHistory = dbutil.Rebind(driverName, `INSERT INTO consent_history (
		id, client_id, psu_token, claims, authorization_scopes, hash,
		accepted_claims, permitted_scopes, cr_dtimes, expire_dtimes
	) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)

	q.deleteConsent = dbutil.Rebind(driverName,
		`DELETE FROM consent_detail WHERE client_id = ? AND psu_token = ?`)

	q.insertConsent = dbutil.Rebind(driverName, `INSERT INTO consent_detail (
		id, client_id, psu_token, claims, authorization_scopes, hash,
		accepted_claims, permitted_scopes, cr_dtimes, expire_dtimes
	) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)

	return q
}

// GetConsentParams holds the inputs for GetConsent (unchanged from the previous
// sqlc-generated shape).
type GetConsentParams struct {
	ClientID string `db:"client_id" json:"client_id"`
	PsuToken string `db:"psu_token" json:"psu_token"`
}

// DeleteConsentParams holds the inputs for DeleteConsent.
type DeleteConsentParams struct {
	ClientID string `db:"client_id" json:"client_id"`
	PsuToken string `db:"psu_token" json:"psu_token"`
}

// InsertConsentHistoryParams holds the inputs for InsertConsentHistory.
type InsertConsentHistoryParams struct {
	ID                  string         `db:"id" json:"id"`
	ClientID            string         `db:"client_id" json:"client_id"`
	PsuToken            string         `db:"psu_token" json:"psu_token"`
	Claims              string         `db:"claims" json:"claims"`
	AuthorizationScopes string         `db:"authorization_scopes" json:"authorization_scopes"`
	Hash                sql.NullString `db:"hash" json:"hash"`
	AcceptedClaims      sql.NullString `db:"accepted_claims" json:"accepted_claims"`
	PermittedScopes     sql.NullString `db:"permitted_scopes" json:"permitted_scopes"`
	CrDtimes            time.Time      `db:"cr_dtimes" json:"cr_dtimes"`
	ExpireDtimes        sql.NullTime   `db:"expire_dtimes" json:"expire_dtimes"`
}

// UpsertConsentParams holds the inputs for UpsertConsent.
type UpsertConsentParams struct {
	ID                  string         `db:"id" json:"id"`
	ClientID            string         `db:"client_id" json:"client_id"`
	PsuToken            string         `db:"psu_token" json:"psu_token"`
	Claims              string         `db:"claims" json:"claims"`
	AuthorizationScopes string         `db:"authorization_scopes" json:"authorization_scopes"`
	Hash                sql.NullString `db:"hash" json:"hash"`
	AcceptedClaims      sql.NullString `db:"accepted_claims" json:"accepted_claims"`
	PermittedScopes     sql.NullString `db:"permitted_scopes" json:"permitted_scopes"`
	CrDtimes            time.Time      `db:"cr_dtimes" json:"cr_dtimes"`
	ExpireDtimes        sql.NullTime   `db:"expire_dtimes" json:"expire_dtimes"`
}

// GetConsent implements Querier. The error is sql.ErrNoRows when no row exists
// (callers treat that as "no stored consent"), so it is surfaced to the caller.
func (q *Queries) GetConsent(ctx context.Context, arg GetConsentParams) (ConsentDetail, error) {
	var i ConsentDetail
	if err := q.db.GetContext(ctx, &i, q.getConsent, arg.ClientID, arg.PsuToken); err != nil {
		return ConsentDetail{}, err
	}
	return i, nil
}

// InsertConsentHistory implements Querier — it appends a snapshot of a consent to
// the append-only consent_history audit trail.
func (q *Queries) InsertConsentHistory(ctx context.Context, arg InsertConsentHistoryParams) error {
	_, err := q.db.ExecContext(ctx, q.insertConsentHistory,
		arg.ID, arg.ClientID, arg.PsuToken, arg.Claims, arg.AuthorizationScopes,
		arg.Hash, arg.AcceptedClaims, arg.PermittedScopes, arg.CrDtimes, arg.ExpireDtimes)
	return err
}

// DeleteConsent implements Querier — it removes the current consent row for the
// (client_id, psu_token) pair.
func (q *Queries) DeleteConsent(ctx context.Context, arg DeleteConsentParams) error {
	_, err := q.db.ExecContext(ctx, q.deleteConsent, arg.ClientID, arg.PsuToken)
	return err
}

// UpsertConsent implements Querier. Postgres' ON CONFLICT upsert isn't portable,
// so the replace is expressed as a delete-then-insert. It issues two statements
// and therefore MUST run inside a transaction (SaveRecord provides one) for the
// replace to be atomic.
func (q *Queries) UpsertConsent(ctx context.Context, arg UpsertConsentParams) error {
	if _, err := q.db.ExecContext(ctx, q.deleteConsent, arg.ClientID, arg.PsuToken); err != nil {
		return err
	}
	_, err := q.db.ExecContext(ctx, q.insertConsent,
		arg.ID, arg.ClientID, arg.PsuToken, arg.Claims, arg.AuthorizationScopes,
		arg.Hash, arg.AcceptedClaims, arg.PermittedScopes, arg.CrDtimes, arg.ExpireDtimes)
	return err
}
