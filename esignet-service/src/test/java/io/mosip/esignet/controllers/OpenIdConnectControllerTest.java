/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.spi.OpenIdConnectService;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.services.CacheUtilService;
import io.mosip.esignet.vci.services.VCICacheService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@RunWith(SpringRunner.class)
@WebMvcTest(value = OpenIdConnectController.class)
public class OpenIdConnectControllerTest {

    @Value("#{${mosip.esignet.discovery.key-values}}")
    private Map<String, Object> discoveryMap;

    @Value("${mosip.esignet.discovery.issuer-id}")
    private String issuerId;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    OpenIdConnectService openIdConnectServiceImpl;

    @MockBean
    TokenService tokenService;

    @MockBean
    CacheUtilService cacheUtilService;

    @MockBean
    AuditPlugin auditWrapper;

    @MockBean
    ParsedAccessToken parsedAccessToken;

    @MockBean
    VCICacheService vciCacheService;


    @Test
    public void getOpenIdConfiguration_thenPass() throws Exception {
        when(openIdConnectServiceImpl.getOpenIdConfiguration()).thenReturn(discoveryMap);

        mockMvc.perform(get("/oidc/.well-known/openid-configuration")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value(issuerId))
                .andExpect(jsonPath("$.authorization_endpoint").value(discoveryMap.get("authorization_endpoint")))
                .andExpect(jsonPath("$.token_endpoint").value(discoveryMap.get("token_endpoint")))
                .andExpect(jsonPath("$.userinfo_endpoint").value(discoveryMap.get("userinfo_endpoint")));
    }

    @Test
    public void getUserinfo_withValidAccessToken_thenPass() throws Exception {
        String output = "encryptedKyc";
        when(openIdConnectServiceImpl.getUserInfo(anyString())).thenReturn(output);
        String expectedHeader = "application/jwt;charset=UTF-8";

        mockMvc.perform(get("/oidc/userinfo")
                        .header("Authorization", "Bearer accessToken"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", expectedHeader))
                .andExpect(content().string(output));

        mockMvc.perform(get("/oidc/userinfo")
                        .header("authorization", "Bearer accessToken"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", expectedHeader))
                .andExpect(content().string(output));
    }

    @Test
    public void getUserinfo_withNoAccessToken_thenFail() throws Exception {
        mockMvc.perform(get("/oidc/userinfo"))
                .andExpect(status().is(401))
                .andExpect(header().string("WWW-Authenticate", "error=\"missing_header\""));
    }

    @Test
    public void getUserinfo_withInvalidAccessToken_thenFail() throws Exception {
        when(openIdConnectServiceImpl.getUserInfo(anyString())).thenThrow(NotAuthenticatedException.class);
        mockMvc.perform(get("/oidc/userinfo")
                        .header("Authorization", "accessToken"))
                .andExpect(status().is(401))
                .andExpect(header().string("WWW-Authenticate", "error=\"invalid_token\""));
    }

    @Test
    public void getUserinfo_withRuntimeException_thenFail() throws Exception {
        when(openIdConnectServiceImpl.getUserInfo(anyString())).thenThrow(NullPointerException.class);
        mockMvc.perform(get("/oidc/userinfo")
                        .header("Authorization", "accessToken"))
                .andExpect(status().is(401))
                .andExpect(header().string("WWW-Authenticate", "error=\"unknown_error\""));
    }
}