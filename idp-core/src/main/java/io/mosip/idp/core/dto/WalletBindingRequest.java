/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;


import java.util.Map;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import io.mosip.idp.core.util.ErrorConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WalletBindingRequest {
	
	@NotBlank(message = ErrorConstants.INVALID_TRANSACTION_ID)
    private String transactionId;

	@NotBlank(message = ErrorConstants.INVALID_INDIVIDUAL_ID)
    private String individualId;

	@NotNull(message = ErrorConstants.INVALID_AUTH_CHALLENGE)
    private AuthChallenge authChallenge;
    
    @NotEmpty(message = ErrorConstants.INVALID_PUBLIC_KEY)
    private Map<String, Object> publicKey;
}
