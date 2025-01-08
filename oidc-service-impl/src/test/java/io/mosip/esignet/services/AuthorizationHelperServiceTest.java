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
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.util.CaptchaHelper;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.keymanagerservice.entity.KeyAlias;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
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

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;

import static io.mosip.esignet.api.util.ErrorConstants.AUTH_FAILED;
import static io.mosip.esignet.api.util.ErrorConstants.SEND_OTP_FAILED;
import static io.mosip.esignet.core.constants.Constants.*;
import static io.mosip.esignet.core.constants.ErrorConstants.*;
import static io.mosip.esignet.core.spi.TokenService.ACR;
import static io.mosip.kernel.keymanagerservice.constant.KeymanagerConstant.CURRENTKEYALIAS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationHelperServiceTest {

    @InjectMocks
    private AuthorizationHelperService authorizationHelperService;

    @Mock
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Mock
    private CaptchaHelper captchaHelper;

    @Mock
    private Authenticator authenticationWrapper;

    @Mock
    private CacheUtilService cacheUtilService;

    @Mock
    private KeyStore keyStore;

    @Mock
    private KeymanagerDBHelper dbHelper;

    @Mock
    private AuditPlugin auditWrapper;

    @Mock
    private TokenService tokenService;

    @Mock
    private HttpServletRequest httpServletRequest;

    ObjectMapper objectMapper=new ObjectMapper();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Map<String, List<String>> claims = new HashMap<>();
        claims.put("profile", Arrays.asList("given_name", "profile_picture", "name", "phone_number", "email"));
        claims.put("email", Arrays.asList("email", "email_verified"));
        claims.put("phone", Arrays.asList("phone_number", "phone_number_verified"));
        ClaimsHelperService claimsHelperService = new ClaimsHelperService();
        ReflectionTestUtils.setField(claimsHelperService, "claims", claims);
        ReflectionTestUtils.setField(claimsHelperService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(authorizationHelperService, "claimsHelperService", claimsHelperService);
    }
    
    @Test
    public void validateSendOtpCaptchaToken_withEmptyToken_thenFail() {
        ReflectionTestUtils.setField(authorizationHelperService, "captchaRequired", List.of("send-otp"));
        try {
        	authorizationHelperService.validateSendOtpCaptchaToken("");
        } catch(EsignetException e) {
        	Assert.assertEquals(ErrorConstants.INVALID_CAPTCHA, e.getErrorCode());
        }
    }
    
    @Test
    public void validateSendOtpCaptchaToken_withValidToken_thenFail() {
        ReflectionTestUtils.setField(authorizationHelperService, "captchaRequired", List.of("send-otp"));
        Mockito.when(captchaHelper.validateCaptcha(Mockito.anyString())).thenReturn(false);
        try {
        	authorizationHelperService.validateSendOtpCaptchaToken("captcha-token");
        } catch(EsignetException e) {
        	Assert.assertEquals(ErrorConstants.INVALID_CAPTCHA, e.getErrorCode());
        }
    }
    
    @Test
    public void validateSendOtpCaptchaToken_withValidToken_thenPass() {
        ReflectionTestUtils.setField(authorizationHelperService, "captchaRequired", List.of("send-otp"));
        Mockito.when(captchaHelper.validateCaptcha(Mockito.anyString())).thenReturn(true);
        authorizationHelperService.validateSendOtpCaptchaToken("captcha-token");
    }

    @Test
    public void validateCaptchaToken_withNoValidator_thenFail() {
        ReflectionTestUtils.setField(authorizationHelperService, "captchaHelper", null);
        try {
            authorizationHelperService.validateCaptchaToken("captcha-token");
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(FAILED_TO_VALIDATE_CAPTCHA, e.getErrorCode());
        }
    }

    @Test
    public void validateCaptchaToken_withInvalidToken_thenFail() {
        ReflectionTestUtils.setField(authorizationHelperService, "captchaHelper", captchaHelper);
        Mockito.when(captchaHelper.validateCaptcha(Mockito.anyString())).thenReturn(false);
        try {
            authorizationHelperService.validateCaptchaToken("captcha-token");
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(ErrorConstants.INVALID_CAPTCHA, e.getErrorCode());
        }
    }

    @Test
    public void validateCaptchaToken_withValidToken_thenPass() {
        ReflectionTestUtils.setField(authorizationHelperService, "captchaHelper", captchaHelper);
        Mockito.when(captchaHelper.validateCaptcha(Mockito.anyString())).thenReturn(true);
        authorizationHelperService.validateCaptchaToken("captcha-token");
    }

    @Test
    public void consumeLinkStatus_withValidDetails_thenPass() {
        String linkCodeHash = "link-code-hash";
        DeferredResult<ResponseWrapper<LinkStatusResponse>> deferredResult = new DeferredResult<>();
        authorizationHelperService.addEntryInLinkStatusDeferredResultMap(linkCodeHash, deferredResult);
        authorizationHelperService.consumeLinkStatus(linkCodeHash);
        Assert.assertTrue(deferredResult.hasResult());
        Assert.assertNotNull(((ResponseWrapper<LinkStatusResponse>)deferredResult.getResult()).getResponse());
        Assert.assertEquals(LINKED_STATUS, ((ResponseWrapper<LinkStatusResponse>)deferredResult.getResult()).getResponse().getLinkStatus());
    }

    @Test
    public void consumeLinkAuthCodeStatus_withValidDetails_thenPass() {
        String linkTransactionId = "link-transaction-id";
        DeferredResult<ResponseWrapper<LinkAuthCodeResponse>> deferredResult = new DeferredResult<>();
        authorizationHelperService.addEntryInLinkAuthCodeStatusDeferredResultMap(linkTransactionId, deferredResult);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setNonce("nonce");
        oidcTransaction.setState("state");
        oidcTransaction.setRedirectUri("redirect-uri");
        Mockito.when(cacheUtilService.getConsentedTransaction(linkTransactionId)).thenReturn(oidcTransaction);

        authorizationHelperService.consumeLinkAuthCodeStatus(linkTransactionId);
        Assert.assertTrue(deferredResult.hasResult());
        Assert.assertNotNull(((ResponseWrapper<LinkAuthCodeResponse>)deferredResult.getResult()).getResponse());
        Assert.assertNotNull(((ResponseWrapper<LinkAuthCodeResponse>)deferredResult.getResult()).getResponse().getCode());
        Assert.assertEquals(oidcTransaction.getState(), ((ResponseWrapper<LinkAuthCodeResponse>)deferredResult.getResult()).getResponse().getState());
        Assert.assertEquals(oidcTransaction.getNonce(), ((ResponseWrapper<LinkAuthCodeResponse>)deferredResult.getResult()).getResponse().getNonce());
        Assert.assertEquals(oidcTransaction.getRedirectUri(), ((ResponseWrapper<LinkAuthCodeResponse>)deferredResult.getResult()).getResponse().getRedirectUri());
    }

    @Test
    public void consumeLinkAuthCodeStatus_withInvalidLinkTransactionId_thenFail() {
        String linkTransactionId = "link-transaction-id";
        DeferredResult<ResponseWrapper<LinkAuthCodeResponse>> deferredResult = new DeferredResult<>();
        authorizationHelperService.addEntryInLinkAuthCodeStatusDeferredResultMap(linkTransactionId, deferredResult);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setNonce("nonce");
        oidcTransaction.setState("state");
        oidcTransaction.setRedirectUri("redirect-uri");
        Mockito.when(cacheUtilService.getConsentedTransaction(linkTransactionId)).thenReturn(null);

        try {
            authorizationHelperService.consumeLinkAuthCodeStatus(linkTransactionId);
            Assert.fail();
        } catch (InvalidTransactionException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void getAuthorizeScopes_test() {
        ReflectionTestUtils.setField(authorizationHelperService, "authorizeScopes", Arrays.asList("history.read", "uin.update"));
        List<String> result = authorizationHelperService.getAuthorizeScopes("openid uin.update history read");
        Assert.assertNotNull(result);
        Assert.assertTrue(Arrays.asList("uin.update").containsAll(result));
    }

    @Test
    public void delegateAuthenticateRequest_withValidDetails_thenPass() throws KycAuthException {
        String transactionId = "transaction-id";
        String individualId = "individual-id";
        List<AuthChallenge> challengeList = new ArrayList<>();
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setResolvedClaims(new Claims());
        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken("kyc-token");
        kycAuthResult.setPartnerSpecificUserToken("psut");
        Mockito.when(authenticationWrapper.doKycAuth(Mockito.anyString(), Mockito.anyString(), anyBoolean(), any(KycAuthDto.class))).thenReturn(kycAuthResult);
        KycAuthResult result = authorizationHelperService.delegateAuthenticateRequest(transactionId, individualId, challengeList, oidcTransaction);
        Assert.assertNotNull(result);
        Assert.assertEquals(kycAuthResult.getKycToken(), result.getKycToken());
        Assert.assertEquals(kycAuthResult.getPartnerSpecificUserToken(), result.getPartnerSpecificUserToken());
    }

    @Test
    public void delegateAuthenticateRequest_withInvalidResult_thenFail() throws KycAuthException {
        String transactionId = "transaction-id";
        String individualId = "individual-id";
        List<AuthChallenge> challengeList = new ArrayList<>();
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setResolvedClaims(new Claims());

        Mockito.when(authenticationWrapper.doKycAuth(Mockito.anyString(), Mockito.anyString(), anyBoolean(), any(KycAuthDto.class))).thenReturn(null);
        try {
            authorizationHelperService.delegateAuthenticateRequest(transactionId, individualId, challengeList, oidcTransaction);
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(AUTH_FAILED, e.getErrorCode());
        }

        KycAuthResult result = new KycAuthResult();
        Mockito.when(authenticationWrapper.doKycAuth(Mockito.anyString(), Mockito.anyString(), anyBoolean(), any(KycAuthDto.class))).thenReturn(result);
        try {
            authorizationHelperService.delegateAuthenticateRequest(transactionId, individualId, challengeList, oidcTransaction);
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(AUTH_FAILED, e.getErrorCode());
        }

        result.setPartnerSpecificUserToken("PSUT");
        result.setKycToken(null);
        Mockito.when(authenticationWrapper.doKycAuth(Mockito.anyString(), Mockito.anyString(), anyBoolean(), any(KycAuthDto.class))).thenReturn(result);
        try {
            authorizationHelperService.delegateAuthenticateRequest(transactionId, individualId, challengeList, oidcTransaction);
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(AUTH_FAILED, e.getErrorCode());
        }

        result.setPartnerSpecificUserToken(null);
        result.setKycToken("kyctoken");
        Mockito.when(authenticationWrapper.doKycAuth(Mockito.anyString(), Mockito.anyString(), anyBoolean(), any(KycAuthDto.class))).thenReturn(result);
        try {
            authorizationHelperService.delegateAuthenticateRequest(transactionId, individualId, challengeList, oidcTransaction);
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(AUTH_FAILED, e.getErrorCode());
        }
    }

    @Test
    public void delegateAuthenticateRequest_ThrowsKycAuthException_thenFail() throws KycAuthException {
        String transactionId = "transaction-id";
        String individualId = "individual-id";
        List<AuthChallenge> challengeList = new ArrayList<>();
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setResolvedClaims(new Claims());
        Mockito.when(authenticationWrapper.doKycAuth(Mockito.anyString(), Mockito.anyString(), anyBoolean(), any(KycAuthDto.class))).thenThrow(KycAuthException.class);
        try{
            authorizationHelperService.delegateAuthenticateRequest(transactionId, individualId, challengeList, oidcTransaction);
            Assert.fail();
        }catch (EsignetException e) {
            Assert.assertEquals(null,e.getErrorCode());
        }
    }

    @Test
    public void validateAuthorizeScopes_withEmptyAcceptedScopes_thenPass() {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        authorizationHelperService.validatePermittedScopes(oidcTransaction, Arrays.asList());
    }

    @Test
    public void validateAuthorizeScopes_withEmptyRequestedScopes_thenFail() {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setRequestedAuthorizeScopes(Arrays.asList());
        try {
            authorizationHelperService.validatePermittedScopes(oidcTransaction, Arrays.asList("send-otp"));
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(INVALID_PERMITTED_SCOPE, e.getErrorCode());
        }
    }

    @Test
    public void validateAuthorizeScopes_withInvalidAcceptedScopes_thenFail() {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setRequestedAuthorizeScopes(Arrays.asList("resident_read", "resident_update"));
        try {
            authorizationHelperService.validatePermittedScopes(oidcTransaction, Arrays.asList("send-otp"));
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(INVALID_PERMITTED_SCOPE, e.getErrorCode());
        }
    }

    @Test
    public void delegateSendOtpRequest_withValidRequest_thenPass() throws SendOtpException {
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setIndividualId("individual-id");
        otpRequest.setOtpChannels(Arrays.asList("email"));
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rpid");
        oidcTransaction.setClientId("client-id");
        SendOtpResult sendOtpResult = new SendOtpResult(oidcTransaction.getAuthTransactionId(), "masked-email", "masked-mobile");
        Mockito.when(authenticationWrapper.sendOtp(Mockito.anyString(), Mockito.anyString(), any(SendOtpDto.class))).thenReturn(sendOtpResult);
        SendOtpResult result = authorizationHelperService.delegateSendOtpRequest(otpRequest, oidcTransaction);
        Assert.assertEquals(sendOtpResult.getMaskedMobile(), result.getMaskedMobile());
        Assert.assertEquals(sendOtpResult.getMaskedEmail(), result.getMaskedEmail());
    }

    @Test
    public void delegateSendOtpRequest_withInvalidTransactionId_thenFail() throws SendOtpException {
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setIndividualId("individual-id");
        otpRequest.setOtpChannels(Arrays.asList("email"));
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rpid");
        oidcTransaction.setClientId("client-id");
        SendOtpResult sendOtpResult = new SendOtpResult("temp-id", "masked-email", "masked-mobile");
        Mockito.when(authenticationWrapper.sendOtp(Mockito.anyString(), Mockito.anyString(), any(SendOtpDto.class))).thenReturn(sendOtpResult);

        try {
            authorizationHelperService.delegateSendOtpRequest(otpRequest, oidcTransaction);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(SEND_OTP_FAILED, ex.getErrorCode());
        }
    }

    @Test
    public void delegateSendOtpRequest_withThrowsSendOtpException_thenFail() throws SendOtpException {
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setIndividualId("individual-id");
        otpRequest.setOtpChannels(Arrays.asList("email"));
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rpid");
        oidcTransaction.setClientId("client-id");
        SendOtpResult sendOtpResult = new SendOtpResult("temp-id", "masked-email", "masked-mobile");
        Mockito.when(authenticationWrapper.sendOtp(Mockito.anyString(), Mockito.anyString(), any(SendOtpDto.class))).thenThrow(SendOtpException.class);
        try {
            authorizationHelperService.delegateSendOtpRequest(otpRequest, oidcTransaction);
            Assert.fail();
        }catch(EsignetException e){
            Assert.assertEquals(null,e.getErrorCode());
        }
    }

    @Test
    public void getProvidedAuthFactors_withValidInput_thenPass() {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setId_token(new HashMap<>());
        Map<String, Object> map = new HashMap<>();
        map.put("values", new String [] {"generated-code", "static-code"});
        resolvedClaims.getId_token().put(ACR, map);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setResolvedClaims(resolvedClaims);

        List<AuthChallenge> challengeList = new ArrayList<>();
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("OTP");
        challengeList.add(authChallenge);

        List<List<AuthenticationFactor>> list = new ArrayList<>();
        AuthenticationFactor otpAuthFactor = new AuthenticationFactor();
        otpAuthFactor.setType("OTP");
        list.add(Arrays.asList(otpAuthFactor));
        AuthenticationFactor pinAuthFactor = new AuthenticationFactor();
        pinAuthFactor.setType("PIN");
        list.add(Arrays.asList(pinAuthFactor));
        Mockito.when(authenticationContextClassRefUtil.getAuthFactors(any(String[].class))).thenReturn(list);

        Set<List<AuthenticationFactor>> result = authorizationHelperService.getProvidedAuthFactors(oidcTransaction, challengeList);
        Assert.assertTrue(result.size() == 1);
        Assert.assertTrue(result.stream().allMatch( l -> l.size() == 1 && l.get(0).getType().equalsIgnoreCase("otp")));
    }

    @Test
    public void getProvidedAuthFactors_withAuthFactorMismatch_thenFail() {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setId_token(new HashMap<>());
        Map<String, Object> map = new HashMap<>();
        map.put("values", new String [] {"generated-code", "static-code"});
        resolvedClaims.getId_token().put(ACR, map);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setResolvedClaims(resolvedClaims);

        List<AuthChallenge> challengeList = new ArrayList<>();
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("OTP1");
        challengeList.add(authChallenge);

        List<List<AuthenticationFactor>> list = new ArrayList<>();
        AuthenticationFactor otpAuthFactor = new AuthenticationFactor();
        otpAuthFactor.setType("OTP");
        list.add(Arrays.asList(otpAuthFactor));
        AuthenticationFactor pinAuthFactor = new AuthenticationFactor();
        pinAuthFactor.setType("PIN");
        list.add(Arrays.asList(pinAuthFactor));
        Mockito.when(authenticationContextClassRefUtil.getAuthFactors(any(String[].class))).thenReturn(list);

        try {
            authorizationHelperService.getProvidedAuthFactors(oidcTransaction, challengeList);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(AUTH_FACTOR_MISMATCH, ex.getErrorCode());
        }
    }

    @Test
    public void setGetIndividualId_test() throws Exception {
        ReflectionTestUtils.setField(authorizationHelperService, "storeIndividualId", true);
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", true);
        ReflectionTestUtils.setField(authorizationHelperService, "aesECBTransformation", "AES/ECB/PKCS5Padding");
        ReflectionTestUtils.setField(authorizationHelperService, "cacheSecretKeyRefId", "TRANSACTION_CACHE");

        Map<String, List<KeyAlias>> keyaliasesMap = new HashMap<>();
        KeyAlias keyAlias = new KeyAlias();
        keyAlias.setAlias("test");
        keyaliasesMap.put(CURRENTKEYALIAS, Arrays.asList(keyAlias));
        Mockito.when(dbHelper.getKeyAliases(Mockito.anyString(), Mockito.anyString(), any(LocalDateTime.class))).thenReturn(keyaliasesMap);
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        Mockito.when(keyStore.getSymmetricKey(Mockito.anyString())).thenReturn(key, key);
        String individualId = "individual-id";
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        authorizationHelperService.setIndividualId(individualId, oidcTransaction);
        Assert.assertNotNull(oidcTransaction.getIndividualId());
        Assert.assertNotEquals(individualId, oidcTransaction.getIndividualId());

        String result = authorizationHelperService.getIndividualId(oidcTransaction);
        Assert.assertEquals(individualId, result);
    }

    @Test
    public void setGetIndividualId_withStoreFlagSetToFalse() {
        ReflectionTestUtils.setField(authorizationHelperService, "storeIndividualId", false);
        String individualId = "individual-id";
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        authorizationHelperService.setIndividualId(individualId, oidcTransaction);
        Assert.assertNull(oidcTransaction.getIndividualId());
        String result = authorizationHelperService.getIndividualId(oidcTransaction);
        Assert.assertNull(individualId, result);
    }

    @Test
    public void setGetIndividualId_withSecureStoreFlagSetToFalse() {
        ReflectionTestUtils.setField(authorizationHelperService, "storeIndividualId", true);
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        String individualId = "individual-id";
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        authorizationHelperService.setIndividualId(individualId, oidcTransaction);
        Assert.assertEquals(individualId, oidcTransaction.getIndividualId());
        String result = authorizationHelperService.getIndividualId(oidcTransaction);
        Assert.assertEquals(individualId, result);
    }

    @Test
    public void testHandleInternalAuthenticateRequest_ValidDetails_thenPass(){
        ReflectionTestUtils.setField(authorizationHelperService, "objectMapper",objectMapper);

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("eyJ0b2tlbiI6ImV5SmhiR2NpT2lKSVV6STFOaUo5LmV5SnpkV0lpT2lKemRXSnFaV04wSWl3aWJtOXVZMlVpT2lKelpYSjJaWEl0Ym05dVkyVWlmUS5CcU5FWF82YUhIc0J2MDVzc0ZqaXVjZ0dzQTZYSW1RWUxWaDZseXFXMXM0In0=");
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setIndividualId("individualId");
        transaction.setNonce("server-nonce");
        Mockito.doNothing().when(tokenService).verifyIdToken(any(), any());

        Cookie cookie = new Cookie("subject", "server-nonce".concat(SERVER_NONCE_SEPARATOR).concat("path-fragment"));
        Mockito.when(httpServletRequest.getCookies()).thenReturn(new Cookie[]{cookie});

        OIDCTransaction haltedTransaction = new OIDCTransaction();
        haltedTransaction.setIndividualId("individualId");
        haltedTransaction.setTransactionId("transactionId");
        haltedTransaction.setServerNonce("server-nonce");
        Mockito.when(cacheUtilService.getHaltedTransaction(Mockito.anyString())).thenReturn(haltedTransaction);

        KycAuthResult result = authorizationHelperService.handleInternalAuthenticateRequest(authChallenge, "subject", transaction, httpServletRequest);

        Assert.assertNotNull(result);
        Assert.assertEquals("subject", result.getKycToken());
        Assert.assertEquals("subject", result.getPartnerSpecificUserToken());
        Assert.assertEquals("individualId", transaction.getIndividualId());
    }

    @Test
    public void testHandleInternalAuthenticateRequest_InvalidIndividualId_thenFail(){
        ReflectionTestUtils.setField(authorizationHelperService, "objectMapper",objectMapper);

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("eyJ0b2tlbiI6ImV5SmhiR2NpT2lKSVV6STFOaUo5LmV5SnpkV0lpT2lKemRXSnFaV04wSW4wLjl0MG5GMkNtVWZaeTlCYlA3cjM4bElhSlJSeTNaSk41MnBRNlpLSl9qVWMifQ==");
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setIndividualId("individualId");
        Mockito.doNothing().when(tokenService).verifyIdToken(any(), any());

        try{
            authorizationHelperService.handleInternalAuthenticateRequest(authChallenge,
                    "invalid_individualId", transaction, httpServletRequest);
        }catch(EsignetException e){
            Assert.assertEquals(INVALID_INDIVIDUAL_ID,e.getErrorCode());
        }
    }

    @Test
    public void testHandleInternalAuthenticateRequest_NoCookie_thenFail() {

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("base64encodedchallenge");
        OIDCTransaction transaction = new OIDCTransaction();
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        try{
            authorizationHelperService.handleInternalAuthenticateRequest(authChallenge, "individualId", transaction, httpServletRequest);
            Assert.fail();
        }catch(EsignetException e){
            Assert.assertEquals("auth_failed",e.getErrorCode());
        }
    }

    @Test
    public void testHandleInternalAuthenticateRequest_NoHaltedTransaction_thenFail() {
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("base64encodedchallenge");
        OIDCTransaction transaction = new OIDCTransaction();
        try {
            authorizationHelperService.handleInternalAuthenticateRequest(authChallenge, "individualId", transaction, httpServletRequest);
            Assert.fail();
        }catch(EsignetException e)
        {
            Assert.assertEquals("auth_failed",e.getErrorCode());
        }
    }
}
