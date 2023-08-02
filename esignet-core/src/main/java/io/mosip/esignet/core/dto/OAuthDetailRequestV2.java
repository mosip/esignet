package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class OAuthDetailRequestV2 extends OAuthDetailRequest {

    private String codeChallenge;
    private String codeChallengeMethod;

}
