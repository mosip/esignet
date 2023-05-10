package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.dto.Claims;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserConsent {
    private Claims claims;
    private List<String> acceptedClaims;
    private List<String> requestedScopes;
    private List<String> authorizedScopes;

}
