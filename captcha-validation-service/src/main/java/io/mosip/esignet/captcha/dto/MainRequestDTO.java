/* 
 * Copyright
 * 
 */
package io.mosip.esignet.captcha.dto;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * This DTO class is used to define the initial request parameters.
 * 
 * @author Rajath KR
 * @author Akshay Jain
 * @since 1.0.0
 *
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class MainRequestDTO<T> implements Serializable {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -4966448852014107698L;

	/**
	 * Id
	 */
	private String id;
	/**
	 * version
	 */
	private String version;
	/**
	 * Request Date Time
	 */
	
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
	@Setter(AccessLevel.NONE)
	@Getter(AccessLevel.NONE)
	private Date requesttime;
	/**
	 * Request Object
	 */
	private T request;
	
	public Date getRequesttime() {
		return requesttime!=null ? new Date(requesttime.getTime()):null;
	}
	public void setRequesttime(Date requesttime) {
		this.requesttime =requesttime!=null ? new Date(requesttime.getTime()):null;
	}

}
