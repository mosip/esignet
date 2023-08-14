package io.mosip.esignet.core.dto.vci;


import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CredentialDefinition {

    private List<String> context;
    private List<String> types;
    private Map<String, Object> credentialSubject;

}
