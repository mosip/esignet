/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.mosip.idp.core.util.ErrorConstants;
import lombok.Data;

@Data
public class BindingOtpRequest {

	@NotBlank(message = ErrorConstants.INVALID_INDIVIDUAL_ID)
    private String individualId;

    @NotNull(message = ErrorConstants.INVALID_OTP_CHANNEL)
    @Size(min = 1, message = ErrorConstants.INVALID_OTP_CHANNEL)
    private List<String> otpChannels;
}
