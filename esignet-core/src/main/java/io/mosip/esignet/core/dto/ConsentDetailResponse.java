package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.util.ConsentAction;
import lombok.Data;

import java.util.List;

@Data
public class ConsentDetailResponse {

    List<ClaimStatus> claimStatusList;
    private String transactionId;
    private ConsentAction consentAction;
}
