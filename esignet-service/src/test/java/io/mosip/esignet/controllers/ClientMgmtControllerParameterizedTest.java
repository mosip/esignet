/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.TestUtil;
import io.mosip.esignet.core.dto.*;
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
import java.util.*;

import static io.mosip.esignet.core.constants.Constants.UTC_DATETIME_PATTERN;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    private ClientDetailCreateV2Request clientDetailCreateV2Request;
    private ClientDetailUpdateV2Request clientDetailUpdateV2Request;
    private String clientIdQueryParam;
    private String errorCode;
    private String title;

    public ClientMgmtControllerParameterizedTest(String title, ClientDetailCreateV2Request clientDetailCreateV2Request,
                                                 ClientDetailUpdateV2Request clientDetailUpdateV2Request,
                                                 String clientIdQueryParam,
                                                 String errorCode) {
        this.title = title;
        this.clientDetailCreateV2Request = clientDetailCreateV2Request;
        this.clientDetailUpdateV2Request = clientDetailUpdateV2Request;
        this.clientIdQueryParam = clientIdQueryParam;
        this.errorCode = errorCode;
    }

    private final static Object[][] TEST_CASES = new Object[][] {
            // test-name, ClientDetailCreateRequest, ClientDetailUpdateRequest, clientIdQueryParam, errorCode
            new Object[]{"Successful create", new ClientDetailCreateV2Request("client-id-v1", "client-name", jwk,
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null, null},
            { "With Null ClientId", new ClientDetailCreateV2Request(null, "client-name", jwk,
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}),  null, null, ErrorConstants.INVALID_CLIENT_ID },
            { "With Empty ClientName", new ClientDetailCreateV2Request("client-id", " ", jwk,
                     "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null,  ErrorConstants.INVALID_CLIENT_NAME },
            { "With Invalid public key", new ClientDetailCreateV2Request("client-id", "Test client", new HashMap<>(),
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null,  ErrorConstants.INVALID_PUBLIC_KEY },
            { "With null public key", new ClientDetailCreateV2Request("client-id", "Test client", null,
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null,  ErrorConstants.INVALID_PUBLIC_KEY },
            { "With null relying party id", new ClientDetailCreateV2Request("client-id", "Test client", jwk,
                    null, Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null,  ErrorConstants.INVALID_RP_ID },
            { "With empty relying party id", new ClientDetailCreateV2Request("client-id", "Test client", jwk,
                    "  ", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null,  ErrorConstants.INVALID_RP_ID },
            { "With null user claims", new ClientDetailCreateV2Request("client-id", "Test client", jwk,
                    "rp-id", null,
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null,  ErrorConstants.INVALID_CLAIM },
            { "With empty user claims", new ClientDetailCreateV2Request("client-id", "Test client",
                    TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject(),
                    "rp-id", Arrays.asList(),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null, null},
            { "With invalid user claims", new ClientDetailCreateV2Request("client-id", "Test client", jwk,
                    "rp-id", Arrays.asList(null, "given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null,  ErrorConstants.INVALID_CLAIM },
            { "With valid & invalid user claims", new ClientDetailCreateV2Request("client-id", "Test client", jwk,
                    "rp-id", Arrays.asList("birthdate", "given_name", "gender", "street"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null,  ErrorConstants.INVALID_CLAIM },
            { "With invalid acr", new ClientDetailCreateV2Request("client-id-v2", "Test client", jwk,
                    "rp-id", Arrays.asList("birthdate", "given_name", "gender"),
                    Arrays.asList("mosip:idp:acr:static-code-1"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null, ErrorConstants.INVALID_ACR },
            { "With patterned redirectUri", new ClientDetailCreateV2Request("client-id", "Test client", jwk,
                    "rp-id", Arrays.asList("birthdate", "given_name", "gender"),
                    Arrays.asList("mosip:idp:acr:static-code-1"), "https://logo-url/png",
                    Arrays.asList("https://dev.mosip.net/home/**"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), null, null, ErrorConstants.INVALID_ACR },
            { "ClientId with spaces", new ClientDetailCreateV2Request("client id", "client-name", jwk,
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}),  null, null, ErrorConstants.INVALID_CLIENT_ID },
            { "RP-Id with spaces", new ClientDetailCreateV2Request("cid#1", "client-name", jwk,
                    "rp id  1", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}),  null, null, ErrorConstants.INVALID_RP_ID },
            { "with duplicate key", new ClientDetailCreateV2Request("client-id-v34", "client-name", jwk,
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}),  null, null, "unknown_error" },
            { "update with invalid clientId", null,  new ClientDetailUpdateV2Request("https://logo-url/png",
                    Arrays.asList("https://logo-url/png"),Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "ACTIVE", Arrays.asList("authorization_code"),
                    "client-name#1", Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), "cid#1", "invalid_client_id" },
            { "update client-details", new ClientDetailCreateV2Request("client-id-up1", "client-name",
                    TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject(),
                    "rp-id", Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                    Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                    Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}),  new ClientDetailUpdateV2Request("https://logo-url/png",
                    Arrays.asList("https://logo-url/png"),Arrays.asList("given_name"),
                    Arrays.asList("mosip:idp:acr:static-code"), "ACTIVE", Arrays.asList("authorization_code"),
                    "client-name#1", Arrays.asList("private_key_jwt"),new HashMap<String,String>(){{put("eng", "clientname");}}), "client-id-up1",  null }

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
        if(this.clientDetailCreateV2Request != null) {
            ResultActions createResultActions = mockMvc.perform(post("/client-mgmt/v2/oidc-client")
                            .contentType(MediaType.APPLICATION_JSON_UTF8)
                            .content(getRequestWrapper(this.clientDetailCreateV2Request)));
            evaluateResultActions(createResultActions, this.clientDetailCreateV2Request.getClientId(),
                    Constants.CLIENT_ACTIVE_STATUS, this.errorCode);
        }

        if(this.clientDetailUpdateV2Request != null) {
           ResultActions updateResultActions = mockMvc.perform(put("/client-mgmt/v2/oidc-client/"+this.clientIdQueryParam)
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .content(getRequestWrapper(this.clientDetailUpdateV2Request)));
            evaluateResultActions(updateResultActions, this.clientIdQueryParam,
                    this.clientDetailUpdateV2Request.getStatus(), this.errorCode);
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
                    .andExpect(jsonPath("$.response.clientId").value(this.clientDetailCreateV2Request.getClientId()))
                    .andExpect(jsonPath("$.response.status").value(Constants.CLIENT_ACTIVE_STATUS));
        }
    }
}
