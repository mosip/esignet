/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.dto.TokenRequest;
import io.mosip.esignet.core.dto.TokenResponse;

import javax.validation.Valid;
import java.util.Map;

public interface OAuthService {

    String JWT_BEARER_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    /**
     * Authenticates client based on the clients registered auth_method, and exchanges auth_code with
     * ID-token and access-token.
     *
     * Request validations MUST be done:
     * Ensure the Authorization Code was issued to the authenticated Client.
     * Verify that the Authorization Code is valid.
     * TODO - If possible, verify that the Authorization Code has not been previously used. How ?
     * Ensure that the redirect_uri parameter value is identical to the redirect_uri parameter value that was included in the initial Authorization Request. If the redirect_uri parameter value is not present when there is only one registered redirect_uri value, the Authorization Server MAY return an error (since the Client should have included the parameter) or MAY proceed without an error (since OAuth 2.0 permits the parameter to be omitted in this case).
     * Verify that the Authorization Code used was issued in response to an OpenID Connect Authentication Request
     *
     * @param tokenRequest
     * @return
     * @throws EsignetException
     */
    TokenResponse getTokens(@Valid TokenRequest tokenRequest,boolean isV2) throws EsignetException;

    /**
     * API to get list of IdP public keys
     * @return list of all the keys used to sign access-token, id-token and user kyc data
     */
    Map<String, Object> getJwks();

    Map<String, Object> getOAuthServerDiscoveryInfo();
}
