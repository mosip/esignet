package io.mosip.esignet.core.dto.vci;

import lombok.Data;

@Data
public class CredentialProof {

    private String proof_type;
    private String jwt;
}
