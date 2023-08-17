package io.mosip.esignet.api.dto;

import lombok.Data;

import java.util.Map;

@Data
public class VCRequestDto {

    private String[] types;
    private String format;
    private Map<String, Object> credentialSubject;
}
