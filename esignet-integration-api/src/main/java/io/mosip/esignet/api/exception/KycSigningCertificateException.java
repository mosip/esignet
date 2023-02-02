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
