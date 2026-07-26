/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

// NewNoOpTransactioner creates a Transactioner that simply executes the function without
// wrapping it in a database transaction. This is used for file-based (declarative) store modes
// where no database transaction management is needed.
func NewNoOpTransactioner() providers.Transactioner {
	return &noOpTransactioner{}
}

// noOpTransactioner is a no-op implementation that simply executes the function directly.
// Used for declarative (file-based) store modes where no database transaction is needed.
type noOpTransactioner struct{}

// Transact executes the given function directly without transaction wrapping.
func (n *noOpTransactioner) Transact(ctx context.Context, txFunc func(context.Context) error) error {
	return txFunc(ctx)
}
