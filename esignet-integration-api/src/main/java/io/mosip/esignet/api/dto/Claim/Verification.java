package io.mosip.esignet.api.dto.Claim;

import io.mosip.esignet.api.util.ErrorConstants;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class Verification {

    @NotBlank(message=ErrorConstants.INVALID_TRUST_FRAMEWORK)
    private FilterCriteria trustFramework;
    private FilterTime time;
    private FilterCriteria assuranceLevel;
    private List<Evidence> evidence;

}
