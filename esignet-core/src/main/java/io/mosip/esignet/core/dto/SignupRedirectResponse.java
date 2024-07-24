package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class SignupRedirectResponse {

	public String transactionId;
	public String idToken;
}
