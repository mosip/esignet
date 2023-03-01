/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto;

import io.mosip.esignet.api.util.ErrorConstants;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AuthChallenge {

    @NotBlank(message = ErrorConstants.INVALID_AUTH_FACTOR_TYPE)
    private String authFactorType;

    @NotBlank(message = ErrorConstants.INVALID_CHALLENGE)
    private String challenge;

    @NotBlank(message = ErrorConstants.INVALID_CHALLENGE_FORMAT)
    private String format;
}
