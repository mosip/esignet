package io.mosip.esignet.core.util;

import io.mosip.esignet.core.constants.ErrorConstants;
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

    @Value("${mosip.esignet.captcha.module-name}")
    private String moduleName;

    @Value("${mosip.esignet.captcha.validator-url}")
    private String validatorUrl;

    public boolean validateCaptcha(String captchaToken) {

        if (captchaToken == null || captchaToken.isEmpty()) {
            throw new EsignetException(ErrorConstants.INVALID_CAPTCHA);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        CaptchaRequest captchaRequest = new CaptchaRequest();
        captchaRequest.setCaptchaToken(captchaToken);
        captchaRequest.setModuleName(moduleName);

        RequestWrapper<CaptchaRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        requestWrapper.setRequest(captchaRequest);

        RequestEntity<RequestWrapper<CaptchaRequest>> requestEntity = RequestEntity
                .post(URI.create(validatorUrl))
                .headers(headers)
                .body(requestWrapper);

        ResponseEntity<?> responseEntity = restTemplate.exchange(
                requestEntity,
                ResponseEntity.class
        );

        if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
            ResponseWrapper<?> responseWrapper = (ResponseWrapper<?>) responseEntity.getBody();
            if (responseWrapper != null && responseWrapper.getResponse() != null) {
                log.info("Captcha Validation Successful");
                return true;
            }
            log.error("Errors received from CaptchaService: {}", responseWrapper.getErrors()); //NOSONAR responseWrapper is already evaluated to be not null
            throw new EsignetException(ErrorConstants.INVALID_CAPTCHA);
        }
        log.error("Errors received from CaptchaService: {}",responseEntity.getStatusCode());
        throw new EsignetException(ErrorConstants.INVALID_CAPTCHA);
    }

}