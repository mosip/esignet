package io.mosip.esignet.core.dto.vci;

import lombok.Data;

@Data
public class CredentialRequest {

    private String format;
    private CredentialProof proof;
}
