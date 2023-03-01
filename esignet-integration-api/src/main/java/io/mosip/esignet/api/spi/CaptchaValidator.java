package io.mosip.esignet.api.spi;

public interface CaptchaValidator {

    boolean validateCaptcha(String captchaToken);
}
