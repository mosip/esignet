package io.mosip.esignet.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.spi.AuthorizationService;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.AuthorizationHelperService;
import io.mosip.esignet.services.CacheUtilService;
import io.mosip.esignet.services.ConsentHelperService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import static io.mosip.esignet.core.constants.Constants.UTC_DATETIME_PATTERN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(AuthorizationControllerV2.class)
public class AuthorizationControllerV2Test {


    @Autowired
    MockMvc mockMvc;

    @Qualifier("authorizationServiceV2")
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
    ConsentHelperService consentHelperService;

    ObjectMapper objectMapper = new ObjectMapper();


    @Test
    public void authenticateEndUser_withValidDetails_returnSuccessResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setIndividualId("1234567890");
        authRequest.setTransactionId("quewertyId");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("12345");
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setFormat("numeric");

        List<AuthChallenge> authChallengeList = new ArrayList<>();
        authChallengeList.add(authChallenge);
        authRequest.setChallengeList(authChallengeList);

        RequestWrapper wrapper = new RequestWrapper<>();
        wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        wrapper.setRequest(authRequest);

        AuthResponseV2 authResponseV2 = new AuthResponseV2();
        authResponseV2.setTransactionId("quewertyId");

        Mockito.when(authorizationHelperService.authResponseV2Mapper(Mockito.any())).thenReturn(authResponseV2);

        mockMvc.perform(post("/authorization/v2/authenticate")
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
        authChallenge.setChallenge("1234567890");
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
        mockMvc.perform(post("/authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(wrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_REQUEST))
                .andExpect(jsonPath("$.errors[0].errorMessage").value("requestTime: invalid_request"));
    }

    @Test
    public void authenticateEndUser_withInvalidTransectionId_returnErrorResponse() throws Exception {
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

        AuthResponseV2 authResponseV2 = new AuthResponseV2();

        Mockito.when(authorizationHelperService.authResponseV2Mapper(Mockito.any())).thenReturn(authResponseV2);

        mockMvc.perform(post("/authorization/v2/authenticate")
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
    }
}
