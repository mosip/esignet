/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.TestUtil;
import io.mosip.esignet.config.SecurityConfig;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.services.ClientManagementServiceImpl;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import static io.mosip.esignet.core.constants.Constants.UTC_DATETIME_PATTERN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@EnableAutoConfiguration(exclude = {ManagementWebSecurityAutoConfiguration.class})
@AutoConfigureMockMvc
public class ClientMgmtControllerParameterizedTest {

    @Autowired
    MockMvc mockMvc;

    ObjectMapper objectMapper = new ObjectMapper();

    private static Map<String, Object> jwk = TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject();

    @AllArgsConstructor
    public static class TestCase {
        String title;
        ClientDetailCreateRequestV2 clientDetailCreateRequestV2;
        ClientDetailUpdateRequestV2 clientDetailUpdateRequestV2;
        String clientIdQueryParam;
        String errorCode;
    }

    private static Stream<TestCase> getTestCases() {
        List<TestCase> TEST_CASES = new ArrayList<>(Arrays.asList(
                new TestCase("Successful create", new ClientDetailCreateRequestV2("client-id-v1", "client-name", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, null),
                new TestCase("With Null ClientId", new ClientDetailCreateRequestV2(null, "client-name", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_CLIENT_ID),
                new TestCase("With Empty ClientName", new ClientDetailCreateRequestV2("client-id", " ", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_CLIENT_NAME),
                new TestCase("With Invalid Language_code", new ClientDetailCreateRequestV2("client-id", "clientname", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("abc", "clientname");
                }}), null, null, ErrorConstants.INVALID_CLIENT_NAME_MAP_KEY),
                new TestCase("With Invalid public key", new ClientDetailCreateRequestV2("client-id", "Test client", new HashMap<>(),
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_PUBLIC_KEY),
                new TestCase("With null public key", new ClientDetailCreateRequestV2("client-id", "Test client", null,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_PUBLIC_KEY),
                new TestCase("With null relying party id", new ClientDetailCreateRequestV2("client-id", "Test client", jwk,
                        null, Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_RP_ID),
                new TestCase("With empty relying party id", new ClientDetailCreateRequestV2("client-id", "Test client", jwk,
                        "  ", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_RP_ID),
                new TestCase("With null user claims", new ClientDetailCreateRequestV2("client-id", "Test client", jwk,
                        "rp-id", null,
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_CLAIM),
                new TestCase("With empty user claims", new ClientDetailCreateRequestV2("client-id", "Test client",
                        TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject(),
                        "rp-id", Arrays.asList(),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, null),
                new TestCase("With invalid user claims", new ClientDetailCreateRequestV2("client-id", "Test client", jwk,
                        "rp-id", Arrays.asList(null, "given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_CLAIM),
                new TestCase("With valid & invalid user claims", new ClientDetailCreateRequestV2("client-id", "Test client", jwk,
                        "rp-id", Arrays.asList("birthdate", "given_name", "gender", "street"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_CLAIM),
                new TestCase("With invalid acr", new ClientDetailCreateRequestV2("client-id-v2", "Test client", jwk,
                        "rp-id", Arrays.asList("birthdate", "given_name", "gender"),
                        Arrays.asList("mosip:idp:acr:static-code-1"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_ACR),
                new TestCase("With patterned redirectUri", new ClientDetailCreateRequestV2("client-id", "Test client", jwk,
                        "rp-id", Arrays.asList("birthdate", "given_name", "gender"),
                        Arrays.asList("mosip:idp:acr:static-code-1"), "https://logo-url/png",
                        Arrays.asList("https://dev.mosip.net/home/**"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_ACR),
                new TestCase("ClientId with spaces", new ClientDetailCreateRequestV2("client id", "client-name", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_CLIENT_ID),
                new TestCase("RP-Id with spaces", new ClientDetailCreateRequestV2("cid#1", "client-name", jwk,
                        "rp id  1", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, ErrorConstants.INVALID_RP_ID),
                new TestCase("with duplicate key", new ClientDetailCreateRequestV2("client-id-v34", "client-name", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, "unknown_error"),
                new TestCase("update with invalid clientId", null, new ClientDetailUpdateRequestV2("https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "ACTIVE", Arrays.asList("authorization_code"),
                        "client-name#1", Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), "cid#1", "invalid_client_id"),
                new TestCase("update with invalid language_code", null, new ClientDetailUpdateRequestV2("https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "ACTIVE", Arrays.asList("authorization_code"),
                        "client-name", Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("abc", "clientname");
                }}), "cid#1", "invalid_language_code"),
                new TestCase("update client-details", new ClientDetailCreateRequestV2("client-id-up1", "client-name",
                        TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject(),
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), new ClientDetailUpdateRequestV2("https://logo-url/png",
                        Arrays.asList("https://logo-url/png", "io.mosip.residentapp://oauth"), Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "ACTIVE", Arrays.asList("authorization_code"),
                        "client-name#1", Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), "client-id-up1", null),
                new TestCase("Create with app redirect URL", new ClientDetailCreateRequestV2("client-id3", "client-name",
                        TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject(),
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("io.mosip.residentapp://oauth", "residentapp://oauth/*"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}), null, null, null)
        ));
        return TEST_CASES.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getTestCases")
    public void testClientManagementEndpoints(TestCase testCase) throws Exception {
        if(testCase.clientDetailCreateRequestV2 != null) {
            ResultActions createResultActions = mockMvc.perform(post("/client-mgmt/oauth-client")
                            .contentType(MediaType.APPLICATION_JSON_UTF8)
                            .content(getRequestWrapper(testCase.clientDetailCreateRequestV2)));
            evaluateResultActions(createResultActions, testCase.clientDetailCreateRequestV2.getClientId(),
                    Constants.CLIENT_ACTIVE_STATUS, testCase.errorCode);
        }

        if(testCase.clientDetailUpdateRequestV2 != null) {
           ResultActions updateResultActions = mockMvc.perform(put("/client-mgmt/oauth-client/"+testCase.clientIdQueryParam)
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .content(getRequestWrapper(testCase.clientDetailUpdateRequestV2)));
            evaluateResultActions(updateResultActions, testCase.clientIdQueryParam,
                    testCase.clientDetailUpdateRequestV2.getStatus(), testCase.errorCode);
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
                    .andExpect(jsonPath("$.errors[0].errorCode").value(errorCode));
        }
        else {
            resultActions.andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors").isEmpty())
                    .andExpect(jsonPath("$.response").isNotEmpty())
                    .andExpect(jsonPath("$.response.clientId").value(clientId))
                    .andExpect(jsonPath("$.response.status").value(status));
        }
    }
}
