/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

// SendOTPResult represents the result of an generate and notify OTP attempt.
type SendOTPResult struct {
	MaskedEmail  string `json:"maskedEmail,omitempty"`
	MaskedMobile string `json:"maskedMobile,omitempty"`
}
