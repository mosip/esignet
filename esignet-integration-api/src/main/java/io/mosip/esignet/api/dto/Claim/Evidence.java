package io.mosip.esignet.api.dto.Claim;

import io.mosip.esignet.api.util.ErrorConstants;
import io.mosip.esignet.api.util.EvidenceType;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class Evidence {

        @NotBlank(message = ErrorConstants.INVALID_EVIDENCE_TYPE)
        private EvidenceType type;
        private FilterCriteria method;
        private DocumentDetails documentDetails;
        private FilterTime time;

}
