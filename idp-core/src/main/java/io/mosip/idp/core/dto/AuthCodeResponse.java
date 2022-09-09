package io.mosip.idp.core.dto;

import lombok.Data;

@Data
public class AuthCodeResponse {

    private String nonce;
    private String code;
    private String redirectUri;
}
