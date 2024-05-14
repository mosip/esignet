package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class ClaimStatus {
    String claim;
    Boolean Available;
    Boolean verified;
}
