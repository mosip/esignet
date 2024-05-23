package io.mosip.esignet.api.dto;

import lombok.Data;

@Data
public class ClaimMetadata {

    private String trustFramework;
    private String assuranceLevel;
    private String verificationProcessName;
    private long verificationCompletedOn;
}
