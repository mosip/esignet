package keymanager

import (
	"crypto/sha1" //nolint:gosec // sha1 used for value identification only, not for any sensitive data — mirrors KeymanagerUtil.getUniqueIdentifier in the Java service.
	"encoding/hex"
	"strings"
	"time"

	"github.com/mosip/esignet/internal/keymanager/db"
)

// expiryFor computes a key's expiry timestamp from its generation time and
// applicable policy — mirrors KeymanagerDBHelper.getExpiryPolicy.
func expiryFor(genTime time.Time, policy db.KeyPolicy) time.Time {
	return genTime.AddDate(0, 0, policy.KeyValidityDuration)
}

// isCurrent reports whether a key generated at genTime, expiring at
// expiryTime under the given pre-expiry buffer, is still "current" at now —
// mirrors KeymanagerUtil.isValidTimestamp. A key stops being served
// preExpireDays before its hard expiry, so a replacement starts being
// generated ahead of the actual cutoff.
func isCurrent(now, genTime, expiryTime time.Time, preExpireDays int) bool {
	return now.After(genTime) && now.Before(expiryTime.AddDate(0, 0, -preExpireDays))
}

// uniqueIdentifier computes key_alias.uni_ident exactly as
// KeymanagerUtil.getUniqueIdentifier / KeymanagerServiceImpl do:
// SHA1(appID + "_" + refID + "_" + now.Format("MM-dd-yyyy")), uppercase hex.
// This value carries a DB-level UNIQUE constraint (uni_ident_const,
// keymgr-key_alias.sql) which is the system's actual defense against
// concurrent duplicate-generation races — see IsDuplicateUniIdent below.
func uniqueIdentifier(appID, refID string, now time.Time) string {
	raw := appID + "_" + refID + "_" + now.Format("01-02-2006") // Go layout for MM-dd-yyyy
	sum := sha1.Sum([]byte(raw))                                //nolint:gosec
	return strings.ToUpper(hex.EncodeToString(sum[:]))
}

// IsDuplicateUniIdent reports whether err is a Postgres unique-violation on
// the uni_ident_const constraint — the expected, benign outcome when two
// concurrent requests decide "no current key, must generate" for the same
// (appID, refID) on the same calendar day. Mirrors the isDuplicateClientID
// helper convention in internal/clientmgmt/service.go.
func IsDuplicateUniIdent(err error) bool {
	if err == nil {
		return false
	}
	msg := err.Error()
	return strings.Contains(msg, "23505") && strings.Contains(msg, "uni_ident_const")
}
