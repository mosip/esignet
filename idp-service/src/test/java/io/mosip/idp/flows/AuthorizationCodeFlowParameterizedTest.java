/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.flows;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
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
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(Parameterized.class)
@SpringBootTest
@AutoConfigureMockMvc(secure = false)
@Slf4j
public class AuthorizationCodeFlowParameterizedTest {

    @ClassRule
    public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();

    @Rule
    public final SpringMethodRule springMethodRule = new SpringMethodRule();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RestTemplate restTemplate;

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

    @Value("${mosip.idp.amr-acr-mapping-file-url}")
    private String mappingFileUrl;

    private TestContextManager testContextManager;

    private String testName;
    private String clientId;
    private String redirectionUrl;
    private String state;
    private String nonce;
    private static JWK clientJWK = TestUtil.generateJWK_RSA();

    private MockRestServiceServer mockRestServiceServer;

    public AuthorizationCodeFlowParameterizedTest(String testName, String clientId, String redirectUrl, String state, String nonce) {
        this.testName = testName;
        this.clientId =clientId;
        this.redirectionUrl = redirectUrl;
        this.state = state;
        this.nonce = nonce;
    }

    private final static Object[][] TEST_CASES = new Object[][] {
            // test-name, clientId, redirectionUrl, state, nonce
            { "Auth-code flow", "mock-oidc-client",  "https://mock.client.com/home", "er345agrR3T", "23424234TY"}
    };

    @Parameterized.Parameters(name = "Test {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(TEST_CASES);
    }

    @Before
    public void setup() throws Exception {
        this.testContextManager = new TestContextManager(getClass());
        this.testContextManager.prepareTestInstance(this);
        this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate);
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

    String partnerPrivateKey = "{\n" +
            "\t\"p\": \"0-40ISxXDmC8SVrudg1e7vQskyWlohadm83RAkUyH6S4h1aTPrNwLVn9WANnyRTqupD1Fr8mYZ7f9nZ2MkMj45UV8uiIjQZr3crMq0YGkzt_LvwhLduWOJ_z9_9zZNHckXei4G8QQFJQYb3TNdGsVVSwff68SSoen8oqvkbkAJs\",\n" +
            "\t\"kty\": \"RSA\",\n" +
            "\t\"q\": \"6as88odcbP2MDT9lkahK2z4QIH25zsa_UdLgAtLwDVpekXfJNOQvuqNY1Gw3Jws6uPDLGcEK42MyeOdCFqklFTvDJlJXMFvgWrmGbCUMvJL-rFyO-kCTGnFBX60ozdJbjfBt3E3QYx3G907Ziuu9o0azey1DJtq_zKwearE-xTs\",\n" +
            "\t\"d\": \"BgdeiCZbr5qZ4haShg9uQinZRYPSUTYc_58YgvQ0WkPKm5fINOgOJPvimdKYBt8OtIWbhojTyn0TKrGPPAqFZCnGY16HkCUN31MbluD2wxYz6SPpZ1zsmP8PbQUVozjEFeLpiTN6nubw_skS_9GGrl1CPb25wTPlZtI3uQ5IiPL_YD5j_w5_J7tejAaRbhlJj48ZDa4CR8BkaUi2QaQmLoyiO_1O-U-Nf17-t1C6zFFKKHQx2lNltE1xFQoHB4WuBA2GnP5LgNFJSLv0p95gQK37nP0TTcuiZVlvFcmbGI_ilWlxRKJUD3mZR6nz25X4SapUWswnrnm7JtUA_UGVGw\",\n" +
            "\t\"e\": \"AQAB\",\n" +
            "\t\"use\": \"sig\",\n" +
            "\t\"kid\": \"1bbdc9de-c24f-4801-b6b3-691ac07641af\",\n" +
            "\t\"qi\": \"pmL_G7T4OF_pr2RCzkkupi1dCbwRX39bMEIs3uirvkoPR5CENvuvsXQ0Oias3taxzLa4nG5JVXHkyOIX8UsK1NFrzZPRKbfNX3h5EAnl3I7cZMtoYJLnawUqaNTukOmDChPlKx1fVjUwsyNn5HSAnmBiaOmm_RHo36tPhgaPUtE\",\n" +
            "\t\"dp\": \"e3b2X60ZOoMYrhOPgK7hc4xEu6TfDcLnJvGMpinxvYWVCyNgvNKEs6cNdMznFbpd1TrFze6mSZDpIQh6a2W57sfX9Z-Kjb4D8T5IZi9xfSzYN2MjYTfgGDT3SK9FZqLsQMLV3LJXYWGS-p5AAcaZA01HVN-miWlEVgrNQ_TAt6k\",\n" +
            "\t\"dq\": \"Yg-BqUoTCI4y6xBS4JieqXlXLTt18YfInF8BsU2yffgRvbxmTPMB8LJCQgsT7iexQhGTOkCgACMN-F0ciAP90vZchEWD34B_G7PF7LZzrOOHSvAg9HaLBUrII424lP-VenCOuihRrna9m-WUN8-MquutwKCTEMg2O39z2FR_wic\",\n" +
            "\t\"n\": \"wXGQA574CU-WTWPILd4S3_1sJf0Yof0kwMeNctXc1thQo70Ljfn9f4igpRe7f8qNs_W6dLuLWemFhGJBQBQ7vvickECKNJfo_EzSD_yyPCg7k_AGbTWTkuoObHrpilwJGyKVSkOIujH_FqHIVkwkVXjWc25Lsb8Gq4nAHNQEqqgaYPLEi5evCR6S0FzcXTPuRh9zH-cM0Onjv4orrfYpEr61HcRp5MXL55b7yBoIYlXD8NfalcgdrWzp4VZHvQ8yT9G5eaf27XUn6ZBeBf7VnELcKFTyw1pK2wqoOxRBc8Y1wO6rEy8PlCU6wD-mbIzcjG1wUfnbgvJOM4A5G41quQ\"\n" +
            "}";

    @Test
    public void authorizationCodeFlowTest() throws Exception {
        String code = null;

        createOIDCClient(clientId, clientJWK.toPublicJWK());
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
        decrypt.setKey(RSAKey.parse(partnerPrivateKey).toPrivateKey());
        decrypt.setCompactSerialization(usertoken);
        String payload = decrypt.getPayload();
        log.info("Successfully fetched user token : {}", payload);
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
                .audience("http://localhost:-1/v1/idp")
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
                        .param("nonce", nonce).param("state", state)
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

    private void createOIDCClient(String clientId, JWK publicJWK) throws Exception {
        ClientDetailCreateRequest createRequest = new ClientDetailCreateRequest();
        createRequest.setClientName("Mock OIDC Client");
        createRequest.setClientId(clientId);
        createRequest.setRelyingPartyId("mock-relying-party-id");
        createRequest.setPublicKey(publicJWK.toJSONObject());
        createRequest.setLogoUri("https://mock.client.com/logo.png");
        createRequest.setGrantTypes(Arrays.asList("authorization_code"));
        createRequest.setClientAuthMethods(Arrays.asList("private_key_jwt"));
        createRequest.setAuthContextRefs(Arrays.asList("mosip:idp:acr:static-code"));
        createRequest.setRedirectUris(Arrays.asList("https://mock.client.com/home", "https://mock.client.com/dashboard"));
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
