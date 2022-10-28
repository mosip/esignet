package io.mosip.idp.core.exception;

import io.mosip.idp.core.util.ErrorConstants;

public class KycAuthException extends Exception {
    private String errorCode;

    public KycAuthException() {
        super(ErrorConstants.AUTH_FAILED);
        this.errorCode = ErrorConstants.AUTH_FAILED;
    }

    public KycAuthException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public KycAuthException(String errorCode, String errorMessage) {
        super(errorCode +  " -> " +  errorMessage);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
