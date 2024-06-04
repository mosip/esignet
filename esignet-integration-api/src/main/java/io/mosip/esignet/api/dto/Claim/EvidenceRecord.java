package io.mosip.esignet.api.dto.Claim;

import lombok.Data;

@Data
public class EvidenceRecord {

    private FilterCriteria type;
    private String personalNumber;
    private FilterTime createdAt;
    private FilterTime dateOfExpiry;
    private EvidenceIssuer source;

}
