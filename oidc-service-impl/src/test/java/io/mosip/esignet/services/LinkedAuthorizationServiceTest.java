/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.api.util.FilterCriteriaMatcher;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.*;
import java.util.function.Consumer;

import static io.mosip.esignet.core.spi.TokenService.ACR;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

    @Mock
    ClaimsHelperService claimsHelperService;

    @Mock
    FilterCriteriaMatcher filterCriteriaMatcher;

    @BeforeEach
    public void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(filterCriteriaMatcher, "objectMapper", objectMapper);

        ReflectionTestUtils.setField(claimsHelperService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(claimsHelperService, "filterCriteriaMatcher", filterCriteriaMatcher);

        AuthorizationHelperService authorizationHelperService = new AuthorizationHelperService();
        ReflectionTestUtils.setField(authorizationHelperService, "authorizeScopes", Arrays.asList("resident-service"));
        ReflectionTestUtils.setField(authorizationHelperService, "authenticationContextClassRefUtil", authenticationContextClassRefUtil);
        ReflectionTestUtils.setField(authorizationHelperService, "authenticationWrapper", authenticationWrapper);
        ReflectionTestUtils.setField(authorizationHelperService, "auditWrapper", auditWrapper);
        ReflectionTestUtils.setField(authorizationHelperService, "cacheUtilService", cacheUtilService);
        ReflectionTestUtils.setField(authorizationHelperService, "claimsHelperService", claimsHelperService);

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
        Assertions.assertNotNull(linkCodeResponse);
        Assertions.assertEquals(transactionId, linkCodeResponse.getTransactionId());
        Assertions.assertEquals(2, transaction.getCurrentLinkCodeLimit());
        Assertions.assertTrue(transaction.getLinkCodeQueue().size() == 1);

        linkCodeResponse = linkedAuthorizationService.generateLinkCode(linkCodeRequest);
        Assertions.assertNotNull(linkCodeResponse);
        Assertions.assertEquals(transactionId, linkCodeResponse.getTransactionId());
        Assertions.assertEquals(1, transaction.getCurrentLinkCodeLimit());
        Assertions.assertTrue(transaction.getLinkCodeQueue().size() == 2);

        linkCodeResponse = linkedAuthorizationService.generateLinkCode(linkCodeRequest);
        Assertions.assertNotNull(linkCodeResponse);
        Assertions.assertEquals(transactionId, linkCodeResponse.getTransactionId());
        Assertions.assertEquals(0, transaction.getCurrentLinkCodeLimit());
        Assertions.assertTrue(transaction.getLinkCodeQueue().size() == 2);
    }

    @Test
    public void generateLinkCode_withInvalidTransactionId_thenFail() {
        LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
        linkCodeRequest.setTransactionId("invalid_transaction_id");
        try {
            linkedAuthorizationService.generateLinkCode(linkCodeRequest);
            Assertions.fail();
        } catch (InvalidTransactionException ex) {
            Assertions.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
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
            Assertions.fail();
        } catch (EsignetException ex) {
            Assertions.assertEquals(ErrorConstants.LINK_CODE_LIMIT_REACHED, ex.getErrorCode());
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
        Assertions.assertNotNull(linkCodeResponse);
        Assertions.assertEquals(transactionId, linkCodeResponse.getTransactionId());
        Assertions.assertEquals(2, transaction.getCurrentLinkCodeLimit());
        Assertions.assertTrue(transaction.getLinkCodeQueue().size() == 1);
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
            Assertions.fail();
        } catch (EsignetException ex) {
            Assertions.assertEquals(ErrorConstants.LINK_CODE_GEN_FAILED, ex.getErrorCode());
        }
    }

    @Test
    public void linkTransaction_withValidInput_thenPass() {
        String transactionId = "transaction-id";
        Claims claims = new Claims();
        Map<String, Object> claimDetail = new HashMap<>();
        claimDetail.put("values", new String[]{"mosip:idp:acr:static-code"});
        Map<String, Map<String, Object>> map = new HashMap<>();
        map.put(ACR, claimDetail);
        claims.setId_token(map);
        Map<String, List<Map<String, Object>>> userinfoMap = new HashMap<>();
        Map<String, Object> nameClaimDetail = new HashMap<>();
        nameClaimDetail.put("essential", true);
        userinfoMap.put("name", Arrays.asList(nameClaimDetail));
        userinfoMap.put("phone_number", Arrays.asList(new HashMap<>()));
        claims.setUserinfo(userinfoMap);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setResolvedClaims(claims);

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata(transactionId, null);
        Mockito.when(cacheUtilService.getLinkCodeGenerated(Mockito.anyString())).thenReturn(linkTransactionMetadata);
        Mockito.when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(oidcTransaction);

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setLogoUri("https://test-client-portal/logo.png");
        when(clientManagementService.getClientDetails(oidcTransaction.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.setLinkedTransaction(transactionId, oidcTransaction)).thenReturn(oidcTransaction);
        when(authenticationContextClassRefUtil.getAuthFactors((String[]) claimDetail.get("values"))).thenReturn(new ArrayList<>());

        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        LinkTransactionResponseV1 linkTransactionResponse = linkedAuthorizationService.linkTransaction(linkTransactionRequest);
        Assertions.assertNotNull(linkTransactionResponse);
        Assertions.assertEquals(clientDetail.getName().get(Constants.NONE_LANG_KEY), linkTransactionResponse.getClientName());
        Assertions.assertEquals(clientDetail.getLogoUri(), linkTransactionResponse.getLogoUrl());
        Assertions.assertNotNull(linkTransactionResponse.getLinkTransactionId());
    }

    @Test
    public void linkTransaction_withInvalidLinkCode_thenFail() {
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        try {
            linkedAuthorizationService.linkTransaction(linkTransactionRequest);
            Assertions.fail();
        } catch (EsignetException ex) {
            Assertions.assertEquals(ErrorConstants.INVALID_LINK_CODE, ex.getErrorCode());
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
            Assertions.fail();
        } catch (InvalidTransactionException ex) {
            Assertions.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void linkTransactionV2_withValidInput_thenPass() {
        String transactionId = "transaction-id";
        Claims claims = new Claims();
        Map<String, Object> claimDetail = new HashMap<>();
        claimDetail.put("values", new String[]{"mosip:idp:acr:static-code"});
        Map<String, Map<String, Object>> map = new HashMap<>();
        map.put(ACR, claimDetail);
        claims.setId_token(map);
        Map<String, List<Map<String, Object>>> userinfoMap = new HashMap<>();
        Map<String, Object> nameClaimDetail = new HashMap<>();
        nameClaimDetail.put("essential", true);
        userinfoMap.put("name", Arrays.asList(nameClaimDetail));
        userinfoMap.put("phone_number", Arrays.asList(new HashMap<>()));
        claims.setUserinfo(userinfoMap);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setResolvedClaims(claims);

        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata(transactionId, null);
        Mockito.when(cacheUtilService.getLinkCodeGenerated(Mockito.anyString())).thenReturn(linkTransactionMetadata);
        Mockito.when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(oidcTransaction);

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setLogoUri("https://test-client-portal/logo.png");
        when(clientManagementService.getClientDetails(oidcTransaction.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.setLinkedTransaction(transactionId, oidcTransaction)).thenReturn(oidcTransaction);
        when(authenticationContextClassRefUtil.getAuthFactors((String[]) claimDetail.get("values"))).thenReturn(new ArrayList<>());

        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        LinkTransactionResponseV2 linkTransactionResponseV2 = linkedAuthorizationService.linkTransactionV2(linkTransactionRequest);
        Assertions.assertNotNull(linkTransactionResponseV2);
        Assertions.assertEquals(clientDetail.getName(), linkTransactionResponseV2.getClientName());
        Assertions.assertEquals(clientDetail.getLogoUri(), linkTransactionResponseV2.getLogoUrl());
        Assertions.assertNotNull(linkTransactionResponseV2.getLinkTransactionId());
    }

    @Test
    public void linkTransactionV2_withInvalidLinkCode_thenFail() {
        LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
        linkTransactionRequest.setLinkCode("link-code");
        try {
            linkedAuthorizationService.linkTransactionV2(linkTransactionRequest);
            Assertions.fail();
        } catch (EsignetException ex) {
            Assertions.assertEquals(ErrorConstants.INVALID_LINK_CODE, ex.getErrorCode());
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
            Assertions.fail();
        } catch (InvalidTransactionException ex) {
            Assertions.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
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
        Assertions.assertEquals(otpResponse.getTransactionId(), otpResponse.getTransactionId());
        Assertions.assertEquals(sendOtpResult.getMaskedEmail(), otpResponse.getMaskedEmail());
        Assertions.assertEquals(sendOtpResult.getMaskedMobile(), otpResponse.getMaskedMobile());
    }

    @Test
    public void sendOtp_withInvalidTransaction_thenPass() {
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setTransactionId("link-transaction-id");

        try {
            linkedAuthorizationService.sendOtp(otpRequest);
            Assertions.fail();
        } catch (InvalidTransactionException ex) {
            Assertions.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
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
        Assertions.assertNotNull(authResponse);
        Assertions.assertEquals(linkedKycAuthRequest.getLinkedTransactionId(), authResponse.getLinkedTransactionId());
    }

    @Test
    public void authenticateUser_withInvalidTransaction_thenFail() {
        LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
        linkedKycAuthRequest.setLinkedTransactionId("link-transaction-id");

        try {
            linkedAuthorizationService.authenticateUser(linkedKycAuthRequest);
            Assertions.fail();
        } catch (InvalidTransactionException ex) {
            Assertions.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
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
        Assertions.assertNotNull(authResponse);
        Assertions.assertEquals(linkedKycAuthRequest.getLinkedTransactionId(), authResponse.getLinkedTransactionId());
    }

    @Test
    public void authenticateUserV2_withInvalidTransaction_thenFail() {
        LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
        linkedKycAuthRequest.setLinkedTransactionId("link-transaction-id");

        try {
            linkedAuthorizationService.authenticateUser(linkedKycAuthRequest);
            Assertions.fail();
        } catch (InvalidTransactionException ex) {
            Assertions.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void saveConsent_withValidInput_thenPass() {
        Mockito.when(cacheUtilService.getLinkedAuthTransaction("link-transaction-id")).thenReturn(new OIDCTransaction());
        LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
        linkedConsentRequest.setLinkedTransactionId("link-transaction-id");
        LinkedConsentResponse linkedConsentResponse = linkedAuthorizationService.saveConsent(linkedConsentRequest);
        Assertions.assertNotNull(linkedConsentResponse);
        Assertions.assertEquals(linkedConsentRequest.getLinkedTransactionId(), linkedConsentResponse.getLinkedTransactionId());
    }

    @Test
    public void saveConsent_withInvalidTransaction_thenFail() {
        LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
        linkedConsentRequest.setLinkedTransactionId("link-transaction-id");
        try {
            linkedAuthorizationService.saveConsent(linkedConsentRequest);
            Assertions.fail();
        } catch (InvalidTransactionException ex) {
            Assertions.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void saveConsentV2_withValidInput_thenPass() {
        Mockito.when(cacheUtilService.getLinkedAuthTransaction("link-transaction-id")).thenReturn(new OIDCTransaction());
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("link-transaction-id");
        LinkedConsentResponse linkedConsentResponse = linkedAuthorizationService.saveConsentV2(linkedConsentRequestV2);
        Assertions.assertNotNull(linkedConsentResponse);
        Assertions.assertEquals(linkedConsentRequestV2.getLinkedTransactionId(), linkedConsentResponse.getLinkedTransactionId());
    }

    @Test
    public void saveConsentV2_withInvalidTransaction_thenFail() {
        LinkedConsentRequestV2 linkedConsentRequestV2 = new LinkedConsentRequestV2();
        linkedConsentRequestV2.setLinkedTransactionId("link-transaction-id");
        try {
            linkedAuthorizationService.saveConsentV2(linkedConsentRequestV2);
            Assertions.fail();
        } catch (InvalidTransactionException ex) {
            Assertions.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
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
        Assertions.assertTrue(deferredResult.isSetOrExpired());
        Assertions.assertNotNull(deferredResult.getResult());
        ResponseWrapper<LinkStatusResponse> responseWrapper = (ResponseWrapper<LinkStatusResponse>) deferredResult.getResult();
        Assertions.assertNotNull(responseWrapper.getResponse());
        Assertions.assertTrue(responseWrapper.getResponse().getLinkStatus().equals("LINKED"));
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
            Assertions.assertEquals(ex.getErrorCode(), ErrorConstants.INVALID_LINK_CODE);
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
        Assertions.assertTrue(deferredResult.isSetOrExpired());
        Assertions.assertNotNull(deferredResult.getResult());
        ResponseWrapper<LinkStatusResponse> responseWrapper = (ResponseWrapper<LinkStatusResponse>) deferredResult.getResult();
        Assertions.assertNotNull(responseWrapper.getResponse());
        Assertions.assertTrue(responseWrapper.getResponse().getLinkStatus().equals("LINKED"));
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
        Assertions.assertNull(deferredResult.getResult());
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
        Assertions.assertTrue(deferredResult.isSetOrExpired());
        Assertions.assertNotNull(deferredResult.getResult());
        ResponseWrapper<LinkAuthCodeResponse> responseWrapper = (ResponseWrapper<LinkAuthCodeResponse>) deferredResult.getResult();
        Assertions.assertNotNull(responseWrapper.getResponse());
        Assertions.assertNotNull(responseWrapper.getResponse().getCode());
        Assertions.assertEquals(oidcTransaction.getNonce(), responseWrapper.getResponse().getNonce());
        Assertions.assertEquals(oidcTransaction.getState(), responseWrapper.getResponse().getState());
        Assertions.assertEquals(oidcTransaction.getRedirectUri(), responseWrapper.getResponse().getRedirectUri());
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
            Assertions.assertEquals(ex.getErrorCode(), ErrorConstants.INVALID_LINK_CODE);
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
        Assertions.assertNull(deferredResult.getResult());
    }

    private OIDCTransaction createIdpTransaction(String[] acrs) {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        Map<String, Map<String, Object>> idClaims = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        map.put("values", acrs);
        idClaims.put(ACR, map);
        Claims requestedClaims = new Claims();
        requestedClaims.setId_token(idClaims);
        oidcTransaction.setResolvedClaims(requestedClaims);
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
