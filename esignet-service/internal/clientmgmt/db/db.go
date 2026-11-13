/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package db implements the clientmgmt persistence layer for the client_detail
// table using jmoiron/sqlx. Queries are written with "?" placeholders and
// rebound for the selected driver (see persistence.Rebind), so the same query
// text runs on PostgreSQL and MySQL. The public surface — Querier,
// ClientDetail, and the *Params types — is hand-maintained.
package db

import (
	"context"
	"database/sql"
	"strings"
	"time"

	"github.com/mosip/esignet/internal/persistence"
)

// TX is the subset of *sqlx.DB / *sqlx.Tx that Queries uses, so one Queries can
// run against either a pooled connection or an open transaction. Only the two
// methods the query layer actually calls are declared. (consentmgmt/db calls its
// sqlc-generated equivalent DBTX; that name stutters as db.DBTX under revive and
// is tolerated there only because generated files are lint-exempt, so this
// hand-written layer uses TX.)
type TX interface {
	ExecContext(ctx context.Context, query string, args ...interface{}) (sql.Result, error)
	GetContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error
}

// clientColumns is the full client_detail column list, in ClientDetail field
// order, shared by every SELECT (and the RETURNING clause) so sqlx.GetContext
// maps all columns by db tag. TestClientColumnsMatchClientDetail guards it
// against drifting from the ClientDetail db tags.
const clientColumns = `id, name, rp_id, logo_uri, redirect_uris, claims, acr_values,
	public_key, public_key_hash, enc_public_key, enc_public_key_hash, enc_public_key_cert,
	grant_types, auth_methods, status, additional_config, cr_dtimes, upd_dtimes`

// Queries is the sqlx-backed Querier implementation. Fixed query strings are
// built once in New; PatchClient builds its SET list from requested fields.
// WithTx binds a Queries to a transaction without rebuilding the fixed queries.
type Queries struct {
	db         TX
	driverName string

	// usesReturning selects the write dialect: PostgreSQL appends RETURNING and
	// reads the row back in the write statement; MySQL writes, then SELECTs.
	usesReturning bool

	getClient       string
	getActiveClient string
	createClient    string
	updateClient    string
}

var _ Querier = (*Queries)(nil)

// New returns a Queries backed by conn (a *sqlx.DB or *sqlx.Tx). driverName
// selects the placeholder style via persistence.Rebind and the write dialect via
// persistence.SupportsReturning, and must be the sql/sqlx driver name
// ("postgres" or "mysql"). Fixed queries are written with "?" and rebound here.
func New(conn TX, driverName string) *Queries {
	q := &Queries{db: conn, driverName: driverName, usesReturning: persistence.SupportsReturning(driverName)}

	q.getClient = persistence.Rebind(driverName,
		`SELECT `+clientColumns+` FROM client_detail WHERE id = ? LIMIT 1`)

	q.getActiveClient = persistence.Rebind(driverName,
		`SELECT `+clientColumns+` FROM client_detail WHERE id = ? AND status = 'ACTIVE' LIMIT 1`)

	insert := `INSERT INTO client_detail (
		id, name, rp_id, logo_uri, redirect_uris, claims, acr_values, public_key,
		public_key_hash, enc_public_key, enc_public_key_hash, enc_public_key_cert,
		grant_types, auth_methods, status, additional_config, cr_dtimes
	) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`

	// Placeholders are positional: SET columns first, then the WHERE id last —
	// UpdateClient binds its args in that order.
	update := `UPDATE client_detail SET
		name = ?, logo_uri = ?, redirect_uris = ?, claims = ?, acr_values = ?,
		grant_types = ?, auth_methods = ?, status = ?, additional_config = ?, upd_dtimes = ?
		WHERE id = ?`

	// PostgreSQL returns the written row atomically with RETURNING, so the write
	// methods read it back in the same statement — no transaction needed. MySQL
	// has no RETURNING, so there the write methods do a separate SELECT read-back
	// and rely on the caller's transaction for a consistent read (the clientmgmt
	// service supplies one; see Service.runWrite).
	if q.usesReturning {
		returning := ` RETURNING ` + clientColumns
		insert += returning
		update += returning
	}

	q.createClient = persistence.Rebind(driverName, insert)
	q.updateClient = persistence.Rebind(driverName, update)
	return q
}

