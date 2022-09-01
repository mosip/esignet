package io.mosip.idp.core.exception;

import io.mosip.idp.core.util.ErrorConstants;

public class InvalidTransactionException extends IdPException {

    public InvalidTransactionException() {
        super(ErrorConstants.INVALID_TRANSACTION);
    }
}
