package client

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

// pgxQuerier is the subset of *pgxpool.Pool used by Repository. Defined
// privately so tests can substitute a fake without a live database.
type pgxQuerier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

// Sentinel errors from Repository.Insert on unique-constraint violations.
// Use errors.Is to disambiguate.
var (
	ErrDuplicateClientID  = errors.New("duplicate client id")
	ErrDuplicatePublicKey = errors.New("duplicate public key")
)

// ClientDetailRow is the persistence shape of a row in `client_detail`.
// List-shaped fields are stored as JSON-array strings.
type ClientDetailRow struct {
	ID               string
	Name             string // plain client name, OR JSON map of lang→name
	RpID             string
	LogoURI          string
	RedirectURIs     string  // JSON array
	Claims           string  // JSON array
	ACRValues        string  // JSON array
	PublicKey        string  // JWK serialised as JSON string
	PublicKeyHash    string  // hex SHA-256, matches the SQL compute_public_key_hash
	GrantTypes       string  // JSON array
	AuthMethods      string  // JSON array
	Status           string  // "ACTIVE" / "INACTIVE"
	AdditionalConfig *string // JSON object; nil when absent
	CrDtimes         time.Time
}

// Repository persists and queries `client_detail` rows.
type Repository struct {
	pool pgxQuerier
}

// NewRepository binds a repo to the given pool. The pool is owned by the
// caller; the repo never closes it. The internal field is typed as the
// pgxQuerier interface for testability.
func NewRepository(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// ExistsByID reports whether a row with the given client id already exists.
func (r *Repository) ExistsByID(ctx context.Context, id string) (bool, error) {
	const q = `SELECT EXISTS(SELECT 1 FROM client_detail WHERE id = $1)`
	var exists bool
	if err := r.pool.QueryRow(ctx, q, id).Scan(&exists); err != nil {
		return false, fmt.Errorf("client_detail exists check: %w", err)
	}
	return exists, nil
}

// Insert writes the row. On a unique-constraint violation, returns either
// ErrDuplicateClientID or ErrDuplicatePublicKey depending on which constraint
// the database reports.
func (r *Repository) Insert(ctx context.Context, row *ClientDetailRow) error {
	if row == nil {
		return errors.New("client_detail insert: row is nil")
	}
	const q = `
INSERT INTO client_detail (
    id, name, rp_id, logo_uri, redirect_uris, claims, acr_values,
    public_key, public_key_hash, grant_types, auth_methods,
    status, additional_config, cr_dtimes
) VALUES (
    $1, $2, $3, $4, $5, $6, $7,
    $8, $9, $10, $11,
    $12, $13, $14
)`
	_, err := r.pool.Exec(ctx, q,
		row.ID, row.Name, row.RpID, row.LogoURI, row.RedirectURIs, row.Claims, row.ACRValues,
		row.PublicKey, row.PublicKeyHash, row.GrantTypes, row.AuthMethods,
		row.Status, row.AdditionalConfig, row.CrDtimes,
	)
	if err != nil {
		return mapInsertError(err)
	}
	return nil
}

// mapInsertError translates Postgres unique_violation (23505) into the
// matching sentinel by constraint name. Other errors are wrapped verbatim.
func mapInsertError(err error) error {
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) && pgErr.Code == "23505" {
		switch pgErr.ConstraintName {
		case "pk_clntdtl_id":
			return fmt.Errorf("client_detail insert: %w", ErrDuplicateClientID)
		case "uk_clntdtl_public_key_hash":
			return fmt.Errorf("client_detail insert: %w", ErrDuplicatePublicKey)
		}
	}
	return fmt.Errorf("client_detail insert: %w", err)
}
