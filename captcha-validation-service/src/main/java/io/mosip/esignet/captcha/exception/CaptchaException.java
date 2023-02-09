package io.mosip.esignet.captcha.exception;

public class CaptchaException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public CaptchaException(String errorCode, String errorMessage) {
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;

	}

	private String errorCode;

	private String errorMessage;

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}
