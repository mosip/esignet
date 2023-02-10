package io.mosip.esignet.captcha.dto;

import java.io.Serializable;
import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * @author Akshay Jain
 * @since 1.0.0
 *
 * @param <T>
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class MainResponseDTO<T> implements Serializable{
	
	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 3384945682672832638L;
	/**
	 * Id
	 */
	private String id;
	/**
	 * version
	 */
	private String version;

	private String responsetime;

	private T response;
	
	/** The error details. */
	private List<ExceptionJSONInfoDTO> errors;
}
