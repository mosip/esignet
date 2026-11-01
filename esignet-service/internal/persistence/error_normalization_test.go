/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package persistence

import (
	"errors"
	"fmt"

	"github.com/go-sql-driver/mysql"
	"github.com/jackc/pgx/v5/pgconn"
)

func (ts *PersistenceTestSuite) TestGetErrorCode() {
	tests := []struct {
		name string
		err  error
		want ErrorInfo
	}{
		{
			name: "nil error",
			err:  nil,
			want: ErrorInfo{Code: ErrUnknown},
		},
		{
			name: "pg unique violation carries constraint name",
			err:  &pgconn.PgError{Code: "23505", ConstraintName: "uni_ident_const"},
			want: ErrorInfo{Code: ErrDuplicateKey, Constraint: "uni_ident_const"},
		},
		{
			name: "pg unique violation other constraint",
			err:  &pgconn.PgError{Code: "23505", ConstraintName: "uk_clntdtl_public_key_hash"},
			want: ErrorInfo{Code: ErrDuplicateKey, Constraint: "uk_clntdtl_public_key_hash"},
		},
		{
			name: "pg non-unique code (foreign key) is unknown",
			err:  &pgconn.PgError{Code: "23503", ConstraintName: "uni_ident_const"},
			want: ErrorInfo{Code: ErrUnknown},
		},
		{
			name: "mysql duplicate strips table-qualified index name",
			err:  &mysql.MySQLError{Number: 1062, Message: "Duplicate entry 'abc' for key 'client_detail.uk_clntdtl_public_key_hash'"},
			want: ErrorInfo{Code: ErrDuplicateKey, Constraint: "uk_clntdtl_public_key_hash"},
		},
		{
			name: "mysql primary key reported as PRIMARY",
			err:  &mysql.MySQLError{Number: 1062, Message: "Duplicate entry 'id1' for key 'client_detail.PRIMARY'"},
			want: ErrorInfo{Code: ErrDuplicateKey, Constraint: "PRIMARY"},
		},
		{
			name: "mysql duplicate on another index",
			err:  &mysql.MySQLError{Number: 1062, Message: "Duplicate entry 'x' for key 'client_detail.other_idx'"},
			want: ErrorInfo{Code: ErrDuplicateKey, Constraint: "other_idx"},
		},
		{
			name: "mysql non-duplicate error (foreign key) is unknown",
			err:  &mysql.MySQLError{Number: 1452, Message: "Cannot add or update a child row: a foreign key constraint fails"},
			want: ErrorInfo{Code: ErrUnknown},
		},
		{
			name: "wrapped pg error is unwrapped via errors.As",
			err:  fmt.Errorf("insert key_alias: %w", &pgconn.PgError{Code: "23505", ConstraintName: "uni_ident_const"}),
			want: ErrorInfo{Code: ErrDuplicateKey, Constraint: "uni_ident_const"},
		},
		{
			name: "stringified pg error: duplicate with parsed constraint",
			err:  errors.New(`ERROR: duplicate key value violates unique constraint "uni_ident_const" (SQLSTATE 23505)`),
			want: ErrorInfo{Code: ErrDuplicateKey, Constraint: "uni_ident_const"},
		},
		{
			name: "stringified mysql error: duplicate with parsed index name",
			err:  errors.New("Error 1062: Duplicate entry 'v' for key 'key_alias.uni_ident_const'"),
			want: ErrorInfo{Code: ErrDuplicateKey, Constraint: "uni_ident_const"},
		},
		{
			name: "unrelated error is unknown",
			err:  errors.New("connection refused"),
			want: ErrorInfo{Code: ErrUnknown},
		},
	}
	for _, tt := range tests {
		ts.Run(tt.name, func() {
			ts.Require().Equal(tt.want, GetErrorCode(tt.err))
		})
	}
}
