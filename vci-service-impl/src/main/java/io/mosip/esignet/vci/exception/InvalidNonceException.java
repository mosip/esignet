package io.mosip.esignet.vci.exception;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.EsignetException;

public class InvalidNonceException extends EsignetException {

    private String clientNonce;

    private int clientNonceExpireSeconds;

    public InvalidNonceException(String cNonce, int cNonceExpireSeconds) {
        super(ErrorConstants.PROOF_INVALID_NONCE);
        this.clientNonce = cNonce;
        this.clientNonceExpireSeconds = cNonceExpireSeconds;
    }

    public String getClientNonce() {
        return this.clientNonce;
    }

    public long getClientNonceExpireSeconds() {
        return this.clientNonceExpireSeconds;
    }
}
