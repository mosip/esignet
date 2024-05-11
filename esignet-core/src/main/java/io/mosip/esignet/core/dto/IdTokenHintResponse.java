package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class IdTokenHintResponse {

	public String transactionId;
	public String idTokenHint;
}
