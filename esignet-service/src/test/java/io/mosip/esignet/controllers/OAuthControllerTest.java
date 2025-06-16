/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.config.LocalAuthenticationEntryPoint;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.spi.OAuthService;
import io.mosip.esignet.services.AuthorizationHelperService;
import io.mosip.esignet.services.CacheUtilService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@WebMvcTest(value = OAuthController.class)
public class OAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuthService oAuthServiceImpl;

    @MockBean
    AuditPlugin auditWrapper;

    @MockBean
    CacheUtilService cacheUtilService;

    @MockBean
    AuthorizationHelperService authorizationHelperService;

    @MockBean
    LocalAuthenticationEntryPoint localAuthenticationEntryPoint;

    @Test
    public void getAllJwks_thenPass() throws Exception {
        Map<String, Object> sampleResult = new HashMap<>();
        sampleResult.put("keys", new ArrayList<>());
        Mockito.when(oAuthServiceImpl.getJwks()).thenReturn(sampleResult);

        mockMvc.perform(get("/oauth/.well-known/jwks.json")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"keys\":[]}"))
                .andExpect(header().string("Content-Type", "application/json"));
    }

    @Test
    public void getToken_withInvalidContentType_thenFail() throws Exception {
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(415));

        mockMvc.perform(post("/oauth/v2/token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(415));
    }

    @Test
    public void getToken_withValidInput_thenPass() throws Exception {
        TokenResponse tokenResponse = new TokenResponse();
        Mockito.when(oAuthServiceImpl.getTokens(Mockito.any(TokenRequestV2.class),Mockito.anyBoolean())).thenReturn(tokenResponse);

        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .param("code", "code")
                        .param("redirect_uri", "https://redirect-uri")
                        .param("grant_type", "authorization_code")
                        .param("client_id", "client_id")
                        .param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                        .param("client_assertion", "client_assertion"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/oauth/v2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .param("code", "code")
                        .param("code_verifier", "code-verifier")
                        .param("redirect_uri", "https://redirect-uri")
                        .param("grant_type", "authorization_code")
                        .param("client_id", "client_id")
                        .param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                        .param("client_assertion", "client_assertion"))
                .andExpect(status().isOk());
    }

    @Test
    public void getToken_withInvalidInput_thenFail() throws Exception {
        Mockito.when(oAuthServiceImpl.getTokens(Mockito.any(TokenRequestV2.class),Mockito.anyBoolean())).thenThrow(InvalidRequestException.class);
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/oauth/v2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getToken_withRuntimeFailure_thenFail() throws Exception {
        Mockito.when(oAuthServiceImpl.getTokens(Mockito.any(TokenRequestV2.class),Mockito.anyBoolean())).thenThrow(EsignetException.class);
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .param("code", "code")
                .param("redirect_uri", "https://redirect-uri")
                .param("grant_type", "authorization_code")
                .param("client_id", "client_id")
                .param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .param("client_assertion", "client_assertion"))
                .andExpect(status().isInternalServerError());

        mockMvc.perform(post("/oauth/v2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .param("code", "code")
                        .param("redirect_uri", "https://redirect-uri")
                        .param("grant_type", "authorization_code")
                        .param("client_id", "client_id")
                        .param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                        .param("client_assertion", "client_assertion"))
                .andExpect(status().isInternalServerError());

        Mockito.when(oAuthServiceImpl.getTokens(Mockito.any(TokenRequestV2.class),Mockito.anyBoolean())).thenThrow(NullPointerException.class);
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .param("code", "code")
                        .param("redirect_uri", "https://redirect-uri")
                        .param("grant_type", "authorization_code")
                        .param("client_id", "client_id")
                        .param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                        .param("client_assertion", "client_assertion"))
                .andExpect(status().isInternalServerError());

        mockMvc.perform(post("/oauth/v2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .param("code", "code")
                        .param("redirect_uri", "https://redirect-uri")
                        .param("grant_type", "authorization_code")
                        .param("client_id", "client_id")
                        .param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                        .param("client_assertion", "client_assertion"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    public void getOAuthDiscoveryInfo_thenPass() throws Exception {

        Map<String, Object> discoveryInfo = new HashMap<>();
        discoveryInfo.put("key", "value");
        Mockito.when(oAuthServiceImpl.getOAuthServerDiscoveryInfo()).thenReturn(discoveryInfo);

        mockMvc.perform(get("/oauth/.well-known/oauth-authorization-server")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"key\":\"value\"}"))
                .andExpect(header().string("Content-Type", "application/json"));
    }

    @Test
    public void authorize_withValidInput_thenPass() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", "test-client-id");
        params.add("client_assertion_type", "assertion-type");
        params.add("client_assertion", "assertion");
        params.add("redirect_uri","http://testexample.com");
        params.add("scope","openid");
        params.add("response_type","code");

        PushedAuthorizationResponse mockResponse = new PushedAuthorizationResponse();
        mockResponse.setRequest_uri("urn:example:request_uri");
        mockResponse.setExpires_in(3600);

        Mockito.when(oAuthServiceImpl.authorize(Mockito.any(PushedAuthorizationRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/oauth/par")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .params(params))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.request_uri").value("urn:example:request_uri"))
                .andExpect(jsonPath("$.expires_in").value(3600));
    }

    @Test
    public void authorize_withRequestUri_thenFail() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", "test-client-id");
        params.add("client_assertion_type", "assertion-type");
        params.add("client_assertion", "assertion");
        params.add("redirect_uri", "http://testexample.com");
        params.add("scope", "openid");
        params.add("request_uri", "invalid-uri");

        mockMvc.perform(post("/oauth/par")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    public void authorize_withMissingRedirectUri_thenFail() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", "test-client-id");
        params.add("client_assertion_type", "assertion-type");
        params.add("client_assertion", "assertion");
        params.add("scope", "openid");
        params.add("response_type","code");

        mockMvc.perform(post("/oauth/par")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.error_description").value("invalid_redirect_uri"));

    }

    @Test
    public void authorize_withMissingScope_thenFail() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", "test-client-id");
        params.add("client_assertion_type", "assertion-type");
        params.add("client_assertion", "assertion");
        params.add("redirect_uri", "http://testexample.com");
        params.add("response_type","code");

        mockMvc.perform(post("/oauth/par")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.error_description").value("invalid_scope"));
    }

    @Test
    public void authorize_withMissingResponseType_thenFail() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", "test-client-id");
        params.add("client_assertion_type", "assertion-type");
        params.add("client_assertion", "assertion");
        params.add("redirect_uri", "http://testexample.com");
        params.add("scope", "openid");

        mockMvc.perform(post("/oauth/par")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.error_description").value("invalid_response_type"));
    }

    @Test
    public void authorize_withUnsupportedAssertionType_thenFail() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", "test-client-id");
        params.add("client_assertion", "assertion");
        params.add("redirect_uri", "http://testexample.com");
        params.add("scope", "openid");
        params.add("response_type","code");

        mockMvc.perform(post("/oauth/par")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.error_description").value("invalid_assertion_type"));
    }

    @Test
    public void authorize_withInvalidClaimsJson_thenFail() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", "test-client-id");
        params.add("client_assertion_type", "assertion-type");
        params.add("client_assertion", "assertion");
        params.add("redirect_uri", "http://testexample.com");
        params.add("scope", "openid");
        params.add("claims", "{invalid-json");
        params.add("response_type","code");

        mockMvc.perform(post("/oauth/par")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

    }

    @Test
    public void authorize_withException_thenFail() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", "test-client-id");
        params.add("client_assertion_type", "assertion-type");
        params.add("client_assertion", "assertion");
        params.add("redirect_uri","http://testexample.com");
        params.add("scope","openid");
        params.add("response_type","code");

        EsignetException ex = new EsignetException("Authorization failed");

        Mockito.when(oAuthServiceImpl.authorize(Mockito.any(PushedAuthorizationRequest.class))).thenThrow(ex);

        mockMvc.perform(post("/oauth/par")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .params(params))
                .andExpect(status().isInternalServerError());
    }

}