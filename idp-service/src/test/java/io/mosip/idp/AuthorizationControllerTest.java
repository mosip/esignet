package io.mosip.idp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.controllers.AuthorizationController;
import io.mosip.idp.core.dto.OauthDetailRequest;
import io.mosip.idp.core.dto.OauthDetailResponse;
import io.mosip.idp.core.dto.RequestWrapper;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.services.AuthorizationServiceImpl;
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
@WebMvcTest(value = AuthorizationController.class, secure = false)
public class AuthorizationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthorizationServiceImpl authorizationService;

    @MockBean
    AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void getOauthDetails_withNoNonce_returnErrorResponse() throws Exception {
        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequest(oauthDetailRequest);
        wrapper.setRequestTime(null);
        mockMvc.perform(post("/authorization/oauth-details")
                .content(objectMapper.writeValueAsString(wrapper))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_REQUEST));
    }

    @Test
    public void getOauthDetails_withInvalidTimestamp_returnErrorResponse() throws Exception {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("level4");
        acrValues.add("level5");
        acrValues.add("level2");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("level5 level2");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");

        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        requestTime = requestTime.plusMinutes(10);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);
        mockMvc.perform(post("/authorization/oauth-details?nonce=23424234TY")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_request"))
                .andExpect(jsonPath("$.errors[0].errorMessage").value("requestTime: invalid_request"));
    }

    @Test
    public void getOauthDetails_withInvalidRedirectUri_returnErrorResponse() throws Exception {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("level4");
        acrValues.add("level5");
        acrValues.add("level2");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri(" ");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("level5 level2");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/oauth-details?nonce=23424234TY")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_redirect_uri"));
    }

    @Test
    public void getOauthDetails_withInvalidAcr_returnErrorResponse() throws Exception {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("level4");
        acrValues.add("level2");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("level5 level2");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/oauth-details?nonce=23424234TY")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_acr"));
    }

    @Test
    public void getOauthDetails_withInvalidDisplay_returnErrorResponse() throws Exception {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("level4");
        acrValues.add("level2");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("level4");
        oauthDetailRequest.setDisplay("none");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/oauth-details?nonce=23424234TY")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_display"));
    }

    @Test
    public void getOauthDetails_withInvalidPrompt_returnErrorResponse() throws Exception {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("level4");
        acrValues.add("level2");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("level4");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("touch");
        oauthDetailRequest.setResponseType("code");
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/oauth-details?nonce=23424234TY")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_prompt"));
    }

    @Test
    public void getOauthDetails_withInvalidResponseType_returnErrorResponse() throws Exception {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("level4");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid profile");
        oauthDetailRequest.setAcrValues("level4");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("none");
        oauthDetailRequest.setResponseType("implicit");
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/oauth-details?nonce=23424234TY")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_response_type"));
    }

    @Test
    public void getOauthDetails_withOnlyOpenIdScope_returnSuccessResponse() throws Exception {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("level4");
        acrValues.add("level5");
        acrValues.add("level2");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid");
        oauthDetailRequest.setAcrValues("level5 level2");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OauthDetailResponse oauthDetailResponse = new OauthDetailResponse();
        oauthDetailResponse.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetails("23424234TY", oauthDetailRequest)).thenReturn(oauthDetailResponse);

        mockMvc.perform(post("/authorization/oauth-details?nonce=23424234TY")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetails_withOutOpenIdScope_returnSuccessResponse() throws Exception {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("level4");
        acrValues.add("level5");
        acrValues.add("level2");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("profile");
        oauthDetailRequest.setAcrValues("level5 level2");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        mockMvc.perform(post("/authorization/oauth-details?nonce=23424234TY")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value("invalid_scope"));
    }

    @Test
    public void getOauthDetails_withOpenIdScope_returnSuccessResponse() throws Exception {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("level4");
        acrValues.add("level5");
        acrValues.add("level2");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("profile openid");
        oauthDetailRequest.setAcrValues("level5 level2");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OauthDetailResponse oauthDetailResponse = new OauthDetailResponse();
        oauthDetailResponse.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetails("23424234TY", oauthDetailRequest)).thenReturn(oauthDetailResponse);

        mockMvc.perform(post("/authorization/oauth-details?nonce=23424234TY")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetails_withOnlyAuthorizeScope_returnSuccessResponse() throws Exception {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("level4");
        acrValues.add("level5");
        acrValues.add("level2");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("resident-service");
        oauthDetailRequest.setAcrValues("level5 level2");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OauthDetailResponse oauthDetailResponse = new OauthDetailResponse();
        oauthDetailResponse.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetails("23424234TY", oauthDetailRequest)).thenReturn(oauthDetailResponse);

        mockMvc.perform(post("/authorization/oauth-details?nonce=23424234TY")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }

    @Test
    public void getOauthDetails_withAuthorizeAndOpenIdScope_returnSuccessResponse() throws Exception {
        HashSet<String> acrValues = new HashSet<>();
        acrValues.add("level4");
        acrValues.add("level5");
        acrValues.add("level2");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(acrValues);

        OauthDetailRequest oauthDetailRequest = new OauthDetailRequest();
        oauthDetailRequest.setClientId("12345");
        oauthDetailRequest.setRedirectUri("https://localhost:9090/v1/idp");
        oauthDetailRequest.setScope("openid resident-service");
        oauthDetailRequest.setAcrValues("level5 level2");
        oauthDetailRequest.setDisplay("page");
        oauthDetailRequest.setPrompt("login");
        oauthDetailRequest.setResponseType("code");
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        wrapper.setRequest(oauthDetailRequest);

        OauthDetailResponse oauthDetailResponse = new OauthDetailResponse();
        oauthDetailResponse.setTransactionId("qwertyId");
        when(authorizationService.getOauthDetails("23424234TY", oauthDetailRequest)).thenReturn(oauthDetailResponse);

        mockMvc.perform(post("/authorization/oauth-details?nonce=23424234TY")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value("qwertyId"));
    }
}
