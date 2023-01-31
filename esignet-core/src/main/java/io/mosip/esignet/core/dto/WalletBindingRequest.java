/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;


import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WalletBindingRequest {

	@NotBlank(message = ErrorConstants.INVALID_INDIVIDUAL_ID)
    private String individualId;

	@NotNull(message = ErrorConstants.INVALID_CHALLENGE_LIST)
	@Size(min = 1, max = 5, message = ErrorConstants.INVALID_CHALLENGE_LIST)
	private List<@Valid AuthChallenge> challengeList;
    
    @NotEmpty(message = ErrorConstants.INVALID_PUBLIC_KEY)
    private Map<String, Object> publicKey;

	@NotEmpty(message = ErrorConstants.INVALID_AUTH_FACTOR_TYPE)
	private String authFactorType; //bind a key to a single auth-factor-type

	@NotEmpty(message = ErrorConstants.INVALID_CHALLENGE_FORMAT)
	private String format;

}
