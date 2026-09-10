/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

const (
	// LoginIDUIN carries a UIN or a VID; the login screen accepts both.
	LoginIDUIN = "uin"
	// LoginIDPhone carries a mobile number handle.
	LoginIDPhone = "phone"
	// LoginIDEmail carries an email address handle.
	LoginIDEmail = "email"
	// LoginIDNRC carries an NRC identifier handle.
	LoginIDNRC = "nrc"
	// LoginIDUsername is the untyped identifier used by flows that do not offer a choice
	// of login ID type.
	LoginIDUsername = "username"
)

// LoginIDKeys lists the identifier keys a flow may supply, in resolution order.
var LoginIDKeys = []string{LoginIDUIN, LoginIDPhone, LoginIDEmail, LoginIDNRC, LoginIDUsername}

// ResolveIndividualID returns the individual ID the flow supplied and the login ID key that
// carried it, so callers can tell which kind of identifier the user used. Returns ok=false
// when no known key holds a non-empty string.
func ResolveIndividualID(identifiers map[string]any) (individualID, loginIDKey string, ok bool) {
	for _, key := range LoginIDKeys {
		if value, isString := identifiers[key].(string); isString && value != "" {
			return value, key, true
		}
	}
	return "", "", false
}
