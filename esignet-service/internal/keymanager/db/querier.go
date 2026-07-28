package db

import "context"

// Querier is the persistence contract the keymanager service programs
// against. db.Queries is the real, sqlx-backed implementation; tests inject
// a hand-written fake instead (mirrors internal/clientmgmt/db.Querier).
type Querier interface {
	// GetKeyAliasesByAppRef returns every non-deleted key_alias row for the
	// given (appID, refID) pair, ordered by key_gen_dtimes descending.
	GetKeyAliasesByAppRef(ctx context.Context, appID string, refID string) ([]KeyAlias, error)

	// GetKeyAliasByCertThumbprint looks up the single non-deleted key_alias
	// row whose cert_thumbprint matches thumbprintHex, globally — not scoped
	// to any (appID, refID) — mirroring the Java kernel-keymanager-service's
	// PrivateKeyDecryptorHelper.getDBKeyStoreData lookup. Callers are
	// responsible for validating the resolved row's (AppID, RefID) against
	// whatever they expected. Returns an error wrapping sql.ErrNoRows when no
	// row matches.
	GetKeyAliasByCertThumbprint(ctx context.Context, thumbprintHex string) (KeyAlias, error)

	// GetKeyAliasByUniIdent looks up the single non-deleted key_alias row
	// whose uni_ident matches uniIdent, globally — not scoped to any
	// (appID, refID) — the symmetric-key analog of
	// GetKeyAliasByCertThumbprint, used by cryptomanager's DecryptAES to
	// resolve the key an AES envelope's embedded unique identifier refers
	// to, then validate the resolved row's (AppID, RefID) against what was
	// supplied (rather than only ever searching within that scope, which
	// can't distinguish "no such key at all" from "that key exists, under
	// a different application/reference id"). Returns an error wrapping
	// sql.ErrNoRows when no row matches.
	GetKeyAliasByUniIdent(ctx context.Context, uniIdent string) (KeyAlias, error)

	// InsertKeyAlias inserts a new key_alias row. Relies on the DB-level
	// UNIQUE(uni_ident) constraint for duplicate-generation safety (see
	// rotation.go / IsDuplicateUniIdent).
	InsertKeyAlias(ctx context.Context, k KeyAlias) error

	// UpdateKeyAliasExpiry updates key_expire_dtimes (and status_code) for an
	// existing alias id — used by RevokeKey and by cert-upload/thumbprint updates.
	UpdateKeyAlias(ctx context.Context, k KeyAlias) error

	// GetKeyPolicy fetches the active key policy for an application id.
	GetKeyPolicy(ctx context.Context, appID string) (KeyPolicy, error)

	// GetKeyStoreRecord fetches a DB-resident private key/certificate record by alias id.
	GetKeyStoreRecord(ctx context.Context, id string) (KeyStoreRecord, error)

	// InsertKeyStoreRecord inserts a new key_store row.
	InsertKeyStoreRecord(ctx context.Context, k KeyStoreRecord) error

	// UpdateKeyStoreRecord replaces the certificate/private key data for an existing key_store row.
	UpdateKeyStoreRecord(ctx context.Context, k KeyStoreRecord) error
}
