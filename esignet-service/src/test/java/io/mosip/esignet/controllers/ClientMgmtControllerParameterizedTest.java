/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.TestUtil;
import io.mosip.esignet.core.dto.ClientDetailCreateRequest;
import io.mosip.esignet.core.dto.ClientDetailUpdateRequest;
import io.mosip.esignet.core.dto.RequestWrapper;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.services.ClientManagementServiceImpl;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.InjectMocks;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static io.mosip.esignet.core.constants.Constants.UTC_DATETIME_PATTERN;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(Parameterized.class)
@SpringBootTest
@AutoConfigureMockMvc
public class ClientMgmtControllerParameterizedTest {

    @ClassRule
    public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();

    @Rule
    public final SpringMethodRule springMethodRule = new SpringMethodRule();

    @Autowired
    private WebApplicationContext wac;

    @InjectMocks
    ClientManagementServiceImpl clientManagementService;

    @Value("${mosip.esignet.amr-acr-mapping-file-url}")
    private String mappingFileUrl;

    @Autowired
    RestTemplate  restTemplate;

    private MockMvc mockMvc;

    private TestContextManager testContextManager;

    ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer mockRestServiceServer;

    private static Map<String, Object> jwk = TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject();

    private ClientDetailCreateRequest clientDetailCreateRequest;
    private ClientDetailUpdateRequest clientDetailUpdateRequest;
    private String clientIdQueryParam;
    private String errorCode;
    private String title;

    public ClientMgmtControllerParameterizedTest(String title, ClientDetailCreateRequest clientDetailCreateRequest,
                                                 ClientDetailUpdateRequest clientDetailUpdateRequest,
                                                 String clientIdQueryParam,
                                                 String errorCode) {
        this.title = title;
        this.clientDetailCreateRequest = clientDetailCreateRequest;
        this.clientDetailUpdateRequest = clientDetailUpdateRequest;
        this.clientIdQueryParam = clientIdQueryParam;
        this.errorCode = errorCode;
    }

    private final static Object[][] TEST_CASES = new Object[][] {
            // test-name, ClientDetailCreateRequest, ClientDetailUpdateRequest, clientIdQueryParam, errorCode
            { "Successful create", new ClientDetailCreateRequest("client-id-v1", "client-name", jwk,
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")),  null, null, null },
            { "With Null ClientId", new ClientDetailCreateRequest(null, "client-name", jwk,
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")),  null, null, ErrorConstants.INVALID_CLIENT_ID },
            { "With Empty ClientName", new ClientDetailCreateRequest("client-id", " ", jwk,
                     "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")), null, null,  ErrorConstants.INVALID_CLIENT_NAME },
            { "With Invalid public key", new ClientDetailCreateRequest("client-id", "Test client", new HashMap<>(),
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")), null, null,  ErrorConstants.INVALID_PUBLIC_KEY },
            { "With null public key", new ClientDetailCreateRequest("client-id", "Test client", null,
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")), null, null,  ErrorConstants.INVALID_PUBLIC_KEY },
            { "With null relying party id", new ClientDetailCreateRequest("client-id", "Test client", jwk,
                    null, Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")), null, null,  ErrorConstants.INVALID_RP_ID },
            { "With empty relying party id", new ClientDetailCreateRequest("client-id", "Test client", jwk,
                    "  ", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")), null, null,  ErrorConstants.INVALID_RP_ID },
            { "With null user claims", new ClientDetailCreateRequest("client-id", "Test client", jwk,
                    "rp-id", null,
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")), null, null,  ErrorConstants.INVALID_CLAIM },
            { "With empty user claims", new ClientDetailCreateRequest("client-id", "Test client",
                    TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject(),
                    "rp-id", Arrays.asList(),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")), null, null, null},
            { "With invalid user claims", new ClientDetailCreateRequest("client-id", "Test client", jwk,
                    "rp-id", Arrays.asList(null, "given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")), null, null,  ErrorConstants.INVALID_CLAIM },
            { "With valid & invalid user claims", new ClientDetailCreateRequest("client-id", "Test client", jwk,
                    "rp-id", Arrays.asList("birthdate", "given_name", "gender", "street"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")), null, null,  ErrorConstants.INVALID_CLAIM },
            { "With invalid acr", new ClientDetailCreateRequest("client-id-v2", "Test client", jwk,
                    "rp-id", Arrays.asList("birthdate", "given_name", "gender"),
                    Arrays.asList("mosip:idp:acr:static-code-1"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")), null, null, ErrorConstants.INVALID_ACR },
            { "With patterned redirectUri", new ClientDetailCreateRequest("client-id", "Test client", jwk,
                    "rp-id", Arrays.asList("birthdate", "given_name", "gender"),
                    Arrays.asList("mosip:idp:acr:static-code-1"), "https://logo-url/png",
                    Arrays.asList("https://dev.mosip.net/home/**"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")), null, null, ErrorConstants.INVALID_ACR },
            { "ClientId with spaces", new ClientDetailCreateRequest("client id", "client-name", jwk,
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")),  null, null, ErrorConstants.INVALID_CLIENT_ID },
            { "RP-Id with spaces", new ClientDetailCreateRequest("cid#1", "client-name", jwk,
                    "rp id  1", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt")),  null, null, ErrorConstants.INVALID_RP_ID }

    };

    @Parameterized.Parameters(name = "Test {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(TEST_CASES);
    }

    @Before
    public void setup() throws Exception {
        this.testContextManager = new TestContextManager(getClass());
        this.testContextManager.prepareTestInstance(this);
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();

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
    public void testClientManagementEndpoints() throws Exception {
        if(this.clientDetailCreateRequest != null) {
            ResultActions createResultActions = mockMvc.perform(post("/client-mgmt/oidc-client")
                            .contentType(MediaType.APPLICATION_JSON_UTF8)
                            .content(getRequestWrapper(this.clientDetailCreateRequest)));
            evaluateResultActions(createResultActions, this.clientDetailCreateRequest.getClientId(),
                    Constants.CLIENT_ACTIVE_STATUS, this.errorCode);
        }

        if(this.clientDetailUpdateRequest != null) {
           ResultActions updateResultActions = mockMvc.perform(post("/client-mgmt/oidc-client/"+this.clientIdQueryParam)
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .content(getRequestWrapper(this.clientDetailUpdateRequest)));
            evaluateResultActions(updateResultActions, this.clientIdQueryParam,
                    this.clientDetailUpdateRequest.getStatus(), this.errorCode);
        }
    }

    private String getRequestWrapper(Object request) throws JsonProcessingException {
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequest(request);
        wrapper.setRequestTime(ZonedDateTime
                .now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        return objectMapper.writeValueAsString(wrapper);
    }

    private void evaluateResultActions(ResultActions resultActions, String clientId, String status, String errorCode)
            throws Exception {
        if(errorCode != null) {
            resultActions.andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors").isNotEmpty())
                    .andExpect(jsonPath("$.errors[0].errorCode").value(this.errorCode));
        }
        else {
            resultActions.andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors").isEmpty())
                    .andExpect(jsonPath("$.response").isNotEmpty())
                    .andExpect(jsonPath("$.response.clientId").value(this.clientDetailCreateRequest.getClientId()))
                    .andExpect(jsonPath("$.response.status").value(Constants.CLIENT_ACTIVE_STATUS));
        }
    }
}
