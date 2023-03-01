package io.mosip.esignet.captcha.spi;

import io.mosip.esignet.captcha.exception.CaptchaException;
import io.mosip.esignet.captcha.exception.InvalidRequestCaptchaException;

public interface CaptchaService{

	Object validateCaptcha(Object captchaRequest) throws CaptchaException, InvalidRequestCaptchaException;


}
