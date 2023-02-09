package io.mosip.esignet.captcha.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import io.mosip.esignet.captcha.constants.ErrorCodes;
import io.mosip.esignet.captcha.constants.ErrorMessages;
import io.mosip.esignet.captcha.controller.CaptchaController;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public abstract class BaseValidator {

	/** The Constant BASE_ID_REPO_VALIDATOR. */
	private static final String BASE_VALIDATOR = "BaseValidator";

	/** The Constant REQUEST. */
	private static final String REQUEST = "request";

	/** The Constant TIMESTAMP. */
	private static final String REQUEST_TIME = "requesttime";

	/** The Constant VER. */
	private static final String VER = "version";

	/** The Constant ID. */
	protected static final String ID = "id";

	/** The Environment. */
	@Autowired
	protected Environment env;

	/** The id. */
	@Resource

	/**
	 * Validate request time.
	 *
	 * @param reqTime
	 *            the timestamp
	 * @param errors
	 *            the errors
	 */
	protected void validateReqTime(Date reqTime, Errors errors) {
		if (Objects.isNull(reqTime)) {
			log.error("", "", "validateReqTime", "requesttime is null");
			errors.rejectValue(REQUEST_TIME, ErrorCodes.PRG_CORE_REQ_003.toString(),
					String.format(ErrorMessages.INVALID_REQUEST_DATETIME.getMessage(), REQUEST_TIME));
		} else {
			LocalDate localDate = reqTime.toInstant().atZone(ZoneId.of("UTC")).toLocalDate();
			LocalDate serverDate = new Date().toInstant().atZone(ZoneId.of("UTC")).toLocalDate();
			if (localDate.isBefore(serverDate) || localDate.isAfter(serverDate)) {
				errors.rejectValue(REQUEST_TIME, ErrorCodes.PRG_CORE_REQ_013.getCode(), String
						.format(ErrorMessages.INVALID_REQUEST_DATETIME_NOT_CURRENT_DATE.getMessage(), REQUEST_TIME));
			}
		}
	}

	/**
	 * Validate version.
	 *
	 * @param ver
	 *            the ver
	 * @param errors
	 *            the errors
	 */
	protected void validateVersion(String ver, Errors errors) {
		String envVersion = env.getProperty("version");
		if (Objects.isNull(ver)) {
			log.error("", "", "validateVersion", "version is null");
			errors.rejectValue(VER, ErrorCodes.PRG_CORE_REQ_002.toString(),
					String.format(ErrorMessages.INVALID_REQUEST_VERSION.getMessage(), VER));
		} else if (envVersion !=null) {
			if (!envVersion.equalsIgnoreCase(ver)) {
				log.error("", "", "validateVersion", "version is not correct");
				errors.rejectValue(VER, ErrorCodes.PRG_CORE_REQ_002.toString(),
						String.format(ErrorMessages.INVALID_REQUEST_VERSION.getMessage(), VER));
			}
		}
	}

	/**
	 * Validate request.
	 *
	 * @param request
	 *            the request
	 * @param errors
	 *            the errors
	 */
	protected void validateRequest(Object request, Errors errors) {
		if (Objects.isNull(request)) {
			log.error("", "", "validateRequest", "\n" + "request is null");
			errors.rejectValue(REQUEST, ErrorCodes.PRG_CORE_REQ_004.getCode(),
					String.format(ErrorMessages.INVALID_REQUEST_BODY.getMessage(), REQUEST));
		}
	}

	
}
