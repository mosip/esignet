package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class UserConsentRequest {
    String psuToken;
    String clientId;
}
