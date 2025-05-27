package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class ParResponse {
    private String requestUri;
    private int expiresIn;
}