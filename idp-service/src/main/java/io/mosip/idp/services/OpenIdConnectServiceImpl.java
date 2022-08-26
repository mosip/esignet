/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.DiscoveryResponse;
import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.exception.NotAuthenticatedException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.AuthorizationService;
import io.mosip.idp.core.spi.TokenGeneratorService;
import io.mosip.idp.repositories.ClientDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenIdConnectServiceImpl implements io.mosip.idp.core.spi.OpenIdConnectService {

    private static final Logger logger = LoggerFactory.getLogger(OpenIdConnectServiceImpl.class);

    //region properties

    @Value("mosip.idp.discovery.issuer")
    private String issuer;

    @Value("mosip.idp.discovery.authorization_endpoint")
    private String authorization_endpoint;

    @Value("mosip.idp.discovery.token_endpoint")
    private String token_endpoint;

    @Value("#{${mosip.idp.discovery.token_endpoint_auth_methods_supported}}")
    private List<String> token_endpoint_auth_methods_supported;

    @Value("#{${mosip.idp.discovery.token_endpoint_auth_signing_alg_values_supported}}")
    private List<String> token_endpoint_auth_signing_alg_values_supported;

    @Value("mosip.idp.discovery.userinfo_endpoint")
    private String userinfo_endpoint;

    @Value("mosip.idp.discovery.check_session_iframe")
    private String check_session_iframe;

    @Value("mosip.idp.discovery.end_session_endpoint")
    private String end_session_endpoint;

    @Value("mosip.idp.discovery.jwks_uri")
    private String jwks_uri;

    @Value("mosip.idp.discovery.registration_endpoint")
    private String registration_endpoint;

    @Value("#{${mosip.idp.discovery.scopes_supported}}")
    private List<String> scopes_supported;

    @Value("#{${mosip.idp.discovery.response_types_supported}}")
    private List<String> response_types_supported;

    @Value("#{${mosip.idp.discovery.acr_values_supported}}")
    private List<String> acr_values_supported;

    @Value("#{${mosip.idp.discovery.subject_types_supported}}")
    private List<String> subject_types_supported;

    @Value("#{${mosip.idp.discovery.userinfo_signing_alg_values_supported}}")
    private List<String> userinfo_signing_alg_values_supported;

    @Value("#{${mosip.idp.discovery.userinfo_encryption_alg_values_supported}}")
    private List<String> userinfo_encryption_alg_values_supported;

    @Value("#{${mosip.idp.discovery.userinfo_encryption_enc_values_supported}}")
    private List<String> userinfo_encryption_enc_values_supported;

    @Value("#{${mosip.idp.discovery.id_token_signing_alg_values_supported}}")
    private List<String> id_token_signing_alg_values_supported;

    @Value("#{${mosip.idp.discovery.id_token_encryption_alg_values_supported}}")
    private List<String> id_token_encryption_alg_values_supported;

    @Value("#{${mosip.idp.discovery.id_token_encryption_enc_values_supported}}")
    private List<String> id_token_encryption_enc_values_supported;

    @Value("#{${mosip.idp.discovery.request_object_signing_alg_values_supported}}")
    private List<String> request_object_signing_alg_values_supported;

    @Value("#{${mosip.idp.discovery.display_values_supported}}")
    private List<String> display_values_supported;

    @Value("#{${mosip.idp.discovery.claim_types_supported}}")
    private List<String> claim_types_supported;

    @Value("#{${mosip.idp.discovery.claims_supported}}")
    private List<String> claims_supported;

    @Value("#{new Boolean('${mosip.idp.discovery.claims_parameter_supported}')}")
    private boolean claims_parameter_supported;

    @Value("mosip.idp.discovery.service_documentation")
    private String service_documentation;

    @Value("#{${mosip.idp.discovery.ui_locales_supported}}")
    private List<String> ui_locales_supported;

    //endregion

    @Autowired
    private ClientDetailRepository clientDetailRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private TokenGeneratorService tokenGeneratorService;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Autowired
    private CacheUtilService cacheUtilService;


    @Override
    public String getUserInfo(String accessToken) throws NotAuthenticatedException {
        if (accessToken == null || accessToken.isBlank())
            throw new NotAuthenticatedException();

        //TODO - validate access token expiry - keymanager

        String accessTokenHash = ""; //TODO - generate access token hash - keymanager
        IdPTransaction transaction = cacheUtilService.getSetKycTransaction(accessTokenHash, null);
        if (transaction == null)
            throw new NotAuthenticatedException();

        return transaction.getEncryptedKyc();
    }

    @Override
    public DiscoveryResponse getOpenIdConfiguration() {

        var response = new DiscoveryResponse();
        response.setIssuer(issuer);
        response.setAuthorization_endpoint(authorization_endpoint);
        response.setToken_endpoint(token_endpoint);
        response.setToken_endpoint_auth_methods_supported(token_endpoint_auth_methods_supported);
        response.setToken_endpoint_auth_signing_alg_values_supported(token_endpoint_auth_signing_alg_values_supported);
        response.setUserinfo_endpoint(userinfo_endpoint);
        response.setCheck_session_iframe(check_session_iframe);
        response.setEnd_session_endpoint(end_session_endpoint);
        response.setJwks_uri(jwks_uri);
        response.setRegistration_endpoint(registration_endpoint);
        response.setScopes_supported(scopes_supported);
        response.setResponse_types_supported(response_types_supported);
        response.setAcr_values_supported(acr_values_supported);
        response.setSubject_types_supported(subject_types_supported);
        response.setUserinfo_signing_alg_values_supported(userinfo_signing_alg_values_supported);
        response.setUserinfo_encryption_alg_values_supported(userinfo_encryption_alg_values_supported);
        response.setUserinfo_encryption_enc_values_supported(userinfo_encryption_enc_values_supported);
        response.setId_token_signing_alg_values_supported(id_token_signing_alg_values_supported);
        response.setId_token_encryption_alg_values_supported(id_token_encryption_alg_values_supported);
        response.setId_token_encryption_enc_values_supported(id_token_encryption_enc_values_supported);
        response.setRequest_object_signing_alg_values_supported(request_object_signing_alg_values_supported);
        response.setDisplay_values_supported(display_values_supported);
        response.setClaim_types_supported(claim_types_supported);
        response.setClaims_supported(claims_supported);
        response.setClaims_parameter_supported(claims_parameter_supported);
        response.setService_documentation(service_documentation);
        response.setUi_locales_supported(ui_locales_supported);

        return response;
    }
}
