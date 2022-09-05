package io.mosip.idp.core.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AuthChallenge {

    @NotBlank
    private String type;

    @NotBlank
    private String challenge;
}
