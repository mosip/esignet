package io.mosip.esignet.core.util;

import io.mosip.esignet.core.dto.CaptchaRequest;
import io.mosip.esignet.core.dto.RequestWrapper;
import io.mosip.esignet.core.dto.ResponseWrapper;
import io.mosip.esignet.core.exception.EsignetException;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static io.mosip.esignet.core.constants.Constants.UTC_DATETIME_PATTERN;

@Service
@Slf4j
public class CaptchaHelper {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${mosip.esignet.captcha-validator.url}")
    private String captchaUrl;

    public boolean validateCaptcha(String captchaToken, String moduleName) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        CaptchaRequest captchaRequest = new CaptchaRequest();
        captchaRequest.setCaptchaToken(captchaToken);
        captchaRequest.setModuleName(moduleName);

        RequestWrapper<CaptchaRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        requestWrapper.setRequest(captchaRequest);

        RequestEntity<RequestWrapper<CaptchaRequest>> requestEntity = RequestEntity
                .post(URI.create(captchaUrl))
                .headers(headers)
                .body(requestWrapper);

        ResponseEntity<?> response = restTemplate.exchange(
                requestEntity,
                ResponseEntity.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            ResponseWrapper<?> responseWrapper = (ResponseWrapper<?>) response.getBody();
            if (responseWrapper.getErrors().isEmpty()) {
                log.info("Captcha Validation Successful");
                return true;
            } else {
                log.error("Errors received from CaptchaService: " + response.getStatusCode());
                throw new EsignetException("Captcha validation failed");
            }
        }
        log.error("Errors received from CaptchaService: " + response.getStatusCode());
        throw new EsignetException("Captcha validation failed");
    }
}