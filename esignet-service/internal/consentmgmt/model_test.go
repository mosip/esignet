/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package consentmgmt

import (
	"database/sql"
	"testing"
	"time"
)

func TestConsentRecord_IsExpired_NullExpiryNeverExpires(t *testing.T) {
	c := &ConsentRecord{}
	if c.IsExpired(time.Now().UTC()) {
		t.Fatal("a null expiry should never be reported as expired")
	}
}

func TestConsentRecord_IsExpired_PastExpiry(t *testing.T) {
	now := time.Now().UTC()
	c := &ConsentRecord{ExpiresAt: sql.NullTime{Time: now.Add(-time.Minute), Valid: true}}
	if !c.IsExpired(now) {
		t.Fatal("expiry in the past should be reported as expired")
	}
}

func TestConsentRecord_IsExpired_FutureExpiry(t *testing.T) {
	now := time.Now().UTC()
	c := &ConsentRecord{ExpiresAt: sql.NullTime{Time: now.Add(time.Minute), Valid: true}}
	if c.IsExpired(now) {
		t.Fatal("expiry in the future should not be reported as expired")
	}
}
