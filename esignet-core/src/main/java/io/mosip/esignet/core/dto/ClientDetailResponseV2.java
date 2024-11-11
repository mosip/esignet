package io.mosip.esignet.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientDetailResponseV2 {
    private String id;
    private String name;
    private String rpId;
    private String logoUri;
    private String redirectUris;
    private String publicKey;
    private String claims;
    private String acrValues;
    private String status;
    private String grantTypes;
    private String clientAuthMethods;
    private Map<String, Object> additionalConfig;
}
