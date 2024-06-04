package io.mosip.esignet.api.dto.Claim;

import lombok.Data;

@Data
public class EvidenceCheckDetail {

    private String checkMethod;
    private String organisation;
    private String txn;
    private FilterTime time;

}
