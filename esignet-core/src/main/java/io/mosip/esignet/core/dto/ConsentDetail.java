package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.dto.Claims;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class ConsentDetail {
    private UUID id;
    private String clientId;
    private String psuToken;
    private Claims claims;
    Map<String, Boolean> authorizationScopes;
    private LocalDateTime createdtimes;
    private LocalDateTime expiredtimes;
    private String signature;
}
