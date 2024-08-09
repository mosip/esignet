package io.mosip.esignet.api.dto.claim;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class ClaimsV2 implements Serializable {

    private Map<String, JsonNode> userinfo;
    private Map<String, ClaimDetail> id_token;
}
