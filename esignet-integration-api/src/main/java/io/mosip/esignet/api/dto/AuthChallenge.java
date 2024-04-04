/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto;

import io.mosip.esignet.api.util.ErrorConstants;
import io.mosip.esignet.api.validator.AuthChallengeFactorFormat;
import io.mosip.esignet.api.validator.AuthChallengeLength;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@AuthChallengeLength
@AuthChallengeFactorFormat
public class AuthChallenge {

    private String authFactorType;

    private String challenge;

    private String format;
}
