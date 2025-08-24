/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;

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
    TokenResponse getTokens(@Valid TokenRequestV2 tokenRequest, String dpopHeader, boolean isV2) throws EsignetException;

    /**
     * API to get list of IdP public keys
     * @return list of all the keys used to sign access-token, id-token and user kyc data
     */
    Map<String, Object> getJwks();

    /**
     * Retrieves the OpenID Connect Discovery metadata as defined by the OpenID Connect Discovery specification.
     * This includes supported endpoints, scopes, response types, grant types, and other server capabilities.
     * @return a map representing the server's discovery information.
     */
    Map<String, Object> getOAuthServerDiscoveryInfo();

    /**
     * Initiates a Pushed Authorization Request (PAR) to the authorization server.
     * This method accepts the full set of authorization parameters and dpop header and returns a request URI that the client can later reference in the authorization endpoint.
     * @return a response containing a request URI and expiration time
     */
    PushedAuthorizationResponse authorize(PushedAuthorizationRequest pushedAuthorizationRequest, String dpopHeader);
}
