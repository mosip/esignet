package io.mosip.esignet.api.dto.Claim;

import lombok.Data;

@Data
public class EvidenceIssuer {

    private String name;
    private String country;
    private String countryCode;
    private String jurisdiction;

}
