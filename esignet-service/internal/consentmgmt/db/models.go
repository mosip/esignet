/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// ConsentDetail and ConsentHistory are the consent_detail / consent_history row
// models for the consentmgmt persistence layer. (Originally sqlc-generated; now
// hand-maintained alongside db.go.)

package db

import (
	"database/sql"
	"time"
)

type ConsentDetail struct {
	ID                  string         `db:"id" json:"id"`
	ClientID            string         `db:"client_id" json:"client_id"`
	PsuToken            string         `db:"psu_token" json:"psu_token"`
	Claims              string         `db:"claims" json:"claims"`
	AuthorizationScopes string         `db:"authorization_scopes" json:"authorization_scopes"`
	CrDtimes            time.Time      `db:"cr_dtimes" json:"cr_dtimes"`
	ExpireDtimes        sql.NullTime   `db:"expire_dtimes" json:"expire_dtimes"`
	Signature           sql.NullString `db:"signature" json:"signature"`
	Hash                sql.NullString `db:"hash" json:"hash"`
	AcceptedClaims      sql.NullString `db:"accepted_claims" json:"accepted_claims"`
	PermittedScopes     sql.NullString `db:"permitted_scopes" json:"permitted_scopes"`
}

type ConsentHistory struct {
	ID                  string         `db:"id" json:"id"`
	ClientID            string         `db:"client_id" json:"client_id"`
	PsuToken            string         `db:"psu_token" json:"psu_token"`
	Claims              string         `db:"claims" json:"claims"`
	AuthorizationScopes string         `db:"authorization_scopes" json:"authorization_scopes"`
	CrDtimes            time.Time      `db:"cr_dtimes" json:"cr_dtimes"`
	ExpireDtimes        sql.NullTime   `db:"expire_dtimes" json:"expire_dtimes"`
	Signature           sql.NullString `db:"signature" json:"signature"`
	Hash                sql.NullString `db:"hash" json:"hash"`
	AcceptedClaims      sql.NullString `db:"accepted_claims" json:"accepted_claims"`
	PermittedScopes     sql.NullString `db:"permitted_scopes" json:"permitted_scopes"`
}
