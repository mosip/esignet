package io.mosip.esignet.core.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class PAROAuthDetailsRequest {

    @NotBlank
    private String clientId;

    @NotBlank
    private String requestUri;
}
