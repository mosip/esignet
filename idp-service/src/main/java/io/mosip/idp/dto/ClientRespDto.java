package io.mosip.idp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ClientRespDto {

    private String clientId;
    private String status;
    private List<String> grantTypes;
}
