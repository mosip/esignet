package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class CaptchaRequest {
    private static final long serialVersionUID = 1L;
    private String captchaToken;
    private String moduleName;
}
