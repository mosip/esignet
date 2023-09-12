/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.OIDCGrantType;
import io.mosip.esignet.core.validator.OIDCClientAssertionType;
import lombok.Data;
import io.mosip.esignet.core.validator.RedirectURL;

import javax.validation.constraints.NotBlank;

@Data
public class TokenRequest {

    /**
     * Authorization code grant type.
     */
    @OIDCGrantType
    private String grant_type;

    /**
     * Authorization code, sent as query param in the client's redirect URI.
     */
    @NotBlank(message = ErrorConstants.INVALID_AUTH_CODE)
    private String code;

    /**
     * Client ID of the OIDC client.
     */
    private String client_id;

    /**
     * Valid client redirect_uri.
     */
    @RedirectURL
    private String redirect_uri;

    /**
     * Type of the client assertion part of this request.
     */
    @OIDCClientAssertionType
    private String client_assertion_type;

    /**
     * Private key signed JWT
     */
    @NotBlank(message = ErrorConstants.INVALID_ASSERTION)
    private String client_assertion;

    /**
     * The code verifier for the PKCE request.
     */
    private String code_verifier;
}
