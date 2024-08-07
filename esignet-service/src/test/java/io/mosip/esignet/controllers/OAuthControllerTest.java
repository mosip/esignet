/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.TokenRequest;
import io.mosip.esignet.core.dto.TokenResponse;
import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.spi.OAuthService;
import io.mosip.esignet.services.AuthorizationHelperService;
import io.mosip.esignet.services.CacheUtilService;
import io.mosip.esignet.vci.services.VCICacheService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@WebMvcTest(value = OAuthController.class)
public class OAuthControllerTest {

    ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuthService oAuthServiceImpl;

    @MockBean
    AuditPlugin auditWrapper;

    @MockBean
    CacheUtilService cacheUtilService;

    @MockBean
    ParsedAccessToken parsedAccessToken;

    @MockBean
    VCICacheService vciCacheService;

    @MockBean
    AuthorizationHelperService authorizationHelperService;

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
        Mockito.when(oAuthServiceImpl.getTokens(Mockito.any(TokenRequest.class),Mockito.anyBoolean())).thenReturn(tokenResponse);

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
        Mockito.when(oAuthServiceImpl.getTokens(Mockito.any(TokenRequest.class),Mockito.anyBoolean())).thenThrow(InvalidRequestException.class);
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/oauth/v2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getToken_withRuntimeFailure_thenFail() throws Exception {
        Mockito.when(oAuthServiceImpl.getTokens(Mockito.any(TokenRequest.class),Mockito.anyBoolean())).thenThrow(EsignetException.class);
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

        Mockito.when(oAuthServiceImpl.getTokens(Mockito.any(TokenRequest.class),Mockito.anyBoolean())).thenThrow(NullPointerException.class);
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
}