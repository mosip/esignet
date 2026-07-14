package db

import "context"

// Querier is the persistence contract the keymanager service programs
// against. db.Queries is the real, sqlx-backed implementation; tests inject
// a hand-written fake instead (mirrors internal/clientmgmt/db.Querier).
type Querier interface {
	// GetKeyAliasesByAppRef returns every non-deleted key_alias row for the
	// given (appID, refID) pair, ordered by key_gen_dtimes descending.
	GetKeyAliasesByAppRef(ctx context.Context, appID string, refID string) ([]KeyAlias, error)

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
