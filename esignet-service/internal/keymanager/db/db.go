// Package db implements the keymgr-schema persistence layer for the
// keymanager service, using jmoiron/sqlx against the existing Postgres
// schema (key_alias, key_policy_def, key_store — unchanged from the Java
// kernel-keymanager-service, though the schema *name* is configurable so a
// separate copy, e.g. "keymgr_go", can be used alongside the Java
// service's "keymgr" without touching its data — see New).
package db

import (
	"context"
	"fmt"

	"github.com/jmoiron/sqlx"
)

// DBTX is satisfied by *sqlx.DB and *sqlx.Tx, allowing Queries to run
// against either a plain connection or a transaction.
type DBTX interface {
	sqlx.ExtContext
	GetContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error
	SelectContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error
}

const keyAliasColumns = `id, app_id, ref_id, key_gen_dtimes, key_expire_dtimes, status_code,
	cert_thumbprint, uni_ident, cr_by, cr_dtimes, upd_by, upd_dtimes, is_deleted, del_dtimes`

const keyStoreColumns = `id, certificate_data, private_key, master_key,
	cr_by, cr_dtimes, upd_by, upd_dtimes, is_deleted, del_dtimes`

// Queries is the sqlx-backed Querier implementation. Query strings are
// built once in New, qualified with the configured schema.
type Queries struct {
	db     DBTX
	schema string

	selectKeyAlias  string
	insertKeyAlias  string
	updateKeyAlias  string
	selectKeyPolicy string
	selectKeyStore  string
	insertKeyStore  string
	updateKeyStore  string
}

var _ Querier = (*Queries)(nil)

// New returns a Queries backed by conn (a *sqlx.DB or *sqlx.Tx), reading
// from/writing to the given Postgres schema (e.g. "keymgr" for the Java
// service's schema, or a separate one such as "keymgr_go" to test this
// library in isolation).
func New(conn DBTX, schema string) *Queries {
	q := &Queries{db: conn, schema: schema}

	q.selectKeyAlias = fmt.Sprintf(`SELECT %s FROM %s.key_alias
		WHERE app_id = $1 AND ref_id = $2 AND (is_deleted IS NULL OR is_deleted = FALSE)
		ORDER BY key_gen_dtimes DESC`, keyAliasColumns, schema)

	q.insertKeyAlias = fmt.Sprintf(`INSERT INTO %s.key_alias
		(id, app_id, ref_id, key_gen_dtimes, key_expire_dtimes, status_code, cert_thumbprint, uni_ident, cr_by, cr_dtimes)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`, schema)

	q.updateKeyAlias = fmt.Sprintf(`UPDATE %s.key_alias SET
		key_expire_dtimes = $2, status_code = $3, cert_thumbprint = $4, upd_by = $5, upd_dtimes = $6
		WHERE id = $1`, schema)

	q.selectKeyPolicy = fmt.Sprintf(`SELECT app_id, key_validity_duration, is_active, pre_expire_days, access_allowed,
		cr_by, cr_dtimes, upd_by, upd_dtimes, is_deleted, del_dtimes
		FROM %s.key_policy_def
		WHERE app_id = $1 AND is_active = TRUE`, schema)

	q.selectKeyStore = fmt.Sprintf(`SELECT %s FROM %s.key_store WHERE id = $1`, keyStoreColumns, schema)

	q.insertKeyStore = fmt.Sprintf(`INSERT INTO %s.key_store
		(id, certificate_data, private_key, master_key, cr_by, cr_dtimes)
		VALUES ($1, $2, $3, $4, $5, $6)`, schema)

	q.updateKeyStore = fmt.Sprintf(`UPDATE %s.key_store SET
		certificate_data = $2, private_key = $3, upd_by = $4, upd_dtimes = $5
		WHERE id = $1`, schema)

	return q
}

// WithTx returns a Queries bound to the given transaction, same schema.
func (q *Queries) WithTx(tx *sqlx.Tx) *Queries {
	q2 := *q
	q2.db = tx
	return &q2
}

func (q *Queries) GetKeyAliasesByAppRef(ctx context.Context, appID string, refID string) ([]KeyAlias, error) {
	var rows []KeyAlias
	if err := q.db.SelectContext(ctx, &rows, q.selectKeyAlias, appID, refID); err != nil {
		return nil, fmt.Errorf("select key_alias: %w", err)
	}
	return rows, nil
}

func (q *Queries) InsertKeyAlias(ctx context.Context, k KeyAlias) error {
	_, err := q.db.ExecContext(ctx, q.insertKeyAlias,
		k.ID, k.AppID, k.RefID, k.KeyGenDtimes, k.KeyExpireDtimes, k.StatusCode,
		k.CertThumbprint, k.UniIdent, k.CrBy, k.CrDtimes)
	if err != nil {
		return fmt.Errorf("insert key_alias: %w", err)
	}
	return nil
}

func (q *Queries) UpdateKeyAlias(ctx context.Context, k KeyAlias) error {
	_, err := q.db.ExecContext(ctx, q.updateKeyAlias, k.ID, k.KeyExpireDtimes, k.StatusCode, k.CertThumbprint, k.UpdBy, k.UpdDtimes)
	if err != nil {
		return fmt.Errorf("update key_alias: %w", err)
	}
	return nil
}

func (q *Queries) GetKeyPolicy(ctx context.Context, appID string) (KeyPolicy, error) {
	var p KeyPolicy
	if err := q.db.GetContext(ctx, &p, q.selectKeyPolicy, appID); err != nil {
		return KeyPolicy{}, fmt.Errorf("select key_policy_def: %w", err)
	}
	return p, nil
}

func (q *Queries) GetKeyStoreRecord(ctx context.Context, id string) (KeyStoreRecord, error) {
	var r KeyStoreRecord
	if err := q.db.GetContext(ctx, &r, q.selectKeyStore, id); err != nil {
		return KeyStoreRecord{}, fmt.Errorf("select key_store: %w", err)
	}
	return r, nil
}

func (q *Queries) InsertKeyStoreRecord(ctx context.Context, k KeyStoreRecord) error {
	_, err := q.db.ExecContext(ctx, q.insertKeyStore, k.ID, k.CertificateData, k.PrivateKey, k.MasterKey, k.CrBy, k.CrDtimes)
	if err != nil {
		return fmt.Errorf("insert key_store: %w", err)
	}
	return nil
}

func (q *Queries) UpdateKeyStoreRecord(ctx context.Context, k KeyStoreRecord) error {
	_, err := q.db.ExecContext(ctx, q.updateKeyStore, k.ID, k.CertificateData, k.PrivateKey, k.UpdBy, k.UpdDtimes)
	if err != nil {
		return fmt.Errorf("update key_store: %w", err)
	}
	return nil
}
