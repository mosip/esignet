package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.EsignetException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.Serializable;

import static io.mosip.esignet.core.constants.Constants.S256;

/**
 * Pkce support for OIDC transaction when using authorization code flow.
 * technique to mitigate auth-code interception attacks.
 */
@Slf4j
@Getter
@Setter
public class ProofKeyCodeExchange implements Serializable {

    private String codeChallenge;
    private String codeChallengeMethod;

    private ProofKeyCodeExchange() {}

    public static ProofKeyCodeExchange getInstance(String codeChallenge, String codeChallengeMethod) {
        if(codeChallengeMethod == null || codeChallenge == null)
            return null;

        if(StringUtils.isEmpty(codeChallenge))
            throw new EsignetException(ErrorConstants.INVALID_PKCE_CHALLENGE);

        switch (codeChallengeMethod) {
            case S256 :
                ProofKeyCodeExchange proofKeyCodeExchange = new ProofKeyCodeExchange();
                proofKeyCodeExchange.setCodeChallenge(codeChallenge);
                proofKeyCodeExchange.setCodeChallengeMethod("S256");
                return proofKeyCodeExchange;
            default:
                throw new EsignetException(ErrorConstants.UNSUPPORTED_PKCE_CHALLENGE_METHOD);
        }
    }
}
