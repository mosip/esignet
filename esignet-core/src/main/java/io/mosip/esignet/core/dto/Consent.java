package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.dto.Claims;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class Consent {
    private UUID id;
    private String clientId;
    private String psuValue;
    private Claims claims;
    Map<String, Boolean> authorizationScopes;
    private LocalDateTime createdOn;
    private LocalDateTime expiration;
    private String signature;
    private String hash;

}
