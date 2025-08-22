/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.config.LocalAuthenticationEntryPoint;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.spi.OpenIdConnectService;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.SecurityHelperService;
import io.mosip.esignet.services.CacheUtilService;
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
import static org.mockito.ArgumentMatchers.any;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;


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
    LocalAuthenticationEntryPoint localAuthenticationEntryPoint;

    @MockBean
    SecurityHelperService securityHelperService;

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
                        .header("Authorization", "Bearer " + createRegularAccessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", expectedHeader))
                .andExpect(content().string(output));

        mockMvc.perform(get("/oidc/userinfo")
                        .header("authorization", "Bearer " + createRegularAccessToken()))
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
        when(openIdConnectServiceImpl.getUserInfo(anyString())).thenThrow(RuntimeException.class);
        mockMvc.perform(get("/oidc/userinfo")
                        .header("Authorization", "Bearer " + createRegularAccessToken()))
                .andExpect(status().is(401))
                .andExpect(header().string("WWW-Authenticate", "error=\"unknown_error\""));
    }

    @Test
    public void getUserinfo_withInvalidSchemeInAuthorizationHeader_thenFail() throws Exception {
        mockMvc.perform(get("/oidc/userinfo")
                        .header("Authorization", "Basic " + createRegularAccessToken()))
                .andExpect(status().is(401))
                .andExpect(header().string("WWW-Authenticate", "error=\"invalid_token\""));
    }


    @Test
    public void getUserinfo_withValidDpopBoundAccessToken_thenPass() throws Exception {
        String output = "encryptedKyc";
        String dpopJkt = "test-thumbprint";
        String dpopBoundAccessToken = createDpopBoundAccessToken(dpopJkt);
        String dpopProof = createValidDpopProof();

        when(openIdConnectServiceImpl.getUserInfo(anyString())).thenReturn(output);
        when(securityHelperService.computeJwkThumbprint(any())).thenReturn(dpopJkt);
        String expectedHeader = "application/jwt;charset=UTF-8";

        mockMvc.perform(get("/oidc/userinfo")
                        .header("Authorization", "DPoP " + dpopBoundAccessToken)
                        .header("DPoP", dpopProof))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", expectedHeader))
                .andExpect(content().string(output));
    }

    @Test
    public void getUserinfo_withDpopBoundTokenButMissingDpopHeader_thenFail() throws Exception {
        String dpopJkt = "test-thumbprint";
        String dpopBoundAccessToken = createDpopBoundAccessToken(dpopJkt);

        mockMvc.perform(get("/oidc/userinfo")
                        .header("Authorization", "DPoP " + dpopBoundAccessToken))
                .andExpect(status().is(400))
                .andExpect(header().string("WWW-Authenticate", "DPoP error=\"invalid_dpop_proof\""));
    }

    @Test
    public void getUserinfo_withDpopBoundTokenButInvalidDpopProof_thenFail() throws Exception {
        String dpopJkt = "test-thumbprint";
        String differentThumbprint = "different-thumbprint";
        String dpopBoundAccessToken = createDpopBoundAccessToken(dpopJkt);
        String invalidDpopProof = createValidDpopProof();

        when(securityHelperService.computeJwkThumbprint(any())).thenReturn(differentThumbprint);

        mockMvc.perform(get("/oidc/userinfo")
                        .header("Authorization", "DPoP " + dpopBoundAccessToken)
                        .header("DPoP", invalidDpopProof))
                .andExpect(status().is(400))
                .andExpect(header().string("WWW-Authenticate", "DPoP error=\"invalid_dpop_proof\""));
    }

    @Test
    public void getUserinfo_withRegularTokenAndDpopHeader_thenPass() throws Exception {
        String output = "encryptedKyc";
        String regularAccessToken = createRegularAccessToken();
        String dpopProof = createValidDpopProof();

        when(openIdConnectServiceImpl.getUserInfo(anyString())).thenReturn(output);
        String expectedHeader = "application/jwt;charset=UTF-8";

        mockMvc.perform(get("/oidc/userinfo")
                        .header("Authorization", "Bearer " + regularAccessToken)
                        .header("DPoP", dpopProof))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", expectedHeader))
                .andExpect(content().string(output));
    }

    private String createDpopBoundAccessToken(String dpopJkt) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
            String payload = "{\"sub\":\"user123\",\"aud\":\"client123\",\"exp\":" + (System.currentTimeMillis() / 1000 + 3600) + ",\"cnf\":{\"jkt\":\"" + dpopJkt + "\"}}"; 
            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes());
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString("dummy-signature".getBytes());
            return encodedHeader + "." + encodedPayload + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String createRegularAccessToken() {
        try {
            String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
            String payload = "{\"sub\":\"user123\",\"aud\":\"client123\",\"exp\":" + (System.currentTimeMillis() / 1000 + 3600) + "}"; 
            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes());
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString("dummy-signature".getBytes());
            return encodedHeader + "." + encodedPayload + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String createValidDpopProof() {
        try {
            RSAKey rsaKey = new RSAKeyGenerator(2048).generate();
            SignedJWT dpopJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).jwk(rsaKey.toPublicJWK()).build(),
                new JWTClaimsSet.Builder().build()
            );
            dpopJwt.sign(new RSASSASigner(rsaKey));
            return dpopJwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}