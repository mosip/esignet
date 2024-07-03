/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;


import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.dto.claim.ClaimDetail;
import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.DuplicateLinkCodeException;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.core.util.KafkaHelperService;
import io.mosip.esignet.core.util.LinkCodeQueue;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.mosip.esignet.core.spi.TokenService.ACR;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LinkedAuthorizationServiceTest {

    @InjectMocks
    private LinkedAuthorizationServiceImpl linkedAuthorizationService;

    @Mock
    private CacheUtilService cacheUtilService;

    @Mock
    private ClientManagementService clientManagementService;

    @Mock
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Mock
    private KafkaHelperService kafkaHelperService;

    @Mock
    Authenticator authenticationWrapper;

    @Mock
    AuthorizationHelperService authorizationHelperService;

    @Mock
    AuditPlugin auditWrapper;

    @Mock
    ConsentHelperService consentHelperService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        AuthorizationHelperService authorizationHelperService = new AuthorizationHelperService();
        ReflectionTestUtils.setField(authorizationHelperService, "authorizeScopes", Arrays.asList("resident-service"));
        ReflectionTestUtils.setField(authorizationHelperService, "authenticationContextClassRefUtil", authenticationContextClassRefUtil);
        ReflectionTestUtils.setField(authorizationHelperService, "authenticationWrapper", authenticationWrapper);
        ReflectionTestUtils.setField(authorizationHelperService, "auditWrapper", auditWrapper);
        ReflectionTestUtils.setField(authorizationHelperService, "cacheUtilService", cacheUtilService);

        ReflectionTestUtils.setField(linkedAuthorizationService, "authorizationHelperService", authorizationHelperService);
        ReflectionTestUtils.setField(linkedAuthorizationService, "linkCodeExpiryInSeconds", 60);
    }

    @Test
    public void generateLinkCode_withValidInputs_thenPass() {
        String transactionId = "transaction-id";
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setCurrentLinkCodeLimit(3);
        transaction.setLinkCodeQueue(new LinkCodeQueue(2));
        Mockito.when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(transaction);

        LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
        linkCodeRequest.setTransactionId(transactionId);
        LinkCodeResponse linkCodeResponse = linkedAuthorizationService.generateLinkCode(linkCodeRequest);
        Assert.assertNotNull(linkCodeResponse);
        Assert.assertEquals(transactionId, linkCodeResponse.getTransactionId());
        Assert.assertEquals(2, transaction.getCurrentLinkCodeLimit());
        Assert.assertTrue(transaction.getLinkCodeQueue().size() == 1);

        linkCodeResponse = linkedAuthorizationService.generateLinkCode(linkCodeRequest);
        Assert.assertNotNull(linkCodeResponse);
        Assert.assertEquals(transactionId, linkCodeResponse.getTransactionId());
        Assert.assertEquals(1, transaction.getCurrentLinkCodeLimit());
        Assert.assertTrue(transaction.getLinkCodeQueue().size() == 2);

        linkCodeResponse = linkedAuthorizationService.generateLinkCode(linkCodeRequest);
        Assert.assertNotNull(linkCodeResponse);
        Assert.assertEquals(transactionId, linkCodeResponse.getTransactionId());
        Assert.assertEquals(0, transaction.getCurrentLinkCodeLimit());
        Assert.assertTrue(transaction.getLinkCodeQueue().size() == 2);
    }

    @Test
    public void generateLinkCode_withInvalidTransactionId_thenFail() {
        LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
        linkCodeRequest.setTransactionId("invalid_transaction_id");
        try {
            linkedAuthorizationService.generateLinkCode(linkCodeRequest);
            Assert.fail();
        } catch (InvalidTransactionException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void generateLinkCode_withExhaustedLinkCodeLimit_thenFail() {
        String transactionId = "transaction-id";
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setCurrentLinkCodeLimit(0);
        transaction.setLinkCodeQueue(new LinkCodeQueue(2));
        Mockito.when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(transaction);

        LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
        linkCodeRequest.setTransactionId(transactionId);
        try {
            linkedAuthorizationService.generateLinkCode(linkCodeRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.LINK_CODE_LIMIT_REACHED, ex.getErrorCode());
        }
    }

    @Test
    public void generateLinkCode_withDuplicateLinkCodeExceptionOnce_thenPass() {
        String transactionId = "transaction-id";
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setCurrentLinkCodeLimit(3);
        transaction.setLinkCodeQueue(new LinkCodeQueue(2));
        Mockito.when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(transaction);

        Mockito.doThrow(new DuplicateLinkCodeException())
                .doNothing()
                .when(cacheUtilService).setLinkCodeGenerated(Mockito.anyString(), Mockito.any());

        LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
        linkCodeRequest.setTransactionId(transactionId);
        LinkCodeResponse linkCodeResponse = linkedAuthorizationService.generateLinkCode(linkCodeRequest);
        Assert.assertNotNull(linkCodeResponse);
        Assert.assertEquals(transactionId, linkCodeResponse.getTransactionId());
        Assert.assertEquals(2, transaction.getCurrentLinkCodeLimit());
        Assert.assertTrue(transaction.getLinkCodeQueue().size() == 1);
    }

    @Test
    public void generateLinkCode_withDuplicateLinkCodeExceptionMoreThanOnce_thenFail() {
        String transactionId = "transaction-id";
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setCurrentLinkCodeLimit(3);
        transaction.setLinkCodeQueue(new LinkCodeQueue(2));
        Mockito.when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(transaction);

        Mockito.doThrow(new DuplicateLinkCodeException())
                .doThrow(new DuplicateLinkCodeException())
                .when(cacheUtilService).setLinkCodeGenerated(Mockito.anyString(), Mockito.any());

        LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
        linkCodeRequest.setTransactionId(transactionId);
        try {
            linkedAuthorizationService.generateLinkCode(linkCodeRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.LINK_CODE_GEN_FAILED, ex.getErrorCode());
        }
    }

    @Test
    public void linkTransaction_withValidInput_thenPass() {
        String transactionId = "transaction-id";
        Claims claims = new Claims();
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setValues(new String[]{"mosip:idp:acr:static-code"});
        Map<String, ClaimDetail> map = new HashMap<>();
        map.put(ACR, claimDetail);
        claims.setId_token(map);
        Map<String, ClaimDetail> userinfoMap = new HashMap<>();
        userinfoMap.put("name", new ClaimDetail());
        userinfoMap.get("name").setEssential(true);
        userinfoMap.put("phone_number", new ClaimDetail());
        claims.setUserinfo(userinfoMap);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setRequestedClaims(claims);

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata(transactionId, null);
        Mockito.when(cacheUtilService.getLinkCodeGenerated(Mockito.anyString())).thenReturn(linkTransactionMetadata);
        Mockito.when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(oidcTransaction);

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setLogoUri("https://test-client-portal/logo.png");
        when(clientManagementService.getClientDetails(oidcTransaction.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.setLinkedTransaction(transactionId, oidcTransaction)).thenReturn(oidcTransaction);
        when(authenticationContextClassRefUtil.getAuthFactors(claimDetail.getValues())).thenReturn(new ArrayList<>());

        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        LinkTransactionResponseV1 linkTransactionResponse = linkedAuthorizationService.linkTransaction(linkTransactionRequest);
        Assert.assertNotNull(linkTransactionResponse);
        Assert.assertEquals(clientDetail.getName().get(Constants.NONE_LANG_KEY), linkTransactionResponse.getClientName());
        Assert.assertEquals(clientDetail.getLogoUri(), linkTransactionResponse.getLogoUrl());
        Assert.assertNotNull(linkTransactionResponse.getLinkTransactionId());
    }

    @Test
    public void linkTransaction_withInvalidLinkCode_thenFail() {
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        try {
            linkedAuthorizationService.linkTransaction(linkTransactionRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_LINK_CODE, ex.getErrorCode());
        }
    }

    @Test
    public void linkTransaction_withInvalidTransactionId_thenFail() {
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("transaction-id", null);
        Mockito.when(cacheUtilService.getLinkCodeGenerated(Mockito.anyString())).thenReturn(linkTransactionMetadata);

        try {
            linkedAuthorizationService.linkTransaction(linkTransactionRequest);
            Assert.fail();
        } catch (InvalidTransactionException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void linkTransactionV2_withValidInput_thenPass() {
        String transactionId = "transaction-id";
        Claims claims = new Claims();
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setValues(new String[]{"mosip:idp:acr:static-code"});
        Map<String, ClaimDetail> map = new HashMap<>();
        map.put(ACR, claimDetail);
        claims.setId_token(map);
        Map<String, ClaimDetail> userinfoMap = new HashMap<>();
        userinfoMap.put("name", new ClaimDetail());
        userinfoMap.get("name").setEssential(true);
        userinfoMap.put("phone_number", new ClaimDetail());
        claims.setUserinfo(userinfoMap);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setRequestedClaims(claims);

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata(transactionId, null);
        Mockito.when(cacheUtilService.getLinkCodeGenerated(Mockito.anyString())).thenReturn(linkTransactionMetadata);
        Mockito.when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(oidcTransaction);

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setLogoUri("https://test-client-portal/logo.png");
        when(clientManagementService.getClientDetails(oidcTransaction.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.setLinkedTransaction(transactionId, oidcTransaction)).thenReturn(oidcTransaction);
        when(authenticationContextClassRefUtil.getAuthFactors(claimDetail.getValues())).thenReturn(new ArrayList<>());

        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        LinkTransactionResponseV2 linkTransactionResponseV2 = linkedAuthorizationService.linkTransactionV2(linkTransactionRequest);
        Assert.assertNotNull(linkTransactionResponseV2);
        Assert.assertEquals(clientDetail.getName(), linkTransactionResponseV2.getClientName());
        Assert.assertEquals(clientDetail.getLogoUri(), linkTransactionResponseV2.getLogoUrl());
        Assert.assertNotNull(linkTransactionResponseV2.getLinkTransactionId());
    }

    @Test
    public void linkTransactionV2_withInvalidLinkCode_thenFail() {
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        try {
            linkedAuthorizationService.linkTransactionV2(linkTransactionRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_LINK_CODE, ex.getErrorCode());
        }
    }

    @Test
    public void linkTransactionV2_withInvalidTransactionId_thenFail() {
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("transaction-id", null);
        Mockito.when(cacheUtilService.getLinkCodeGenerated(Mockito.anyString())).thenReturn(linkTransactionMetadata);

        try {
            linkedAuthorizationService.linkTransactionV2(linkTransactionRequest);
            Assert.fail();
        } catch (InvalidTransactionException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void sendOtp_withValidInput_thenPass() throws SendOtpException {
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setTransactionId("link-transaction-id");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setLinkedTransactionId("link-transaction-id");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("relying-party-id");
        oidcTransaction.setClientId("client-id");
        Mockito.when(cacheUtilService.getLinkedSessionTransaction("link-transaction-id")).thenReturn(oidcTransaction);

        SendOtpResult sendOtpResult = new SendOtpResult(oidcTransaction.getAuthTransactionId(), "masked-email", " masked-mobile");
        Mockito.when(authenticationWrapper.sendOtp(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(sendOtpResult);

        OtpResponse otpResponse = linkedAuthorizationService.sendOtp(otpRequest);
        Assert.assertEquals(otpResponse.getTransactionId(), otpResponse.getTransactionId());
        Assert.assertEquals(sendOtpResult.getMaskedEmail(), otpResponse.getMaskedEmail());
        Assert.assertEquals(sendOtpResult.getMaskedMobile(), otpResponse.getMaskedMobile());
    }

    @Test
    public void sendOtp_withInvalidTransaction_thenPass() {
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setTransactionId("link-transaction-id");

        try {
            linkedAuthorizationService.sendOtp(otpRequest);
            Assert.fail();
        } catch (InvalidTransactionException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void authenticateUser_withValidInput_thenPass() throws KycAuthException {
        LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
        linkedKycAuthRequest.setLinkedTransactionId("link-transaction-id");

        OIDCTransaction oidcTransaction = createIdpTransaction(new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"});
        oidcTransaction.setLinkedTransactionId("link-transaction-id");
        Mockito.when(cacheUtilService.getLinkedSessionTransaction(linkedKycAuthRequest.getLinkedTransactionId())).thenReturn(oidcTransaction);

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken("test-kyc-token");
        kycAuthResult.setPartnerSpecificUserToken("test-psut");
        when(authenticationWrapper.doKycAuth(anyString(), anyString(), anyBoolean(), any())).thenReturn(kycAuthResult);

        linkedKycAuthRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("OTP"));
        linkedKycAuthRequest.setChallengeList(authChallenges);

        LinkedKycAuthResponse authResponse = linkedAuthorizationService.authenticateUser(linkedKycAuthRequest);
        Assert.assertNotNull(authResponse);
        Assert.assertEquals(linkedKycAuthRequest.getLinkedTransactionId(), authResponse.getLinkedTransactionId());
    }

    @Test
    public void authenticateUser_withInvalidTransaction_thenFail() {
        LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
        linkedKycAuthRequest.setLinkedTransactionId("link-transaction-id");

        try {
            linkedAuthorizationService.authenticateUser(linkedKycAuthRequest);
            Assert.fail();
        } catch (InvalidTransactionException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void authenticateUserV2_withValidInput_thenPass() throws KycAuthException {
        LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
        linkedKycAuthRequest.setLinkedTransactionId("link-transaction-id");

        OIDCTransaction oidcTransaction = createIdpTransaction(new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"});
        oidcTransaction.setConsentAction(ConsentAction.NOCAPTURE);
        oidcTransaction.setLinkedTransactionId("link-transaction-id");
        Mockito.when(cacheUtilService.getLinkedSessionTransaction(linkedKycAuthRequest.getLinkedTransactionId())).thenReturn(oidcTransaction);

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken("test-kyc-token");
        kycAuthResult.setPartnerSpecificUserToken("test-psut");
        when(authenticationWrapper.doKycAuth(anyString(), anyString(), anyBoolean(), any())).thenReturn(kycAuthResult);

        linkedKycAuthRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("OTP"));
        linkedKycAuthRequest.setChallengeList(authChallenges);

        LinkedKycAuthResponseV2 authResponse = linkedAuthorizationService.authenticateUserV2(linkedKycAuthRequest);
        Assert.assertNotNull(authResponse);
        Assert.assertEquals(linkedKycAuthRequest.getLinkedTransactionId(), authResponse.getLinkedTransactionId());
    }

    @Test
    public void authenticateUserV2_withInvalidTransaction_thenFail() {
        LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
        linkedKycAuthRequest.setLinkedTransactionId("link-transaction-id");

        try {
            linkedAuthorizationService.authenticateUser(linkedKycAuthRequest);
            Assert.fail();
        } catch (InvalidTransactionException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void saveConsent_withValidInput_thenPass() {
        Mockito.when(cacheUtilService.getLinkedAuthTransaction("link-transaction-id")).thenReturn(new OIDCTransaction());
        LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
        linkedConsentRequest.setLinkedTransactionId("link-transaction-id");
        LinkedConsentResponse linkedConsentResponse = linkedAuthorizationService.saveConsent(linkedConsentRequest);
        Assert.assertNotNull(linkedConsentResponse);
        Assert.assertEquals(linkedConsentRequest.getLinkedTransactionId(), linkedConsentResponse.getLinkedTransactionId());
    }

    @Test
    public void saveConsent_withInvalidTransaction_thenFail() {
        LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
        linkedConsentRequest.setLinkedTransactionId("link-transaction-id");
        try {
            linkedAuthorizationService.saveConsent(linkedConsentRequest);
            Assert.fail();
        } catch (InvalidTransactionException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void saveConsentV2_withValidInput_thenPass() {
        Mockito.when(cacheUtilService.getLinkedAuthTransaction("link-transaction-id")).thenReturn(new OIDCTransaction());
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("link-transaction-id");
        LinkedConsentResponse linkedConsentResponse = linkedAuthorizationService.saveConsentV2(linkedConsentRequestV2);
        Assert.assertNotNull(linkedConsentResponse);
        Assert.assertEquals(linkedConsentRequestV2.getLinkedTransactionId(), linkedConsentResponse.getLinkedTransactionId());
    }

    @Test
    public void saveConsentV2_withInvalidTransaction_thenFail() {
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("link-transaction-id");
        try {
            linkedAuthorizationService.saveConsentV2(linkedConsentRequestV2);
            Assert.fail();
        } catch (InvalidTransactionException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void linkStatus_withValidInput_thenPass() {
        LinkStatusRequest linkStatusRequest = new LinkStatusRequest();
        linkStatusRequest.setLinkCode("link-code");
        linkStatusRequest.setTransactionId("transaction-id");

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("transaction-id", "link-transaction-id");
        Mockito.when(cacheUtilService.getLinkCodeGenerated(Mockito.anyString())).thenReturn(linkTransactionMetadata);

        DeferredResult<ResponseWrapper<LinkStatusResponse>> deferredResult = new DeferredResult(3l*1000);
        setTimeoutHandler(deferredResult);
        setErrorHandler(deferredResult);
        linkedAuthorizationService.getLinkStatus(deferredResult, linkStatusRequest);
        Assert.assertTrue(deferredResult.isSetOrExpired());
        Assert.assertNotNull(deferredResult.getResult());
        ResponseWrapper<LinkStatusResponse> responseWrapper = (ResponseWrapper<LinkStatusResponse>) deferredResult.getResult();
        Assert.assertNotNull(responseWrapper.getResponse());
        Assert.assertTrue(responseWrapper.getResponse().getLinkStatus().equals("LINKED"));
    }

    @Test
    public void linkStatus_withInvalidLinkCode_thenFail() {
        LinkStatusRequest linkStatusRequest = new LinkStatusRequest();
        linkStatusRequest.setLinkCode("link-code");
        linkStatusRequest.setTransactionId("transaction-id");

        DeferredResult<ResponseWrapper<LinkStatusResponse>> deferredResult = new DeferredResult(3l*1000);
        setTimeoutHandler(deferredResult);
        setErrorHandler(deferredResult);
        try {
            linkedAuthorizationService.getLinkStatus(deferredResult, linkStatusRequest);
        } catch (EsignetException ex) {
            Assert.assertEquals(ex.getErrorCode(), ErrorConstants.INVALID_LINK_CODE);
        }
    }

    @Test
    public void linkStatus_withAlreadyLinkedCode_thenPass() {
        LinkStatusRequest linkStatusRequest = new LinkStatusRequest();
        linkStatusRequest.setLinkCode("link-code");
        linkStatusRequest.setTransactionId("transaction-id");

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("transaction-id", "link-transaction-id");
        Mockito.when(cacheUtilService.getLinkedTransactionMetadata(Mockito.anyString())).thenReturn(linkTransactionMetadata);

        DeferredResult<ResponseWrapper<LinkStatusResponse>> deferredResult = new DeferredResult(3l*1000);
        setTimeoutHandler(deferredResult);
        setErrorHandler(deferredResult);
        linkedAuthorizationService.getLinkStatus(deferredResult, linkStatusRequest);
        Assert.assertTrue(deferredResult.isSetOrExpired());
        Assert.assertNotNull(deferredResult.getResult());
        ResponseWrapper<LinkStatusResponse> responseWrapper = (ResponseWrapper<LinkStatusResponse>) deferredResult.getResult();
        Assert.assertNotNull(responseWrapper.getResponse());
        Assert.assertTrue(responseWrapper.getResponse().getLinkStatus().equals("LINKED"));
    }

    @Test
    public void linkStatus_withNotLinked_thenFail() {
        LinkStatusRequest linkStatusRequest = new LinkStatusRequest();
        linkStatusRequest.setLinkCode("link-code");
        linkStatusRequest.setTransactionId("transaction-id");

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("transaction-id", null);
        Mockito.when(cacheUtilService.getLinkCodeGenerated(Mockito.anyString())).thenReturn(linkTransactionMetadata);

        DeferredResult<ResponseWrapper<LinkStatusResponse>> deferredResult = new DeferredResult(1l*1000);
        setTimeoutHandler(deferredResult);
        setErrorHandler(deferredResult);
        linkedAuthorizationService.getLinkStatus(deferredResult, linkStatusRequest);
        Assert.assertNull(deferredResult.getResult());
    }

    @Test
    public void getLinkCode_withValidInput_thenPass() {
        LinkAuthCodeRequest linkAuthCodeRequest = new LinkAuthCodeRequest();
        linkAuthCodeRequest.setLinkedCode("linked-code");
        linkAuthCodeRequest.setTransactionId("transaction-id");

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("transaction-id", "linked-transaction-id");
        Mockito.when(cacheUtilService.getLinkedTransactionMetadata(Mockito.anyString())).thenReturn(linkTransactionMetadata);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setNonce("nonce");
        oidcTransaction.setState("state");
        oidcTransaction.setRedirectUri("http://test-redirect-uri/test");
        Mockito.when(cacheUtilService.getConsentedTransaction(linkTransactionMetadata.getLinkedTransactionId())).thenReturn(oidcTransaction);

        DeferredResult<ResponseWrapper<LinkAuthCodeResponse>> deferredResult = new DeferredResult(1l*1000);
        setTimeoutHandler(deferredResult);
        setErrorHandler(deferredResult);
        linkedAuthorizationService.getLinkAuthCode(deferredResult, linkAuthCodeRequest);
        Assert.assertTrue(deferredResult.isSetOrExpired());
        Assert.assertNotNull(deferredResult.getResult());
        ResponseWrapper<LinkAuthCodeResponse> responseWrapper = (ResponseWrapper<LinkAuthCodeResponse>) deferredResult.getResult();
        Assert.assertNotNull(responseWrapper.getResponse());
        Assert.assertNotNull(responseWrapper.getResponse().getCode());
        Assert.assertEquals(oidcTransaction.getNonce(), responseWrapper.getResponse().getNonce());
        Assert.assertEquals(oidcTransaction.getState(), responseWrapper.getResponse().getState());
        Assert.assertEquals(oidcTransaction.getRedirectUri(), responseWrapper.getResponse().getRedirectUri());
    }

    @Test
    public void getLinkCode_withUnLinkedTransaction_thenFail() {
        LinkAuthCodeRequest linkAuthCodeRequest = new LinkAuthCodeRequest();
        linkAuthCodeRequest.setLinkedCode("linked-code");
        linkAuthCodeRequest.setTransactionId("transaction-id");

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("transaction-id", null);
        Mockito.when(cacheUtilService.getLinkedTransactionMetadata(Mockito.anyString())).thenReturn(linkTransactionMetadata);

        DeferredResult<ResponseWrapper<LinkAuthCodeResponse>> deferredResult = new DeferredResult(1l*1000);
        setTimeoutHandler(deferredResult);
        setErrorHandler(deferredResult);
        try {
            linkedAuthorizationService.getLinkAuthCode(deferredResult, linkAuthCodeRequest);
        } catch (EsignetException ex) {
            Assert.assertEquals(ex.getErrorCode(), ErrorConstants.INVALID_LINK_CODE);
        }
    }

    @Test
    public void getLinkCode_withUnConsentedTransaction_thenFail() {
        LinkAuthCodeRequest linkAuthCodeRequest = new LinkAuthCodeRequest();
        linkAuthCodeRequest.setLinkedCode("linked-code");
        linkAuthCodeRequest.setTransactionId("transaction-id");

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("transaction-id", "linked-transaction-id");
        Mockito.when(cacheUtilService.getLinkedTransactionMetadata(Mockito.anyString())).thenReturn(linkTransactionMetadata);

        DeferredResult<ResponseWrapper<LinkAuthCodeResponse>> deferredResult = new DeferredResult(1l*1000);
        setTimeoutHandler(deferredResult);
        setErrorHandler(deferredResult);
        linkedAuthorizationService.getLinkAuthCode(deferredResult, linkAuthCodeRequest);
        Assert.assertNull(deferredResult.getResult());
    }

    private OIDCTransaction createIdpTransaction(String[] acrs) {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        Map<String, ClaimDetail> idClaims = new HashMap<>();
        idClaims.put(ACR, new ClaimDetail(null, acrs, false));
        Claims requestedClaims = new Claims();
        requestedClaims.setId_token(idClaims);
        oidcTransaction.setRequestedClaims(requestedClaims);
        oidcTransaction.setClientId("test-client");
        oidcTransaction.setRelyingPartyId("test-rp-client");
        return oidcTransaction;
    }

    private AuthChallenge getAuthChallengeDto(String type) {
        AuthChallenge auth = new AuthChallenge();
        auth.setAuthFactorType(type);
        auth.setChallenge("111111");
        return auth;
    }

    private List<AuthenticationFactor> getAuthFactors(String acr) {
        List<AuthenticationFactor> acrAuthFactors = new ArrayList<>();
        switch (acr){
            case "mosip:idp:acr:generated-code":
                acrAuthFactors.add(new AuthenticationFactor("OTP", 0, null));
                break;
            case "mosip:idp:acr:static-code":
                acrAuthFactors.add(new AuthenticationFactor("PIN", 0, null));
                break;
            case "mosip:idp:acr:linked-wallet":
                acrAuthFactors.add(new AuthenticationFactor("LFA", 0, null));
                break;
            case "mosip:idp:acr:biometrics":
                acrAuthFactors.add(new AuthenticationFactor("BIO", 0, null));
                break;
            case "mosip:idp:acr:biometrics-generated-code":
                acrAuthFactors.add(new AuthenticationFactor("BIO", 0, null));
                acrAuthFactors.add(new AuthenticationFactor("OTP", 0, null));
                break;
            case "mosip:idp:acr:biometrics-static-code":
                acrAuthFactors.add(new AuthenticationFactor("L1-bio-device", 0, null));
                acrAuthFactors.add(new AuthenticationFactor("PIN", 0, null));
                break;
        }
        return acrAuthFactors;
    }

    private void setTimeoutHandler(DeferredResult deferredResult) {
        deferredResult.onTimeout(new Runnable() {
            @Override
            public void run() {
                ResponseWrapper responseWrapper = new ResponseWrapper();
                responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
                responseWrapper.setErrors(new ArrayList<>());
                responseWrapper.getErrors().add(new Error(ErrorConstants.RESPONSE_TIMEOUT, ErrorConstants.RESPONSE_TIMEOUT));
                deferredResult.setErrorResult(responseWrapper);
            }
        });
    }

    private void setErrorHandler(DeferredResult deferredResult) {
        deferredResult.onError(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                ResponseWrapper responseWrapper = new ResponseWrapper();
                responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
                responseWrapper.setErrors(new ArrayList<>());
                if(throwable instanceof EsignetException) {
                    String errorCode = ((EsignetException) throwable).getErrorCode();
                    responseWrapper.getErrors().add(new Error(errorCode, errorCode));
                } else
                    responseWrapper.getErrors().add(new Error(ErrorConstants.UNKNOWN_ERROR,ErrorConstants.UNKNOWN_ERROR));
                deferredResult.setErrorResult(responseWrapper);
            }
        });
    }
}
