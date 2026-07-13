/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package common for shared models
package common

// RequestWrapper is the MOSIP envelope for requests.
type RequestWrapper struct {
	RequestTime string `json:"requestTime"`
}

// ResponseWrapper is the MOSIP envelope for responses.
type ResponseWrapper struct {
	ResponseTime string  `json:"responseTime"`
	Errors       []Error `json:"errors,omitempty"`
}

// Error is a single entry in a MOSIP API error list.
type Error struct {
	ErrorCode    string `json:"errorCode"`
	ErrorMessage string `json:"errorMessage"`
}
