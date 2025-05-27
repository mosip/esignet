package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class PushedAuthorizationResponse {
    private String request_uri;
    private int expires_in;
}