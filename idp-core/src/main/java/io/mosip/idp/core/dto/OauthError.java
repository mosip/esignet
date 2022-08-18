package io.mosip.idp.core.dto;

import lombok.Data;

@Data
public class OauthError {

    private String error;
    private String error_description;
}
