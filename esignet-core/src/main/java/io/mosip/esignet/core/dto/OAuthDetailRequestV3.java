package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class OAuthDetailRequestV3 extends OAuthDetailRequestV2 {

    private String idTokenHint;

}
