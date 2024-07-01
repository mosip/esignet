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

import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@RunWith(MockitoJUnitRunner.class)
public class CaptchaHelperTest {

    @Mock
    RestTemplate restTemplate;

    @InjectMocks
    CaptchaHelper captchaHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(captchaHelper, "validatorUrl", "https://api-internal.camdgc-dev1.mosip.net/v1/captcha/validatecaptcha");
        ReflectionTestUtils.setField(captchaHelper,"moduleName","esignet");
    }

    @Test
    public void validateCaptcha_WithNullCaptchaToken_ThrowsException() {
        CaptchaHelper captchaHelper=new CaptchaHelper();
        Assert.assertThrows(EsignetException.class,()->captchaHelper.validateCaptcha(null));
    }

    @Test
    public void validateCaptcha_WithCaptchaToken_ThrowsException() {
        CaptchaHelper captchaHelper=new CaptchaHelper();
        Assert.assertThrows(EsignetException.class,()->captchaHelper.validateCaptcha(""));
    }

    @Test
    public void validateCaptcha_WithNullResponse_ThrowsException() {
        Mockito.when(restTemplate.exchange((RequestEntity<?>) any(), (Class<Object>) any())).thenReturn(null);
        Assert.assertThrows(EsignetException.class,()->captchaHelper.validateCaptcha("captchaToken"));
    }

    @Test
    public void validateCaptcha_SuccessfulResponse() {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponse("success");
        ResponseEntity<ResponseWrapper> responseEntity = ResponseEntity.ok(responseWrapper);
        Mockito.when(restTemplate.exchange(Mockito.any(RequestEntity.class), Mockito.eq(ResponseWrapper.class)))
                .thenReturn(responseEntity);
        boolean result = captchaHelper.validateCaptcha("captchaToken");
        Assert.assertTrue(result);
    }

    @Test
    public void validateCaptcha_UnsuccessfulValidation_ThrowsEsignetException() {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setErrors(new ArrayList<>());
        ResponseEntity<ResponseWrapper> responseEntity = ResponseEntity.ok(responseWrapper);
        Mockito.when(restTemplate.exchange(Mockito.any(RequestEntity.class), Mockito.eq(ResponseWrapper.class)))
                .thenReturn(responseEntity);
        Assert.assertThrows(EsignetException.class, () -> captchaHelper.validateCaptcha("captchaToken"));
    }

    @Test
    public void validateCaptcha_RequestException_ThrowsEsignetException() {
        Mockito.when(restTemplate.exchange(Mockito.any(RequestEntity.class), Mockito.eq(ResponseWrapper.class)))
                .thenThrow(new RestClientException("Request failed"));
        Assert.assertThrows(EsignetException.class, () -> captchaHelper.validateCaptcha("captchaToken"));
    }

}
