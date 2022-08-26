/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.util;

public class ErrorConstants {

    public static final String INVALID_REQUEST="invalid_request";
    public static final String INVALID_CLIENT_ID="invalid_client_id";
    public static final String INVALID_RESPONSE_TYPE="invalid_response_type";
    public static final String INVALID_GRANT_TYPE="invalid_grant_type";
    public static final String INVALID_SCOPE="invalid_scope";
    public static final String INVALID_REDIRECT_URI="invalid_redirect_uri";
    public static final String INVALID_DISPLAY="invalid_display";
    public static final String INVALID_PROMPT="invalid_prompt";
    public static final String INVALID_ASSERTION_TYPE="invalid_assertion_type";
    public static final String INVALID_TRANSACTION="invalid_transaction";
    public static final String INVALID_CODE="invalid_code";
    public static final String INVALID_ASSERTION="invalid_assertion";
    public static final String INVALID_ACR="invalid_acr";
    public static final String INVALID_AUTH_TOKEN="invalid_token";
    public static final String AUTH_FAILED="auth_failed";
    public static final String AUTH_PASSED="auth_passed";
    public static final String ACR_AMR_MAPPING_NOT_FOUND="acr_amr_mapping_not_found";
    public static final String NO_ACR_REGISTERED="no_acr_registered";
    public static final String DEFAULT_ERROR_CODE = "unknown_error";
    public static final String DEFAULT_ERROR_MSG = "UNKNOWN ERROR";
    public static final String INVALID_INPUT_ERROR_MSG = "UNSUPPORTED INPUT PARAMETER";

    public static final String DUPLICATE_CLIENT_ID = "duplicate_client_id";
    public static final String INVALID_CLAIM = "invalid_claim";
    public static final String INVALID_JWKS = "invalid_jwks";
}
