/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.flows;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.idp.TestUtil;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.TokenService;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.repository.ClientDetailRepository;
import io.mosip.idp.services.CacheUtilService;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jwe.JsonWebEncryption;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc(addFilters = false)
@Slf4j
public class AuthCodeFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private ClientDetailRepository clientDetailRepository;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Autowired
    RestTemplate restTemplate;

    @Value("${mosip.idp.amr-acr-mapping-file-url}")
    private String mappingFileUrl;

    @Value("${mosip.idp.authn.mock.impl.policy-repo}")
    private String policyDir;
    private MockRestServiceServer mockRestServiceServer;

    @Before
    public void init() throws IOException {
        mockRestServiceServer = MockRestServiceServer.createServer(restTemplate);
        mockRestServiceServer.expect(requestTo(mappingFileUrl))
                .andRespond(withSuccess("{\n" +
                        "  \"amr\" : {\n" +
                        "    \"PIN\" :  [{ \"type\": \"PIN\" }],\n" +
                        "    \"OTP\" :  [{ \"type\": \"OTP\" }],\n" +
                        "    \"Wallet\" :  [{ \"type\": \"WALLET\" }],\n" +
                        "    \"L1-bio-device\" :  [{ \"type\": \"BIO\", \"count\": 1 }]\n" +
                        "  },\n" +
                        "  \"acr_amr\" : {\n" +
                        "    \"mosip:idp:acr:static-code\" : [\"PIN\"],\n" +
                        "    \"mosip:idp:acr:generated-code\" : [\"OTP\"],\n" +
                        "    \"mosip:idp:acr:linked-wallet\" : [ \"Wallet\" ],\n" +
                        "    \"mosip:idp:acr:biometrics\" : [ \"L1-bio-device\" ]\n" +
                        "  }\n" +
                        "}",  MediaType.APPLICATION_JSON_UTF8));



    }

    @Test
    public void authorizationCodeFlowTest() throws Exception {
        String clientId = "service-oidc-client";
        String redirectionUrl = "https://service.client.com/home";
        String state = "er345agrR3T";
        String nonce = "23424234TY";
        String code = null;
        String replyingPartyId = "mock-relying-party-id";
        JWK clientJWK = TestUtil.generateJWK_RSA();

        createOIDCClient(clientId, clientJWK.toPublicJWK(), replyingPartyId);
        log.info("Successfully create OIDC Client {}", clientId);

        OAuthDetailResponse oAuthDetailResponse = getOauthDetails(clientId, redirectionUrl, state, nonce);
        log.info("Successfully received oauth details : {}", oAuthDetailResponse);

        AuthResponse authResponse = authenticate(oAuthDetailResponse.getTransactionId());
        log.info("Successfully completed kyc auth : {}", authResponse);

        code = getAuthCode(oAuthDetailResponse.getTransactionId(), state, nonce);
        Assert.assertTrue(code != null && !code.isBlank());
        log.info("Authorization code generated : {}", code);

        TokenResponse tokenResponse = getAccessToken(clientId, code, redirectionUrl, clientJWK);
        log.info("Successfully fetched auth token in exchange with auth_code : {}", tokenResponse);

        String usertoken = getUserInfo(tokenResponse.getAccess_token());
        Assert.assertNotNull(usertoken);

        JsonWebEncryption decrypt = new JsonWebEncryption();
        decrypt.setKey(getRelyingPartyPrivateKey(replyingPartyId));
        decrypt.setCompactSerialization(usertoken);
        String payload = decrypt.getPayload();


        log.info("Successfully fetched user token : {}", payload);
    }

    private PrivateKey getRelyingPartyPrivateKey(String relyingPartyId) throws Exception {
        DocumentContext mockRelyingPartyJson = JsonPath.parse(new File(policyDir, relyingPartyId+"_policy.json"));
        Map<String, String> keyMap = mockRelyingPartyJson.read("$.privateKey");
        return RSAKey.parse(new JSONObject(keyMap).toJSONString()).toPrivateKey();
    }

    private String getUserInfo(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/oidc/userinfo")
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .header("Authorization", Constants.BEARER + Constants.SPACE + accessToken))
                .andExpect(status().isOk()).andReturn();

        return result.getResponse().getContentAsString();
    }


    private TokenResponse getAccessToken(String clientId, String authCode, String redirectUri, JWK jwk)
            throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(jwk.getKeyID())
                .build();

        Instant issuedInstant = Instant.now();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer(clientId)
                .audience("http://localhost:8088/v1/idp")
                .subject(clientId)
                .issueTime(Date.from(issuedInstant))
                .expirationTime(Date.from(issuedInstant.plusSeconds(60)))
                .build();
        SignedJWT signedJWT = new SignedJWT(header, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(jwk.toRSAKey().toPrivateKey()));

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .contentType("application/x-www-form-urlencoded")
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .param("code", authCode)
                        .param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                        .param("client_id", clientId)
                        .param("redirect_uri", redirectUri)
                        .param("grant_type", "authorization_code")
                        .param("client_assertion", signedJWT.serialize()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.id_token").isNotEmpty()).andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
    }

    private String getAuthCodeWithRedirection(String transactionId,String state, String nonce) throws Exception  {
        AuthCodeRequest authCodeRequest = new AuthCodeRequest();
        authCodeRequest.setTransactionId(transactionId);
        authCodeRequest.setAcceptedClaims(Arrays.asList("email", "name", "gender"));
        RequestWrapper<AuthCodeRequest> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(authCodeRequest);

        MvcResult result = mockMvc.perform(post("/authorization/auth-code")
                        .param("nonce", nonce).param("state", state)
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(wrapper)))
                .andExpect(status().is3xxRedirection()).andReturn();

        String code = null;
        String[] parts = result.getResponse().getRedirectedUrl().split("\\?");
        for(String param : parts[1].split("&")) {
            if(param.startsWith("state"))
                Assert.assertTrue(param.endsWith(state));
            if(param.startsWith("nonce"))
                Assert.assertTrue(param.endsWith(nonce));
            if(param.startsWith("code"))
                code = param.split("=")[1];
        }
        return code;
    }

    private String getAuthCode(String transactionId,String state, String nonce) throws Exception  {
        AuthCodeRequest authCodeRequest = new AuthCodeRequest();
        authCodeRequest.setTransactionId(transactionId);
        authCodeRequest.setAcceptedClaims(Arrays.asList("email", "name", "gender"));
        RequestWrapper<AuthCodeRequest> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(authCodeRequest);

        MvcResult result = mockMvc.perform(post("/authorization/auth-code")
                        .param("nonce", nonce).param("state", state)
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(wrapper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.response.redirectUri").isNotEmpty()).andReturn();

        ResponseWrapper<AuthCodeResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<ResponseWrapper<AuthCodeResponse>>() {});
        return response.getResponse().getCode();
    }

    private AuthResponse authenticate(String transactionId) throws Exception {
        KycAuthRequest kycAuthRequest = new KycAuthRequest();
        kycAuthRequest.setIndividualId("8267411571");
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("PIN");
        authChallenge.setChallenge("34789");
        kycAuthRequest.setChallengeList(Arrays.asList(authChallenge));
        kycAuthRequest.setTransactionId(transactionId);

        RequestWrapper<KycAuthRequest> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(kycAuthRequest);

        MvcResult result = mockMvc.perform(post("/authorization/authenticate")
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(wrapper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.response.transactionId").value(transactionId)).andReturn();

        ResponseWrapper<AuthResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<ResponseWrapper<AuthResponse>>() {});
        return response.getResponse();
    }

    private OAuthDetailResponse getOauthDetails(String clientId, String redirectionUrl,String state, String nonce) throws Exception {
        OAuthDetailRequest oAuthDetailRequest = new OAuthDetailRequest();
        oAuthDetailRequest.setClientId(clientId);
        oAuthDetailRequest.setRedirectUri(redirectionUrl);
        oAuthDetailRequest.setAcrValues("level0 mosip:idp:acr:static-code");
        oAuthDetailRequest.setPrompt("login");
        oAuthDetailRequest.setDisplay("popup");
        oAuthDetailRequest.setScope("openid profile");
        oAuthDetailRequest.setResponseType("code");
        oAuthDetailRequest.setNonce(nonce);
        oAuthDetailRequest.setState(state);
        Claims claims = new Claims();
        claims.setUserinfo(new HashMap<>());
        claims.getUserinfo().put("email", new ClaimDetail(null,null,true));
        oAuthDetailRequest.setClaims(claims);

        RequestWrapper<OAuthDetailRequest> request = new RequestWrapper<>();
        request.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        request.setRequest(oAuthDetailRequest);

        MvcResult result = mockMvc.perform(post("/authorization/oauth-details")
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.response.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.response.essentialClaims.length()").value(1))
                .andExpect(jsonPath("$.response.voluntaryClaims.length()").value(2)).andReturn();

        ResponseWrapper<OAuthDetailResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<ResponseWrapper<OAuthDetailResponse>>() {});
        return response.getResponse();
    }

    private void createOIDCClient(String clientId, JWK publicJWK, String relyingPartyId) throws Exception {
        ClientDetailCreateRequest createRequest = new ClientDetailCreateRequest();
        createRequest.setClientName("Mock OIDC Client");
        createRequest.setClientId(clientId);
        createRequest.setRelyingPartyId(relyingPartyId);
        createRequest.setPublicKey(publicJWK.toJSONObject());
        createRequest.setLogoUri("https://mock.client.com/logo.png");
        createRequest.setGrantTypes(Arrays.asList("authorization_code"));
        createRequest.setClientAuthMethods(Arrays.asList("private_key_jwt"));
        createRequest.setAuthContextRefs(Arrays.asList("mosip:idp:acr:static-code"));
        createRequest.setRedirectUris(Arrays.asList("https://service.client.com/home", "https://mock.client.com/dashboard"));
        createRequest.setUserClaims(Arrays.asList("name", "email", "gender"));
        RequestWrapper<ClientDetailCreateRequest> request = new RequestWrapper<>();
        request.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        request.setRequest(createRequest);

        mockMvc.perform(post("/client-mgmt/oidc-client")
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.response.clientId").value(clientId))
                .andExpect(jsonPath("$.response.status").value(Constants.CLIENT_ACTIVE_STATUS));
    }

}
