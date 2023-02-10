package io.mosip.idp.authwrapper.dto;

import lombok.Data;

@Data
public class ReCaptchaError {

    private String errorCode;
    private String message;
}
