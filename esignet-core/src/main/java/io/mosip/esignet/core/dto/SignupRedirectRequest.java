package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class SignupRedirectRequest {

    @NotBlank(message = ErrorConstants.INVALID_TRANSACTION_ID)
    private String transactionId;

    //@NotBlank(message = ErrorConstants.INVALID_PATH_FRAGMENT)
    private String pathFragment;
}
