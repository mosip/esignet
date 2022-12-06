package io.mosip.idp.binding.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class BindingTransaction implements Serializable {

	private String individualId;
    private String authTransactionId;
	private String authChallengeType;
}
