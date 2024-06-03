package io.mosip.esignet.api.dto.Claim;

import lombok.Data;

@Data
public class DocumentDetails {
    private FilterCriteria type;
    private String documentNumber;
    private String dateOfIssuance;
    private String dateOfExpiry;
    private Issuer issuer;

}