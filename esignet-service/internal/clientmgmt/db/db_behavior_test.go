/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package db

import (
	"context"
	"database/sql"
	"time"
)

// recordingDBTX is a fake TX that records the query text and args passed to
// ExecContext/GetContext so tests can assert exact argument ordering without a
// live database. GetContext copies getRow into the *ClientDetail destination.
type recordingDBTX struct {
	execQuery string
	execArgs  []interface{}
	getQuery  string
	getArgs   []interface{}

	getRow       ClientDetail
	getErr       error
	execErr      error
	rowsAffected int64
}

func (r *recordingDBTX) ExecContext(_ context.Context, query string, args ...interface{}) (sql.Result, error) {
	r.execQuery = query
	r.execArgs = args
	if r.execErr != nil {
		return nil, r.execErr
	}
	return fakeResult{rows: r.rowsAffected}, nil
}

func (r *recordingDBTX) GetContext(_ context.Context, dest interface{}, query string, args ...interface{}) error {
	r.getQuery = query
	r.getArgs = args
	if r.getErr != nil {
		return r.getErr
	}
	if p, ok := dest.(*ClientDetail); ok {
		*p = r.getRow
	}
	return nil
}

type fakeResult struct{ rows int64 }

func (f fakeResult) LastInsertId() (int64, error) { return 0, nil }
func (f fakeResult) RowsAffected() (int64, error) { return f.rows, nil }

var fixedTime = time.Date(2024, 1, 1, 0, 0, 0, 0, time.UTC)

func sampleRow() ClientDetail {
	return ClientDetail{ID: "client-1", Name: `{"@none":"C"}`, Status: "ACTIVE", CrDtimes: fixedTime}
}

func sampleCreateParams() CreateClientParams {
	return CreateClientParams{
		ID: "client-1", Name: `{"@none":"C"}`, RpID: "rp-1", LogoURI: "https://logo",
		RedirectUris: `["https://cb"]`, Claims: `["name"]`, AcrValues: `["acr"]`,
		PublicKey: `{"kty":"RSA"}`, PublicKeyHash: "pkhash",
		GrantTypes: `["authorization_code"]`, AuthMethods: `["private_key_jwt"]`,
		Status: "ACTIVE", CrDtimes: fixedTime,
	}
}

func sampleUpdateParams() UpdateClientParams {
	return UpdateClientParams{
		ID: "client-1", Name: `{"@none":"C"}`, LogoURI: "https://logo",
		RedirectUris: `["https://cb"]`, Claims: `["name"]`, AcrValues: `["acr"]`,
		GrantTypes: `["authorization_code"]`, AuthMethods: `["private_key_jwt"]`,
		Status: "ACTIVE", UpdDtimes: sql.NullTime{Time: fixedTime, Valid: true},
	}
}

func samplePatchParams() PatchClientParams {
	return PatchClientParams{
		ID: "client-1", Name: `{"@none":"C"}`, LogoURI: "https://logo",
		RedirectUris: `["https://cb"]`, Claims: `["name"]`, AcrValues: `["acr"]`,
		GrantTypes: `["authorization_code"]`, AuthMethods: `["private_key_jwt"]`,
		Status: "ACTIVE", UpdDtimes: sql.NullTime{Time: fixedTime, Valid: true},
	}
}

// TestCreateClientMySQLArgOrder covers the MySQL path: a write with the 17 columns
// in declared order, then a read-back SELECT by id.
func (ts *DBTestSuite) TestCreateClientMySQLArgOrder() {
	rec := &recordingDBTX{getRow: sampleRow(), rowsAffected: 1}
	q := New(rec, "mysql")
	p := sampleCreateParams()

	got, err := q.CreateClient(context.Background(), p)

	ts.Require().NoError(err)
	ts.Assert().Equal(sampleRow(), got)
	ts.Assert().Contains(rec.execQuery, "INSERT INTO client_detail")
	ts.Assert().Equal([]interface{}{
		p.ID, p.Name, p.RpID, p.LogoURI, p.RedirectUris, p.Claims, p.AcrValues,
		p.PublicKey, p.PublicKeyHash, p.EncPublicKey, p.EncPublicKeyHash, p.EncPublicKeyCert,
		p.GrantTypes, p.AuthMethods, p.Status, p.AdditionalConfig, p.CrDtimes,
	}, rec.execArgs)
	ts.Assert().Equal([]interface{}{p.ID}, rec.getArgs, "read-back is by id")
}

// TestCreateClientPostgresReturning covers the Postgres path: one INSERT ...
// RETURNING statement, no separate read-back.
func (ts *DBTestSuite) TestCreateClientPostgresReturning() {
	rec := &recordingDBTX{getRow: sampleRow()}
	q := New(rec, "postgres")
	p := sampleCreateParams()

	got, err := q.CreateClient(context.Background(), p)

	ts.Require().NoError(err)
	ts.Assert().Equal(sampleRow(), got)
	ts.Assert().Empty(rec.execQuery, "RETURNING path issues no separate Exec")
	ts.Assert().Contains(rec.getQuery, "INSERT INTO client_detail")
	ts.Assert().Contains(rec.getQuery, "RETURNING")
	ts.Assert().Equal([]interface{}{
		p.ID, p.Name, p.RpID, p.LogoURI, p.RedirectUris, p.Claims, p.AcrValues,
		p.PublicKey, p.PublicKeyHash, p.EncPublicKey, p.EncPublicKeyHash, p.EncPublicKeyCert,
		p.GrantTypes, p.AuthMethods, p.Status, p.AdditionalConfig, p.CrDtimes,
	}, rec.getArgs)
}

