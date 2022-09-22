package io.mosip.idp.core.dto;

import lombok.Data;

import java.util.List;

@Data
public class ClientDetail {

    private String id;
    private String name;
    private String rpId;
    private String logoUri;
    private List<String> redirectUris;
    private String publicKey;
    private List<String> claims;
    private List<String> acrValues;
    private String status;
    private List<String> grantTypes;
    private List<String> clientAuthMethods;
}
