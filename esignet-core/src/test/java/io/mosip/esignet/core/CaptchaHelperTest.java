package io.mosip.esignet.core;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.ResponseWrapper;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.CaptchaHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class CaptchaHelperTest {

    @Mock
    RestTemplate restTemplate;

    CaptchaHelper captchaHelper;

    @BeforeEach
    public void setUp() {
        captchaHelper = new CaptchaHelper(restTemplate, "https://api-internal.camdgc-dev1.mosip.net/v1/captcha/validatecaptcha",
                "esignet", List.of("binding-otp"));
    }

    @Test
    public void validateCaptchaToken_withEmptyToken_thenFail() {
        ReflectionTestUtils.setField(captchaHelper, "captchaRequired", List.of("binding-otp"));
        try {
            captchaHelper.validateCaptchaToken("", "binding-otp");
        } catch(EsignetException e) {
            Assertions.assertEquals(ErrorConstants.INVALID_CAPTCHA, e.getErrorCode());
        }
    }


    @Test
    public void validateCaptchaToken_withInValidToken_thenFail() {
        ReflectionTestUtils.setField(captchaHelper, "captchaRequired", List.of("binding-otp"));
        try {
            captchaHelper.validateCaptchaToken("captcha-token", "binding-otp");
        } catch(EsignetException e) {
            Assertions.assertEquals(ErrorConstants.INVALID_CAPTCHA, e.getErrorCode());
        }
    }

    @Test
    public void validateCaptcha_withNullCaptchaToken_thenFail() {
        Assertions.assertThrows(EsignetException.class,()->captchaHelper.validateCaptcha(null));
    }

    @Test
    public void validateCaptcha_withEmptyCaptchaToken_thenFail() {
        Assertions.assertThrows(EsignetException.class,()->captchaHelper.validateCaptcha(""));
    }

    @Test
    public void validateCaptchaToken_withValidData_thenPass() {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponse("success");
        ResponseEntity<ResponseWrapper> responseEntity = ResponseEntity.ok(responseWrapper);
        Mockito.when(restTemplate.exchange(Mockito.any(RequestEntity.class), Mockito.eq(ResponseWrapper.class)))
                .thenReturn(responseEntity);
        boolean result = captchaHelper.validateCaptcha("captchaToken");
        Assertions.assertTrue(result);
    }


    @Test
    public void validateCaptcha_withNullResponse_thenFail() {
        Mockito.when(restTemplate.exchange((RequestEntity<?>) any(), (Class<Object>) any())).thenReturn(null);
        Assertions.assertThrows(EsignetException.class,()->captchaHelper.validateCaptcha("captchaToken"));
    }

    @Test
    public void validateCaptcha_validData_thenPass() {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponse("success");
        ResponseEntity<ResponseWrapper> responseEntity = ResponseEntity.ok(responseWrapper);
        Mockito.when(restTemplate.exchange(Mockito.any(RequestEntity.class), Mockito.eq(ResponseWrapper.class)))
                .thenReturn(responseEntity);
        boolean result = captchaHelper.validateCaptcha("captchaToken");
        Assertions.assertTrue(result);
    }

    @Test
    public void validateCaptcha_unsuccessfulValidation_thenFail() {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setErrors(Arrays.asList("server_unavailable"));
        ResponseEntity<ResponseWrapper> responseEntity = ResponseEntity.ok(responseWrapper);
        Mockito.when(restTemplate.exchange(Mockito.any(RequestEntity.class), Mockito.eq(ResponseWrapper.class)))
                .thenReturn(responseEntity);
        Assertions.assertThrows(EsignetException.class, () -> captchaHelper.validateCaptcha("captchaToken"));
    }

    @Test
    public void validateCaptcha_withRequestException_thenFail() {
        Mockito.when(restTemplate.exchange(Mockito.any(RequestEntity.class), Mockito.eq(ResponseWrapper.class)))
                .thenThrow(new RestClientException("Request failed"));
        Assertions.assertThrows(EsignetException.class, () -> captchaHelper.validateCaptcha("captchaToken"));
    }

}
