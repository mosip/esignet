package io.mosip.esignet.api.dto.Claim;

import io.mosip.esignet.api.util.ErrorConstants;
import io.mosip.esignet.api.util.EvidenceType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class Evidence {

        @NotBlank(message = ErrorConstants.INVALID_EVIDENCE_TYPE)
        private EvidenceType type;
        private FilterCriteria method;
        private FilterTime time;
        private VerificationMethod verificationMethod;
        private List<EvidenceCheckDetail> checkDetails;
        private DocumentDetails documentDetails;
        private String attestation;
        private FilterCriteria signatureType;
        private FilterCriteria issuer;
        private String serialNumber;
        private FilterTime createdAt;
        private EvidenceRecord record;

}
