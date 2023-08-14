package io.mosip.esignet.core.dto.vci;

import lombok.Data;

@Data
public class CredentialProof {

    /**
     * The proof object MUST contain a proof_type claim of type JSON string denoting the concrete proof type.
     */
    private String proof_type;

    /**
     *
     */
    private String jwt;
}
