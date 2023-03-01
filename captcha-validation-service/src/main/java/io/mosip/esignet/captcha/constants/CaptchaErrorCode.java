package io.mosip.esignet.captcha.constants;

public enum CaptchaErrorCode {

	INVALID_CAPTCHA_CODE("KER-CVS-001", "Invalid Captcha entered"),
	INVALID_CAPTCHA_REQUEST("KER-CVS-002", "Invalid request , Request can't be null or empty");

	private final String errorCode;
	private final String errorMessage;

	private CaptchaErrorCode(String errorCode, String errorMessage) {
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

}
