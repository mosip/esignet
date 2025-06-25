package io.mosip.esignet.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.esignet.TestUtil;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.ClientDetailCreateRequestV3;
import io.mosip.esignet.core.dto.ClientDetailUpdateRequestV3;
import io.mosip.esignet.core.dto.RequestWrapper;
import lombok.AllArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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
@AutoConfigureMockMvc
public class ClientMgmtV2ControllerParameterizedTest {

    private static Map<String, Object> jwk = TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject();

    @Autowired
    private MockMvc mockMvc;

    private static ObjectMapper mapper = new ObjectMapper();

    @AllArgsConstructor
    public static class TestCase {
        String title;
        ClientDetailCreateRequestV3 clientDetailCreateRequestV3;
        ClientDetailUpdateRequestV3 clientDetailUpdateRequestV3;
        String clientIdQueryParam;
        String errorCode;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getTestCases")
    public void testClientManagementEndpoints(TestCase testCase) throws Exception {
        ClientDetailCreateRequestV3 clientDetailCreateRequestV3 = testCase.clientDetailCreateRequestV3;
        ClientDetailUpdateRequestV3 clientDetailUpdateRequestV3 = testCase.clientDetailUpdateRequestV3;
        String clientIdQueryParam = testCase.clientIdQueryParam;
        String errorCode = testCase.errorCode;

        if (clientDetailCreateRequestV3 != null) {
            ResultActions createResultActions = mockMvc.perform(post("/client-mgmt/client")
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .content(getRequestWrapper(clientDetailCreateRequestV3)));
            evaluateResultActions(createResultActions, clientDetailCreateRequestV3.getClientId(),
                    Constants.CLIENT_ACTIVE_STATUS, errorCode);
        }

        if (clientDetailUpdateRequestV3 != null) {
            ResultActions updateResultActions = mockMvc.perform(put("/client-mgmt/client/" + clientIdQueryParam)
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .content(getRequestWrapper(clientDetailUpdateRequestV3)));
            evaluateResultActions(updateResultActions, clientIdQueryParam,
                    clientDetailUpdateRequestV3.getStatus(), errorCode);
        }
    }

