package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.validator.ClientAdditionalConfigConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class ClientDetailCreateRequestV3 extends ClientDetailCreateRequestV2 {

    @ClientAdditionalConfigConstraint
    private Map<String, Object> additionalConfig;

    public ClientDetailCreateRequestV3(String clientId, String clientName, Map<String, Object> publicKey, String relyingPartyId,
                                       List<String> userClaims, List<String> authContextRefs, String logoUri,
                                       List<String> redirectUris, List<String> grantTypes, List<String> clientAuthMethods,
                                       Map<String, String> clientNameLangMap, Map<String, Object> additionalConfig) {
        super(clientId, clientName, publicKey, relyingPartyId, userClaims, authContextRefs, logoUri, redirectUris,
                grantTypes, clientAuthMethods, clientNameLangMap);
        this.additionalConfig = additionalConfig;
    }
}
