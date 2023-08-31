package io.mosip.esignet.core.dto.vci;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

@Data
public class CredentialDefinition {

    @JsonProperty("@context")
    private List<String> context;

    @NotBlank(message = ErrorConstants.INVALID_REQUEST)
    private List<String> type;

    private Map<String, Object> credentialSubject;

}
