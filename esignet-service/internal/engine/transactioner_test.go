/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"errors"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

func (ts *TransactionerTestSuite) TestNoOpTransactioner_Transact_Success() {
	tx := NewNoOpTransactioner()

	called := false
	err := tx.Transact(context.Background(), func(_ context.Context) error {
		called = true
		return nil
	})
	require.NoError(ts.T(), err)
	require.True(ts.T(), called)
}

func (ts *TransactionerTestSuite) TestNoOpTransactioner_Transact_PropagatesError() {
	tx := NewNoOpTransactioner()

	wantErr := errors.New("boom")
	err := tx.Transact(context.Background(), func(_ context.Context) error {
		return wantErr
	})
	require.ErrorIs(ts.T(), err, wantErr)
}

func (ts *TransactionerTestSuite) TestNoOpTransactioner_Transact_PassesContextThrough() {
	tx := NewNoOpTransactioner()

	type ctxKey struct{}
	ctx := context.WithValue(context.Background(), ctxKey{}, "value")

	var gotValue any
	err := tx.Transact(ctx, func(passedCtx context.Context) error {
		gotValue = passedCtx.Value(ctxKey{})
		return nil
	})
	require.NoError(ts.T(), err)
	require.Equal(ts.T(), "value", gotValue)
}

type TransactionerTestSuite struct {
	suite.Suite
}

func TestTransactionerTestSuite(t *testing.T) {
	suite.Run(t, new(TransactionerTestSuite))
}
