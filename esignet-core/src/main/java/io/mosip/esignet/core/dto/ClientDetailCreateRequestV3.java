package io.mosip.esignet.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.mosip.esignet.core.validator.ClientAdditionalConfig;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class ClientDetailCreateRequestV3 extends ClientDetailCreateRequestV2 {

    @ClientAdditionalConfig
    JsonNode additionalConfig;

    public ClientDetailCreateRequestV3(String clientId, String clientName, Map<String, Object> publicKey, String relyingPartyId,
                                       List<String> userClaims, List<String> authContextRefs, String logoUri,
                                       List<String> redirectUris, List<String> grantTypes, List<String> clientAuthMethods,
                                       Map<String, String> clientNameLangMap, JsonNode additionalConfig) {
        super(clientId, clientName, publicKey, relyingPartyId, userClaims, authContextRefs, logoUri, redirectUris,
                grantTypes, clientAuthMethods, clientNameLangMap);
        this.additionalConfig = additionalConfig;
    }
}
