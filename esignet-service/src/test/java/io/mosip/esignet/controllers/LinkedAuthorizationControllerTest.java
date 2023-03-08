package io.mosip.esignet.controllers;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_AUTH_FACTOR_TYPE;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CHALLENGE;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CHALLENGE_FORMAT;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_IDENTIFIER;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_LINK_CODE;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_OTP_CHANNEL;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_TRANSACTION_ID;
import static io.mosip.esignet.core.constants.ErrorConstants.RESPONSE_TIMEOUT;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.core.dto.LinkAuthCodeRequest;
import io.mosip.esignet.core.dto.LinkCodeRequest;
import io.mosip.esignet.core.dto.LinkCodeResponse;
import io.mosip.esignet.core.dto.LinkStatusRequest;
import io.mosip.esignet.core.dto.LinkTransactionRequest;
import io.mosip.esignet.core.dto.LinkTransactionResponse;
import io.mosip.esignet.core.dto.LinkedConsentRequest;
import io.mosip.esignet.core.dto.LinkedConsentResponse;
import io.mosip.esignet.core.dto.LinkedKycAuthRequest;
import io.mosip.esignet.core.dto.LinkedKycAuthResponse;
import io.mosip.esignet.core.dto.OtpRequest;
import io.mosip.esignet.core.dto.OtpResponse;
import io.mosip.esignet.core.dto.RequestWrapper;
import io.mosip.esignet.core.dto.ResponseWrapper;
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
}
