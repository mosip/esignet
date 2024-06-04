package io.mosip.esignet.api.dto.Claim;

import lombok.Data;

@Data
public class DocumentDetails {

    private FilterCriteria type;
    private String documentNumber;
    private FilterTime dateOfIssuance;
    private FilterTime dateOfExpiry;
    private EvidenceIssuer issuer;


}