package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.TokenRequest;
import io.mosip.idp.core.dto.TokenResponse;
import io.mosip.idp.core.exception.IdPException;
import org.jose4j.jwk.JsonWebKeySet;

public interface OAuthService {

    static final String JWT_BEARER_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    /**
     * Ensure the Authorization Code was issued to the authenticated Client.
     * Verify that the Authorization Code is valid.
     * TODO - If possible, verify that the Authorization Code has not been previously used. How ?
     * Ensure that the redirect_uri parameter value is identical to the redirect_uri parameter value that was included in the initial Authorization Request. If the redirect_uri parameter value is not present when there is only one registered redirect_uri value, the Authorization Server MAY return an error (since the Client should have included the parameter) or MAY proceed without an error (since OAuth 2.0 permits the parameter to be omitted in this case).
     * Verify that the Authorization Code used was issued in response to an OpenID Connect Authentication Request
     *
     * @param tokenRequest
     * @return
     * @throws IdPException
     */
    TokenResponse getTokens(TokenRequest tokenRequest) throws IdPException;

    JsonWebKeySet getJwks();
}
