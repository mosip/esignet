package common

import (
	"encoding/json"
	"log"
	"net/http"
	"time"
)

// MOSIPTimeLayout is MOSIP date-time format
const MOSIPTimeLayout = "2006-01-02T15:04:05.000Z"

// GetResponseTime to get datetime in MOSIP followed format
func GetResponseTime() string {
	return time.Now().UTC().Format(MOSIPTimeLayout)
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
		log.Printf("writeJSON encode error: %v", err)
	}
}
