/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import io.mosip.idp.core.validator.OIDCClientAssertionType;
import io.mosip.idp.core.validator.OIDCGrantType;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import static io.mosip.idp.core.util.ErrorConstants.*;

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
    @NotBlank(message = INVALID_AUTH_CODE)
    private String code;

    /**
     * Client ID of the OIDC client.
     */
    private String client_id;

    /**
     * Valid client redirect_uri.
     */
    @NotBlank(message = INVALID_REDIRECT_URI)
    @URL(message = INVALID_REDIRECT_URI)
    private String redirect_uri;

    /**
     * Type of the client assertion part of this request.
     */
    @OIDCClientAssertionType
    private String client_assertion_type;

    /**
     * Private key signed JWT
     */
    @NotBlank(message = INVALID_ASSERTION)
    private String client_assertion;

}
