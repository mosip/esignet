/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.core.dto.OAuthDetailRequest;
import io.mosip.idp.core.dto.OAuthDetailResponse;
import io.mosip.idp.core.dto.RequestWrapper;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.LinkedAuthorizationService;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.services.AuthorizationHelperService;
import io.mosip.idp.services.AuthorizationServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(value = AuthorizationController.class)
public class AuthorizationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthorizationServiceImpl authorizationService;

    @MockBean
    AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @MockBean
    AuthorizationHelperService authorizationHelperService;

    @MockBean
    LinkedAuthorizationService linkAuthorizationService;

    ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void init() throws IdPException {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("mosip:idp:acr:static-code");
        acrValues.add("mosip:idp:acr:biometrics");
        acrValues.add("mosip:idp:acr:linked-wallet");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);
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
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponse oauthDetailResponse = new OAuthDetailResponse();
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
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponse oauthDetailResponse = new OAuthDetailResponse();
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
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponse oauthDetailResponse = new OAuthDetailResponse();
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
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponse oauthDetailResponse = new OAuthDetailResponse();
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
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OAuthDetailResponse oauthDetailResponse = new OAuthDetailResponse();
        oauthDetailResponse.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetails( oauthDetailRequest)).thenReturn(oauthDetailResponse);

        mockMvc.perform(post("/authorization/oauth-details")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }
}
