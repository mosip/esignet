/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.core.dto.DiscoveryResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
public class OpenIdConnectControllerTest {


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

    //region Variables
    String OIDC_CONFIG_URL = "/oidc/.well-known/openid-configuration";

    //endregion

    ObjectMapper objectMapper = new ObjectMapper();
    protected MockMvc mvc;
    @Autowired
    WebApplicationContext webApplicationContext;

    @Before
    public void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    //region Create Client Test

    @Test
    public void getOpenIdConfiguration_pass() throws Exception {
        DiscoveryResponse configuration = getConfiguration();

        Assert.assertEquals(configuration.getIssuer(), issuer);
        Assert.assertEquals(configuration.getAuthorization_endpoint(), authorization_endpoint);
        Assert.assertEquals(configuration.getToken_endpoint(), token_endpoint);
        Assert.assertEquals(configuration.getToken_endpoint_auth_methods_supported(), token_endpoint_auth_methods_supported);
        Assert.assertEquals(configuration.getToken_endpoint_auth_signing_alg_values_supported(), token_endpoint_auth_signing_alg_values_supported);
        Assert.assertEquals(configuration.getUserinfo_endpoint(), userinfo_endpoint);
        Assert.assertEquals(configuration.getCheck_session_iframe(), check_session_iframe);
        Assert.assertEquals(configuration.getEnd_session_endpoint(), end_session_endpoint);
        Assert.assertEquals(configuration.getJwks_uri(), jwks_uri);
        Assert.assertEquals(configuration.getRegistration_endpoint(), registration_endpoint);
        Assert.assertEquals(configuration.getScopes_supported(), scopes_supported);
        Assert.assertEquals(configuration.getResponse_types_supported(), response_types_supported);
        Assert.assertEquals(configuration.getAcr_values_supported(), acr_values_supported);
        Assert.assertEquals(configuration.getSubject_types_supported(), subject_types_supported);
        Assert.assertEquals(configuration.getUserinfo_signing_alg_values_supported(), userinfo_signing_alg_values_supported);
        Assert.assertEquals(configuration.getUserinfo_encryption_alg_values_supported(), userinfo_encryption_alg_values_supported);
        Assert.assertEquals(configuration.getUserinfo_encryption_enc_values_supported(), userinfo_encryption_enc_values_supported);
        Assert.assertEquals(configuration.getId_token_signing_alg_values_supported(), id_token_signing_alg_values_supported);
        Assert.assertEquals(configuration.getId_token_encryption_alg_values_supported(), id_token_encryption_alg_values_supported);
        Assert.assertEquals(configuration.getId_token_encryption_enc_values_supported(), id_token_encryption_enc_values_supported);
        Assert.assertEquals(configuration.getRequest_object_signing_alg_values_supported(), request_object_signing_alg_values_supported);
        Assert.assertEquals(configuration.getDisplay_values_supported(), display_values_supported);
        Assert.assertEquals(configuration.getClaim_types_supported(), claim_types_supported);
        Assert.assertEquals(configuration.getClaims_supported(), claims_supported);
        Assert.assertEquals(configuration.isClaims_parameter_supported(), claims_parameter_supported);
        Assert.assertEquals(configuration.getService_documentation(), service_documentation);
        Assert.assertEquals(configuration.getUi_locales_supported(), ui_locales_supported);
    }

    //endregion

    //region private methods

    private DiscoveryResponse getConfiguration() throws Exception {
        MvcResult mvcCreateResult = mvc.perform(
                MockMvcRequestBuilders.get(OIDC_CONFIG_URL)
                .contentType(MediaType.APPLICATION_JSON_VALUE)).andReturn();

        int createStatus = mvcCreateResult.getResponse().getStatus();
        Assert.assertEquals(200, createStatus);

        return getResponse(mvcCreateResult.getResponse().getContentAsString());
    }

    private DiscoveryResponse getResponse(String responseContent) throws IOException {
        return objectMapper.readValue(responseContent, DiscoveryResponse.class);
    }

    //endregion
}
