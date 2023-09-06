/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;


@Data
public class TokenResponse {

    /**
     * OpenID Connect identity token.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String id_token;

    /**
     * The type of the access token, set to Bearer, DPoP or N_A.
     */
    private String token_type;

    /**
     * The access token. The token that will be used to call the UserInfo endpoint.
     */
    private String access_token;

    /**
     * The lifetime of the access token, in seconds.
     */
    private int expires_in;

    /**
     * JSON string containing a nonce to be used to create a proof of possession of key material when requesting a Credential
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String c_nonce;

    /**
     *  JSON integer denoting the lifetime in seconds of the c_nonce.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer c_nonce_expires_in;
}
