/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package db

import (
	"context"
)

// Querier is the persistence contract the clientmgmt service programs against.
// db.Queries implements it; both are hand-maintained.
type Querier interface {
	CreateClient(ctx context.Context, arg CreateClientParams) (ClientDetail, error)
	GetActiveClient(ctx context.Context, id string) (ClientDetail, error)
	GetClient(ctx context.Context, id string) (ClientDetail, error)
	PatchClient(ctx context.Context, arg PatchClientParams) (ClientDetail, error)
	UpdateClient(ctx context.Context, arg UpdateClientParams) (ClientDetail, error)
}

var _ Querier = (*Queries)(nil)
