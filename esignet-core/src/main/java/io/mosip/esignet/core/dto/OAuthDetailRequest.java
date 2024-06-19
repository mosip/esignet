/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.api.dto.claim.ClaimsV2;
import io.mosip.esignet.core.validator.OIDCDisplay;
import io.mosip.esignet.core.validator.OIDCPrompt;
import io.mosip.esignet.core.validator.OIDCResponseType;
import io.mosip.esignet.core.validator.OIDCScope;
import lombok.Data;

import io.mosip.esignet.core.validator.RedirectURL;
import javax.validation.constraints.NotBlank;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLIENT_ID;

@Data
public class OAuthDetailRequest {

    @NotBlank(message = INVALID_CLIENT_ID)
    private String clientId;

    @OIDCScope
    private String scope;

    @OIDCResponseType
    private String responseType;

    @RedirectURL
    private String redirectUri;

    /**
     * Optional
     */
    @OIDCDisplay
    private String display;

    /**
     * Optional
     */
    @OIDCPrompt
    private String prompt;

    /**
     * Optional
     */
    private String nonce;

    /**
     * Optional
     */
    private String state;

    /**
     * Optional
     */
    private String acrValues; //Space-separated string

    /**
     * Optional
     * Maximum Authentication Age. Specifies the allowable elapsed time in seconds since the last time
     * the End-User was actively authenticated by the OP. If the elapsed time is greater than this value,
     * the OP MUST attempt to actively re-authenticate the End-User.
     */
    private int maxAge;

    /**
     * Optional value
     * The userinfo and id_token members of the claims request both are JSON objects with the
     * names of the individual Claims being requested as the member names.
     */
    private ClaimsV2 claims;

    /**
     * Optional
     * End-User's preferred languages and scripts for Claims being returned,
     * represented as a space-separated list of BCP47 [RFC5646] language tag values, ordered by preference.
     */
    private String claimsLocales;

    /**
     * Optional
     */
    private String uiLocales;
}
