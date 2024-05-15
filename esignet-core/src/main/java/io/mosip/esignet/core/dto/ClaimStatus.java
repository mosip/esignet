package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class ClaimStatus {

    private String claim;
    private boolean verified;
    private boolean available;
}
