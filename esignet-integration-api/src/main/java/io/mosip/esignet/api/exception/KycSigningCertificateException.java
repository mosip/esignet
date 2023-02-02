/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.exception;

import io.mosip.esignet.api.util.ErrorConstants;

public class KycSigningCertificateException extends Exception {
	
    private String errorCode;

    public KycSigningCertificateException() {
        super(ErrorConstants.KYC_SIGNING_CERTIFICATE_FAILED);
        this.errorCode = ErrorConstants.KYC_SIGNING_CERTIFICATE_FAILED;
    }

    public KycSigningCertificateException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public KycSigningCertificateException(String errorCode, String errorMessage) {
        super(errorCode +  " -> " +  errorMessage);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
    
}
