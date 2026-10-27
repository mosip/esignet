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
// the offending index name in the message's "for key '…'" segment (MySQL
// exposes no separate constraint-name field), which is parsed out and matched
// exactly, or by suffix for a table-qualified "table.INDEX" key. Because MySQL
// always names a primary-key index "PRIMARY" regardless of the DDL constraint
// name, pass "PRIMARY" to catch a primary-key violation on MySQL — it matches
// both `for key 'PRIMARY'` and `for key 'table.PRIMARY'`.
//
// Name matching is case-sensitive, so pass names exactly as they appear in the
// DDL (e.g. "uk_clntdtl_public_key_hash") or, for a MySQL primary key,
// "PRIMARY". A plain-string fallback covers errors that are not driver-typed
// (e.g. stringified errors in tests); it recognizes the codes only with their
// explicit "SQLSTATE 23505" / "Error 1062" markers.
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
		return myErr.Number == 1062 && (len(names) == 0 || mysqlKeyMatches(myErr.Message, names))
	}

	// Fallback for non-driver-typed errors (e.g. stringified errors in tests):
	// recognize the codes only with their explicit markers, not bare digits that
	// could appear in a port, id, or timestamp elsewhere in the text.
	msg := err.Error()
	switch {
	case strings.Contains(msg, "SQLSTATE 23505"):
		return len(names) == 0 || containsAny(msg, names) // PG: substring match preserved
	case strings.Contains(msg, "Error 1062"):
		return len(names) == 0 || mysqlKeyMatches(msg, names)
	default:
		return false
	}
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

// mysqlKeyMatches extracts the index name from a MySQL duplicate-key message
// ("… for key 'INDEX'" or "… for key 'TABLE.INDEX'") and reports whether it
// equals one of names, or — for a table-qualified key — whether its suffix after
// the last '.' does (so "PRIMARY" matches "table.PRIMARY"). It returns false
// when the "for key" segment is absent rather than guessing against the whole
// message.
func mysqlKeyMatches(message string, names []string) bool {
	key, ok := parseMySQLDupKey(message)
	if !ok {
		return false
	}
	suffix := key
	if i := strings.LastIndex(key, "."); i >= 0 {
		suffix = key[i+1:]
	}
	for _, n := range names {
		if key == n || suffix == n {
			return true
		}
	}
	return false
}

// parseMySQLDupKey returns the key name inside the "for key '…'" segment of a
// MySQL duplicate-entry message, and whether it was found.
func parseMySQLDupKey(message string) (string, bool) {
	const marker = "for key '"
	i := strings.Index(message, marker)
	if i < 0 {
		return "", false
	}
	rest := message[i+len(marker):]
	if j := strings.Index(rest, "'"); j >= 0 {
		return rest[:j], true
	}
	return "", false
}
