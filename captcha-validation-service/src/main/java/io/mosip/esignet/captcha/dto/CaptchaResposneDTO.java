package io.mosip.esignet.captcha.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class CaptchaResposneDTO implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	@JsonProperty("success")
	private boolean success;
	
	@JsonProperty("message")
	private String message;

}
