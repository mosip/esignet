/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dbutil

import "testing"

func TestRebind(t *testing.T) {
	tests := []struct {
		name       string
		driverName string
		query      string
		want       string
	}{
		{
			name:       "postgres converts to dollar placeholders",
			driverName: "postgres",
			query:      "SELECT * FROM t WHERE a = ? AND b = ?",
			want:       "SELECT * FROM t WHERE a = $1 AND b = $2",
		},
		{
			name:       "pgx converts to dollar placeholders",
			driverName: "pgx",
			query:      "SELECT * FROM t WHERE a = ?",
			want:       "SELECT * FROM t WHERE a = $1",
		},
		{
			name:       "mysql keeps question marks",
			driverName: "mysql",
			query:      "SELECT * FROM t WHERE a = ? AND b = ?",
			want:       "SELECT * FROM t WHERE a = ? AND b = ?",
		},
		{
			name:       "no placeholders unchanged",
			driverName: "postgres",
			query:      "SELECT 1",
			want:       "SELECT 1",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := Rebind(tt.driverName, tt.query); got != tt.want {
				t.Errorf("Rebind(%q, %q) = %q, want %q", tt.driverName, tt.query, got, tt.want)
			}
		})
	}
}
