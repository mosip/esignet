package io.mosip.esignet.household.integration.exception;
import io.mosip.esignet.household.integration.util.ErrorConstants;

public class HouseholdentityException extends RuntimeException {

	private String errorCode;

	public HouseholdentityException() {
        super(ErrorConstants.UNKNOWN_ERROR);
        this.errorCode = ErrorConstants.UNKNOWN_ERROR;
    }

	public HouseholdentityException(String errorCode) {
        super(ErrorConstants.UNKNOWN_ERROR);
        this.errorCode = errorCode;
    }

	public String getErrorCode() {
		return errorCode;
	}
}
