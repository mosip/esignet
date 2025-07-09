package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class CompleteSignupRedirectRequest {

    @NotBlank(message = ErrorConstants.INVALID_TRANSACTION_ID)
    private String transactionId;
}
