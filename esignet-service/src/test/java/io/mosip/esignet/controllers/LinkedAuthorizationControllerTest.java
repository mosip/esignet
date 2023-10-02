package io.mosip.esignet.controllers;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_AUTH_FACTOR_TYPE;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CHALLENGE;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CHALLENGE_FORMAT;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_IDENTIFIER;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_LINK_CODE;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_OTP_CHANNEL;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_TRANSACTION_ID;
import static io.mosip.esignet.core.constants.ErrorConstants.RESPONSE_TIMEOUT;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_ACCEPTED_CLAIM;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_PERMITTED_SCOPE;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_SIGNATURE_FORMAT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
import io.mosip.esignet.vci.services.VCICacheService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.LinkedAuthorizationService;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.AuthorizationHelperService;
import io.mosip.esignet.services.CacheUtilService;

@RunWith(SpringRunner.class)
@WebMvcTest(value = LinkedAuthorizationController.class)
public class LinkedAuthorizationControllerTest {

    ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    @MockBean
    private LinkedAuthorizationService linkedAuthorizationService;

    @MockBean
    private AuthorizationHelperService authorizationHelperService;

    @InjectMocks
    LinkedAuthorizationController linkedAuthorizationController;

    @MockBean
    CacheUtilService cacheUtilService;
    
    @MockBean
    AuditPlugin auditWrapper;

    @MockBean
    ParsedAccessToken parsedAccessToken;

    @MockBean
    VCICacheService vciCacheService;

    @Test
    public void generateLinkCode_withValidRequest_thenPass() throws Exception {
        RequestWrapper<LinkCodeRequest> requestWrapper = new RequestWrapper<>();
        LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
        linkCodeRequest.setTransactionId("transactionId");
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        requestWrapper.setRequest(linkCodeRequest);

        LinkCodeResponse linkCodeResponse = new LinkCodeResponse();
        linkCodeResponse.setLinkCode("link-code");
        linkCodeResponse.setTransactionId("transactionId");
        linkCodeResponse.setExpireDateTime("expire-date-time");
        Mockito.when(linkedAuthorizationService.generateLinkCode(Mockito.any(LinkCodeRequest.class))).thenReturn(linkCodeResponse);

        mockMvc.perform(post("/linked-authorization/link-code")
                .content(objectMapper.writeValueAsString(requestWrapper))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.transactionId").value(linkCodeResponse.getTransactionId()))
                .andExpect(jsonPath("$.response.linkCode").value(linkCodeResponse.getLinkCode()));
    }

    @Test
    public void generateLinkCode_withNullTransactionId_thenFail() throws Exception {
        RequestWrapper<LinkCodeRequest> requestWrapper = new RequestWrapper<>();
        LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        requestWrapper.setRequest(linkCodeRequest);

        mockMvc.perform(post("/linked-authorization/link-code")
                .content(objectMapper.writeValueAsString(requestWrapper))
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID));
    }

