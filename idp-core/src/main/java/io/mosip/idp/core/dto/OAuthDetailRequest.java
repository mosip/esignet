/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import io.mosip.idp.core.validator.*;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import static io.mosip.idp.core.util.ErrorConstants.INVALID_CLIENT_ID;
import static io.mosip.idp.core.util.ErrorConstants.INVALID_REDIRECT_URI;

@Data
public class OAuthDetailRequest {

    @NotNull(message = INVALID_CLIENT_ID)
    @NotBlank(message = INVALID_CLIENT_ID)
    private String clientId;

    @OIDCScope
    private String scope;

    @OIDCResponseType
    private String responseType;

    @NotNull(message = INVALID_REDIRECT_URI)
    @URL(message = INVALID_REDIRECT_URI)
    private String redirectUri;

    @OIDCDisplay
    private String display;

    @OIDCPrompt
    private String prompt;

    @AuthContextRef
    private String acrValues; //Space-separated string

    /**
     * Optional value
     */
    private Claims claims;
}
