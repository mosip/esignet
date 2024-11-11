package io.mosip.esignet.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientDetailCreateRequestV3 extends ClientDetailCreateRequestV2 {
    private Map<String, Object> additionalConfig;
}
