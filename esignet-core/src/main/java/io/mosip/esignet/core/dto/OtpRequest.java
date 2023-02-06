/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.OtpChannel;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class OtpRequest {

    @NotBlank(message = ErrorConstants.INVALID_TRANSACTION_ID)
    private String transactionId;

    @NotBlank(message = ErrorConstants.INVALID_IDENTIFIER)
    private String individualId;

    @NotNull(message = ErrorConstants.INVALID_OTP_CHANNEL)
    @Size(min = 1, message = ErrorConstants.INVALID_OTP_CHANNEL)
    private List<@OtpChannel String> otpChannels;

    private String captchaToken;
}
