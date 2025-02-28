package io.mosip.esignet.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.mosip.esignet.core.validator.ClientAdditionalConfig;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class ClientDetailUpdateRequestV3 extends ClientDetailUpdateRequestV2 {

    @ClientAdditionalConfig
    private JsonNode additionalConfig;

    public ClientDetailUpdateRequestV3(String logUri, List<String> redirectUris, List<String> userClaims, List<String> authContextRefs, String status, List<String> grantTypes, String clientName, List<String> clientAuthMethods, Map<String, String> clientNameLangMap, JsonNode additionalConfig) {
        super(logUri, redirectUris, userClaims, authContextRefs, status, grantTypes, clientName, clientAuthMethods, clientNameLangMap);
        this.additionalConfig = additionalConfig;
    }
}