// TestUpdateClientBindsIDLast covers UpdateClient's SET-columns-then-id ordering.
func (ts *DBTestSuite) TestUpdateClientBindsIDLast() {
	rec := &recordingDBTX{getRow: sampleRow(), rowsAffected: 1}
	q := New(rec, "mysql")
	p := sampleUpdateParams()

	_, err := q.UpdateClient(context.Background(), p)

	ts.Require().NoError(err)
	ts.Assert().Equal([]interface{}{
		p.Name, p.LogoURI, p.RedirectUris, p.Claims, p.AcrValues,
		p.GrantTypes, p.AuthMethods, p.Status, p.AdditionalConfig, p.UpdDtimes, p.ID,
	}, rec.execArgs)
	ts.Assert().Equal(p.ID, rec.execArgs[len(rec.execArgs)-1], "id must be the last bound arg")
}

// TestUpdateClientNotFound covers the MySQL read-back yielding sql.ErrNoRows when
// the id does not exist.
func (ts *DBTestSuite) TestUpdateClientNotFound() {
	rec := &recordingDBTX{getErr: sql.ErrNoRows}
	q := New(rec, "mysql")

	_, err := q.UpdateClient(context.Background(), sampleUpdateParams())

	ts.Assert().ErrorIs(err, sql.ErrNoRows)
}

// TestPatchClientUpdatesOnlySelectedColumns proves a PATCH cannot write values
// copied from an earlier read of unrelated columns.
func (ts *DBTestSuite) TestPatchClientUpdatesOnlySelectedColumns() {
	rec := &recordingDBTX{getRow: sampleRow(), rowsAffected: 1}
	q := New(rec, "mysql")
	p := samplePatchParams()
	p.Fields = PatchClientFields{LogoURI: true, Status: true}

	_, err := q.PatchClient(context.Background(), p)

	ts.Require().NoError(err)
	ts.Assert().Equal("UPDATE client_detail SET logo_uri = ?, status = ?, upd_dtimes = ? WHERE id = ?", rec.execQuery)
	ts.Assert().Equal([]interface{}{p.LogoURI, p.Status, p.UpdDtimes, p.ID}, rec.execArgs)
	ts.Assert().Equal([]interface{}{p.ID}, rec.getArgs)
}

// TestPatchClientEncKeyColumns covers one request attribute spanning three
// nullable stored columns, including an explicit clear.
func (ts *DBTestSuite) TestPatchClientEncKeyColumns() {
	rec := &recordingDBTX{getRow: sampleRow(), rowsAffected: 1}
	q := New(rec, "mysql")
	p := samplePatchParams()
	p.Fields = PatchClientFields{EncPublicKey: true}

	_, err := q.PatchClient(context.Background(), p)

	ts.Require().NoError(err)
	ts.Assert().Equal("UPDATE client_detail SET enc_public_key = ?, enc_public_key_hash = ?, enc_public_key_cert = ?, upd_dtimes = ? WHERE id = ?", rec.execQuery)
	ts.Assert().Equal([]interface{}{p.EncPublicKey, p.EncPublicKeyHash, p.EncPublicKeyCert, p.UpdDtimes, p.ID}, rec.execArgs)
}

// TestPatchClientPostgresReturning covers dynamic placeholder rebinding and
// one-statement read-back on PostgreSQL.
func (ts *DBTestSuite) TestPatchClientPostgresReturning() {
	rec := &recordingDBTX{getRow: sampleRow()}
	q := New(rec, "postgres")
	p := samplePatchParams()
	p.Fields = PatchClientFields{Name: true}

	_, err := q.PatchClient(context.Background(), p)

	ts.Require().NoError(err)
	ts.Assert().Empty(rec.execQuery)
	ts.Assert().Contains(rec.getQuery, "UPDATE client_detail SET name = $1, upd_dtimes = $2 WHERE id = $3 RETURNING")
	ts.Assert().Equal([]interface{}{p.Name, p.UpdDtimes, p.ID}, rec.getArgs)
}

// TestPatchClientNoRowsIsErrNoRows covers the MySQL absent-row path:
// RowsAffected()==0 maps to sql.ErrNoRows, without a read-back.
func (ts *DBTestSuite) TestPatchClientNoRowsIsErrNoRows() {
	rec := &recordingDBTX{rowsAffected: 0}
	q := New(rec, "mysql")
	p := samplePatchParams()
	p.Fields = PatchClientFields{Status: true}

	_, err := q.PatchClient(context.Background(), p)

	ts.Assert().ErrorIs(err, sql.ErrNoRows)
	ts.Assert().Empty(rec.getQuery, "no read-back when the row is absent")
}

// TestPatchClientReturningNotFound covers PostgreSQL's absent-row path.
func (ts *DBTestSuite) TestPatchClientReturningNotFound() {
	rec := &recordingDBTX{getErr: sql.ErrNoRows}
	q := New(rec, "postgres")

	_, err := q.PatchClient(context.Background(), samplePatchParams())

	ts.Assert().ErrorIs(err, sql.ErrNoRows)
}

// TestWithTxReusesPrebuiltQueries checks WithTx swaps the connection while reusing
// the query strings built in New (no rebinding per transaction).
func (ts *DBTestSuite) TestWithTxReusesPrebuiltQueries() {
	base := New(nil, "postgres")
	rec := &recordingDBTX{getRow: sampleRow()}

	txq := base.WithTx(rec)

	ts.Assert().Equal(base.createClient, txq.createClient, "WithTx reuses the prebuilt query string")
	ts.Assert().Equal(base.usesReturning, txq.usesReturning)
	ts.Assert().Same(rec, txq.db.(*recordingDBTX), "WithTx binds the given connection")

	_, err := txq.CreateClient(context.Background(), sampleCreateParams())
	ts.Require().NoError(err)
	ts.Assert().Contains(rec.getQuery, "RETURNING")
}