    @Test
    public void generateLinkCode_withInvalidTransactionException_thenFail() throws Exception {
        RequestWrapper<LinkCodeRequest> requestWrapper = new RequestWrapper<>();
        LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
        linkCodeRequest.setTransactionId("transactionId");
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        requestWrapper.setRequest(linkCodeRequest);

        Mockito.when(linkedAuthorizationService.generateLinkCode(Mockito.any(LinkCodeRequest.class))).thenThrow(new InvalidTransactionException());

        mockMvc.perform(post("/linked-authorization/link-code")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION));
    }

    @Test
    public void linkTransaction_withValidRequest_thenPass() throws Exception {
        RequestWrapper<LinkTransactionRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        requestWrapper.setRequest(linkTransactionRequest);

        Mockito.when(linkedAuthorizationService.linkTransaction(Mockito.any(LinkTransactionRequest.class)))
                .thenReturn(new LinkTransactionResponse());

        mockMvc.perform(post("/linked-authorization/link-transaction")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").exists())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    public void linkTransaction_withInvalidLinkCode_thenFail() throws Exception {
        RequestWrapper<LinkTransactionRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("");
        requestWrapper.setRequest(linkTransactionRequest);

        mockMvc.perform(post("/linked-authorization/link-transaction")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_LINK_CODE));
    }

    @Test
    public void linkTransaction_withInvalidTransactionException_thenFail() throws Exception {
        RequestWrapper<LinkTransactionRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        requestWrapper.setRequest(linkTransactionRequest);

        Mockito.when(linkedAuthorizationService.linkTransaction(Mockito.any(LinkTransactionRequest.class)))
                .thenThrow(new InvalidTransactionException());

        mockMvc.perform(post("/linked-authorization/link-transaction")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION));
    }

    @Test
    public void linkTransactionV2_withValidRequest_thenPass() throws Exception {
        RequestWrapper<LinkTransactionRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        requestWrapper.setRequest(linkTransactionRequest);

        Mockito.when(linkedAuthorizationService.linkTransactionV2(Mockito.any(LinkTransactionRequest.class)))
                .thenReturn(new LinkTransactionResponseV2());

        mockMvc.perform(post("/linked-authorization/v2/link-transaction")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").exists())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    public void linkTransactionV2_withInvalidLinkCode_thenFail() throws Exception {
        RequestWrapper<LinkTransactionRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("");
        requestWrapper.setRequest(linkTransactionRequest);

        mockMvc.perform(post("/linked-authorization/v2/link-transaction")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_LINK_CODE));
    }

    @Test
    public void linkTransactionV2_withInvalidTransactionException_thenFail() throws Exception {
        RequestWrapper<LinkTransactionRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        requestWrapper.setRequest(linkTransactionRequest);

        Mockito.when(linkedAuthorizationService.linkTransactionV2(Mockito.any(LinkTransactionRequest.class)))
                .thenThrow(new InvalidTransactionException());

        mockMvc.perform(post("/linked-authorization/v2/link-transaction")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION));
    }

    @Test
    public void sendOtp_withValidRequest_thenPass() throws Exception {
        RequestWrapper<OtpRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setOtpChannels(Arrays.asList("email"));
        otpRequest.setTransactionId("transactionId");
        otpRequest.setIndividualId("individualId");
        requestWrapper.setRequest(otpRequest);

        OtpResponse otpResponse = new OtpResponse();
        otpResponse.setMaskedEmail("email");
        otpResponse.setMaskedMobile("mobile");
        Mockito.when(linkedAuthorizationService.sendOtp(Mockito.any(OtpRequest.class))).thenReturn(otpResponse);

        mockMvc.perform(post("/linked-authorization/send-otp")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

    }

    @Test
    public void sendOtp_withInvalidRequest_thenFail() throws Exception {
        RequestWrapper<OtpRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        OtpRequest otpRequest = new OtpRequest();
        requestWrapper.setRequest(otpRequest);

        MvcResult result = mockMvc.perform(post("/linked-authorization/send-otp")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk()).andReturn();

        List<String> errorCodes = Arrays.asList(INVALID_OTP_CHANNEL, INVALID_IDENTIFIER, INVALID_TRANSACTION_ID);
        ResponseWrapper responseWrapper = objectMapper.readValue(result.getResponse().getContentAsString(), ResponseWrapper.class);
        Assert.assertTrue(responseWrapper.getErrors().size() == 3);
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(1)).getErrorCode()));
        Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(2)).getErrorCode()));
    }

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

        Mockito.when(linkedAuthorizationService.authenticateUser(Mockito.any(LinkedKycAuthRequest.class))).thenReturn(new LinkedKycAuthResponse());

        mockMvc.perform(post("/linked-authorization/authenticate")
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

        mockMvc.perform(post("/linked-authorization/authenticate")
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

        mockMvc.perform(post("/linked-authorization/authenticate")
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

        mockMvc.perform(post("/linked-authorization/authenticate")
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

        mockMvc.perform(post("/linked-authorization/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_CHALLENGE_LIST));

        linkedKycAuthRequest.setChallengeList(new ArrayList<>());
        mockMvc.perform(post("/linked-authorization/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_CHALLENGE_LIST));

        AuthChallenge authChallenge = new AuthChallenge();
        linkedKycAuthRequest.setChallengeList(Arrays.asList(authChallenge));
        MvcResult mvcResult = mockMvc.perform(post("/linked-authorization/authenticate")
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
        RequestWrapper<LinkedConsentRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
        linkedConsentRequest.setLinkedTransactionId("link-transaction-id");
        requestWrapper.setRequest(linkedConsentRequest);

        LinkedConsentResponse linkedConsentResponse = new LinkedConsentResponse();
        Mockito.when(linkedAuthorizationService.saveConsent(Mockito.any(LinkedConsentRequest.class))).thenReturn(linkedConsentResponse);

        mockMvc.perform(post("/linked-authorization/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").exists())
                .andExpect(jsonPath("$.errors").isEmpty());
    }
    
    @Test
    public void saveConsent_withException_thenFail() throws Exception {
        RequestWrapper<LinkedConsentRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
        linkedConsentRequest.setLinkedTransactionId("link-transaction-id");
        linkedConsentRequest.setPermittedAuthorizeScopes(Arrays.asList("openid","email"));
        linkedConsentRequest.setAcceptedClaims(Arrays.asList("email","names"));
        requestWrapper.setRequest(linkedConsentRequest);

        Mockito.when(linkedAuthorizationService.saveConsent(Mockito.any(LinkedConsentRequest.class))).thenThrow(EsignetException.class);

        mockMvc.perform(post("/linked-authorization/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    public void saveConsent_withInvalidTransactionId_thenFail() throws Exception {
        RequestWrapper<LinkedConsentRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
        linkedConsentRequest.setLinkedTransactionId("  ");
        requestWrapper.setRequest(linkedConsentRequest);

        mockMvc.perform(post("/linked-authorization/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_TRANSACTION_ID));
    }

    @Test
    public void saveConsent_withInvalidAcceptedClaims_thenFail() throws Exception {
        RequestWrapper<LinkedConsentRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
        linkedConsentRequest.setLinkedTransactionId("link-transaction-id");
        linkedConsentRequest.setAcceptedClaims(Arrays.asList("","names"));
        requestWrapper.setRequest(linkedConsentRequest);

        mockMvc.perform(post("/linked-authorization/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_ACCEPTED_CLAIM));
    }

    @Test
    public void saveConsent_withInvalidPermittedAuthorizeScopes_thenFail() throws Exception {
        RequestWrapper<LinkedConsentRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
        linkedConsentRequest.setLinkedTransactionId("link-transaction-id");
        linkedConsentRequest.setAcceptedClaims(Arrays.asList("email","names"));
        linkedConsentRequest.setPermittedAuthorizeScopes(Arrays.asList("","openid"));
        requestWrapper.setRequest(linkedConsentRequest);

        mockMvc.perform(post("/linked-authorization/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_PERMITTED_SCOPE));
    }

    @Test
    public void getLinkStatus_withInvalidLinkCode_thenFail() throws Exception {
        RequestWrapper<LinkStatusRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkStatusRequest linkStatusRequest = new LinkStatusRequest();
        linkStatusRequest.setLinkCode("link-code");
        linkStatusRequest.setTransactionId("transaction-id");
        requestWrapper.setRequest(linkStatusRequest);

        MvcResult mvcResult = mockMvc.perform(post("/linked-authorization/link-status")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Trigger a timeout on the request
        MockAsyncContext ctx = (MockAsyncContext) mvcResult.getRequest().getAsyncContext();
        for (AsyncListener listener : ctx.getListeners()) {
            AsyncEvent asyncEvent = new AsyncEvent(ctx, new EsignetException(ErrorConstants.INVALID_LINK_CODE));
            listener.onError(asyncEvent);
        }

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_LINK_CODE));
    }

    @Test
    public void getLinkStatus_withTimeout_thenFail() throws Exception {
        RequestWrapper<LinkStatusRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkStatusRequest linkStatusRequest = new LinkStatusRequest();
        linkStatusRequest.setLinkCode("link-code");
        linkStatusRequest.setTransactionId("transaction-id");
        requestWrapper.setRequest(linkStatusRequest);

        MvcResult mvcResult = mockMvc.perform(post("/linked-authorization/link-status")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Trigger a timeout on the request
        MockAsyncContext ctx = (MockAsyncContext) mvcResult.getRequest().getAsyncContext();
        for (AsyncListener listener : ctx.getListeners()) {
            listener.onTimeout(null);
        }

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(RESPONSE_TIMEOUT));
    }
    
    @Test
    public void getLinkStatus_withException_thenFail() throws Exception {
    	RequestWrapper<LinkStatusRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkStatusRequest linkStatusRequest = new LinkStatusRequest();
        linkStatusRequest.setLinkCode("link-code");
        linkStatusRequest.setTransactionId("transaction-id");
        requestWrapper.setRequest(linkStatusRequest);
        
        Mockito.doThrow(EsignetException.class).when(linkedAuthorizationService).getLinkStatus(Mockito.any(DeferredResult.class), 
        		Mockito.any(LinkStatusRequest.class));

        mockMvc.perform(post("/linked-authorization/link-status")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    public void getLinkAuthCode_withInvalidLinkCode_thenFail() throws Exception {
        RequestWrapper<LinkAuthCodeRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkAuthCodeRequest linkAuthCodeRequest = new LinkAuthCodeRequest();
        linkAuthCodeRequest.setTransactionId("transaction-id");
        linkAuthCodeRequest.setLinkedCode("linked-code");
        requestWrapper.setRequest(linkAuthCodeRequest);

        MvcResult mvcResult = mockMvc.perform(post("/linked-authorization/link-auth-code")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Trigger a timeout on the request
        MockAsyncContext ctx = (MockAsyncContext) mvcResult.getRequest().getAsyncContext();
        for (AsyncListener listener : ctx.getListeners()) {
            AsyncEvent asyncEvent = new AsyncEvent(ctx, new EsignetException(ErrorConstants.INVALID_LINK_CODE));
            listener.onError(asyncEvent);
        }

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_LINK_CODE));
    }
    
    @Test
    public void getLinkAuthCode_withException_thenFail() throws Exception {
        RequestWrapper<LinkAuthCodeRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkAuthCodeRequest linkAuthCodeRequest = new LinkAuthCodeRequest();
        linkAuthCodeRequest.setTransactionId("transaction-id");
        linkAuthCodeRequest.setLinkedCode("linked-code");
        requestWrapper.setRequest(linkAuthCodeRequest);
        
        Mockito.doThrow(EsignetException.class).when(linkedAuthorizationService).getLinkAuthCode(Mockito.any(DeferredResult.class), 
        		Mockito.any(LinkAuthCodeRequest.class));

        mockMvc.perform(post("/linked-authorization/link-auth-code")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    public void getLinkAuthCode_withTimeout_thenFail() throws Exception {
        RequestWrapper<LinkAuthCodeRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkAuthCodeRequest linkAuthCodeRequest = new LinkAuthCodeRequest();
        linkAuthCodeRequest.setTransactionId("transaction-id");
        linkAuthCodeRequest.setLinkedCode("linked-code");
        requestWrapper.setRequest(linkAuthCodeRequest);

        MvcResult mvcResult = mockMvc.perform(post("/linked-authorization/link-auth-code")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Trigger a timeout on the request
        MockAsyncContext ctx = (MockAsyncContext) mvcResult.getRequest().getAsyncContext();
        for (AsyncListener listener : ctx.getListeners()) {
            listener.onTimeout(null);
        }

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(RESPONSE_TIMEOUT));
    }

    @Test
    public void authenticateV2_withValidRequest_thenPass() throws Exception {
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

        Mockito.when(linkedAuthorizationService.authenticateUserV2(Mockito.any(LinkedKycAuthRequest.class))).thenReturn(new LinkedKycAuthResponseV2());

        mockMvc.perform(post("/linked-authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").exists())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    public void authenticateV2_withException_thenFail() throws Exception {
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

        Mockito.when(linkedAuthorizationService.authenticateUserV2(Mockito.any(LinkedKycAuthRequest.class))).thenThrow(EsignetException.class);

        mockMvc.perform(post("/linked-authorization/v2/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    public void authenticateV2_withInvalidTransactionId_thenFail() throws Exception {
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
    public void authenticateV2_withInvalidIndividualId_thenFail() throws Exception {
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
    public void authenticateV2_withInvalidChallengeList_thenFail() throws Exception {
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
        mockMvc.perform(post("/linked-authorization/authenticate")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_CHALLENGE_LIST));

        AuthChallenge authChallenge = new AuthChallenge();
        linkedKycAuthRequest.setChallengeList(Arrays.asList(authChallenge));
        MvcResult mvcResult = mockMvc.perform(post("/linked-authorization/authenticate")
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
    public void saveConsentV2_withValidRequest_thenPass() throws Exception {
        RequestWrapper<LinkedConsentRequestV2> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("link-transaction-id");
        linkedConsentRequestV2.setAcceptedClaims(Arrays.asList("email","names"));
        linkedConsentRequestV2.setPermittedAuthorizeScopes(Arrays.asList("openid","email"));
        linkedConsentRequestV2.setSignature("eyJ4NXQjUzI1NiI6InpCRm1ILW94QTJUczdtLTI2V3ZaTTFyaG9HckFuRXdpX3hLcHBoTFEzWnciLCJhbGciOiJSUzI1NiJ9.BYOnWu4gyzPluh5H6bWsznWSD39WPl_YcWmjGff6j0-CGlDwfq61VsDCQp1lZp0GOZj8ebHIhWJndg2UotRjBnw1HXjRL3UFTMgf3WoTecQsDQKjAE8HCUwYbtF7j1wYha5o5P2Ah-CVJhgVbY947ZoKFo7w1ER0Dgjc_GHESHuCkly_KFrw2Nd0MNtBmkkrhr01QGjM62LbLf_UrTyIapQbu8tSTPIcpScM-2cLNaT7PdA0KXedPOVDLKrcz7EpG4xgpg9uUZ6uxs10spp39k_orJNO3x8dxhLZQTu1KHRGFb3It6KJlKwOYrdeOVyJnA2KcqhZ-7u69YhWvDIp4w");
        requestWrapper.setRequest(linkedConsentRequestV2);

        LinkedConsentResponse linkedConsentResponse = new LinkedConsentResponse();
        Mockito.when(linkedAuthorizationService.saveConsentV2(Mockito.any(LinkedConsentRequestV2.class))).thenReturn(linkedConsentResponse);

        mockMvc.perform(post("/linked-authorization/v2/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").exists())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    public void saveConsentV2_withException_thenFail() throws Exception {
        RequestWrapper<LinkedConsentRequestV2> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("link-transaction-id");
        requestWrapper.setRequest(linkedConsentRequestV2);

        Mockito.when(linkedAuthorizationService.saveConsentV2(Mockito.any(LinkedConsentRequestV2.class))).thenThrow(EsignetException.class);

        mockMvc.perform(post("/linked-authorization/v2/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    public void saveConsentV2_withInvalidTransactionId_thenFail() throws Exception {
        RequestWrapper<LinkedConsentRequestV2> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("  ");
        linkedConsentRequestV2.setSignature("eyJ4NXQjUzI1NiI6InpCRm1ILW94QTJUczdtLTI2V3ZaTTFyaG9HckFuRXdpX3hLcHBoTFEzWnciLCJhbGciOiJSUzI1NiJ9.BYOnWu4gyzPluh5H6bWsznWSD39WPl_YcWmjGff6j0-CGlDwfq61VsDCQp1lZp0GOZj8ebHIhWJndg2UotRjBnw1HXjRL3UFTMgf3WoTecQsDQKjAE8HCUwYbtF7j1wYha5o5P2Ah-CVJhgVbY947ZoKFo7w1ER0Dgjc_GHESHuCkly_KFrw2Nd0MNtBmkkrhr01QGjM62LbLf_UrTyIapQbu8tSTPIcpScM-2cLNaT7PdA0KXedPOVDLKrcz7EpG4xgpg9uUZ6uxs10spp39k_orJNO3x8dxhLZQTu1KHRGFb3It6KJlKwOYrdeOVyJnA2KcqhZ-7u69YhWvDIp4w");
        requestWrapper.setRequest(linkedConsentRequestV2);

        mockMvc.perform(post("/linked-authorization/v2/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_TRANSACTION_ID));
    }

    @Test
    public void saveConsentV2_withInvalidAcceptedClaims_thenFail() throws Exception {
        RequestWrapper<LinkedConsentRequestV2> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("transaction-id");
        linkedConsentRequestV2.setAcceptedClaims(Arrays.asList("","names"));
        linkedConsentRequestV2.setSignature("eyJ4NXQjUzI1NiI6InpCRm1ILW94QTJUczdtLTI2V3ZaTTFyaG9HckFuRXdpX3hLcHBoTFEzWnciLCJhbGciOiJSUzI1NiJ9.BYOnWu4gyzPluh5H6bWsznWSD39WPl_YcWmjGff6j0-CGlDwfq61VsDCQp1lZp0GOZj8ebHIhWJndg2UotRjBnw1HXjRL3UFTMgf3WoTecQsDQKjAE8HCUwYbtF7j1wYha5o5P2Ah-CVJhgVbY947ZoKFo7w1ER0Dgjc_GHESHuCkly_KFrw2Nd0MNtBmkkrhr01QGjM62LbLf_UrTyIapQbu8tSTPIcpScM-2cLNaT7PdA0KXedPOVDLKrcz7EpG4xgpg9uUZ6uxs10spp39k_orJNO3x8dxhLZQTu1KHRGFb3It6KJlKwOYrdeOVyJnA2KcqhZ-7u69YhWvDIp4w");
        requestWrapper.setRequest(linkedConsentRequestV2);

        mockMvc.perform(post("/linked-authorization/v2/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_ACCEPTED_CLAIM));
    }

    @Test
    public void saveConsentV2_withInvalidPermittedAuthorizeScopes_thenFail() throws Exception {
        RequestWrapper<LinkedConsentRequestV2> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("transaction-id");
        linkedConsentRequestV2.setPermittedAuthorizeScopes(Arrays.asList("","openid"));
        linkedConsentRequestV2.setSignature("eyJ4NXQjUzI1NiI6InpCRm1ILW94QTJUczdtLTI2V3ZaTTFyaG9HckFuRXdpX3hLcHBoTFEzWnciLCJhbGciOiJSUzI1NiJ9.BYOnWu4gyzPluh5H6bWsznWSD39WPl_YcWmjGff6j0-CGlDwfq61VsDCQp1lZp0GOZj8ebHIhWJndg2UotRjBnw1HXjRL3UFTMgf3WoTecQsDQKjAE8HCUwYbtF7j1wYha5o5P2Ah-CVJhgVbY947ZoKFo7w1ER0Dgjc_GHESHuCkly_KFrw2Nd0MNtBmkkrhr01QGjM62LbLf_UrTyIapQbu8tSTPIcpScM-2cLNaT7PdA0KXedPOVDLKrcz7EpG4xgpg9uUZ6uxs10spp39k_orJNO3x8dxhLZQTu1KHRGFb3It6KJlKwOYrdeOVyJnA2KcqhZ-7u69YhWvDIp4w");
        requestWrapper.setRequest(linkedConsentRequestV2);

        mockMvc.perform(post("/linked-authorization/v2/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_PERMITTED_SCOPE));
    }

    @Test
    public void saveConsentV2_withInvalidSignatureFormat_thenFail() throws Exception {
        RequestWrapper<LinkedConsentRequestV2> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("transaction-id");
        linkedConsentRequestV2.setPermittedAuthorizeScopes(Arrays.asList("email","openid"));
        linkedConsentRequestV2.setSignature("invalid-signature");
        requestWrapper.setRequest(linkedConsentRequestV2);

        mockMvc.perform(post("/linked-authorization/v2/consent")
                        .content(objectMapper.writeValueAsString(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].errorCode").value(INVALID_SIGNATURE_FORMAT));
    }
}
