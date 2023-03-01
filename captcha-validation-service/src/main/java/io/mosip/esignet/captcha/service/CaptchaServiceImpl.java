package io.mosip.esignet.captcha.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import io.mosip.esignet.captcha.constants.CaptchaErrorCode;
import io.mosip.esignet.captcha.controller.CaptchaController;
import io.mosip.esignet.captcha.dto.CaptchaRequestDTO;
import io.mosip.esignet.captcha.dto.CaptchaResposneDTO;
import io.mosip.esignet.captcha.dto.ExceptionJSONInfoDTO;
import io.mosip.esignet.captcha.dto.GoogleCaptchaDTO;
import io.mosip.esignet.captcha.dto.MainResponseDTO;
import io.mosip.esignet.captcha.exception.CaptchaException;
import io.mosip.esignet.captcha.exception.InvalidRequestCaptchaException;
import io.mosip.esignet.captcha.spi.CaptchaService;
import io.mosip.kernel.core.util.DateUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CaptchaServiceImpl implements CaptchaService {

	@Value("${mosip.esignet.captcha.secretkey}")
	public String recaptchaSecret;

	@Value("${mosip.esignet.captcha.recaptcha.verify.url}")
	public String recaptchaVerifyUrl;

	@Value("${mosip.esignet.captcha.id.validate}")
	public String mosipcaptchaValidateId;

	@Value("${version}")
	private String version;

	@Autowired
	private RestTemplate restTemplate;

	private final String CAPTCHA_SUCCESS = " Captcha successfully verified";


	@Override
	public Object validateCaptcha(Object captchaRequest) throws CaptchaException, InvalidRequestCaptchaException {

		log.info("In captcha service to validate the token request"
				+ ((CaptchaRequestDTO) captchaRequest).getCaptchaToken());

		validateCaptchaRequest((CaptchaRequestDTO) captchaRequest);

		MainResponseDTO<CaptchaResposneDTO> mainResponse = new MainResponseDTO<>();

		MultiValueMap<String, String> param = new LinkedMultiValueMap<>();
		param.add("secret", recaptchaSecret);
		param.add("response", ((CaptchaRequestDTO) captchaRequest).getCaptchaToken().trim());

		GoogleCaptchaDTO captchaResponse = null;

		try {
			log.info("In captcha service try block to validate the token request via a google verify site rest call"
							+ ((CaptchaRequestDTO) captchaRequest).getCaptchaToken() + "  " + recaptchaVerifyUrl);
			
			captchaResponse = this.restTemplate.postForObject(recaptchaVerifyUrl, param, GoogleCaptchaDTO.class);
			if (captchaResponse != null) {
				log.debug("sessionId", "idType", "id", captchaResponse.toString());
			}
		} catch (RestClientException ex) {
			log.error("In captcha service to validate the token request via a google verify site rest call has failed --->"
							+ ((CaptchaRequestDTO) captchaRequest).getCaptchaToken() + "  " + recaptchaVerifyUrl + "  "
							+ ex);
			if (captchaResponse != null && captchaResponse.getErrorCodes() !=null) {
			throw new CaptchaException(captchaResponse.getErrorCodes().get(0).getErrorCode(),
					captchaResponse.getErrorCodes().get(0).getMessage());
			}
		}

		if (captchaResponse!=null && captchaResponse.isSuccess()) {
			log.info("In captcha service token request has been successfully verified --->"
							+ captchaResponse.isSuccess());
			mainResponse.setId(mosipcaptchaValidateId);
			mainResponse.setResponsetime(captchaResponse.getChallengeTs());
			mainResponse.setVersion(version);
			CaptchaResposneDTO response = new CaptchaResposneDTO();
			response.setMessage(CAPTCHA_SUCCESS);
			response.setSuccess(captchaResponse.isSuccess());
			mainResponse.setResponse(response);
		} else {
			if (captchaResponse != null) {
				log.error("In captcha service token request has failed --->"
								+ captchaResponse.isSuccess());
			}
			mainResponse.setId(mosipcaptchaValidateId);
			mainResponse.setResponsetime(getCurrentResponseTime());
			mainResponse.setVersion(version);
			mainResponse.setResponse(null);
			ExceptionJSONInfoDTO error = new ExceptionJSONInfoDTO(CaptchaErrorCode.INVALID_CAPTCHA_CODE.getErrorCode(),
					CaptchaErrorCode.INVALID_CAPTCHA_CODE.getErrorMessage());
			List<ExceptionJSONInfoDTO> errorList = new ArrayList<ExceptionJSONInfoDTO>();
			errorList.add(error);
			mainResponse.setErrors(errorList);

		}
		return mainResponse;

	}

	private void validateCaptchaRequest(CaptchaRequestDTO captchaRequest) throws InvalidRequestCaptchaException {

	 if (captchaRequest.getCaptchaToken() == null || captchaRequest.getCaptchaToken().trim().length() == 0) {
		 	log.debug("sessionId", "idType", "id", captchaRequest.toString());
			throw new InvalidRequestCaptchaException(CaptchaErrorCode.INVALID_CAPTCHA_REQUEST.getErrorCode(),
					CaptchaErrorCode.INVALID_CAPTCHA_REQUEST.getErrorMessage());
		}
	}

	private String getCurrentResponseTime() {
		String dateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
		return DateUtils.formatDate(new Date(System.currentTimeMillis()), dateTimeFormat);
	}

}
