package io.mosip.esignet.captcha.exceptionutils;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.mosip.esignet.captcha.dto.ExceptionJSONInfoDTO;
import io.mosip.esignet.captcha.dto.MainResponseDTO;
import io.mosip.esignet.captcha.exception.CaptchaException;
import io.mosip.esignet.captcha.exception.InvalidRequestCaptchaException;
import io.mosip.esignet.captcha.exception.InvalidRequestParameterException;
import io.mosip.kernel.core.util.DateUtils;

@RestControllerAdvice
public class CaptchaExceptionHandler {

	@Autowired
	protected Environment env;

	@Value("${mosip.esignet.captcha.id.validate}")
	public String mosipcaptchaValidateId;

	@Value("${version}")
	private String version;

	private static String dateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

	public static String getCurrentResponseTime() {
		return DateUtils.formatDate(new Date(System.currentTimeMillis()), dateTimeFormat);
	}

	@ExceptionHandler(InvalidRequestCaptchaException.class)
	public MainResponseDTO<?> handleInvalidCaptchaReqest(InvalidRequestCaptchaException ex) {
		MainResponseDTO<?> response = new MainResponseDTO<>();
		response.setId(mosipcaptchaValidateId);
		response.setVersion(version);
		response.setResponsetime(getCurrentResponseTime());
		response.setResponse(null);
		ArrayList<ExceptionJSONInfoDTO> errors = new ArrayList<ExceptionJSONInfoDTO>();
		ExceptionJSONInfoDTO errorDetails = new ExceptionJSONInfoDTO(ex.getErrorCode(), ex.getErrorMessage());
		errors.add(errorDetails);
		response.setErrors(errors);
		return response;
	}

	@ExceptionHandler(CaptchaException.class)
	public MainResponseDTO<?> handleCaptchaException(CaptchaException ex) {
		MainResponseDTO<?> response = new MainResponseDTO<>();
		response.setId(mosipcaptchaValidateId);
		response.setVersion(version);
		response.setResponsetime(getCurrentResponseTime());
		response.setResponse(null);
		ArrayList<ExceptionJSONInfoDTO> errors = new ArrayList<ExceptionJSONInfoDTO>();
		ExceptionJSONInfoDTO errorDetails = new ExceptionJSONInfoDTO(ex.getErrorCode(), ex.getErrorMessage());
		errors.add(errorDetails);
		response.setErrors(errors);
		return response;
	}
	
	@ExceptionHandler(Exception.class)
	public MainResponseDTO<?> handleException(Exception ex) {
		MainResponseDTO<?> response = new MainResponseDTO<>();
		response.setId(mosipcaptchaValidateId);
		response.setVersion(version);
		response.setResponsetime(getCurrentResponseTime());
		response.setResponse(null);
		ArrayList<ExceptionJSONInfoDTO> errors = new ArrayList<ExceptionJSONInfoDTO>();
		ExceptionJSONInfoDTO errorDetails = new ExceptionJSONInfoDTO("",ex.getMessage());
		errors.add(errorDetails);
		response.setErrors(errors);
		return response;
	}

}
