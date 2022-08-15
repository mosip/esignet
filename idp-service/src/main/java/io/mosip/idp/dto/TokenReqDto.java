/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.dto;

import io.mosip.idp.validators.OIDCClientAssertionType;
import io.mosip.idp.validators.OIDCGrantType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import static io.mosip.idp.util.ErrorConstants.INVALID_REQUEST;

@Data
public class TokenReqDto {

    /**
     * Authorization code grant type.
     */
    @OIDCGrantType
    private String grant_type;

    /**
     * Authorization code, sent as query param in the client's redirect URI.
     */
    @NotNull(message = INVALID_REQUEST)
    @NotBlank(message = INVALID_REQUEST)
    private String code;

    /**
     * Client ID of the OIDC client.
     */
    @NotNull(message = INVALID_REQUEST)
    @NotBlank(message = INVALID_REQUEST)
    private String client_id;

    /**
     * Type of the client assertion part of this request.
     */
    @OIDCClientAssertionType
    private String client_assertion_type;

    /**
     * Private key signed JWT
     */
    @NotNull(message = INVALID_REQUEST)
    @NotBlank(message = INVALID_REQUEST)
    private String client_assertion;

}
