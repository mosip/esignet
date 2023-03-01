/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.exception;

import io.mosip.esignet.api.util.ErrorConstants;

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
