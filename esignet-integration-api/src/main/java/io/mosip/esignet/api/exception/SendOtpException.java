/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.exception;

import io.mosip.esignet.api.util.ErrorConstants;

public class SendOtpException extends Exception {

    private String errorCode;

    public SendOtpException() {
        super(ErrorConstants.SEND_OTP_FAILED);
        this.errorCode = ErrorConstants.SEND_OTP_FAILED;
    }

    public SendOtpException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
