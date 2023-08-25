package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.validator.PKCECodeChallengeMethod;
import io.mosip.esignet.core.validator.TargetResource;
import lombok.Data;

@Data
public class OAuthDetailRequestV2 extends OAuthDetailRequest {

    private String codeChallenge;

    @PKCECodeChallengeMethod
    private String codeChallengeMethod;

    @TargetResource
    private String resource;

}
