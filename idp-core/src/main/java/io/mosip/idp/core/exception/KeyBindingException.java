/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.exception;

import io.mosip.idp.core.util.ErrorConstants;

public class KeyBindingException extends Exception {

    private String errorCode;

    public KeyBindingException() {
        super(ErrorConstants.KEY_BINDING_FAILED);
        this.errorCode = ErrorConstants.KEY_BINDING_FAILED;
    }

    public KeyBindingException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public KeyBindingException(String errorCode, String errorMessage) {
        super(errorCode +  " -> " +  errorMessage);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}