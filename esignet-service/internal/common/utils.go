/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package common

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"

	applog "github.com/mosip/esignet/internal/log"
)

// MOSIPTimeLayout is MOSIP date-time format
const MOSIPTimeLayout = "2006-01-02T15:04:05.000Z"

// GetResponseTime to get datetime in MOSIP followed format
func GetResponseTime() string {
	return time.Now().UTC().Format(MOSIPTimeLayout)
}

// IsBlank reports whether s is empty or contains only whitespace.
func IsBlank(s string) bool {
	return strings.TrimSpace(s) == ""
}

// WriteError Method write any error
func WriteError(w http.ResponseWriter, status int, code, msg string) {
	WriteJSON(w, status, ResponseWrapper{
		Errors:       []Error{{ErrorCode: code, ErrorMessage: msg}},
		ResponseTime: GetResponseTime(),
	})
}

// WriteJSON writes the json response
func WriteJSON(w http.ResponseWriter, status int, body interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(body); err != nil {
		applog.GetLogger().Error("writeJSON encode error", applog.Error(err))
	}
}
