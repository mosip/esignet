package io.mosip.householdid.esignet.integration.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class KycTransactionDto implements Serializable {
    private String kycToken;
    private String psut;
    private long householdId;
}
