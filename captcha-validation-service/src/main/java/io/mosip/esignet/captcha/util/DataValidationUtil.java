package io.mosip.esignet.captcha.util;

import java.util.ArrayList;
import java.util.List;
import org.springframework.validation.Errors;

import io.mosip.esignet.captcha.dto.ExceptionJSONInfoDTO;
import io.mosip.esignet.captcha.dto.MainResponseDTO;
import io.mosip.esignet.captcha.exception.InvalidRequestParameterException;


public final class DataValidationUtil {

	/**
	 * Instantiates a new data validation util.
	 */
	private DataValidationUtil() {
	}
	

	/**
	 * Get list of errors from error object and build and throw {@code InvalidRequestParameterException}.
	 *
	 * @param errors the errors
	 * @throws InvalidRequestParameterException the InvalidRequestParameterException
	 */
	public static void validate(Errors errors, String operation) throws InvalidRequestParameterException {
		MainResponseDTO<?> response= new MainResponseDTO<>();
		List<ExceptionJSONInfoDTO> errorList = new ArrayList<>();
		
		if (errors.hasErrors()) {
			errors.getAllErrors().stream()
					.forEach(error ->{
						ExceptionJSONInfoDTO ex= new ExceptionJSONInfoDTO();
						ex.setErrorCode(error.getCode());
						ex.setMessage(error.getDefaultMessage());
						errorList.add(ex);
					} );
			throw new InvalidRequestParameterException(errorList, operation,response);
		}
	}

}
