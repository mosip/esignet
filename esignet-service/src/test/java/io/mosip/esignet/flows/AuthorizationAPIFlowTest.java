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
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.claim.ClaimDetail;
import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.api.dto.KycAuthDto;
import io.mosip.esignet.api.dto.claim.ClaimsV2;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.TestUtil;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.repository.ClientDetailRepository;
import io.mosip.esignet.services.CacheUtilService;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.junit.*;
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
import java.util.*;

import static io.mosip.esignet.api.util.ErrorConstants.AUTH_FAILED;
import static io.mosip.esignet.core.constants.Constants.UTC_DATETIME_PATTERN;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc(addFilters = false)
@Slf4j
public class AuthorizationAPIFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RestTemplate restTemplate;

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
    AuditPlugin auditWrapper;

    @Value("${mosip.esignet.amr-acr-mapping-file-url}")
    private String mappingFileUrl;

    @Value("${mosip.esignet.mock.authenticator.policy-repo}")
    private String policyDir;

    private MockRestServiceServer mockRestServiceServer;
    private String clientId = "healthservicev1";
    private String state = "er345agrR3T";
    private String nonce = "23424234TY";
    private String replyingPartyId = "mock-relying-party-id";
    private String redirectionUrl = "http://health-services.com/userprofile";
    private JWK clientJWK = TestUtil.generateJWK_RSA();
    private boolean created = false;


    @Before
    public void init() throws Exception {
        mockRestServiceServer = MockRestServiceServer.createServer(restTemplate);
        mockRestServiceServer.expect(requestTo(mappingFileUrl))
                .andRespond(withSuccess("{\n" +
                        "  \"amr\" : {\n" +
                        "    \"PIN\" :  [{ \"type\": \"PIN\" }],\n" +
                        "    \"OTP\" :  [{ \"type\": \"OTP\" }],\n" +
                        "    \"WFA\" :  [{ \"type\": \"WFA\" }],\n" +
                        "    \"L1-bio-device\" :  [{ \"type\": \"BIO\", \"count\": 1 }]\n" +
                        "  },\n" +
                        "  \"acr_amr\" : {\n" +
                        "    \"mosip:idp:acr:static-code\" : [\"PIN\"],\n" +
                        "    \"mosip:idp:acr:generated-code\" : [\"OTP\"],\n" +
                        "    \"mosip:idp:acr:linked-wallet\" : [ \"WFA\" ],\n" +
                        "    \"mosip:idp:acr:biometrics\" : [ \"L1-bio-device\" ]\n" +
                        "  }\n" +
                        "}",  MediaType.APPLICATION_JSON_UTF8));
    }

    @Test
    public void invalidClientId_thenFail() throws Exception {
        ResponseWrapper<OAuthDetailResponseV1> oAuthDetailResponseWrapper = getOauthDetails("invalid-client", redirectionUrl, state, nonce);
        assertErrorCode(oAuthDetailResponseWrapper, ErrorConstants.INVALID_CLIENT_ID);
    }

    @Test
    public void authWithInvalidTransactionId_thenFail() throws Exception {
        ResponseWrapper<OAuthDetailResponseV1> oAuthDetailResponseWrapper = getOauthDetails(clientId, redirectionUrl, state, nonce);
        OAuthDetailResponseV1 oAuthDetailResponse = oAuthDetailResponseWrapper.getResponse();

        ResponseWrapper<AuthResponse> authResponseResponseWrapper = authenticate(null);
        assertErrorCode(authResponseResponseWrapper, ErrorConstants.INVALID_TRANSACTION_ID);

        authResponseResponseWrapper = authenticate("   ");
        assertErrorCode(authResponseResponseWrapper, ErrorConstants.INVALID_TRANSACTION_ID);

        authResponseResponseWrapper = authenticate("   "+oAuthDetailResponse.getTransactionId());
        assertErrorCode(authResponseResponseWrapper, ErrorConstants.INVALID_TRANSACTION);

        authResponseResponseWrapper = authenticate("wrewrerwerwerwrer-------");
        assertErrorCode(authResponseResponseWrapper, ErrorConstants.INVALID_TRANSACTION);

        authResponseResponseWrapper = authenticate(oAuthDetailResponse.getTransactionId());
        Assert.assertNotNull(authResponseResponseWrapper);
        Assert.assertNotNull(authResponseResponseWrapper.getResponseTime());
        Assert.assertNotNull(authResponseResponseWrapper.getResponse());
        Assert.assertEquals(oAuthDetailResponse.getTransactionId(), authResponseResponseWrapper.getResponse().getTransactionId());
    }

    @Test
    public void callAuthTwiceWithValidTransactionId_thenFail() throws Exception {
        ResponseWrapper<OAuthDetailResponseV1> oAuthDetailResponseWrapper = getOauthDetails(clientId, redirectionUrl, state, nonce);
        OAuthDetailResponseV1 oAuthDetailResponse = oAuthDetailResponseWrapper.getResponse();
        ResponseWrapper<AuthResponse> authResponseResponseWrapper = authenticate(oAuthDetailResponse.getTransactionId());
        Assert.assertNotNull(authResponseResponseWrapper);
        Assert.assertNotNull(authResponseResponseWrapper.getResponseTime());
        Assert.assertNotNull(authResponseResponseWrapper.getResponse());
        Assert.assertEquals(oAuthDetailResponse.getTransactionId(), authResponseResponseWrapper.getResponse().getTransactionId());

        authResponseResponseWrapper = authenticate(oAuthDetailResponse.getTransactionId());
        assertErrorCode(authResponseResponseWrapper, ErrorConstants.INVALID_TRANSACTION);
    }

    @Test
    public void getAuthCodeAfterAuthCall_thenPass() throws Exception {
        ResponseWrapper<OAuthDetailResponseV1> oAuthDetailResponseWrapper = getOauthDetails(clientId, redirectionUrl, state, nonce);
        OAuthDetailResponseV1 oAuthDetailResponse = oAuthDetailResponseWrapper.getResponse();

        ResponseWrapper<AuthResponse> authResponseResponseWrapper = authenticate(oAuthDetailResponse.getTransactionId());
        Assert.assertNotNull(authResponseResponseWrapper);
        Assert.assertNotNull(authResponseResponseWrapper.getResponseTime());
        Assert.assertNotNull(authResponseResponseWrapper.getResponse());
        Assert.assertEquals(oAuthDetailResponse.getTransactionId(), authResponseResponseWrapper.getResponse().getTransactionId());

        ResponseWrapper<AuthCodeResponse> responseWrapper = getAuthCode(oAuthDetailResponse.getTransactionId(), state, nonce);
        Assert.assertNotNull(responseWrapper);
        Assert.assertNotNull(responseWrapper.getResponseTime());
        Assert.assertNotNull(responseWrapper.getResponse());
        Assert.assertNotNull(responseWrapper.getResponse().getCode());
        Assert.assertEquals(state, responseWrapper.getResponse().getState());
        Assert.assertEquals(nonce, responseWrapper.getResponse().getNonce());
        Assert.assertEquals(redirectionUrl, responseWrapper.getResponse().getRedirectUri());
    }

    @Test
    public void getAuthCodeBeforeAuthCall_thenFail() throws Exception {
        ResponseWrapper<OAuthDetailResponseV1> oAuthDetailResponseWrapper = getOauthDetails(clientId, redirectionUrl, state, nonce);
        OAuthDetailResponseV1 oAuthDetailResponse = oAuthDetailResponseWrapper.getResponse();
        ResponseWrapper<AuthCodeResponse> responseWrapper = getAuthCode(oAuthDetailResponse.getTransactionId(), state, nonce);
        assertErrorCode(responseWrapper, ErrorConstants.INVALID_TRANSACTION);
    }

    @Test
    public void sendotpAfterSuccessAuthCall_thenFail() throws Exception {
        ResponseWrapper<OAuthDetailResponseV1> oAuthDetailResponseWrapper = getOauthDetails(clientId, redirectionUrl, state, nonce);
        OAuthDetailResponseV1 oAuthDetailResponse = oAuthDetailResponseWrapper.getResponse();

        ResponseWrapper<AuthResponse> authResponseResponseWrapper = authenticate(oAuthDetailResponse.getTransactionId());
        Assert.assertNotNull(authResponseResponseWrapper);
        Assert.assertNotNull(authResponseResponseWrapper.getResponseTime());
        Assert.assertNotNull(authResponseResponseWrapper.getResponse());
        Assert.assertEquals(oAuthDetailResponse.getTransactionId(), authResponseResponseWrapper.getResponse().getTransactionId());

        ResponseWrapper<OtpResponse> otpResponseResponseWrapper = sendOtp(authResponseResponseWrapper.getResponse().getTransactionId(),
                "8267411571");
        assertErrorCode(otpResponseResponseWrapper, ErrorConstants.INVALID_TRANSACTION);
    }

    @Test
    public void sendotpAfterSuccessAuthcodeCall_thenFail() throws Exception {
        ResponseWrapper<OAuthDetailResponseV1> oAuthDetailResponseWrapper = getOauthDetails(clientId, redirectionUrl, state, nonce);
        OAuthDetailResponseV1 oAuthDetailResponse = oAuthDetailResponseWrapper.getResponse();

        ResponseWrapper<AuthResponse> authResponseResponseWrapper = authenticate(oAuthDetailResponse.getTransactionId());
        Assert.assertNotNull(authResponseResponseWrapper);
        Assert.assertNotNull(authResponseResponseWrapper.getResponseTime());
        Assert.assertNotNull(authResponseResponseWrapper.getResponse());
        Assert.assertEquals(oAuthDetailResponse.getTransactionId(), authResponseResponseWrapper.getResponse().getTransactionId());

        ResponseWrapper<AuthCodeResponse> responseWrapper = getAuthCode(oAuthDetailResponse.getTransactionId(), state, nonce);
        Assert.assertNotNull(responseWrapper);
        Assert.assertNotNull(responseWrapper.getResponseTime());
        Assert.assertNotNull(responseWrapper.getResponse());
        Assert.assertNotNull(responseWrapper.getResponse().getCode());
        Assert.assertEquals(state, responseWrapper.getResponse().getState());
        Assert.assertEquals(nonce, responseWrapper.getResponse().getNonce());
        Assert.assertEquals(redirectionUrl, responseWrapper.getResponse().getRedirectUri());

        ResponseWrapper<OtpResponse> otpResponseResponseWrapper = sendOtp(authResponseResponseWrapper.getResponse().getTransactionId(),
                "8267411571");
        assertErrorCode(otpResponseResponseWrapper, ErrorConstants.INVALID_TRANSACTION);
    }

    @Test
    public void sendotpAfterFailedAuthcodeCall_thenFail() throws Exception {
        ResponseWrapper<OAuthDetailResponseV1> oAuthDetailResponseWrapper = getOauthDetails(clientId, redirectionUrl, state, nonce);
        OAuthDetailResponseV1 oAuthDetailResponse = oAuthDetailResponseWrapper.getResponse();

        ResponseWrapper<AuthResponse> authResponseResponseWrapper = authenticate(oAuthDetailResponse.getTransactionId());
        Assert.assertNotNull(authResponseResponseWrapper);
        Assert.assertNotNull(authResponseResponseWrapper.getResponseTime());
        Assert.assertNotNull(authResponseResponseWrapper.getResponse());
        Assert.assertEquals(oAuthDetailResponse.getTransactionId(), authResponseResponseWrapper.getResponse().getTransactionId());

        ResponseWrapper<AuthCodeResponse> responseWrapper = getAuthCodeWithInvalidClaim(oAuthDetailResponse.getTransactionId());
        assertErrorCode(responseWrapper, ErrorConstants.INVALID_ACCEPTED_CLAIM);

        ResponseWrapper<OtpResponse> otpResponseResponseWrapper = sendOtp(authResponseResponseWrapper.getResponse().getTransactionId(),
                "8267411571");
        assertErrorCode(otpResponseResponseWrapper, ErrorConstants.INVALID_TRANSACTION);
    }

    @Test
    public void sendotpAfterFailedAuthCall_thenPass() throws Exception {
        ResponseWrapper<OAuthDetailResponseV1> oAuthDetailResponseWrapper = getOauthDetails(clientId, redirectionUrl, state, nonce);
        OAuthDetailResponseV1 oAuthDetailResponse = oAuthDetailResponseWrapper.getResponse();

        ResponseWrapper<AuthResponse> authResponseResponseWrapper = authenticateWithInvalidPin(oAuthDetailResponse.getTransactionId());
        assertErrorCode(authResponseResponseWrapper, AUTH_FAILED);

        ResponseWrapper<OtpResponse> otpResponseResponseWrapper = sendOtp(oAuthDetailResponse.getTransactionId(),
                "8267411571");
        Assert.assertNotNull(otpResponseResponseWrapper);
        Assert.assertNotNull(otpResponseResponseWrapper.getResponseTime());
        Assert.assertNotNull(otpResponseResponseWrapper.getResponse());
        Assert.assertEquals(oAuthDetailResponse.getTransactionId(), otpResponseResponseWrapper.getResponse().getTransactionId());
    }

    private void assertErrorCode(ResponseWrapper responseWrapper, String expectedErrorCode) {
        Assert.assertNotNull(responseWrapper);
        Assert.assertNotNull(responseWrapper.getResponseTime());
        Assert.assertNull(responseWrapper.getResponse());
        Assert.assertNotNull(responseWrapper.getErrors());
        Assert.assertEquals(expectedErrorCode, ((Error)responseWrapper.getErrors().get(0)).getErrorCode());
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
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
    }

    private ResponseWrapper<OtpResponse> sendOtp(String transactionId, String individualId) throws Exception {
        RequestWrapper<OtpRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setTransactionId(transactionId);
        otpRequest.setIndividualId(individualId);
        otpRequest.setOtpChannels(Arrays.asList("email"));
        requestWrapper.setRequest(otpRequest);

        MvcResult result = mockMvc.perform(post("/authorization/send-otp")
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(requestWrapper)))
                .andExpect(status().isOk())
                .andReturn();

        ResponseWrapper<OtpResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<ResponseWrapper<OtpResponse>>() {});
        return response;
    }

    private ResponseWrapper<AuthCodeResponse> getAuthCode(String transactionId,String state, String nonce) throws Exception  {
        AuthCodeRequest authCodeRequest = new AuthCodeRequest();
        authCodeRequest.setTransactionId(transactionId);
        authCodeRequest.setAcceptedClaims(Arrays.asList("email", "given_name", "gender"));
        RequestWrapper<AuthCodeRequest> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(authCodeRequest);

        MvcResult result = mockMvc.perform(post("/authorization/auth-code")
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(wrapper)))
                .andExpect(status().isOk())
                .andReturn();

        ResponseWrapper<AuthCodeResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<ResponseWrapper<AuthCodeResponse>>() {});
        return response;
    }

    private ResponseWrapper<AuthCodeResponse> getAuthCodeWithInvalidClaim(String transactionId) throws Exception  {
        AuthCodeRequest authCodeRequest = new AuthCodeRequest();
        authCodeRequest.setTransactionId(transactionId);
        authCodeRequest.setAcceptedClaims(Arrays.asList("email", "name1", "gender"));
        RequestWrapper<AuthCodeRequest> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(authCodeRequest);

        MvcResult result = mockMvc.perform(post("/authorization/auth-code")
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(wrapper)))
                .andExpect(status().isOk())
                .andReturn();

        ResponseWrapper<AuthCodeResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<ResponseWrapper<AuthCodeResponse>>() {});
        return response;
    }

    private ResponseWrapper<AuthResponse> authenticateWithInvalidPin(String transactionId) throws Exception {
        AuthRequest kycAuthDto = new AuthRequest();
        kycAuthDto.setIndividualId("8267411571");
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("PIN");
        authChallenge.setChallenge("1234");
        authChallenge.setFormat("number");
        kycAuthDto.setChallengeList(Arrays.asList(authChallenge));
        kycAuthDto.setTransactionId(transactionId);

        RequestWrapper<AuthRequest> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(kycAuthDto);

        MvcResult result = mockMvc.perform(post("/authorization/authenticate")
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(wrapper)))
                .andExpect(status().isOk())
                .andReturn();

        ResponseWrapper<AuthResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<ResponseWrapper<AuthResponse>>() {});
        return response;
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
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(wrapper)))
                .andExpect(status().isOk())
                .andReturn();

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
                        .param("nonce", nonce).param("state", state)
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ResponseWrapper<OAuthDetailResponseV1> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<ResponseWrapper<OAuthDetailResponseV1>>() {});
        return response;
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
