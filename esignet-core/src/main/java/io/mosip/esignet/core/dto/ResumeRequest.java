package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ResumeRequest {

    @NotBlank(message = ErrorConstants.INVALID_TRANSACTION_ID)
    private String transactionId;

    private boolean withError;
}
