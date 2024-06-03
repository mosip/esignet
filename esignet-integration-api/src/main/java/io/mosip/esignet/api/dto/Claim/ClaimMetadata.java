package io.mosip.esignet.api.dto.Claim;

import lombok.Data;

@Data
public class ClaimMetadata {

    private String trustFramework;
    private String assuranceLevel;
    private String verificationProcessName;
    private long verificationCompletedOn;
}
