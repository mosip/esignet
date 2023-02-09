package io.mosip.esignet.captcha.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.mosip.esignet.captcha.dto.ExceptionJSONInfoDTO;
import io.mosip.esignet.captcha.dto.MainRequestDTO;
import io.mosip.esignet.captcha.dto.MainResponseDTO;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.util.DateUtils;



public class GenericUtil {
	
	private GenericUtil() {
	}
	
	private static String dateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
	
	public static String getCurrentResponseTime() {
		return DateUtils.formatDate(new Date(System.currentTimeMillis()), dateTimeFormat);
	}
	
	/**
	 * This method will return the MainResponseDTO with id and version
	 * 
	 * @param mainRequestDto
	 * @return MainResponseDTO<?>
	 */
	public  MainResponseDTO<?> getMainResponseDto(MainRequestDTO<?> mainRequestDto ){
		MainResponseDTO<?> response=new MainResponseDTO<>();
		response.setId(mainRequestDto.getId());
		response.setVersion(mainRequestDto.getVersion());
		
		return response;
	}
	
	public static  <T extends BaseUncheckedException> ResponseEntity<MainResponseDTO<?>> errorResponse(final T e,MainResponseDTO<?> response){
		ExceptionJSONInfoDTO errorDetails = new ExceptionJSONInfoDTO(e.getErrorCode(), 
				e.getErrorText());
		MainResponseDTO<?> errorRes = response;
		List<ExceptionJSONInfoDTO> errorList=new ArrayList<>();
		errorList.add(errorDetails);
		errorRes.setErrors(errorList);
		errorRes.setResponsetime(getCurrentResponseTime());
		return new ResponseEntity<>(errorRes, HttpStatus.OK);
	}

}
