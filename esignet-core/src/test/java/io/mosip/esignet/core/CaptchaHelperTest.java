package io.mosip.esignet.core;

import io.mosip.esignet.core.dto.ResponseWrapper;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.CaptchaHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@RunWith(MockitoJUnitRunner.class)
public class CaptchaHelperTest {

    @Mock
    RestTemplate restTemplate;

    CaptchaHelper captchaHelper;

    @Before
    public void setUp() {
        captchaHelper = new CaptchaHelper(restTemplate, "https://api-internal.camdgc-dev1.mosip.net/v1/captcha/validatecaptcha",
                "esignet");
    }

    @Test
    public void validateCaptcha_withNullCaptchaToken_thenFail() {
        Assert.assertThrows(EsignetException.class,()->captchaHelper.validateCaptcha(null));
    }

    @Test
    public void validateCaptcha_withEmptyCaptchaToken_thenFail() {
        Assert.assertThrows(EsignetException.class,()->captchaHelper.validateCaptcha(""));
    }

    @Test
    public void validateCaptcha_withNullResponse_thenFail() {
        Mockito.when(restTemplate.exchange((RequestEntity<?>) any(), (Class<Object>) any())).thenReturn(null);
        Assert.assertThrows(EsignetException.class,()->captchaHelper.validateCaptcha("captchaToken"));
    }

    @Test
    public void validateCaptcha_validData_thenPass() {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponse("success");
        ResponseEntity<ResponseWrapper> responseEntity = ResponseEntity.ok(responseWrapper);
        Mockito.when(restTemplate.exchange(Mockito.any(RequestEntity.class), Mockito.eq(ResponseWrapper.class)))
                .thenReturn(responseEntity);
        boolean result = captchaHelper.validateCaptcha("captchaToken");
        Assert.assertTrue(result);
    }

    @Test
    public void validateCaptcha_unsuccessfulValidation_thenFail() {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setErrors(Arrays.asList("server_unavailable"));
        ResponseEntity<ResponseWrapper> responseEntity = ResponseEntity.ok(responseWrapper);
        Mockito.when(restTemplate.exchange(Mockito.any(RequestEntity.class), Mockito.eq(ResponseWrapper.class)))
                .thenReturn(responseEntity);
        Assert.assertThrows(EsignetException.class, () -> captchaHelper.validateCaptcha("captchaToken"));
    }

    @Test
    public void validateCaptcha_withRequestException_thenFail() {
        Mockito.when(restTemplate.exchange(Mockito.any(RequestEntity.class), Mockito.eq(ResponseWrapper.class)))
                .thenThrow(new RestClientException("Request failed"));
        Assert.assertThrows(EsignetException.class, () -> captchaHelper.validateCaptcha("captchaToken"));
    }

}
