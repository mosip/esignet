/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dbutil

import (
	"errors"
	"fmt"
	"testing"

	"github.com/go-sql-driver/mysql"
	"github.com/jackc/pgx/v5/pgconn"
)

func TestIsUniqueViolation(t *testing.T) {
	tests := []struct {
		name  string
		err   error
		names []string
		want  bool
	}{
		{
			name:  "nil error",
			err:   nil,
			names: []string{"uni_ident_const"},
			want:  false,
		},
		{
			name:  "pg unique violation matching name",
			err:   &pgconn.PgError{Code: "23505", ConstraintName: "uni_ident_const"},
			names: []string{"uni_ident_const"},
			want:  true,
		},
		{
			name:  "pg unique violation non-matching name",
			err:   &pgconn.PgError{Code: "23505", ConstraintName: "some_other_const"},
			names: []string{"uni_ident_const"},
			want:  false,
		},
		{
			name:  "pg unique violation with no names matches any",
			err:   &pgconn.PgError{Code: "23505", ConstraintName: "anything"},
			names: nil,
			want:  true,
		},
		{
			name:  "pg non-unique code (foreign key violation)",
			err:   &pgconn.PgError{Code: "23503", ConstraintName: "uni_ident_const"},
			names: []string{"uni_ident_const"},
			want:  false,
		},
		{
			name:  "mysql duplicate with table-qualified unique index",
			err:   &mysql.MySQLError{Number: 1062, Message: "Duplicate entry 'abc' for key 'client_detail.uk_clntdtl_public_key_hash'"},
			names: []string{"uk_clntdtl_public_key_hash"},
			want:  true,
		},
		{
			name:  "mysql primary key violation matched via PRIMARY",
			err:   &mysql.MySQLError{Number: 1062, Message: "Duplicate entry 'id1' for key 'client_detail.PRIMARY'"},
			names: []string{"pk_clntdtl_id", "PRIMARY"},
			want:  true,
		},
		{
			name:  "mysql duplicate non-matching name",
			err:   &mysql.MySQLError{Number: 1062, Message: "Duplicate entry 'x' for key 'client_detail.other_idx'"},
			names: []string{"uk_clntdtl_public_key_hash"},
			want:  false,
		},
		{
			name:  "mysql non-duplicate error (foreign key)",
			err:   &mysql.MySQLError{Number: 1452, Message: "Cannot add or update a child row: a foreign key constraint fails"},
			names: nil,
			want:  false,
		},
		{
			name:  "wrapped pg error unwrapped via errors.As",
			err:   fmt.Errorf("insert key_alias: %w", &pgconn.PgError{Code: "23505", ConstraintName: "uni_ident_const"}),
			names: []string{"uni_ident_const"},
			want:  true,
		},
		{
			name:  "plain string fallback matching name",
			err:   errors.New("ERROR: duplicate key value violates unique constraint (SQLSTATE 23505) uni_ident_const"),
			names: []string{"uni_ident_const"},
			want:  true,
		},
		{
			name:  "plain string fallback non-matching name",
			err:   errors.New("ERROR: duplicate key value violates unique constraint (SQLSTATE 23505) some_other_const"),
			names: []string{"uni_ident_const"},
			want:  false,
		},
		{
			name:  "unrelated error",
			err:   errors.New("connection refused"),
			names: nil,
			want:  false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := IsUniqueViolation(tt.err, tt.names...); got != tt.want {
				t.Errorf("IsUniqueViolation(%v, %v) = %v, want %v", tt.err, tt.names, got, tt.want)
			}
		})
	}
}
