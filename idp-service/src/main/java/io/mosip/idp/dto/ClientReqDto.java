package io.mosip.idp.dto;


import lombok.Data;

import java.util.List;

@Data
public class ClientReqDto {

    //TODO Need to add DTO validations and java docs
    private String clientId;
    private String clientName;
    private String certificate;
    private String status;
    private String relayingPartyId;
    private String claims;
    private String authMethods;
    private String logoUri;
    private List<String> redirectUris;
}
