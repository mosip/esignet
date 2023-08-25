package io.mosip.esignet.core.dto.vci;

import lombok.Data;

import java.util.Map;

@Data
public class ParsedAccessToken {

    private Map<String, Object> claims;
    private boolean isActive;
}
