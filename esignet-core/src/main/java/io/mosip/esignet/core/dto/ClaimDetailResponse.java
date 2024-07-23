package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.util.ConsentAction;
import lombok.Data;

import java.util.List;

@Data
public class ClaimDetailResponse {

    private String transactionId;
    private ConsentAction consentAction;
    private boolean profileUpdateRequired;
    private List<ClaimStatus> claimStatus;
}
