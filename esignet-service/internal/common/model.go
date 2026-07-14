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
