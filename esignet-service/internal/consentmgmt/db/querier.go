/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Querier is the persistence contract the consentmgmt service programs against.
// (Originally sqlc-generated; now hand-maintained — db.Queries implements it.)

package db

import (
	"context"
)

type Querier interface {
	DeleteConsent(ctx context.Context, arg DeleteConsentParams) error
	GetConsent(ctx context.Context, arg GetConsentParams) (ConsentDetail, error)
	InsertConsentHistory(ctx context.Context, arg InsertConsentHistoryParams) error
	UpsertConsent(ctx context.Context, arg UpsertConsentParams) error
}

var _ Querier = (*Queries)(nil)
