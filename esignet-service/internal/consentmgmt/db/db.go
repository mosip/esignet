/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package db implements the consentmgmt persistence layer for the consent_detail
// and consent_history tables using jmoiron/sqlx. Queries are written with "?"
// placeholders and rebound once per driver in New (see persistence.Rebind), so
// the same query text runs on PostgreSQL and MySQL. The consent_detail upsert and
// the conditional insert are the dialect-specific statements, selected by driver
// name in New.
package db

import (
	"context"
	"database/sql"
	"time"

	"github.com/jmoiron/sqlx"

	"github.com/mosip/esignet/internal/persistence"
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

// consentInsertColumns/consentInsertValues are shared by the consent_history
// insert and the consent_detail upsert, which write the same ten columns.
const (
	consentInsertColumns = `id, client_id, psu_token, claims, authorization_scopes, hash,
		accepted_claims, permitted_scopes, cr_dtimes, expire_dtimes`
	consentInsertValues = `?, ?, ?, ?, ?, ?, ?, ?, ?, ?`
)

// Queries is the sqlx-backed Querier implementation. Query strings are built
// once in New, rebound to the driver's placeholder style, and shared by every
// Queries derived through WithTx.
type Queries struct {
	db TX

	getConsent            string
	getConsentForUpdate   string
	insertConsentHistory  string
	deleteConsent         string
	upsertConsent         string
	insertConsentIfAbsent string
}

var _ Querier = (*Queries)(nil)

// New returns a Queries backed by conn (a *sqlx.DB or *sqlx.Tx). driverName
// selects the placeholder style via persistence.Rebind and the upsert dialect,
// and must be the sql/sqlx driver name ("postgres"/"pgx" or "mysql"). Queries
// are written with "?" and rebound here once.
func New(conn TX, driverName string) *Queries {
	q := &Queries{db: conn}
	q.getConsent = persistence.Rebind(driverName,
		`SELECT `+consentColumns+` FROM consent_detail WHERE client_id = ? AND psu_token = ? LIMIT 1`)

	q.insertConsentHistory = persistence.Rebind(driverName,
		`INSERT INTO consent_history (`+consentInsertColumns+`) VALUES (`+consentInsertValues+`)`)

	q.deleteConsent = persistence.Rebind(driverName,
		`DELETE FROM consent_detail WHERE client_id = ? AND psu_token = ?`)

	q.upsertConsent = persistence.Rebind(driverName, upsertConsentQuery(driverName))

	// The locking read is the plain read plus FOR UPDATE, so the two can never drift apart.
	q.getConsentForUpdate = q.getConsent + ` FOR UPDATE`

	q.insertConsentIfAbsent = persistence.Rebind(driverName, insertConsentIfAbsentQuery(driverName))

	return q
}

// upsertConsentQuery returns the single-statement, atomic upsert for
// consent_detail keyed on the unique (client_id, psu_token) constraint. A
// delete-then-insert is not safe here: concurrent saves for the same pair can
// both pass the delete and then collide on the unique key. The row id is kept on
// conflict, matching the original ON CONFLICT behaviour.
func upsertConsentQuery(driverName string) string {
	insert := `INSERT INTO consent_detail (` + consentInsertColumns + `) VALUES (` + consentInsertValues + `)`
	if driverName == persistence.DriverMySQL {
		return insert + ` ON DUPLICATE KEY UPDATE
			claims = VALUES(claims),
			authorization_scopes = VALUES(authorization_scopes),
			hash = VALUES(hash),
			accepted_claims = VALUES(accepted_claims),
			permitted_scopes = VALUES(permitted_scopes),
			cr_dtimes = VALUES(cr_dtimes),
			expire_dtimes = VALUES(expire_dtimes)`
	}
	return insert + ` ON CONFLICT (client_id, psu_token) DO UPDATE SET
		claims = EXCLUDED.claims,
		authorization_scopes = EXCLUDED.authorization_scopes,
		hash = EXCLUDED.hash,
		accepted_claims = EXCLUDED.accepted_claims,
		permitted_scopes = EXCLUDED.permitted_scopes,
		cr_dtimes = EXCLUDED.cr_dtimes,
		expire_dtimes = EXCLUDED.expire_dtimes`
}

// insertConsentIfAbsentQuery returns the insert that creates the consent_detail row
// for a (client_id, psu_token) pair only when none exists yet. It affects no row
// otherwise, so the caller can tell a created row from one a concurrent insert
// created first.
func insertConsentIfAbsentQuery(driverName string) string {
	if driverName == persistence.DriverMySQL {
		return `INSERT IGNORE INTO consent_detail (` + consentInsertColumns + `) VALUES (` + consentInsertValues + `)`
	}
	return `INSERT INTO consent_detail (` + consentInsertColumns + `) VALUES (` + consentInsertValues + `)
		ON CONFLICT (client_id, psu_token) DO NOTHING`
}

// WithTx returns a Queries that runs against tx but reuses the already-rebound
// query strings, so a transaction does not rebuild them per request.
func (q *Queries) WithTx(tx TX) *Queries {
	c := *q
	c.db = tx
	return &c
}

// GetConsentParams holds the inputs for GetConsent.
type GetConsentParams struct {
	ClientID string `db:"client_id" json:"client_id"`
	PsuToken string `db:"psu_token" json:"psu_token"`
}

// DeleteConsentParams holds the inputs for DeleteConsent; it has the same shape
// as GetConsentParams.
type DeleteConsentParams GetConsentParams

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

// UpsertConsentParams holds the inputs for UpsertConsent; it is an alias of
// InsertConsentHistoryParams because both statements write the same ten columns.
type UpsertConsentParams = InsertConsentHistoryParams

// GetConsent implements Querier. The error is sql.ErrNoRows when no row exists
// (callers treat that as "no stored consent"), so it is surfaced to the caller.
func (q *Queries) GetConsent(ctx context.Context, arg GetConsentParams) (ConsentDetail, error) {
	var i ConsentDetail
	if err := q.db.GetContext(ctx, &i, q.getConsent, arg.ClientID, arg.PsuToken); err != nil {
		return ConsentDetail{}, err
	}
	return i, nil
}

// GetConsentForUpdate implements Querier — it reads the current consent row like
// GetConsent and keeps it locked until the surrounding transaction ends, so a
// read-merge-write of the pair is serialised across connections and replicas. It
// must run on a transaction.
func (q *Queries) GetConsentForUpdate(ctx context.Context, arg GetConsentParams) (ConsentDetail, error) {
	var i ConsentDetail
	if err := q.db.GetContext(ctx, &i, q.getConsentForUpdate, arg.ClientID, arg.PsuToken); err != nil {
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

// UpsertConsent implements Querier — it inserts the consent row, or updates the
// existing one for the (client_id, psu_token) pair, in a single atomic statement.
func (q *Queries) UpsertConsent(ctx context.Context, arg UpsertConsentParams) error {
	_, err := q.db.ExecContext(ctx, q.upsertConsent,
		arg.ID, arg.ClientID, arg.PsuToken, arg.Claims, arg.AuthorizationScopes,
		arg.Hash, arg.AcceptedClaims, arg.PermittedScopes, arg.CrDtimes, arg.ExpireDtimes)
	return err
}

// InsertConsentIfAbsent implements Querier — it inserts the consent row unless one
// already exists for the (client_id, psu_token) pair and reports whether it did.
// A false result with a nil error means a concurrent insert created the row first.
func (q *Queries) InsertConsentIfAbsent(ctx context.Context, arg UpsertConsentParams) (bool, error) {
	res, err := q.db.ExecContext(ctx, q.insertConsentIfAbsent,
		arg.ID, arg.ClientID, arg.PsuToken, arg.Claims, arg.AuthorizationScopes,
		arg.Hash, arg.AcceptedClaims, arg.PermittedScopes, arg.CrDtimes, arg.ExpireDtimes)
	if err != nil {
		return false, err
	}
	n, err := res.RowsAffected()
	if err != nil {
		return false, err
	}
	return n > 0, nil
}
