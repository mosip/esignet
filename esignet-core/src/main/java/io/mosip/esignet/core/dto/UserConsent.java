package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.dto.Claims;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class UserConsent {
    String psuToken;
    String clientId;
    Claims Claims;
    Map<String, Boolean> authorizationScopes;
    LocalDateTime expirydtimes;
    String signature;
    String hash;
    List<String> acceptedClaims;
    List<String> permittedScopes;
}
