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
	"regexp"

	"github.com/jmoiron/sqlx"

	"github.com/mosip/esignet/internal/dbutil"
)

// TX is satisfied by *sqlx.DB and *sqlx.Tx, allowing Queries to run
// against either a plain connection or a transaction.
type TX interface {
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
	db     TX
	schema string

	selectKeyAlias             string
	selectKeyAliasByThumbprint string
	selectKeyAliasByUniIdent   string
	insertKeyAlias             string
	updateKeyAlias             string
	selectKeyPolicy            string
	selectKeyPolicyExists      string
	selectKeyStore             string
	insertKeyStore             string
	updateKeyStore             string
}

var _ Querier = (*Queries)(nil)

// schemaIdentifier restricts the (operator-controlled, env-var sourced)
// schema name to a safe SQL identifier before it is interpolated into raw
// query text below — placeholders cannot bind identifiers, so this check is
// the only guard against SQL injection via KEYMANAGER_DB_SCHEMA.
var schemaIdentifier = regexp.MustCompile(`^[A-Za-z_][A-Za-z0-9_]*$`)

// New returns a Queries backed by conn (a *sqlx.DB or *sqlx.Tx), reading
// from/writing to the given schema (e.g. "keymgr" for the Java service's
// schema, or a separate one such as "keymgr_go" to test this library in
// isolation). driverName selects the placeholder style via dbutil.Rebind and
// must be the sql/sqlx driver name ("postgres"/"pgx" or "mysql").
//
// Queries are written with "?" placeholders and rebound once here. When
// driverName == "mysql", schema is a MySQL *database* name (MySQL has no
// schema-within-database; the "%s.table" qualification resolves to
// database.table there), and the connecting user needs privileges on it.
func New(conn TX, schema, driverName string) *Queries {
	if !schemaIdentifier.MatchString(schema) {
		panic(fmt.Sprintf("keymanager/db: invalid schema identifier %q", schema))
	}
	q := &Queries{db: conn, schema: schema}

	// ref_id is nullable, and ROOT rows are persisted with ref_id NULL (see
	// persistNewAlias/ensureCurrentKey, which blank ReferenceID for ROOT) —
	// "ref_id = ?" alone can never match a NULL column value, even when the
	// bound value is the empty string, so the OR clause covers that case
	// explicitly. Without it, a NULL-stored ROOT row would look like it
	// doesn't exist yet and a duplicate could be generated. The ref_id value
	// is bound twice (see GetKeyAliasesByAppRef).
	q.selectKeyAlias = dbutil.Rebind(driverName, fmt.Sprintf(`SELECT %s FROM %s.key_alias
		WHERE app_id = ? AND (ref_id = ? OR (ref_id IS NULL AND ? = '')) AND (is_deleted IS NULL OR is_deleted = FALSE)
		ORDER BY key_gen_dtimes DESC`, keyAliasColumns, schema))

	q.selectKeyAliasByThumbprint = dbutil.Rebind(driverName, fmt.Sprintf(`SELECT %s FROM %s.key_alias
		WHERE cert_thumbprint = ? AND (is_deleted IS NULL OR is_deleted = FALSE)
		ORDER BY key_gen_dtimes DESC LIMIT 1`, keyAliasColumns, schema))

	q.selectKeyAliasByUniIdent = dbutil.Rebind(driverName, fmt.Sprintf(`SELECT %s FROM %s.key_alias
		WHERE uni_ident = ? AND (is_deleted IS NULL OR is_deleted = FALSE)
		ORDER BY key_gen_dtimes DESC LIMIT 1`, keyAliasColumns, schema))

	q.insertKeyAlias = dbutil.Rebind(driverName, fmt.Sprintf(`INSERT INTO %s.key_alias
		(id, app_id, ref_id, key_gen_dtimes, key_expire_dtimes, status_code, cert_thumbprint, uni_ident, cr_by, cr_dtimes)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`, schema))

	// Placeholders are positional: SET columns first, then the WHERE id last —
	// UpdateKeyAlias binds its args in that order.
	q.updateKeyAlias = dbutil.Rebind(driverName, fmt.Sprintf(`UPDATE %s.key_alias SET
		key_expire_dtimes = ?, status_code = ?, cert_thumbprint = ?, upd_by = ?, upd_dtimes = ?
		WHERE id = ?`, schema))

	q.selectKeyPolicy = dbutil.Rebind(driverName, fmt.Sprintf(`SELECT app_id, key_validity_duration, is_active, pre_expire_days, access_allowed,
		cr_by, cr_dtimes, upd_by, upd_dtimes, is_deleted, del_dtimes
		FROM %s.key_policy_def
		WHERE app_id = ? AND is_active = TRUE`, schema))

	q.selectKeyPolicyExists = dbutil.Rebind(driverName, fmt.Sprintf(`SELECT EXISTS(
		SELECT 1 FROM %s.key_policy_def
		WHERE app_id = ? AND (is_deleted IS NULL OR is_deleted = FALSE))`, schema))

	q.selectKeyStore = dbutil.Rebind(driverName, fmt.Sprintf(`SELECT %s FROM %s.key_store
		WHERE id = ? AND (is_deleted IS NULL OR is_deleted = FALSE)`, keyStoreColumns, schema))

	q.insertKeyStore = dbutil.Rebind(driverName, fmt.Sprintf(`INSERT INTO %s.key_store
		(id, certificate_data, private_key, master_key, cr_by, cr_dtimes)
		VALUES (?, ?, ?, ?, ?, ?)`, schema))

	// Placeholders are positional: SET columns first, then the WHERE id last —
	// UpdateKeyStoreRecord binds its args in that order.
	q.updateKeyStore = dbutil.Rebind(driverName, fmt.Sprintf(`UPDATE %s.key_store SET
		certificate_data = ?, private_key = ?, upd_by = ?, upd_dtimes = ?
		WHERE id = ?`, schema))

	return q
}

