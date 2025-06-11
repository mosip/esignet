/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.claim.ClaimDetail;
import io.mosip.esignet.api.dto.claim.ClaimsV2;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.core.config.LocalAuthenticationEntryPoint;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.AuthorizationService;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.AuthorizationHelperService;
import io.mosip.esignet.services.CacheUtilService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.servlet.http.HttpServletResponse;

import static io.mosip.esignet.api.util.ErrorConstants.INVALID_AUTH_FACTOR_TYPE_FORMAT;
import static io.mosip.esignet.api.util.ErrorConstants.INVALID_CHALLENGE_LENGTH;
import static io.mosip.esignet.core.constants.Constants.UTC_DATETIME_PATTERN;
import static io.mosip.esignet.core.constants.ErrorConstants.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(value = AuthorizationController.class)
public class AuthorizationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthorizationService authorizationService;

    @MockBean
    AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @MockBean
    AuthorizationHelperService authorizationHelperService;

    @MockBean
    AuditPlugin auditWrapper;

    @MockBean
    CacheUtilService cacheUtilService;


    @MockBean
    RestTemplate restTemplate;

    @MockBean
    LocalAuthenticationEntryPoint localAuthenticationEntryPoint;


    @Value("${mosip.esignet.claims.schema.url}")
    private String schemaUrl;

    ObjectMapper objectMapper = new ObjectMapper();

    ClaimDetail claimDetail;

    ClaimsV2 claimsV2;



    @Before
    public void init() throws EsignetException, JsonProcessingException {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("mosip:idp:acr:static-code");
        acrValues.add("mosip:idp:acr:biometrics");
        acrValues.add("mosip:idp:acr:linked-wallet");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        String address="{\"essential\":true}";
        String verifiedClaims="[{\"verification\":{\"trust_framework\":{\"value\":\"pwd\"}},\"claims\":{\"name\":null,\"email\":{\"essential\":1}}},{\"verification\":{\"trust_framework\":{\"value\":\"pwd\"}},\"claims\":{\"birthdate\":{\"essential\":true},\"address\":null}},{\"verification\":{\"trust_framework\":{\"value\":\"kaif\"}},\"claims\":{\"gender\":{\"essential\":true},\"email\":{\"essential\":true}}}]";

        JsonNode addressNode = objectMapper.readValue(address, JsonNode.class);
        JsonNode verifiedClaimNode = objectMapper.readValue(verifiedClaims, JsonNode.class);

        Map<String, JsonNode> userinfoMap = new HashMap<>();
        userinfoMap.put("address", addressNode);
        userinfoMap.put("verified_claims", verifiedClaimNode);
        Map<String, ClaimDetail> idTokenMap = new HashMap<>();


        claimDetail = new ClaimDetail("claim_value", null, true, "secondary");

        idTokenMap.put("some_claim", claimDetail);
        ClaimsV2 claimsV2 = new ClaimsV2();
        claimsV2.setUserinfo(userinfoMap);
        claimsV2.setId_token(idTokenMap);

    }


    @Test
    public void getOauthDetails_withInvalidTimestamp_returnErrorResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");

        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");

        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        requestTime = requestTime.plusMinutes(10);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);
        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_REQUEST))
                .andExpect(jsonPath("$.errors[0].errorMessage").value("requestTime: invalid_request"));
    }

    @Test
    public void getOauthDetails_withInvalidRedirectUri_returnErrorResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri(" ");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_REDIRECT_URI));
    }

    @Test
    public void getOauthDetails_withInvalidAcr_returnSuccessResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code level2");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);


        OAuthDetailResponseV1 oauthDetailResponse = new OAuthDetailResponseV1();
        oauthDetailResponse.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetails(oauthDetailRequest)).thenReturn(oauthDetailResponse);

        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetails_withInvalidDisplay_returnErrorResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("none");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_DISPLAY));
    }

    @Test
    public void getOauthDetails_withInvalidPrompt_returnErrorResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("touch");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_PROMPT));
    }

    @Test
    public void getOauthDetails_withInvalidResponseType_returnErrorResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("none");
        oauthDetailRequest.setResponseType("implicit");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_RESPONSE_TYPE));
    }

    @Test
    public void getOauthDetails_withOnlyOpenIdScope_returnSuccessResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponseV1 oauthDetailResponse = new OAuthDetailResponseV1();
        oauthDetailResponse.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetails(oauthDetailRequest)).thenReturn(oauthDetailResponse);

        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetails_withOutOpenIdScope_returnSuccessResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("profile");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_SCOPE));
    }

    @Test
    public void getOauthDetails_withOpenIdScope_returnSuccessResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("profile openid");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponseV1 oauthDetailResponse = new OAuthDetailResponseV1();
        oauthDetailResponse.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetails(oauthDetailRequest)).thenReturn(oauthDetailResponse);

        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetails_withOnlyAuthorizeScope_returnSuccessResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("resident-service");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponseV1 oauthDetailResponse = new OAuthDetailResponseV1();
        oauthDetailResponse.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetails(oauthDetailRequest)).thenReturn(oauthDetailResponse);

        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetails_withAuthorizeAndOpenIdScope_returnSuccessResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid resident-service");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponseV1 oauthDetailResponse = new OAuthDetailResponseV1();
        oauthDetailResponse.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetails( oauthDetailRequest)).thenReturn(oauthDetailResponse);

        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));

        OAuthDetailResponseV2 oauthDetailResponseV2 = new OAuthDetailResponseV2();
        oauthDetailResponseV2.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetailsV2(Mockito.any())).thenReturn(oauthDetailResponseV2);
        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));

        when(authorizationService.getOauthDetailsV3(Mockito.any(), Mockito.any())).thenReturn(oauthDetailResponseV2);
        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetailsV2_withInvalidTimestamp_returnErrorResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");

        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);

        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        requestTime = requestTime.plusMinutes(10);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);
        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_REQUEST))
                .andExpect(jsonPath("$.errors[0].errorMessage").value("requestTime: invalid_request"));

        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_REQUEST))
                .andExpect(jsonPath("$.errors[0].errorMessage").value("requestTime: invalid_request"));
    }

    @Test
    public void getOauthDetailsV2_withInvalidRedirectUri_returnErrorResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri(" ");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_REDIRECT_URI));

        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_REDIRECT_URI));
    }

    @Test
    public void getOauthDetailsV2_withInvalidAcr_returnSuccessResponse() throws Exception {
        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code level2");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponseV2 oauthDetailResponseV2 = new OAuthDetailResponseV2();
        oauthDetailResponseV2.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetailsV2(oauthDetailRequest)).thenReturn(oauthDetailResponseV2);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));

        when(authorizationService.getOauthDetailsV3(Mockito.any(), Mockito.any())).thenReturn(oauthDetailResponseV2);
        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetailsV2_withInvalidChallengeCode_returnErrorResponse() throws Exception {
        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri(" ");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setCodeChallenge("123");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_PKCE_CHALLENGE));

        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_PKCE_CHALLENGE));
    }

    @Test
    public void getOauthDetailsV2_withUnsupportedChallengeCodeMethod_returnErrorResponse() throws Exception {
        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri(" ");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setCodeChallenge("123");
        oauthDetailRequest.setCodeChallengeMethod("S123");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.UNSUPPORTED_PKCE_CHALLENGE_METHOD));

        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.UNSUPPORTED_PKCE_CHALLENGE_METHOD));
    }


    @Test
    public void getOauthDetailsV2_withInvalidDisplay_returnErrorResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("none");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_DISPLAY));

        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_DISPLAY));
    }

    @Test
    public void getOauthDetailsV2_withInvalidPrompt_returnErrorResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("touch");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_PROMPT));

        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_PROMPT));
    }

    @Test
    public void getOauthDetailsV2_withInvalidResponseType_returnErrorResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("none");
        oauthDetailRequest.setResponseType("implicit");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_RESPONSE_TYPE));

        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_RESPONSE_TYPE));
    }

    @Test
    public void getOauthDetailsV2_withOnlyOpenIdScope_returnSuccessResponse() throws Exception {
        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponseV2 oauthDetailResponseV2 = new OAuthDetailResponseV2();
        oauthDetailResponseV2.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetailsV2(oauthDetailRequest)).thenReturn(oauthDetailResponseV2);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));

        when(authorizationService.getOauthDetailsV3(Mockito.any(), Mockito.any())).thenReturn(oauthDetailResponseV2);
        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetailsV2_withOutOpenIdScope_returnErrorResponse() throws Exception {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("profile");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_SCOPE));

        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_SCOPE));
    }

    @Test
    public void getOauthDetailsV2_withOpenIdScope_returnSuccessResponse() throws Exception {
        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("profile openid");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponseV2 oauthDetailResponseV2 = new OAuthDetailResponseV2();
        oauthDetailResponseV2.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetailsV2(oauthDetailRequest)).thenReturn(oauthDetailResponseV2);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));

        when(authorizationService.getOauthDetailsV3(Mockito.any(), Mockito.any())).thenReturn(oauthDetailResponseV2);
        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetailsV2_withOnlyAuthorizeScope_returnSuccessResponse() throws Exception {
        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("resident-service");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponseV2 oauthDetailResponseV2 = new OAuthDetailResponseV2();
        oauthDetailResponseV2.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetailsV2(oauthDetailRequest)).thenReturn(oauthDetailResponseV2);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));

        when(authorizationService.getOauthDetailsV3(Mockito.any(), Mockito.any())).thenReturn(oauthDetailResponseV2);
        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetailsV2_withAuthorizeAndOpenIdScope_returnSuccessResponse() throws Exception {
        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid resident-service");
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        oauthDetailRequest.setNonce("23424234TY");
        oauthDetailRequest.setClaims(claimsV2);
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponseV2 oauthDetailResponseV2 = new OAuthDetailResponseV2();
        oauthDetailResponseV2.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetailsV2(oauthDetailRequest)).thenReturn(oauthDetailResponseV2);

        mockMvc.perform(post("/authorization/v2/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));

        when(authorizationService.getOauthDetailsV3(Mockito.any(), Mockito.any())).thenReturn(oauthDetailResponseV2);
        mockMvc.perform(post("/authorization/v3/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void sendOtp_withInvalidChannel_returnSuccessResponse() throws Exception {
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setIndividualId("1234567890");
        otpRequest.setOtpChannels(new ArrayList<>());
        otpRequest.setTransactionId("1234567890");
        otpRequest.setCaptchaToken("1234567890");

        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(otpRequest);


        OtpResponse otpResponse = new OtpResponse();
        otpResponse.setMaskedEmail("emain");
        otpResponse.setTransactionId("1234567890");
        otpResponse.setMaskedMobile("84898989898");
        when(authorizationService.sendOtp(otpRequest)).thenReturn(otpResponse);

        mockMvc.perform(post("/authorization/send-otp")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_OTP_CHANNEL));
    }


    @Test
    public void authenticateEndUser_withValidDetails_returnSuccessResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("quewertyId");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("123456");
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setFormat("alpha-numeric");

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);
        authRequest.setChallengeList(authChallengeList);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);

        AuthResponseV2 authResponseV2 = new AuthResponseV2();
        authResponseV2.setTransactionId("quewertyId");
        when(authorizationService.authenticateUserV2(authRequest)).thenReturn(authResponseV2);

        mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("quewertyId"));

        AuthRequestV2 authRequestV2 = new AuthRequestV2();
        authRequestV2.setIndividualId("1234567890");
        authRequestV2.setTransactionId("quewertyId");
        authRequestV2.setChallengeList(authChallengeList);
        wrapper.setRequest(authRequestV2);
        when(authorizationService.authenticateUserV3(Mockito.any(), Mockito.any())).thenReturn(authResponseV2);
        mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("quewertyId"));
    }

    @Test
    public void authenticateEndUser_withInvalidTimestamp_returnErrorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("1234567890");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("123456");
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setFormat("alpha-numeric");

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);

        authRequest.setChallengeList(authChallengeList);

        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        requestTime = requestTime.plusMinutes(10);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(authRequest);
        when(authorizationService.authenticateUserV2(authRequest)).thenReturn(new AuthResponseV2());
        mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_REQUEST))
                .andExpect(jsonPath("$.errors[0].errorMessage").value("requestTime: invalid_request"));

        mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_REQUEST))
                .andExpect(jsonPath("$.errors[0].errorMessage").value("requestTime: invalid_request"));
    }

    @Test
    public void authenticateEndUser_withInvalidFormat_returnErrorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("1234567890");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("123456");
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setFormat("jwt");

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);

        authRequest.setChallengeList(authChallengeList);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);
        when(authorizationService.authenticateUserV2(authRequest)).thenReturn(new AuthResponseV2());
        mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_challenge_format"));

        mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_challenge_format"));
    }

    @Test
    public void authenticateEndUser_withValidKBIDetails_returnSuccessResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("1234567890");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("eyJmdWxsTmFtZSI6IkthaWYgU2lkZGlxdWUiLCJkb2IiOiIyMDAwLTA3LTI2In0\u003d");
        authChallenge.setAuthFactorType("KBI");
        authChallenge.setFormat("base64url-encoded-json");

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);

        authRequest.setChallengeList(authChallengeList);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);
        when(authorizationService.authenticateUserV2(authRequest)).thenReturn(new AuthResponseV2());
        mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void authenticateEndUser_withInvalidChallenge_returnErrorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("1234567890");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("eyJmdWxsTmFtZSI6IjEyMyIsImRvYiI6IjIwMDAtMDctMjYifQ==");
        authChallenge.setAuthFactorType("KBI");
        authChallenge.setFormat("base64url-encoded-json");

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);

        authRequest.setChallengeList(authChallengeList);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);
        when(authorizationService.authenticateUserV2(authRequest)).thenReturn(new AuthResponseV2());
        mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_challenge"));

        mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_challenge"));
    }

    @Test
    public void authenticateEndUser_withInvalidChallengeJson_returnErrorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("1234567890");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("abc");
        authChallenge.setAuthFactorType("KBI");
        authChallenge.setFormat("base64url-encoded-json");

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);

        authRequest.setChallengeList(authChallengeList);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);
        when(authorizationService.authenticateUserV2(authRequest)).thenReturn(new AuthResponseV2());
        mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_challenge"));

        mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_challenge"));
    }

    @Test
    public void authenticateEndUser_withNullAuthFactorType_returnErrorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("1234567890");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("1234567890");
        authChallenge.setAuthFactorType(null);
        authChallenge.setFormat("jwt");

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);

        authRequest.setChallengeList(authChallengeList);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);
        when(authorizationService.authenticateUserV2(authRequest)).thenReturn(new AuthResponseV2());
        MvcResult mvcResult=mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        List<String> errorCodes = Arrays.asList(INVALID_AUTH_FACTOR_TYPE, INVALID_AUTH_FACTOR_TYPE_FORMAT, INVALID_CHALLENGE_LENGTH);
        ResponseWrapper responseWrapper = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ResponseWrapper.class);
        Assert.assertTrue(responseWrapper.getErrors().size() == 1);
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));

        mvcResult=mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        responseWrapper = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ResponseWrapper.class);
        Assert.assertTrue(responseWrapper.getErrors().size() == 1);
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));
    }

    @Test
    public void authenticateEndUser_withNullAuthChallenge_returnErrorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("1234567890");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("1234567890");
        authChallenge.setAuthFactorType(null);
        authChallenge.setFormat(null);

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);

        authRequest.setChallengeList(authChallengeList);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);
        when(authorizationService.authenticateUserV2(authRequest)).thenReturn(new AuthResponseV2());
        when(authorizationService.authenticateUserV2(authRequest)).thenReturn(new AuthResponseV2());
        MvcResult mvcResult=mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        List<String> errorCodes = Arrays.asList(INVALID_AUTH_FACTOR_TYPE, INVALID_AUTH_FACTOR_TYPE_FORMAT,INVALID_CHALLENGE_FORMAT,
                INVALID_CHALLENGE_LENGTH);
        ResponseWrapper responseWrapper = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ResponseWrapper.class);
        Assert.assertTrue(responseWrapper.getErrors().size() == 1);
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));

        mvcResult=mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        responseWrapper = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ResponseWrapper.class);
        Assert.assertTrue(responseWrapper.getErrors().size() == 1);
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));
    }

    @Test
    public void authenticateEndUser_withBlankFormat_returnErrorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("1234567890");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("123456");
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setFormat("");

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);

        authRequest.setChallengeList(authChallengeList);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);
        when(authorizationService.authenticateUserV2(authRequest)).thenReturn(new AuthResponseV2());
        MvcResult mvcResult=mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        List<String> errorCodes = Arrays.asList(INVALID_CHALLENGE_FORMAT, INVALID_AUTH_FACTOR_TYPE_FORMAT);
        ResponseWrapper responseWrapper = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ResponseWrapper.class);
        Assert.assertTrue(responseWrapper.getErrors().size() == 1);
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));

        mvcResult=mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        responseWrapper = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ResponseWrapper.class);
        Assert.assertTrue(responseWrapper.getErrors().size() == 1);
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));
    }

    @Test
    public void authenticateEndUser_withNullFormat_returnErrorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("1234567890");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("123456");
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setFormat(null);

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);

        authRequest.setChallengeList(authChallengeList);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);
        when(authorizationService.authenticateUserV2(authRequest)).thenReturn(new AuthResponseV2());
        MvcResult mvcResult=mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        List<String> errorCodes = Arrays.asList(INVALID_CHALLENGE_FORMAT, INVALID_AUTH_FACTOR_TYPE_FORMAT);
        ResponseWrapper responseWrapper = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ResponseWrapper.class);
        Assert.assertTrue(responseWrapper.getErrors().size() == 1);
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));

        mvcResult=mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        responseWrapper = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ResponseWrapper.class);
        Assert.assertTrue(responseWrapper.getErrors().size() == 1);
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));
    }

    @Test
    public void authenticateEndUser_withInvalidTransactionId_returnErrorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("1234567890");
        authChallenge.setAuthFactorType("PWD");
        authChallenge.setFormat("alpha-numeric");

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);
        authRequest.setChallengeList(authChallengeList);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);


        mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID))
                .andExpect(jsonPath("$.errors[0].errorMessage").value("request.transactionId: invalid_transaction_id"));

        mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID))
                .andExpect(jsonPath("$.errors[0].errorMessage").value("request.transactionId: invalid_transaction_id"));
    }

    @Test
    public void authenticateEndUser_withInvalidAuthChallenge_returnErrorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("1234567890");


        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);

        mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_CHALLENGE_LIST))
                .andExpect(jsonPath("$.errors[0].errorMessage").value("request.challengeList: invalid_no_of_challenges"));

        mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_CHALLENGE_LIST))
                .andExpect(jsonPath("$.errors[0].errorMessage").value("request.challengeList: invalid_no_of_challenges"));
    }

    @Test
    public void authenticateEndUser_throwsException_thenReturnErrorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("1234567890");
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("1234567890");
        authChallenge.setAuthFactorType("PWD");
        authChallenge.setFormat("alpha-numeric");
        authRequest.setChallengeList(Arrays.asList(authChallenge));

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);

        when(authorizationService.authenticateUserV2(authRequest)).thenThrow(new EsignetException());

        mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(UNKNOWN_ERROR));

        AuthRequestV2 authRequestV2 = new AuthRequestV2();
        authRequestV2.setIndividualId("1234567890");
        authRequestV2.setTransactionId("1234567890");
        authRequestV2.setChallengeList(Arrays.asList(authChallenge));
        wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequestV2);
        when(authorizationService.authenticateUserV3(Mockito.any(), Mockito.any())).thenThrow(new EsignetException());
        mockMvc.perform(post("/authorization/v3/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(UNKNOWN_ERROR));
    }


    @Test
    public void getAuthorizationCode_withValidDetails_thenSuccessResposne() throws Exception {
        AuthCodeRequest authCodeRequest = new AuthCodeRequest();
        authCodeRequest.setTransactionId("1234567890");
        authCodeRequest.setAcceptedClaims(Arrays.asList("name", "email"));
        authCodeRequest.setPermittedAuthorizeScopes(Arrays.asList("openid", "profile"));


        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authCodeRequest);

        AuthCodeResponse authCodeResponse = new AuthCodeResponse();
        authCodeResponse.setCode("code");
        when(authorizationService.getAuthCode(authCodeRequest)).thenReturn(authCodeResponse);
        mockMvc.perform(post("/authorization/auth-code")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.code").value("code"));
    }

    @Test
    public void getAuthorizationCode_withInValidAcceptedClaim_thenErrorResponse() throws Exception {
        AuthCodeRequest authCodeRequest = new AuthCodeRequest();
        authCodeRequest.setTransactionId("1234567890");
        authCodeRequest.setAcceptedClaims(Arrays.asList("name",""));
        authCodeRequest.setPermittedAuthorizeScopes(Arrays.asList("openid", "profile"));


        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authCodeRequest);

        AuthCodeResponse authCodeResponse = new AuthCodeResponse();
        authCodeResponse.setCode("code");
        when(authorizationService.getAuthCode(authCodeRequest)).thenReturn(authCodeResponse);
        mockMvc.perform(post("/authorization/auth-code")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_ACCEPTED_CLAIM));
    }

    @Test
    public void getAuthorizationCode_withInValidPermittedAuthorizeScopes_thenErrorResposne() throws Exception {
        AuthCodeRequest authCodeRequest = new AuthCodeRequest();
        authCodeRequest.setTransactionId("1234567890");
        authCodeRequest.setAcceptedClaims(Arrays.asList("name","email"));
        authCodeRequest.setPermittedAuthorizeScopes(Arrays.asList(" ", "profile"));


        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authCodeRequest);

        AuthCodeResponse authCodeResponse = new AuthCodeResponse();
        authCodeResponse.setCode("code");
        when(authorizationService.getAuthCode(authCodeRequest)).thenReturn(authCodeResponse);
        mockMvc.perform(post("/authorization/auth-code")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_PERMITTED_SCOPE));
    }
    
    
    @Test
    public void prepareSignupRedirect_withValidInput_thenPass() throws Exception {
  	  SignupRedirectRequest signupRedirectRequest = new SignupRedirectRequest();
  	  signupRedirectRequest.setTransactionId("TransactionId");
  	  signupRedirectRequest.setPathFragment("Path Fragment");
  	
      RequestWrapper<Object> wrapper = new RequestWrapper<>();
      wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
      wrapper.setRequest(signupRedirectRequest);

      SignupRedirectResponse signupRedirectResponse = new SignupRedirectResponse();
      signupRedirectResponse.setIdToken("idToken");
      signupRedirectResponse.setTransactionId("TransactionId");

      when(authorizationService.prepareSignupRedirect(Mockito.any(SignupRedirectRequest.class), Mockito.any(HttpServletResponse.class))).thenReturn(signupRedirectResponse);
      mockMvc.perform(post("/authorization/prepare-signup-redirect")
                      .content(objectMapper.writeValueAsString(wrapper))
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.response.idToken").value("idToken"))
              .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    public void prepareSignupRedirect_OnException_thenErrorResponse() throws Exception {
        SignupRedirectRequest signupRedirectRequest = new SignupRedirectRequest();
        signupRedirectRequest.setTransactionId("TransactionId");
        signupRedirectRequest.setPathFragment("Path Fragment");

        RequestWrapper<Object> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(signupRedirectRequest);

        when(authorizationService.prepareSignupRedirect(Mockito.any(SignupRedirectRequest.class), Mockito.any(HttpServletResponse.class)))
                .thenThrow(new EsignetException());
        mockMvc.perform(post("/authorization/prepare-signup-redirect")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(UNKNOWN_ERROR));

        when(authorizationService.prepareSignupRedirect(Mockito.any(SignupRedirectRequest.class), Mockito.any(HttpServletResponse.class)))
                .thenThrow(new NullPointerException());
        mockMvc.perform(post("/authorization/prepare-signup-redirect")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(UNKNOWN_ERROR));
    }
    
    @Test
    public void prepareSignupRedirect_withInvalidTransactionId_thenFail() throws Exception {
    	SignupRedirectRequest signupRedirectRequest = new SignupRedirectRequest();
    	signupRedirectRequest.setTransactionId("");
    	signupRedirectRequest.setPathFragment("Path Fragment");
    	
        RequestWrapper<Object> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(signupRedirectRequest);

        SignupRedirectResponse signupRedirectResponse = new SignupRedirectResponse();
        signupRedirectResponse.setIdToken("idToken");
        signupRedirectResponse.setTransactionId("TransactionId");
        when(authorizationService.prepareSignupRedirect(Mockito.any(SignupRedirectRequest.class), Mockito.any(HttpServletResponse.class))).thenReturn(signupRedirectResponse);
        mockMvc.perform(post("/authorization/prepare-signup-redirect")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID));
       }
    
    @Test
    @Ignore
    public void prepareSignupRedirect_withInvalidPathFragment_thenFail() throws Exception {
    	SignupRedirectRequest signupRedirectRequest = new SignupRedirectRequest();
    	signupRedirectRequest.setTransactionId("Transaction_Id");
    	signupRedirectRequest.setPathFragment("");
    	
        RequestWrapper<Object> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(signupRedirectRequest);

        SignupRedirectResponse signupRedirectResponse = new SignupRedirectResponse();
        signupRedirectResponse.setIdToken("idToken");
        signupRedirectResponse.setTransactionId("TransactionId");
        when(authorizationService.prepareSignupRedirect(Mockito.any(SignupRedirectRequest.class), Mockito.any(HttpServletResponse.class))).thenReturn(signupRedirectResponse);
        mockMvc.perform(post("/authorization/prepare-signup-redirect")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_PATH_FRAGMENT));
       }


  

    @Test
    public void getClaimDetails_withValidDetails_thenSuccessResponse() throws Exception {

        ClaimDetailResponse claimDetailResponse = new ClaimDetailResponse();
        claimDetailResponse.setConsentAction(ConsentAction.CAPTURE);
        claimDetailResponse.setClaimStatus(List.of(new ClaimStatus("name", false, true),
                new ClaimStatus("phone_number", false, true),
                new ClaimStatus("email", false, true)));
        when(authorizationService.getClaimDetails("transactionId")).thenReturn(claimDetailResponse);

        mockMvc.perform(get("/authorization/claim-details").header("oauth-details-key", "transactionId"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.response.consentAction").value("CAPTURE"));
    }

    @Test
    public void getClaimDetails_OnException_thenErrorResponse() throws Exception {
        when(authorizationService.getClaimDetails("transactionId")).thenThrow(new InvalidTransactionException());

        mockMvc.perform(get("/authorization/claim-details").header("oauth-details-key", "transactionId"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_TRANSACTION));

        when(authorizationService.getClaimDetails("test-transactionId")).thenThrow(new EsignetException());

        mockMvc.perform(get("/authorization/claim-details").header("oauth-details-key", "test-transactionId"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(UNKNOWN_ERROR));
    }

    @Test
    public void completeSignupRedirect_withValidDetails_thenSuccessResponse() throws Exception {
        CompleteSignupRedirectRequest completeSignupRedirectRequest = new CompleteSignupRedirectRequest();
        completeSignupRedirectRequest.setTransactionId("123131231");

        RequestWrapper<Object> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(completeSignupRedirectRequest);

        CompleteSignupRedirectResponse completeSignupRedirectResponse = new CompleteSignupRedirectResponse();
        completeSignupRedirectResponse.setStatus("status");
        when(authorizationService.completeSignupRedirect(completeSignupRedirectRequest)).thenReturn(completeSignupRedirectResponse);

        mockMvc.perform(post("/authorization/complete-signup-redirect")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.status").value("status"));
    }

    @Test
    public void completeSignupRedirect_OnException_thenErrorResponse() throws Exception {
        CompleteSignupRedirectRequest completeSignupRedirectRequest = new CompleteSignupRedirectRequest();
        completeSignupRedirectRequest.setTransactionId("123131231");

        RequestWrapper<Object> wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(completeSignupRedirectRequest);

        when(authorizationService.completeSignupRedirect(Mockito.any())).thenThrow(new InvalidTransactionException());

        mockMvc.perform(post("/authorization/complete-signup-redirect")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_TRANSACTION));
    }

    @Test
    public void getPAROAuthDetails_withValidDetails_thenOAuthDetailsResponse() throws Exception {
        RequestWrapper<PAROAuthDetailsRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        PAROAuthDetailsRequest request = new PAROAuthDetailsRequest();
        request.setRequestUri("requestUri");
        request.setClientId("clientId");
        requestWrapper.setRequest(request);

        OAuthDetailResponseV2 response = new OAuthDetailResponseV2();
        response.setTransactionId("transactionId123");

        when(authorizationService.getPAROAuthDetails(Mockito.any(), Mockito.any())).thenReturn(response);

        mockMvc.perform(post("/authorization/par-oauth-details")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value(response.getTransactionId()));
    }

    @Test
    public void getPAROAuthDetails_withInvalidRequestUri_thenThrowError() throws Exception {
        RequestWrapper<PAROAuthDetailsRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        PAROAuthDetailsRequest request = new PAROAuthDetailsRequest();
        request.setRequestUri("requestUri");
        request.setClientId("clientId");
        requestWrapper.setRequest(request);

        when(authorizationService.getPAROAuthDetails(Mockito.any(), Mockito.any())).thenThrow(new EsignetException(ErrorConstants.INVALID_REQUEST));

        mockMvc.perform(post("/authorization/par-oauth-details")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_REQUEST));
    }

}