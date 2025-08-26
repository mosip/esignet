/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.exception;

import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.Getter;
import lombok.Setter;

public class EsignetException extends RuntimeException {

    private String errorCode;

    @Setter
    @Getter
    private String dpopNonceHeaderValue;

    public EsignetException() {
        super(ErrorConstants.UNKNOWN_ERROR);
        this.errorCode = ErrorConstants.UNKNOWN_ERROR;
    }

    public EsignetException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public EsignetException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}
