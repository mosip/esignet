package io.mosip.esignet.captcha.serviceimpl.test;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import io.mosip.esignet.captcha.dto.CaptchaRequestDTO;
import io.mosip.esignet.captcha.dto.CaptchaResposneDTO;
import io.mosip.esignet.captcha.dto.GoogleCaptchaDTO;
import io.mosip.esignet.captcha.dto.MainResponseDTO;
import io.mosip.esignet.captcha.exception.CaptchaException;
import io.mosip.esignet.captcha.exception.InvalidRequestCaptchaException;
import io.mosip.esignet.captcha.service.CaptchaServiceImpl;

@RunWith(JUnit4.class)
@SpringBootTest
@ContextConfiguration(classes = { CaptchaServiceImpl.class })
public class CaptchaServiceImplTest {

	@InjectMocks
	private CaptchaServiceImpl captchaServiceImpl;

	@Value("${mosip.esignet.captcha.secretkey}")
	public String recaptchaSecret;

	@Value("${mosip.esignet.captcha.recaptcha.verify.url}")
	public String recaptchaVerifyUrl;

	@Value("${mosip.esignet.captcha.id.validate}")
	public String mosipcaptchaValidateId;

	@Value("${version}")
	private String version;

	@Mock
	private RestTemplate restTemplate;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		ReflectionTestUtils.setField(captchaServiceImpl, "recaptchaSecret", "demo");
		ReflectionTestUtils.setField(captchaServiceImpl, "recaptchaVerifyUrl",
				"https://www.google.com/recaptcha/api/siteverify");
		ReflectionTestUtils.setField(captchaServiceImpl, "mosipcaptchaValidateId", "123");
		ReflectionTestUtils.setField(captchaServiceImpl, "version", "2.0");
	}

	
	@Test
	public void validateCaptchaTest() throws CaptchaException, InvalidRequestCaptchaException {
		CaptchaRequestDTO captchaRequest = new CaptchaRequestDTO();
		
		MainResponseDTO<CaptchaResposneDTO> mainResponse = new MainResponseDTO<>();
		MultiValueMap<String, String> param = new LinkedMultiValueMap<>();
		param.add("secret", recaptchaSecret);
		GoogleCaptchaDTO captchaResponse = new GoogleCaptchaDTO();
		captchaResponse.setHostname(recaptchaVerifyUrl);
		captchaResponse.setSuccess(true);
		captchaResponse.setChallengeTs("Success");

		captchaRequest.setCaptchaToken("temp");
		captchaRequest.getCaptchaToken();

		CaptchaResposneDTO res = new CaptchaResposneDTO();
		res.setMessage("captcha scuccessfully set");
		res.setSuccess(true);
		mainResponse.setResponse(res);
		mainResponse.setId(mosipcaptchaValidateId);
		mainResponse.setVersion(version);

		Mockito.when(restTemplate.postForObject("https://www.google.com/recaptcha/api/siteverify",
				"{secret=[demo], response=[aRsasahksasa]}", GoogleCaptchaDTO.class)).thenReturn(captchaResponse);
		assertNotNull(captchaServiceImpl.validateCaptcha(captchaRequest));
	}

	@Test(expected = InvalidRequestCaptchaException.class)
	public void validateCaptchaExceptionTest() throws CaptchaException, InvalidRequestCaptchaException {
		CaptchaRequestDTO captchaRequest = new CaptchaRequestDTO();
		GoogleCaptchaDTO captchaResponse = new GoogleCaptchaDTO();
		captchaResponse.setHostname(recaptchaVerifyUrl);
		captchaResponse.setSuccess(true);
		captchaResponse.setChallengeTs("Success");
		captchaServiceImpl.validateCaptcha(captchaRequest);
	}

}
