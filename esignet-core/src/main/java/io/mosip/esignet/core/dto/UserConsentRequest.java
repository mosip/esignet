package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class UserConsentRequest {
    String psu_token;
    String clientId;
}
