package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class AuthRequestV2 extends AuthRequest {

    private String captchaToken;
}
