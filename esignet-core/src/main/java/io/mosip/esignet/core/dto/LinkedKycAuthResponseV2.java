package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class LinkedKycAuthResponseV2 {
    private String linkedTransactionId;
    private Consent consentAction;
}
