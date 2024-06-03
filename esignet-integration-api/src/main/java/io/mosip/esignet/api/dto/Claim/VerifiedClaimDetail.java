package io.mosip.esignet.api.dto.Claim;

import io.mosip.esignet.api.util.ErrorConstants;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class VerifiedClaimDetail {

    @NotBlank
    private Verification verification;

    @NotBlank(message = ErrorConstants.INVALID_CLAIMS)
    private Claims claims;
}
