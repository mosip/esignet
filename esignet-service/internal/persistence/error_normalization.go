/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package persistence

import (
	"errors"
	"strings"

	"github.com/go-sql-driver/mysql"
	"github.com/jackc/pgx/v5/pgconn"
)

// ErrorCode is an eSignet-level classification of a database error, so service
// callers can react to the failure reason without depending on driver-specific
// types or SQLSTATE / error numbers.
type ErrorCode int

const (
	// ErrUnknown means the error is not one this package classifies; callers
	// should treat it as an opaque internal failure.
	ErrUnknown ErrorCode = iota
	// ErrDuplicateKey is a unique- or primary-key-constraint violation
	// (PostgreSQL SQLSTATE 23505 / MySQL error 1062).
	ErrDuplicateKey
)

// ErrorInfo is the normalized result of classifying a database error. Code is
// the eSignet-level category; Constraint is the offending unique/primary-key
// constraint or index name for an ErrDuplicateKey (empty otherwise), with any
// MySQL "table." qualifier stripped so the name matches the DDL / PostgreSQL
// form. Note MySQL always reports a primary key as "PRIMARY" regardless of the
// DDL constraint name, so a caller distinguishing a primary-key violation must
// also accept "PRIMARY".
type ErrorInfo struct {
	Code       ErrorCode
	Constraint string
}

// GetErrorCode classifies err into an eSignet ErrorInfo, so the service layer can
// decide on the failure reason from the ErrorInfo alone rather than inspecting
// driver types. It understands both drivers (PostgreSQL *pgconn.PgError, MySQL
// *mysql.MySQLError) and a plain-string fallback for non-driver-typed errors
// (e.g. stringified errors in tests), gated on the complete "(SQLSTATE 23505)" /
// "Error 1062:" (or "Error 1062 (") markers so bare digits in a port, id or
// timestamp — or a longer code like "Error 10620" — can't trip it.
// A nil or unrecognized error yields {ErrUnknown, ""}.
func GetErrorCode(err error) ErrorInfo {
	if err == nil {
		return ErrorInfo{Code: ErrUnknown}
	}

	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		if pgErr.Code == "23505" {
			return ErrorInfo{Code: ErrDuplicateKey, Constraint: pgErr.ConstraintName}
		}
		return ErrorInfo{Code: ErrUnknown}
	}

	var myErr *mysql.MySQLError
	if errors.As(err, &myErr) {
		if myErr.Number == 1062 {
			return ErrorInfo{Code: ErrDuplicateKey, Constraint: mysqlDupKey(myErr.Message)}
		}
		return ErrorInfo{Code: ErrUnknown}
	}

	// Fallback for non-driver-typed errors (e.g. stringified errors, which the
	// keymanager self-heal path classifies). Both drivers' stringified forms
	// still carry the offending name — Postgres as `unique constraint "NAME"`,
	// MySQL as "for key '…'" — so it is parsed out.
	msg := err.Error()
	switch {
	case strings.Contains(msg, "(SQLSTATE 23505)"):
		return ErrorInfo{Code: ErrDuplicateKey, Constraint: pgConstraintFromString(msg)}
	case strings.Contains(msg, "Error 1062:") || strings.Contains(msg, "Error 1062 ("):
		return ErrorInfo{Code: ErrDuplicateKey, Constraint: mysqlDupKey(msg)}
	default:
		return ErrorInfo{Code: ErrUnknown}
	}
}

// mysqlDupKey extracts the index name from a MySQL duplicate-key message
// ("… for key 'INDEX'" or "… for key 'TABLE.INDEX'"), stripping any "table."
// qualifier so the result matches the DDL / PostgreSQL constraint name (or
// "PRIMARY" for a primary key). It returns "" when the "for key" segment is
// absent.
func mysqlDupKey(message string) string {
	const marker = "for key '"
	i := strings.Index(message, marker)
	if i < 0 {
		return ""
	}
	rest := message[i+len(marker):]
	j := strings.Index(rest, "'")
	if j < 0 {
		return ""
	}
	key := rest[:j]
	if dot := strings.LastIndex(key, "."); dot >= 0 {
		key = key[dot+1:]
	}
	return key
}

// pgConstraintFromString extracts the constraint name from a stringified
// PostgreSQL error's standard `unique constraint "NAME"` segment (the form
// *pgconn.PgError.Error() and lib/pq both produce). It returns "" when that
// segment is absent.
func pgConstraintFromString(msg string) string {
	const marker = `unique constraint "`
	i := strings.Index(msg, marker)
	if i < 0 {
		return ""
	}
	rest := msg[i+len(marker):]
	if j := strings.Index(rest, `"`); j >= 0 {
		return rest[:j]
	}
	return ""
}
