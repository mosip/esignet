package io.mosip.esignet.core.config;

import io.mosip.esignet.core.util.CaptchaHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class SharedComponentConfig {

    @Autowired
    private RestTemplate restTemplate;

    @Bean
    public CaptchaHelper captchaHelper(@Value("${mosip.esignet.captcha.validator-url}") String validatorUrl,
                                       @Value("${mosip.esignet.captcha.module-name}") String moduleName,
                                       @Value("#{'${mosip.esignet.captcha.required:}'}")List<String> captchaRequired) {
        return new CaptchaHelper(restTemplate, validatorUrl, moduleName, captchaRequired);
    }
}
