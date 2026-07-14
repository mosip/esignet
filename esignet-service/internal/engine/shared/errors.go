/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package shared provides common types and errors for authentication providers.
package shared

import "github.com/thunder-id/thunderid/pkg/thunderidengine/common"

// NotImplementedError is returned when a feature is not implemented.
var NotImplementedError = &common.ServiceError{
	Code: "not_implemented",
	Type: common.ClientErrorType,
	Error: common.I18nMessage{
		Key:          "not_implemented",
		DefaultValue: "Not implemented",
	},
	ErrorDescription: common.I18nMessage{
		Key:          "not_implemented_description",
		DefaultValue: "The feature is not implemented",
	},
}

// ClientNotFoundError is returned when a client is not found.
var ClientNotFoundError = &common.ServiceError{
	Code: "client_not_found",
	Type: common.ClientErrorType,
	Error: common.I18nMessage{
		Key:          "client_not_found",
		DefaultValue: "Client not found",
	},
	ErrorDescription: common.I18nMessage{
		Key:          "client_not_found_description",
		DefaultValue: "The client with the given ID was not found",
	},
}

// InvalidIndividualIDError is returned when the individual_id in identifiers is missing or invalid.
var InvalidIndividualIDError = &common.ServiceError{
	Code: "missing_or_invalid_individual_id",
	Type: common.ClientErrorType,
	Error: common.I18nMessage{
		Key:          "missing_or_invalid_individual_id",
		DefaultValue: "Missing or invalid individual_id in identifiers",
	},
	ErrorDescription: common.I18nMessage{
		Key:          "missing_or_invalid_individual_id_description",
		DefaultValue: "The individual_id in identifiers is missing or invalid",
	},
}

// InvalidRequestError is returned when the request is invalid.
var InvalidRequestError = &common.ServiceError{
	Code: "invalid_request",
	Type: common.ClientErrorType,
	Error: common.I18nMessage{
		Key:          "invalid_request",
		DefaultValue: "Invalid request",
	},
	ErrorDescription: common.I18nMessage{
		Key:          "invalid_request_description",
		DefaultValue: "The request is invalid",
	},
}

// AuthenticationFailedError is returned when the authentication failed.
var AuthenticationFailedError = &common.ServiceError{
	Code: "authentication_failed",
	Type: common.ClientErrorType,
	Error: common.I18nMessage{
		Key:          "authentication_failed",
		DefaultValue: "Authentication failed",
	},
	ErrorDescription: common.I18nMessage{
		Key:          "authentication_failed_description",
		DefaultValue: "The authentication failed with the given credentials",
	},
}

// SendOTPFailedError is returned when the send OTP failed.
var SendOTPFailedError = &common.ServiceError{
	Code: "send_otp_failed",
	Type: common.ClientErrorType,
	Error: common.I18nMessage{
		Key:          "send_otp_failed",
		DefaultValue: "Send OTP failed",
	},
	ErrorDescription: common.I18nMessage{
		Key:          "send_otp_failed_description",
		DefaultValue: "The send OTP failed with the given credentials",
	},
}

// FileNotFoundError is returned when a required configuration file is not found.
var FileNotFoundError = &common.ServiceError{
	Code: "file_not_found",
	Type: common.ClientErrorType,
	Error: common.I18nMessage{
		Key:          "file_not_found",
		DefaultValue: "File not found",
	},
	ErrorDescription: common.I18nMessage{
		Key:          "file_not_found_description",
		DefaultValue: "The file with the given ID was not found",
	},
}

// FileUnmarshallError is returned when a configuration file cannot be unmarshalled.
var FileUnmarshallError = &common.ServiceError{
	Code: "file_unmarshall_error",
	Type: common.ClientErrorType,
	Error: common.I18nMessage{
		Key:          "file_unmarshall_error",
		DefaultValue: "File unmarshall error",
	},
	ErrorDescription: common.I18nMessage{
		Key:          "file_unmarshall_error_description",
		DefaultValue: "The file could not be unmarshalled",
	},
}
