package io.mosip.esignet.api.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VCRequestDto {

    private List<String> context; //holds @context values
    private List<String> type;
    private String format;
    private Map<String, Object> credentialSubject;
}
