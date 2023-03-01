package io.mosip.esignet.captcha.exception;

import java.util.List;

import io.mosip.esignet.captcha.dto.ExceptionJSONInfoDTO;
import io.mosip.esignet.captcha.dto.MainResponseDTO;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import lombok.Getter;
import lombok.Setter;

/**
 * @author M1046129
 *
 */

@Getter
@Setter
public class InvalidRequestParameterException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3898906527162403384L;
	
	private MainResponseDTO<?> mainResponseDto;
	private List<ExceptionJSONInfoDTO> exptionList;
	private String operation;
	
	public InvalidRequestParameterException() {
		super();
	}

	public InvalidRequestParameterException(String errCode, String errMessage,MainResponseDTO<?> response) {
		this.mainResponseDto=response;
	}
	public InvalidRequestParameterException(String errorCode, String errorMessage, Throwable rootCause,MainResponseDTO<?> response) {
		this.mainResponseDto=response;
	}
	
	
	public InvalidRequestParameterException(List<ExceptionJSONInfoDTO> exptionList,MainResponseDTO<?> response) {
		this.mainResponseDto=response;
		this.exptionList=exptionList;
	}
	
	public InvalidRequestParameterException(List<ExceptionJSONInfoDTO> exptionList,String operation,MainResponseDTO<?> response) {
		this.mainResponseDto=response;
		this.exptionList=exptionList;
		this.operation=operation;
	}
}
