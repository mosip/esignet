/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import javax.validation.constraints.NotBlank;

import io.mosip.idp.core.util.ErrorConstants;
import lombok.Data;

@Data
public class ValidateBindingRequest {
	
	@NotBlank(message = ErrorConstants.INVALID_INDIVIDUAL_ID)
    private String individualId;
	
	@NotBlank(message = ErrorConstants.INVALID_WFA_TOKEN)
    private String wfaToken;

}
