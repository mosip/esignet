/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

import "crypto/rand"

// GenerateTransactionID generates a cryptographically random 10-digit numeric string,
// reusing any transaction id already established for this runtime context (so
// SendOTP/AuthenticateUser/GetUserAttributes calls for the same flow execution share
// one transaction id, as mock-identity-system and MOSIP IDA require).
func GenerateTransactionID(runtimeMetadata map[string]string) (string, error) {
	if runtimeMetadata != nil && runtimeMetadata["ext_TransactionID"] != "" {
		return runtimeMetadata["ext_TransactionID"], nil
	}

	const digitCount = 10
	b := make([]byte, digitCount)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	for i := range b {
		b[i] = '0' + b[i]%10
	}
	return string(b), nil
}
