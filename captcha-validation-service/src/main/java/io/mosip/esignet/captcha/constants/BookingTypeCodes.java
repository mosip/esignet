package io.mosip.esignet.captcha.constants;

public enum BookingTypeCodes {

	NEW_PREREGISTRATION("NEW_PREREGISTRATION"),

	UPDATE_REGISTRATION("UPDATE_REGISTRATION"),

	LOST_FORGOTTEN_UIN("LOST_FORGOTTEN_UIN");

	BookingTypeCodes(String bookingTypeCode) {
		this.bookingTypeCode = bookingTypeCode;
	}

	private String bookingTypeCode;

	public String getBookingTypeCode() {
		return bookingTypeCode;
	}

}
