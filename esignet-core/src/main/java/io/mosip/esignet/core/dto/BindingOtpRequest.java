/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.OtpChannel;
import lombok.Data;

@Data
public class BindingOtpRequest {

    @NotBlank(message = ErrorConstants.INVALID_IDENTIFIER)
    private String individualId;

    @NotNull(message = ErrorConstants.INVALID_OTP_CHANNEL)
    @Size(min = 1, message = ErrorConstants.INVALID_OTP_CHANNEL)
    private List<@OtpChannel String> otpChannels;
}
