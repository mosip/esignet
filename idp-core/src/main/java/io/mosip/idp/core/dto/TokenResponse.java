/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import lombok.Data;

@Data
public class TokenResponse {

    //OpenID Connect identity token.
    private String id_token;

    //The type of the access token, set to Bearer, DPoP or N_A.
    private String access_token;

    //The scope of the access token.
    private String scope;

    //The lifetime of the access token, in seconds.
    private int expires_in;
}
