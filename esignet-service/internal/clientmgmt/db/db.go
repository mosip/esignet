/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package db implements the clientmgmt persistence layer for the client_detail
// table using jmoiron/sqlx. Queries are written with "?" placeholders and
// rebound once per driver in New (see dbutil.Rebind), so the same query text
// runs on PostgreSQL and MySQL. (Originally sqlc-generated; now hand-written —
// the public surface, Querier/ClientDetail/*Params, is unchanged.)
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

// clientColumns is the full client_detail column list, in ClientDetail field
// order, shared by every SELECT so sqlx.GetContext maps all columns by db tag.
const clientColumns = `id, name, rp_id, logo_uri, redirect_uris, claims, acr_values,
	public_key, public_key_hash, enc_public_key, enc_public_key_hash, enc_public_key_cert,
	grant_types, auth_methods, status, additional_config, cr_dtimes, upd_dtimes`

// Queries is the sqlx-backed Querier implementation. Query strings are built
// once in New, rebound to the driver's placeholder style.
type Queries struct {
	db TX

	getClient               string
	getActiveClient         string
	createClient            string
	updateClient            string
	patchClientExpected     string
	patchClientExpectedNull string
}

var _ Querier = (*Queries)(nil)

// New returns a Queries backed by conn (a *sqlx.DB or *sqlx.Tx). driverName
// selects the placeholder style via dbutil.Rebind and must be the sql/sqlx
// driver name ("postgres"/"pgx" or "mysql"). Queries are written with "?" and
// rebound here once.
func New(conn TX, driverName string) *Queries {
	q := &Queries{db: conn}

	q.getClient = dbutil.Rebind(driverName,
		`SELECT `+clientColumns+` FROM client_detail WHERE id = ? LIMIT 1`)

	q.getActiveClient = dbutil.Rebind(driverName,
		`SELECT `+clientColumns+` FROM client_detail WHERE id = ? AND status = 'ACTIVE' LIMIT 1`)

	q.createClient = dbutil.Rebind(driverName, `INSERT INTO client_detail (
		id, name, rp_id, logo_uri, redirect_uris, claims, acr_values, public_key,
		public_key_hash, enc_public_key, enc_public_key_hash, enc_public_key_cert,
		grant_types, auth_methods, status, additional_config, cr_dtimes
	) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)

	// Placeholders are positional: SET columns first, then the WHERE id last —
	// UpdateClient binds its args in that order.
	q.updateClient = dbutil.Rebind(driverName, `UPDATE client_detail SET
		name = ?, logo_uri = ?, redirect_uris = ?, claims = ?, acr_values = ?,
		grant_types = ?, auth_methods = ?, status = ?, additional_config = ?, upd_dtimes = ?
		WHERE id = ?`)

	// PatchClient carries an optimistic-lock guard on upd_dtimes. Postgres'
	// null-safe "IS NOT DISTINCT FROM" isn't portable, so the guard is expressed
	// as two variants chosen in Go by whether the expected value is NULL.
	const patchSet = `UPDATE client_detail SET
		name = ?, logo_uri = ?, redirect_uris = ?, claims = ?, acr_values = ?,
		grant_types = ?, auth_methods = ?, status = ?, additional_config = ?,
		enc_public_key = ?, enc_public_key_hash = ?, enc_public_key_cert = ?, upd_dtimes = ?
		WHERE id = ?`
	q.patchClientExpected = dbutil.Rebind(driverName, patchSet+` AND upd_dtimes = ?`)
	q.patchClientExpectedNull = dbutil.Rebind(driverName, patchSet+` AND upd_dtimes IS NULL`)

	return q
}

// CreateClientParams holds the inputs for CreateClient (unchanged from the
// previous sqlc-generated shape).
type CreateClientParams struct {
	ID               string         `db:"id" json:"id"`
	Name             string         `db:"name" json:"name"`
	RpID             string         `db:"rp_id" json:"rp_id"`
	LogoUri          string         `db:"logo_uri" json:"logo_uri"`
	RedirectUris     string         `db:"redirect_uris" json:"redirect_uris"`
	Claims           string         `db:"claims" json:"claims"`
	AcrValues        string         `db:"acr_values" json:"acr_values"`
	PublicKey        string         `db:"public_key" json:"public_key"`
	PublicKeyHash    string         `db:"public_key_hash" json:"public_key_hash"`
	EncPublicKey     sql.NullString `db:"enc_public_key" json:"enc_public_key"`
	EncPublicKeyHash sql.NullString `db:"enc_public_key_hash" json:"enc_public_key_hash"`
	EncPublicKeyCert sql.NullString `db:"enc_public_key_cert" json:"enc_public_key_cert"`
	GrantTypes       string         `db:"grant_types" json:"grant_types"`
	AuthMethods      string         `db:"auth_methods" json:"auth_methods"`
	Status           string         `db:"status" json:"status"`
	AdditionalConfig sql.NullString `db:"additional_config" json:"additional_config"`
	CrDtimes         time.Time      `db:"cr_dtimes" json:"cr_dtimes"`
}

// UpdateClientParams holds the inputs for UpdateClient.
type UpdateClientParams struct {
	ID               string         `db:"id" json:"id"`
	Name             string         `db:"name" json:"name"`
	LogoUri          string         `db:"logo_uri" json:"logo_uri"`
	RedirectUris     string         `db:"redirect_uris" json:"redirect_uris"`
	Claims           string         `db:"claims" json:"claims"`
	AcrValues        string         `db:"acr_values" json:"acr_values"`
	GrantTypes       string         `db:"grant_types" json:"grant_types"`
	AuthMethods      string         `db:"auth_methods" json:"auth_methods"`
	Status           string         `db:"status" json:"status"`
	AdditionalConfig sql.NullString `db:"additional_config" json:"additional_config"`
	UpdDtimes        sql.NullTime   `db:"upd_dtimes" json:"upd_dtimes"`
}

// PatchClientParams holds the inputs for PatchClient. ExpectedUpdDtimes is the
// optimistic-lock guard: the update applies only if the row's current
// upd_dtimes still matches it (including the NULL case).
type PatchClientParams struct {
	ID                string         `db:"id" json:"id"`
	Name              string         `db:"name" json:"name"`
	LogoUri           string         `db:"logo_uri" json:"logo_uri"`
	RedirectUris      string         `db:"redirect_uris" json:"redirect_uris"`
	Claims            string         `db:"claims" json:"claims"`
	AcrValues         string         `db:"acr_values" json:"acr_values"`
	GrantTypes        string         `db:"grant_types" json:"grant_types"`
	AuthMethods       string         `db:"auth_methods" json:"auth_methods"`
	Status            string         `db:"status" json:"status"`
	AdditionalConfig  sql.NullString `db:"additional_config" json:"additional_config"`
	EncPublicKey      sql.NullString `db:"enc_public_key" json:"enc_public_key"`
	EncPublicKeyHash  sql.NullString `db:"enc_public_key_hash" json:"enc_public_key_hash"`
	EncPublicKeyCert  sql.NullString `db:"enc_public_key_cert" json:"enc_public_key_cert"`
	UpdDtimes         sql.NullTime   `db:"upd_dtimes" json:"upd_dtimes"`
	ExpectedUpdDtimes sql.NullTime   `db:"expected_upd_dtimes" json:"expected_upd_dtimes"`
}

// GetClient implements Querier.
func (q *Queries) GetClient(ctx context.Context, id string) (ClientDetail, error) {
	var i ClientDetail
	if err := q.db.GetContext(ctx, &i, q.getClient, id); err != nil {
		return ClientDetail{}, err
	}
	return i, nil
}

// GetActiveClient implements Querier.
func (q *Queries) GetActiveClient(ctx context.Context, id string) (ClientDetail, error) {
	var i ClientDetail
	if err := q.db.GetContext(ctx, &i, q.getActiveClient, id); err != nil {
		return ClientDetail{}, err
	}
	return i, nil
}

// CreateClient inserts a new client_detail row and returns it. The write and
// the read-back are separate statements (MySQL has no RETURNING); a
// unique-violation error from the INSERT is surfaced for the caller to classify
// (see clientmgmt.isDuplicateClientID / isDuplicatePublicKeyHash).
func (q *Queries) CreateClient(ctx context.Context, arg CreateClientParams) (ClientDetail, error) {
	_, err := q.db.ExecContext(ctx, q.createClient,
		arg.ID, arg.Name, arg.RpID, arg.LogoUri, arg.RedirectUris, arg.Claims, arg.AcrValues,
		arg.PublicKey, arg.PublicKeyHash, arg.EncPublicKey, arg.EncPublicKeyHash, arg.EncPublicKeyCert,
		arg.GrantTypes, arg.AuthMethods, arg.Status, arg.AdditionalConfig, arg.CrDtimes)
	if err != nil {
		return ClientDetail{}, err
	}
	return q.GetClient(ctx, arg.ID)
}

// UpdateClient updates a client_detail row and returns it. The follow-up read
// yields sql.ErrNoRows when the id doesn't exist, preserving the not-found
// semantics the caller expects.
func (q *Queries) UpdateClient(ctx context.Context, arg UpdateClientParams) (ClientDetail, error) {
	_, err := q.db.ExecContext(ctx, q.updateClient,
		arg.Name, arg.LogoUri, arg.RedirectUris, arg.Claims, arg.AcrValues,
		arg.GrantTypes, arg.AuthMethods, arg.Status, arg.AdditionalConfig, arg.UpdDtimes, arg.ID)
	if err != nil {
		return ClientDetail{}, err
	}
	return q.GetClient(ctx, arg.ID)
}

// PatchClient updates a client_detail row under an optimistic lock on
// upd_dtimes. RowsAffected()==0 means either the id is absent or the expected
// upd_dtimes no longer matches (concurrent modification); both map to
// sql.ErrNoRows, which the caller translates to ErrClientConflict — matching the
// previous RETURNING-based semantics. On success the row is read back.
func (q *Queries) PatchClient(ctx context.Context, arg PatchClientParams) (ClientDetail, error) {
	query := q.patchClientExpectedNull
	args := []interface{}{
		arg.Name, arg.LogoUri, arg.RedirectUris, arg.Claims, arg.AcrValues,
		arg.GrantTypes, arg.AuthMethods, arg.Status, arg.AdditionalConfig,
		arg.EncPublicKey, arg.EncPublicKeyHash, arg.EncPublicKeyCert, arg.UpdDtimes, arg.ID,
	}
	if arg.ExpectedUpdDtimes.Valid {
		query = q.patchClientExpected
		args = append(args, arg.ExpectedUpdDtimes)
	}

	res, err := q.db.ExecContext(ctx, query, args...)
	if err != nil {
		return ClientDetail{}, err
	}
	n, err := res.RowsAffected()
	if err != nil {
		return ClientDetail{}, err
	}
	if n == 0 {
		return ClientDetail{}, sql.ErrNoRows
	}
	return q.GetClient(ctx, arg.ID)
}
