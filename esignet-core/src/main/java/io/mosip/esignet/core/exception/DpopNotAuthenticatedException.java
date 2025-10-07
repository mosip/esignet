package io.mosip.esignet.core.exception;

import io.mosip.esignet.core.constants.ErrorConstants;

public class DpopNotAuthenticatedException extends EsignetException {
    public DpopNotAuthenticatedException() {
        super(ErrorConstants.INVALID_AUTH_TOKEN);
    }
}