package io.mosip.idp.binding.dto;

import lombok.Data;

@Data
public class BindingTransaction {

    private String individualId;
    private String authTransactionId;
	private String authChallengeType;
}