// WithTx returns a copy of q bound to conn (typically an *sqlx.Tx), reusing the
// query strings already built in New instead of rebinding them for every
// transaction.
func (q *Queries) WithTx(conn TX) *Queries {
	clone := *q
	clone.db = conn
	return &clone
}

// CreateClientParams holds the inputs for CreateClient.
type CreateClientParams struct {
	ID               string         `db:"id" json:"id"`
	Name             string         `db:"name" json:"name"`
	RpID             string         `db:"rp_id" json:"rp_id"`
	LogoURI          string         `db:"logo_uri" json:"logo_uri"`
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
	LogoURI          string         `db:"logo_uri" json:"logo_uri"`
	RedirectUris     string         `db:"redirect_uris" json:"redirect_uris"`
	Claims           string         `db:"claims" json:"claims"`
	AcrValues        string         `db:"acr_values" json:"acr_values"`
	GrantTypes       string         `db:"grant_types" json:"grant_types"`
	AuthMethods      string         `db:"auth_methods" json:"auth_methods"`
	Status           string         `db:"status" json:"status"`
	AdditionalConfig sql.NullString `db:"additional_config" json:"additional_config"`
	UpdDtimes        sql.NullTime   `db:"upd_dtimes" json:"upd_dtimes"`
}

// PatchClientFields identifies the stored columns affected by a PATCH request.
// Name covers both clientName and clientNameLangMap; EncPublicKey covers the
// key, hash, and certificate columns, which must be written together.
type PatchClientFields struct {
	Name             bool
	LogoURI          bool
	RedirectURIs     bool
	Claims           bool
	AcrValues        bool
	GrantTypes       bool
	AuthMethods      bool
	Status           bool
	AdditionalConfig bool
	EncPublicKey     bool
}