// WithTx returns a Queries bound to the given transaction, same schema.
func (q *Queries) WithTx(tx *sqlx.Tx) *Queries {
	q2 := *q
	q2.db = tx
	return &q2
}

// GetKeyAliasesByAppRef implements Querier.
func (q *Queries) GetKeyAliasesByAppRef(ctx context.Context, appID string, refID string) ([]KeyAlias, error) {
	var rows []KeyAlias
	// selectKeyAlias has three placeholders (app_id, ref_id, and the ref_id = ''
	// NULL-guard), so refID is bound twice.
	if err := q.db.SelectContext(ctx, &rows, q.selectKeyAlias, appID, refID, refID); err != nil {
		return nil, fmt.Errorf("select key_alias: %w", err)
	}
	return rows, nil
}

// GetKeyAliasByCertThumbprint looks up the single non-deleted key_alias row
// whose cert_thumbprint matches thumbprintHex, globally (not scoped to any
// app_id/ref_id). Returns a wrapped sql.ErrNoRows when nothing matches.
func (q *Queries) GetKeyAliasByCertThumbprint(ctx context.Context, thumbprintHex string) (KeyAlias, error) {
	var row KeyAlias
	if err := q.db.GetContext(ctx, &row, q.selectKeyAliasByThumbprint, thumbprintHex); err != nil {
		return KeyAlias{}, fmt.Errorf("select key_alias by cert_thumbprint: %w", err)
	}
	return row, nil
}

// GetKeyAliasByUniIdent looks up the single non-deleted key_alias row whose
// uni_ident matches uniIdent, globally (not scoped to any app_id/ref_id).
// Returns a wrapped sql.ErrNoRows when nothing matches.
func (q *Queries) GetKeyAliasByUniIdent(ctx context.Context, uniIdent string) (KeyAlias, error) {
	var row KeyAlias
	if err := q.db.GetContext(ctx, &row, q.selectKeyAliasByUniIdent, uniIdent); err != nil {
		return KeyAlias{}, fmt.Errorf("select key_alias by uni_ident: %w", err)
	}
	return row, nil
}

// InsertKeyAlias implements Querier.
func (q *Queries) InsertKeyAlias(ctx context.Context, k KeyAlias) error {
	_, err := q.db.ExecContext(ctx, q.insertKeyAlias,
		k.ID, k.AppID, k.RefID, k.KeyGenDtimes, k.KeyExpireDtimes, k.StatusCode,
		k.CertThumbprint, k.UniIdent, k.CrBy, k.CrDtimes)
	if err != nil {
		return fmt.Errorf("insert key_alias: %w", err)
	}
	return nil
}

// UpdateKeyAlias implements Querier.
func (q *Queries) UpdateKeyAlias(ctx context.Context, k KeyAlias) error {
	// Args match updateKeyAlias's placeholder order: SET columns first, id last.
	_, err := q.db.ExecContext(ctx, q.updateKeyAlias, k.KeyExpireDtimes, k.StatusCode, k.CertThumbprint, k.UpdBy, k.UpdDtimes, k.ID)
	if err != nil {
		return fmt.Errorf("update key_alias: %w", err)
	}
	return nil
}

// GetKeyPolicy implements Querier.
func (q *Queries) GetKeyPolicy(ctx context.Context, appID string) (KeyPolicy, error) {
	var p KeyPolicy
	if err := q.db.GetContext(ctx, &p, q.selectKeyPolicy, appID); err != nil {
		return KeyPolicy{}, fmt.Errorf("select key_policy_def: %w", err)
	}
	return p, nil
}

// HasKeyPolicy implements Querier.
func (q *Queries) HasKeyPolicy(ctx context.Context, appID string) (bool, error) {
	var exists bool
	if err := q.db.GetContext(ctx, &exists, q.selectKeyPolicyExists, appID); err != nil {
		return false, fmt.Errorf("select key_policy_def exists: %w", err)
	}
	return exists, nil
}

// GetKeyStoreRecord implements Querier.
func (q *Queries) GetKeyStoreRecord(ctx context.Context, id string) (KeyStoreRecord, error) {
	var r KeyStoreRecord
	if err := q.db.GetContext(ctx, &r, q.selectKeyStore, id); err != nil {
		return KeyStoreRecord{}, fmt.Errorf("select key_store: %w", err)
	}
	return r, nil
}

// InsertKeyStoreRecord implements Querier.
func (q *Queries) InsertKeyStoreRecord(ctx context.Context, k KeyStoreRecord) error {
	_, err := q.db.ExecContext(ctx, q.insertKeyStore, k.ID, k.CertificateData, k.PrivateKey, k.MasterKey, k.CrBy, k.CrDtimes)
	if err != nil {
		return fmt.Errorf("insert key_store: %w", err)
	}
	return nil
}

// UpdateKeyStoreRecord implements Querier.
func (q *Queries) UpdateKeyStoreRecord(ctx context.Context, k KeyStoreRecord) error {
	// Args match updateKeyStore's placeholder order: SET columns first, id last.
	_, err := q.db.ExecContext(ctx, q.updateKeyStore, k.CertificateData, k.PrivateKey, k.UpdBy, k.UpdDtimes, k.ID)
	if err != nil {
		return fmt.Errorf("update key_store: %w", err)
	}
	return nil
}
