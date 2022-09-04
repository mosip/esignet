/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.exception;

public class IdPException extends Exception {

    private String errorCode;

    public IdPException() {
        super("UNKNOWN_ERROR");
        this.errorCode = "UNKNOWN_ERROR";
    }

    public IdPException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
