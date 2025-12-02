package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.RequestUri;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class PushedOAuthDetailRequest {

    @NotBlank(message = ErrorConstants.INVALID_CLIENT_ID)
    private String clientId;

    @RequestUri
    private String requestUri;
}
