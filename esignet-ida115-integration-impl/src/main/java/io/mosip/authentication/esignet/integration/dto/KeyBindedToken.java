package io.mosip.authentication.esignet.integration.dto;


import lombok.Data;

@Data
public class KeyBindedToken {

    private String token;
    private String type;
    private String format;
}
