package io.mosip.esignet.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class ClientDetailUpdateRequestV3 extends ClientDetailUpdateRequestV2 {
    private Map<String, Object> additionalConfig;

    public ClientDetailUpdateRequestV3(String logUri, List<String> redirectUris, List<String> userClaims, List<String> authContextRefs, String status, List<String> grantTypes, String clientName, List<String> clientAuthMethods, Map<String,String> clientNameLangMap, Map<String, Object> additionalConfig){
        super(logUri,redirectUris,userClaims,authContextRefs,status,grantTypes,clientName,clientAuthMethods, clientNameLangMap);
        this.additionalConfig=additionalConfig;
    }
}
