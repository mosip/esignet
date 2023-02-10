package io.mosip.idp.authwrapper.service;

import io.mosip.esignet.api.spi.CaptchaValidator;
import io.mosip.idp.authwrapper.dto.ReCaptchaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@ConditionalOnProperty(value = "mosip.esignet.integration.captcha-validator", havingValue = "GoogleRecaptchaValidatorService")
@Component
@Slf4j
public class GoogleRecaptchaValidatorService implements CaptchaValidator {

    @Value("${mosip.esignet.captcha-validator.url}")
    private String captchaVerifyUrl;

    @Value("${mosip.esignet.captcha-validator.secret}")
    private String verifierSecret;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public boolean validateCaptcha(String captchaToken) {
        MultiValueMap<String, String> param = new LinkedMultiValueMap<>();
        param.add("secret", verifierSecret);
        param.add("response", captchaToken.trim());

        ReCaptchaResponse reCaptchaResponse = restTemplate.postForObject(captchaVerifyUrl, param,
                ReCaptchaResponse.class);

        if(reCaptchaResponse != null && reCaptchaResponse.isSuccess()) {
            return true;
        }

        log.error("Recaptcha validation failed with errors : {}", reCaptchaResponse != null ?
                reCaptchaResponse.getErrorCodes() : "Response is null");
        return false;
    }
}
