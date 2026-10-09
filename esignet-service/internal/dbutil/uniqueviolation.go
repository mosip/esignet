/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dbutil

import (
	"errors"
	"strings"

	"github.com/go-sql-driver/mysql"
	"github.com/jackc/pgx/v5/pgconn"
)

// IsUniqueViolation reports whether err is a unique- or primary-key-constraint
// violation, optionally restricted to one of the given constraint/index names.
// With no names it matches any unique violation.
//
// It understands both drivers: PostgreSQL reports SQLSTATE 23505 with a
// ConstraintName field (matched exactly); MySQL reports error 1062 and embeds
// the offending index name in the message (MySQL exposes no separate
// constraint-name field), so names are matched as substrings there. Because
// MySQL always names a primary-key index "PRIMARY" regardless of the DDL
// constraint name, pass "PRIMARY" to catch a primary-key violation on MySQL —
// it matches both `for key 'PRIMARY'` and `for key 'table.PRIMARY'`.
//
// Name matching is case-sensitive, so pass names exactly as they appear in the
// DDL (e.g. "uk_clntdtl_public_key_hash") or, for a MySQL primary key,
// "PRIMARY". A plain-string fallback covers errors that are not driver-typed
// (e.g. constructed in tests).
func IsUniqueViolation(err error, names ...string) bool {
	if err == nil {
		return false
	}

	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		return pgErr.Code == "23505" && (len(names) == 0 || equalsAny(pgErr.ConstraintName, names))
	}

	var myErr *mysql.MySQLError
	if errors.As(err, &myErr) {
		return myErr.Number == 1062 && (len(names) == 0 || containsAny(myErr.Message, names))
	}

	// Fallback for non-driver-typed errors (e.g. plain errors built in tests).
	msg := err.Error()
	isUnique := strings.Contains(msg, "23505") || strings.Contains(msg, "1062")
	return isUnique && (len(names) == 0 || containsAny(msg, names))
}

// equalsAny reports whether value exactly equals one of names.
func equalsAny(value string, names []string) bool {
	for _, n := range names {
		if value == n {
			return true
		}
	}
	return false
}

// containsAny reports whether value contains one of names as a substring.
func containsAny(value string, names []string) bool {
	for _, n := range names {
		if strings.Contains(value, n) {
			return true
		}
	}
	return false
}
