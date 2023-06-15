package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.util.ConsentAction;
import lombok.Data;

@Data
public class LinkedKycAuthResponseV2 {
    private String linkedTransactionId;
    private ConsentAction consentAction;
}
