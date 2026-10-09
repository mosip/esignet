/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package dbutil holds small cross-database helpers shared by the clientmgmt,
// consentmgmt and keymanager persistence layers: placeholder rebinding and
// unique-constraint-violation classification across PostgreSQL and MySQL.
package dbutil

import "github.com/jmoiron/sqlx"

// Rebind rewrites `?`-style placeholders into the style driverName expects
// ($1, $2, … for "postgres"/"pgx", ? for "mysql"). Write every query with `?`
// and call Rebind once, where the query string is built, so the same query
// text works on every supported database. driverName is matched by
// sqlx.BindType, which already knows the common driver names; an unrecognized
// name leaves the query unchanged.
func Rebind(driverName, query string) string {
	return sqlx.Rebind(sqlx.BindType(driverName), query)
}
