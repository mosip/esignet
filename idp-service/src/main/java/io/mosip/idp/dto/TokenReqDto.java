/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.dto;

import lombok.Data;

@Data
public class TokenReqDto {

    //TODO Need to add DTO validations

    /**
     * Authorization code grant type.
     */
    private String grant_type;

    /**
     * Authorization code, sent as query param in the client's redirect URI.
     */
    private String code;

    /**
     * Client Id of the OIDC client.
     */
    private String client_id;

    /**
     * Type of the client assertion part of this request.
     * Allowed value:
     * urn:ietf:params:oauth:client-assertion-type:jwt-bearer
     */
    private String client_assertion_type;

    /**
     * Private key signed JWT
     */
    private String client_assertion;

}