// PatchClientParams holds the values and selected columns for PatchClient.
type PatchClientParams struct {
	ID               string            `db:"id" json:"id"`
	Fields           PatchClientFields `json:"-"`
	Name             string            `db:"name" json:"name"`
	LogoURI          string            `db:"logo_uri" json:"logo_uri"`
	RedirectUris     string            `db:"redirect_uris" json:"redirect_uris"`
	Claims           string            `db:"claims" json:"claims"`
	AcrValues        string            `db:"acr_values" json:"acr_values"`
	GrantTypes       string            `db:"grant_types" json:"grant_types"`
	AuthMethods      string            `db:"auth_methods" json:"auth_methods"`
	Status           string            `db:"status" json:"status"`
	AdditionalConfig sql.NullString    `db:"additional_config" json:"additional_config"`
	EncPublicKey     sql.NullString    `db:"enc_public_key" json:"enc_public_key"`
	EncPublicKeyHash sql.NullString    `db:"enc_public_key_hash" json:"enc_public_key_hash"`
	EncPublicKeyCert sql.NullString    `db:"enc_public_key_cert" json:"enc_public_key_cert"`
	UpdDtimes        sql.NullTime      `db:"upd_dtimes" json:"upd_dtimes"`
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

// CreateClient inserts a new client_detail row and returns it. On PostgreSQL the
// insert uses RETURNING, so the row comes back atomically from the one statement.
// On MySQL (no RETURNING) the insert and the read-back are separate statements, so
// the caller must run them in a transaction (the clientmgmt service does). A
// unique-violation error from the INSERT is surfaced for the caller to classify
// (see clientmgmt.isDuplicateClientID / isDuplicatePublicKeyHash).
func (q *Queries) CreateClient(ctx context.Context, arg CreateClientParams) (ClientDetail, error) {
	args := []interface{}{
		arg.ID, arg.Name, arg.RpID, arg.LogoURI, arg.RedirectUris, arg.Claims, arg.AcrValues,
		arg.PublicKey, arg.PublicKeyHash, arg.EncPublicKey, arg.EncPublicKeyHash, arg.EncPublicKeyCert,
		arg.GrantTypes, arg.AuthMethods, arg.Status, arg.AdditionalConfig, arg.CrDtimes,
	}
	if q.usesReturning {
		var row ClientDetail
		if err := q.db.GetContext(ctx, &row, q.createClient, args...); err != nil {
			return ClientDetail{}, err
		}
		return row, nil
	}
	if _, err := q.db.ExecContext(ctx, q.createClient, args...); err != nil {
		return ClientDetail{}, err
	}
	return q.GetClient(ctx, arg.ID)
}

// UpdateClient updates a client_detail row and returns it. On PostgreSQL the
// update uses RETURNING (atomic, one statement); on MySQL the write and the
// follow-up read are separate statements that the caller must run in a
// transaction. Either way a missing id yields sql.ErrNoRows (RETURNING matches no
// row, or the follow-up read finds none), preserving the not-found semantics the
// caller expects.
func (q *Queries) UpdateClient(ctx context.Context, arg UpdateClientParams) (ClientDetail, error) {
	args := []interface{}{
		arg.Name, arg.LogoURI, arg.RedirectUris, arg.Claims, arg.AcrValues,
		arg.GrantTypes, arg.AuthMethods, arg.Status, arg.AdditionalConfig, arg.UpdDtimes, arg.ID,
	}
	if q.usesReturning {
		var row ClientDetail
		if err := q.db.GetContext(ctx, &row, q.updateClient, args...); err != nil {
			return ClientDetail{}, err
		}
		return row, nil
	}
	if _, err := q.db.ExecContext(ctx, q.updateClient, args...); err != nil {
		return ClientDetail{}, err
	}
	return q.GetClient(ctx, arg.ID)
}

// PatchClient updates only the selected columns and upd_dtimes. An absent id
// maps to sql.ErrNoRows. PostgreSQL returns the written row with RETURNING;
// MySQL checks matched rows and reads the row back in the caller's transaction.
func (q *Queries) PatchClient(ctx context.Context, arg PatchClientParams) (ClientDetail, error) {
	set := make([]string, 0, 13)
	args := make([]interface{}, 0, 14)
	add := func(selected bool, column string, value interface{}) {
		if selected {
			set = append(set, column+" = ?")
			args = append(args, value)
		}
	}
	add(arg.Fields.Name, "name", arg.Name)
	add(arg.Fields.LogoURI, "logo_uri", arg.LogoURI)
	add(arg.Fields.RedirectURIs, "redirect_uris", arg.RedirectUris)
	add(arg.Fields.Claims, "claims", arg.Claims)
	add(arg.Fields.AcrValues, "acr_values", arg.AcrValues)
	add(arg.Fields.GrantTypes, "grant_types", arg.GrantTypes)
	add(arg.Fields.AuthMethods, "auth_methods", arg.AuthMethods)
	add(arg.Fields.Status, "status", arg.Status)
	add(arg.Fields.AdditionalConfig, "additional_config", arg.AdditionalConfig)
	add(arg.Fields.EncPublicKey, "enc_public_key", arg.EncPublicKey)
	add(arg.Fields.EncPublicKey, "enc_public_key_hash", arg.EncPublicKeyHash)
	add(arg.Fields.EncPublicKey, "enc_public_key_cert", arg.EncPublicKeyCert)
	add(true, "upd_dtimes", arg.UpdDtimes)

	query := `UPDATE client_detail SET ` + strings.Join(set, ", ") + ` WHERE id = ?`
	args = append(args, arg.ID)
	if q.usesReturning {
		query += ` RETURNING ` + clientColumns
	}
	query = persistence.Rebind(q.driverName, query)

	if q.usesReturning {
		var row ClientDetail
		if err := q.db.GetContext(ctx, &row, query, args...); err != nil {
			return ClientDetail{}, err
		}
		return row, nil
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
