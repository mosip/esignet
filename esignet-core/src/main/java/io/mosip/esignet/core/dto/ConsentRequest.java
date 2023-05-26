package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.api.util.SignedBy;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ConsentRequest {
    String psu_token;
    String clientId;
    Claims Claims;
    Map<String, Boolean> authorizeScopes;
    LocalDateTime expiration;
    String signature;
    String hash;
    SignedBy signedBy;
}
