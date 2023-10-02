package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.validator.CodeChallenge;
import io.mosip.esignet.core.validator.PKCECodeChallengeMethod;
import lombok.Data;

@Data
@CodeChallenge
public class OAuthDetailRequestV2 extends OAuthDetailRequest {

    private String codeChallenge;

    @PKCECodeChallengeMethod
    private String codeChallengeMethod;
}
