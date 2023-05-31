package io.mosip.esignet.controllers;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_AUTH_FACTOR_TYPE;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CHALLENGE;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CHALLENGE_FORMAT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.services.ConsentHelperService;
import org.junit.Assert;
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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.spi.LinkedAuthorizationService;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.AuthorizationHelperService;
import io.mosip.esignet.services.CacheUtilService;

@RunWith(SpringRunner.class)
@WebMvcTest(value = LinkedAuthorizationControllerV2.class)
public class LinkedAuthorizationControllerV2Test {

    ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    @MockBean
    @Qualifier("linkedAuthorizationServiceV2")
    private LinkedAuthorizationService linkedAuthorizationService;


    @MockBean
    private AuthorizationHelperService authorizationHelperService;

    @MockBean
    CacheUtilService cacheUtilService;

    @MockBean
    AuditPlugin auditWrapper;

    @MockBean
    ConsentHelperService consentHelperService;

    @Test
    public void authenticate_withValidRequest_thenPass() throws Exception {
        RequestWrapper<LinkedKycAuthRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
        linkedKycAuthRequest.setLinkedTransactionId("link-transaction-id");
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setFormat("format");
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setChallenge("challenge");
        linkedKycAuthRequest.setChallengeList(Arrays.asList(authChallenge));
        linkedKycAuthRequest.setIndividualId("individualId");
        requestWrapper.setRequest(linkedKycAuthRequest);
        LinkedKycAuthResponseV2 linkedKycAuthResponseV2 = new LinkedKycAuthResponseV2();
        linkedKycAuthResponseV2.setConsentAction(ConsentAction.CAPTURE);
        linkedKycAuthResponseV2.setLinkedTransactionId(linkedKycAuthRequest.getLinkedTransactionId());
        Mockito.when(linkedAuthorizationService.authenticateUser(Mockito.any(LinkedKycAuthRequest.class))).thenReturn(new LinkedKycAuthResponse());
        Mockito.when(authorizationHelperService.linkedKycAuthResponseV2Mapper(Mockito.any(LinkedKycAuthResponse.class))).thenReturn(new LinkedKycAuthResponseV2());
        Mockito.when(consentHelperService.processLinkedConsent(Mockito.any())).thenReturn(linkedKycAuthResponseV2);
        mockMvc.perform(post("/linked-authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").exists())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    public void authenticate_withException_thenFail() throws Exception {
        RequestWrapper<LinkedKycAuthRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
        linkedKycAuthRequest.setLinkedTransactionId("link-transaction-id");
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setFormat("format");
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setChallenge("challenge");
        linkedKycAuthRequest.setChallengeList(Arrays.asList(authChallenge));
        linkedKycAuthRequest.setIndividualId("individualId");
        requestWrapper.setRequest(linkedKycAuthRequest);

        Mockito.when(linkedAuthorizationService.authenticateUser(Mockito.any(LinkedKycAuthRequest.class))).thenThrow(EsignetException.class);

        mockMvc.perform(post("/linked-authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    public void authenticate_withInvalidTransactionId_thenFail() throws Exception {
        RequestWrapper<LinkedKycAuthRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
        linkedKycAuthRequest.setLinkedTransactionId("  ");
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setFormat("format");
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setChallenge("challenge");
        linkedKycAuthRequest.setChallengeList(Arrays.asList(authChallenge));
        linkedKycAuthRequest.setIndividualId("individualId");
        requestWrapper.setRequest(linkedKycAuthRequest);

        mockMvc.perform(post("/linked-authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID));
    }

    @Test
    public void authenticate_withInvalidIndividualId_thenFail() throws Exception {
        RequestWrapper<LinkedKycAuthRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
        linkedKycAuthRequest.setLinkedTransactionId("txn-id");
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setFormat("format");
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setChallenge("challenge");
        linkedKycAuthRequest.setChallengeList(Arrays.asList(authChallenge));
        linkedKycAuthRequest.setIndividualId("");
        requestWrapper.setRequest(linkedKycAuthRequest);

        mockMvc.perform(post("/linked-authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_IDENTIFIER));
    }

    @Test
    public void authenticate_withInvalidChallengeList_thenFail() throws Exception {
        RequestWrapper<LinkedKycAuthRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
        linkedKycAuthRequest.setLinkedTransactionId("txn-id");
        linkedKycAuthRequest.setIndividualId("individualId");
        requestWrapper.setRequest(linkedKycAuthRequest);

        mockMvc.perform(post("/linked-authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_CHALLENGE_LIST));

        linkedKycAuthRequest.setChallengeList(new ArrayList<>());
        mockMvc.perform(post("/linked-authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_CHALLENGE_LIST));

        AuthChallenge authChallenge = new AuthChallenge();
        linkedKycAuthRequest.setChallengeList(Arrays.asList(authChallenge));
        MvcResult mvcResult = mockMvc.perform(post("/linked-authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        List<String> errorCodes = Arrays.asList(INVALID_AUTH_FACTOR_TYPE, INVALID_CHALLENGE, INVALID_CHALLENGE_FORMAT);
        ResponseWrapper responseWrapper = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ResponseWrapper.class);
        Assert.assertTrue(responseWrapper.getErrors().size() == 3);
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(1)).getErrorCode()));
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(2)).getErrorCode()));
    }

    @Test
    public void saveConsent_withValidRequest_thenPass() throws Exception {
        RequestWrapper<LinkedConsentRequestV2> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("txn-id");
        linkedConsentRequestV2.setLinkedTransactionId("txn-id");
        linkedConsentRequestV2.setSignature("signature");

        requestWrapper.setRequest(linkedConsentRequestV2);

        Mockito.when(linkedAuthorizationService.saveConsent(Mockito.any())).thenReturn(new LinkedConsentResponse());
        Mockito.when(linkedAuthorizationService.saveConsent(Mockito.any())).thenThrow(EsignetException.class);
        mockMvc.perform(post("/linked-authorization/v2/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").exists())
                .andExpect(jsonPath("$.errors").isEmpty());

    }

    @Test
    public void saveConsent_withInValidRequest_thenFail() throws Exception {
        RequestWrapper<LinkedConsentRequestV2> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("txn-id");
        linkedConsentRequestV2.setLinkedTransactionId("txn-id");
        linkedConsentRequestV2.setSignature("signature");

        requestWrapper.setRequest(linkedConsentRequestV2);

        //Mockito.when(linkedAuthorizationService.saveConsent(Mockito.any())).thenReturn(new LinkedConsentResponse());
        Mockito.when(linkedAuthorizationService.saveConsent(Mockito.any())).thenThrow(InvalidTransactionException.class);
        mockMvc.perform(post("/linked-authorization/v2/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty());


    }
}

