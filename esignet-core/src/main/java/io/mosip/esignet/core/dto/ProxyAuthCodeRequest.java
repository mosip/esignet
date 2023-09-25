package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class ProxyAuthCodeRequest {

    private String transactionId;
    private String proxyAuthorizationCode;
}
