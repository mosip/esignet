/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.flows;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.esignet.TestUtil;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.claim.ClaimDetail;
import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.api.dto.KycAuthDto;
import io.mosip.esignet.api.dto.claim.ClaimsV2;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.repository.ClientDetailRepository;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.services.CacheUtilService;
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
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static io.mosip.esignet.core.constants.Constants.UTC_DATETIME_PATTERN;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

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
    private Authenticator authenticationWrapper;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    AuditPlugin auditWrapper;

    @Value("${mosip.esignet.amr-acr-mapping-file-url}")
    private String mappingFileUrl;

    @Value("${mosip.esignet.mock.authenticator.policy-repo}")
    private String policyDir;

    private MockRestServiceServer mockRestServiceServer;
    private String clientId = "service-oidc-client";
    private String state = "er345agrR3T";
    private String nonce = "23424234TY";
    private String replyingPartyId = "mock-relying-party-id";
    private String redirectionUrl = "https://service.client.com/home";
    private JWK clientJWK = TestUtil.generateJWK_RSA();

    private String oauthDetailsHashHeader = null;
    private String oauthDetailsKeyHeader = null;

    @Before
    public void init() throws Exception {
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


        createOIDCClient(clientId, clientJWK.toPublicJWK(), replyingPartyId);
        log.info("Successfully create OIDC Client {}", clientId);
    }

    @Test
    public void authorizationCodeFlowTest() throws Exception {
        String redirectionUrl = "https://service.client.com/home";
        String code = null;

        ResponseWrapper<OAuthDetailResponseV1> oAuthDetailResponseWrapper = getOauthDetails(clientId, redirectionUrl, state, nonce);
        OAuthDetailResponseV1 oAuthDetailResponse = oAuthDetailResponseWrapper.getResponse();
        log.info("Successfully received oauth details : {}", oAuthDetailResponse);

        oauthDetailsKeyHeader = oAuthDetailResponse.getTransactionId();
        oauthDetailsHashHeader = IdentityProviderUtil.generateB64EncodedHash( IdentityProviderUtil.ALGO_SHA_256,
                objectMapper.writeValueAsString(oAuthDetailResponse));

        ResponseWrapper<AuthResponse> authResponseResponseWrapper = authenticate(oAuthDetailResponse.getTransactionId());
        AuthResponse authResponse = authResponseResponseWrapper.getResponse();
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
                        .accept("application/jwt")
                        .header("Authorization", Constants.BEARER + Constants.SPACE + accessToken))
                .andExpect(status().isOk()).andExpect(content().contentType("application/jwt;charset=UTF-8")).andReturn();

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
                .audience("http://localhost:8088/v1/esignet/oauth/token")
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
                        .header("oauth-details-key", oauthDetailsKeyHeader)
                        .header("oauth-details-hash", oauthDetailsHashHeader)
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(wrapper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.response.redirectUri").isNotEmpty()).andReturn();

        ResponseWrapper<AuthCodeResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<ResponseWrapper<AuthCodeResponse>>() {});
        return response.getResponse().getCode();
    }

    private ResponseWrapper<AuthResponse> authenticate(String transactionId) throws Exception {
        AuthRequest kycAuthDto = new AuthRequest();
        kycAuthDto.setIndividualId("8267411571");
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("PIN");
        authChallenge.setChallenge("34789");
        authChallenge.setFormat("number");
        kycAuthDto.setChallengeList(Arrays.asList(authChallenge));
        kycAuthDto.setTransactionId(transactionId);

        RequestWrapper<AuthRequest> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(kycAuthDto);

        MvcResult result = mockMvc.perform(post("/authorization/authenticate")
                        .header("oauth-details-key", oauthDetailsKeyHeader)
                        .header("oauth-details-hash", oauthDetailsHashHeader)
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(wrapper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.response.transactionId").value(transactionId)).andReturn();

        ResponseWrapper<AuthResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<ResponseWrapper<AuthResponse>>() {});
        return response;
    }

    private ResponseWrapper<OAuthDetailResponseV1> getOauthDetails(String clientId, String redirectionUrl,String state, String nonce) throws Exception {
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
        ClaimsV2 claims = new ClaimsV2();
        claims.setUserinfo(new HashMap<>());
        claims.getUserinfo().put("email", getClaimDetail(null, null, true));
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

        ResponseWrapper<OAuthDetailResponseV1> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<ResponseWrapper<OAuthDetailResponseV1>>() {});
        return response;
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

    private JsonNode getClaimDetail(String value, String[] values, boolean essential) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("value", value);
        detail.put("values", values);
        detail.put("essential", essential);
        try {
            return objectMapper.readTree(objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
