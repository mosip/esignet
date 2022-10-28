package io.mosip.idp.core.exception;

import io.mosip.idp.core.util.ErrorConstants;

public class KycExchangeException extends Exception {

    private String errorCode;

    public KycExchangeException() {
        super(ErrorConstants.DATA_EXCHANGE_FAILED);
        this.errorCode = ErrorConstants.DATA_EXCHANGE_FAILED;
    }

    public KycExchangeException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public KycExchangeException(String errorCode, String errorMessage) {
        super(errorCode +  " -> " +  errorMessage);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
