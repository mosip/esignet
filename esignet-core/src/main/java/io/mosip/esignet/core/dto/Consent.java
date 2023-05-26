package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.dto.Claims;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class Consent {
    UUID id;
    String psuValue;
    String clientId;
    Claims claims;
    Map<String, Boolean> authorizeScopes;
    LocalDateTime createdOn;
    LocalDateTime expiration;
    String signature;
    String hash;
    String signedBy;

}
