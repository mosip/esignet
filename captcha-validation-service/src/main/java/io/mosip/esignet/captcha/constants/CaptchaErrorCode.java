package io.mosip.esignet.captcha.constants;

public enum CaptchaErrorCode {

	INVALID_CAPTCHA_CODE("PRG-PAM-005", "Invalid Captcha entered"),
	INVALID_CAPTCHA_REQUEST("PRG-PAM-006", "Invalid request , Request can't be null or empty");

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