    private static Stream<TestCase> getTestCases() {
        List<JsonNode> validAdditionalConfigs = getValidAdditionalConfigs();
        int i = 0, size = validAdditionalConfigs.size();
        List<TestCase> TEST_CASES = new ArrayList<>(Arrays.asList(
                // test-name, ClientDetailCreateRequest, ClientDetailUpdateRequest, clientIdQueryParam, errorCode
                new TestCase("Successful create", new ClientDetailCreateRequestV3("client-id-#12c", "client-name", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), null, validAdditionalConfigs.get((i++)%size)), null, null, null),
                new TestCase("Invalid client name lang map value", new ClientDetailCreateRequestV3("client-id-#12c-1", "client-name", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "This is a very long client name that definitely exceeds fifty characters in length.");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_CLIENT_NAME_LENGTH),
                new TestCase("Duplicate client id", new ClientDetailCreateRequestV3("client-id-#12c", "client-name", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.DUPLICATE_CLIENT_ID),
                new TestCase("With Null ClientId", new ClientDetailCreateRequestV3(null, "client-name", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_CLIENT_ID),
                new TestCase("With Empty ClientName", new ClientDetailCreateRequestV3("client-id-v2", " ", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_CLIENT_NAME),
                new TestCase("With Invalid Language_code", new ClientDetailCreateRequestV3("client-id-v2", "clientname", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("abc", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_CLIENT_NAME_MAP_KEY),
                new TestCase("With Invalid public key", new ClientDetailCreateRequestV3("client-id-v2", "Test client", new HashMap<>(),
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_PUBLIC_KEY),
                new TestCase("With null public key", new ClientDetailCreateRequestV3("client-id-v2", "Test client", null,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_PUBLIC_KEY),
                new TestCase("With null relying party id", new ClientDetailCreateRequestV3("client-id-v2", "Test client", jwk,
                        null, Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_RP_ID),
                new TestCase("With empty relying party id", new ClientDetailCreateRequestV3("client-id-v2", "Test client", jwk,
                        "  ", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_RP_ID),
                new TestCase("With null user claims", new ClientDetailCreateRequestV3("client-id-v2", "Test client", jwk,
                        "rp-id", null,
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_CLAIM),
                new TestCase("With empty user claims", new ClientDetailCreateRequestV3("client-id-v2#2", "Test client",
                        TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject(),
                        "rp-id", Arrays.asList(),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, null),
                new TestCase("With invalid user claims", new ClientDetailCreateRequestV3("client-id-v2", "Test client", jwk,
                        "rp-id", Arrays.asList(null, "given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_CLAIM),
                new TestCase("With valid & invalid user claims", new ClientDetailCreateRequestV3("client-id-v2", "Test client", jwk,
                        "rp-id", Arrays.asList("birthdate", "given_name", "gender", "street"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_CLAIM),
                new TestCase("With invalid acr", new ClientDetailCreateRequestV3("client-id-v2", "Test client", jwk,
                        "rp-id", Arrays.asList("birthdate", "given_name", "gender"),
                        Arrays.asList("mosip:idp:acr:static-code-1"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_ACR),
                new TestCase("With patterned redirectUri", new ClientDetailCreateRequestV3("client-id-v2#3", "Test client", jwk,
                        "rp-id", Arrays.asList("birthdate", "given_name", "gender"),
                        Arrays.asList("mosip:idp:acr:static-code-1"), "https://logo-url/png",
                        Arrays.asList("https://dev.mosip.net/home/**"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_ACR),
                new TestCase("ClientId with spaces", new ClientDetailCreateRequestV3("client id", "client-name", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_CLIENT_ID),
                new TestCase("RP-Id with spaces", new ClientDetailCreateRequestV3("cid#1", "client-name", jwk,
                        "rp id  1", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, ErrorConstants.INVALID_RP_ID),
                new TestCase("with duplicate key", new ClientDetailCreateRequestV3("client-id-v2#4", "client-name", jwk,
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, "duplicate_public_key"),
                new TestCase("update with invalid clientId", null, new ClientDetailUpdateRequestV3("https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "ACTIVE", Arrays.asList("authorization_code"),
                        "client-name#1", Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), "cid#1", "invalid_client_id"),
                new TestCase("update with invalid language_code", null, new ClientDetailUpdateRequestV3("https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "ACTIVE", Arrays.asList("authorization_code"),
                        "client-name", Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("abc", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), "cid#1", "invalid_language_code"),
                new TestCase("update with invalid client name lang map value", null, new ClientDetailUpdateRequestV3("https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "ACTIVE", Arrays.asList("authorization_code"),
                        "client-name", Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "This is a very long client name that definitely exceeds fifty characters in length.");
                }}, validAdditionalConfigs.get((i++)%size)), "cid#1", ErrorConstants.INVALID_CLIENT_NAME_LENGTH),
                new TestCase("update client-details", new ClientDetailCreateRequestV3("client-id-up2", "client-name",
                        TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject(),
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), new ClientDetailUpdateRequestV3("https://logo-url/png",
                        Arrays.asList("https://logo-url/png", "io.mosip.residentapp://oauth"), Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "ACTIVE", Arrays.asList("authorization_code"),
                        "client-name#1", Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), "client-id-up2", null),
                new TestCase("Create with app redirect URL", new ClientDetailCreateRequestV3("client-id-v2", "client-name",
                        TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject(),
                        "rp-id", Arrays.asList("given_name"),
                        Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                        Arrays.asList("io.mosip.residentapp://oauth", "residentapp://oauth/*"), Arrays.asList("authorization_code"),
                        Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                    put("eng", "clientname");
                }}, validAdditionalConfigs.get((i++)%size)), null, null, null)
        ));
        List<JsonNode> invalidAdditionalConfigs = getInvalidAdditionalConfigs();
        for (JsonNode additionalConfig : invalidAdditionalConfigs) {
            TEST_CASES.add(
                    new TestCase("with invalid additional config", new ClientDetailCreateRequestV3("client-id-v2", "client-name", jwk,
                            "rp-id", Arrays.asList("given_name"),
                            Arrays.asList("mosip:idp:acr:static-code"), "https://logo-url/png",
                            Arrays.asList("https://logo-url/png"), Arrays.asList("authorization_code"),
                            Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                        put("eng", "clientname");
                    }},
                            additionalConfig), null, null, ErrorConstants.INVALID_ADDITIONAL_CONFIG)
            );
            TEST_CASES.add(
                    new TestCase("update with invalid additional config",
                            null,
                            new ClientDetailUpdateRequestV3("https://logo-url/png",
                                    Arrays.asList("https://logo-url/png", "io.mosip.residentapp://oauth"), Arrays.asList("given_name"),
                                    Arrays.asList("mosip:idp:acr:static-code"), "ACTIVE", Arrays.asList("authorization_code"),
                                    "client-name#1", Arrays.asList("private_key_jwt"), new HashMap<String, String>() {{
                                put("eng", "clientname");
                            }}, additionalConfig), "client-id-up2", ErrorConstants.INVALID_ADDITIONAL_CONFIG)
            );
        }
        return TEST_CASES.stream();
    }

    private String getRequestWrapper(Object request) throws JsonProcessingException {
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequest(request);
        wrapper.setRequestTime(ZonedDateTime
                .now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        return mapper.writeValueAsString(wrapper);
    }

    private void evaluateResultActions(ResultActions resultActions, String clientId, String status, String errorCode)
            throws Exception {
        if (errorCode != null) {
            resultActions.andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors").isNotEmpty())
                    .andExpect(jsonPath("$.errors[0].errorCode").value(errorCode));
        } else {
            resultActions.andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors").isEmpty())
                    .andExpect(jsonPath("$.response").isNotEmpty())
                    .andExpect(jsonPath("$.response.clientId").value(clientId))
                    .andExpect(jsonPath("$.response.status").value(Constants.CLIENT_ACTIVE_STATUS));
        }
    }

    public static List<JsonNode> getValidAdditionalConfigs() {
        List<JsonNode> configList = new ArrayList<>();

        configList.add(null);

        ObjectNode validConfig = mapper.createObjectNode();
        validConfig.put("userinfo_response_type", "JWS");
        validConfig.set("purpose", mapper.valueToTree(Map.ofEntries(
                Map.entry("type", "verify"),
                Map.entry("title", Map.ofEntries(
                        Map.entry("@none", "title")
                )),
                Map.entry("subTitle", Map.ofEntries(
                        Map.entry("@none", "subTitle")
                ))
        )));
        validConfig.put("signup_banner_required", true);
        validConfig.put("forgot_pwd_link_required", true);
        validConfig.put("consent_expire_in_mins", 10);
        configList.add(validConfig);

        ObjectNode config = validConfig.deepCopy();
        config.remove("purpose");  // config without purpose
        configList.add(config);

        config = validConfig.deepCopy();
        ((ObjectNode) config.get("purpose")).remove(List.of("title", "subTitle"));  // purpose without title and subTitle
        configList.add(config);

        config = validConfig.deepCopy();
        ((ObjectNode) config.get("purpose")).set("title", mapper.valueToTree(Map.ofEntries(
                Map.entry("@none", "title"),
                Map.entry("eng", "title")  // title in other language
        )));
        configList.add(config);

        return configList;
    }

    public static List<JsonNode> getInvalidAdditionalConfigs() {
        List<JsonNode> configList = new ArrayList<>();

        ObjectNode validConfig = mapper.createObjectNode();
        validConfig.put("userinfo_response_type", "JWS");
        validConfig.set("purpose", mapper.valueToTree(Map.ofEntries(
                Map.entry("type", "verify"),
                Map.entry("title", Map.ofEntries(
                        Map.entry("@none", "title")
                )),
                Map.entry("subTitle", Map.ofEntries(
                        Map.entry("@none", "subTitle")
                ))
        )));
        validConfig.put("signup_banner_required", true);
        validConfig.put("forgot_pwd_link_required", true);
        validConfig.put("consent_expire_in_mins", 10);

        ObjectNode config = validConfig.deepCopy();
        config.put("userinfo_response_type", "ABC");  // invalid userinfo
        configList.add(config);

        config = validConfig.deepCopy();
        config.set("purpose", mapper.valueToTree(Map.ofEntries(   // purpose without type field
                Map.entry("title", Map.ofEntries(
                        Map.entry("@none", "title")
                ))
        )));
        configList.add(config);

        config = validConfig.deepCopy();
        config.set("purpose", mapper.valueToTree(Map.ofEntries(
                Map.entry("type", "dummy"),   // invalid purpose type
                Map.entry("title", "")
        )));
        configList.add(config);

        config = validConfig.deepCopy();
        config.set("purpose", mapper.valueToTree(Map.ofEntries(
                Map.entry("type", "verify"),
                Map.entry("title", Map.ofEntries(Map.entry("eng", "title in english"))) // title without @none key
        )));
        configList.add(config);

        config = validConfig.deepCopy();
        config.set("purpose", mapper.valueToTree(Map.ofEntries(
                Map.entry("type", "verify"),
                Map.entry("title", Map.ofEntries(Map.entry("@none", "title in default"), Map.entry("eng", "title in english"))),
                Map.entry("subTitle", Map.ofEntries(Map.entry("fr", "subTitle in french"), Map.entry("eng", "title in english"))) // subTitle without @none key
        )));
        configList.add(config);

        config = validConfig.deepCopy();
        config.put("signup_banner_required", 1); // anything other than boolean
        configList.add(config);

        config = validConfig.deepCopy();
        config.put("forgot_pwd_link_required", 1); // anything other than boolean
        configList.add(config);

        config = validConfig.deepCopy();
        config.put("consent_expire_in_mins", ""); // anything other than number
        configList.add(config);

        config = validConfig.deepCopy();
        config.put("consent_expire_in_mins", 5); // consent less than 10 mins
        configList.add(config);

        return configList;
    }
}