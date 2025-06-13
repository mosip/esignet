package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.RequestUri;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class PAROAuthDetailsRequest {

    @NotBlank(message = ErrorConstants.INVALID_CLIENT_ID)
    private String clientId;

    @RequestUri
    private String requestUri;
}
