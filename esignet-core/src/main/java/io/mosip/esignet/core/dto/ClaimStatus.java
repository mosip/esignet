package io.mosip.esignet.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClaimStatus {

    private String claim;
    private boolean verified;
    private boolean available;
}
