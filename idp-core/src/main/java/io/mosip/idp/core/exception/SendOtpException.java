package io.mosip.idp.core.exception;

import io.mosip.idp.core.util.ErrorConstants;

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
