package io.mosip.esignet.core.exception;

import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.Getter;
import lombok.Setter;

public class DpopNonceMissingException extends EsignetException {

    @Setter
    @Getter
    private String dpopNonceHeaderValue;

    private final String message = "Authorization server requires nonce in DPoP proof";

    public DpopNonceMissingException(String dpopNonceHeaderValue) {
        super(ErrorConstants.USE_DPOP_NONCE);
        this.dpopNonceHeaderValue = dpopNonceHeaderValue;
    }

    @Override
    public String getMessage() {
        return message;
    }

}
