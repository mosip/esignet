package io.mosip.esignet.core.dto.vci;

import lombok.Data;

import java.util.List;

@Data
public class CredentialMetadata {

    private String id;
    private String format;
    private String scope;
    private List<String> proof_types_supported;

}
