/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import io.mosip.esignet.api.dto.KycExchangeResult;
import io.mosip.esignet.api.dto.KycSigningCertificateData;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.exception.KycSigningCertificateException;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.SecurityHelperService;
import io.mosip.kernel.keymanagerservice.dto.AllCertificatesDataResponseDto;
import io.mosip.kernel.keymanagerservice.dto.CertificateDataResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static io.mosip.esignet.api.util.ErrorConstants.DATA_EXCHANGE_FAILED;
import static io.mosip.esignet.core.constants.Constants.BEARER;
import static io.mosip.esignet.core.constants.ErrorConstants.*;
import static io.mosip.esignet.core.spi.OAuthService.JWT_BEARER_TYPE;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class OAuthServiceTest {

    @InjectMocks
    private OAuthServiceImpl oAuthService;

    @Mock
    private ClientManagementService clientManagementService;

    @Mock
    private AuthorizationHelperService authorizationHelperService;

    @Mock
    private Authenticator authenticationWrapper;

    @Mock
    private TokenService tokenService;

    @Mock
    private CacheUtilService cacheUtilService;

    @Mock
    private KeymanagerService keymanagerService;

    @Mock
    private AuditPlugin auditWrapper;

    @Mock
    private SecurityHelperService securityHelperService;

    @Test
    public void getTokens_withValidRequest_thenPass() throws KycExchangeException {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test-page");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client-assertion");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        oidcTransaction.setIndividualId("individual-id");
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));
        KycExchangeResult kycExchangeResult = new KycExchangeResult();
        kycExchangeResult.setEncryptedKyc("encrypted-kyc");

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        Mockito.when(authenticationWrapper.doKycExchange(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(kycExchangeResult);
        Mockito.when(tokenService.getAccessToken(Mockito.any(),Mockito.any())).thenReturn("test-access-token");
        Mockito.when(tokenService.getIDToken(Mockito.any())).thenReturn("test-id-token");
        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest,false);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNotNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertEquals(kycExchangeResult.getEncryptedKyc(), oidcTransaction.getEncryptedKyc());
    }

    @Test
    public void getTokens_withValidRequestWithPKCE_thenPass() throws KycExchangeException {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test-page");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client-assertion");
        tokenRequest.setCode_verifier("eyIxIjoxNzYsIjIiOjEzOCwiMyI6MiwiNCI6NTd9");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        oidcTransaction.setIndividualId("individual-id");
        oidcTransaction.setProofKeyCodeExchange(ProofKeyCodeExchange.getInstance("KgFzotzIWt3ZMFusBrpCIyWTP-F9QJdtM4Qb8m3I-4Q",
                "S256"));
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));
        KycExchangeResult kycExchangeResult = new KycExchangeResult();
        kycExchangeResult.setEncryptedKyc("encrypted-kyc");

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        Mockito.when(authenticationWrapper.doKycExchange(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(kycExchangeResult);
        Mockito.when(tokenService.getAccessToken(Mockito.any(),Mockito.any())).thenReturn("test-access-token");
        Mockito.when(tokenService.getIDToken(Mockito.any())).thenReturn("test-id-token");
        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest,false);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNotNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertEquals(kycExchangeResult.getEncryptedKyc(), oidcTransaction.getEncryptedKyc());
    }

    @Test
    public void getTokens_withInvalidAuthCode_thenFail() {
        TokenRequest tokenRequest = new TokenRequest();
        try {
            oAuthService.getTokens(tokenRequest,false);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withNullClientIdInRequest_thenPass() throws KycExchangeException {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test-page");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client-assertion");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        oidcTransaction.setIndividualId("individual-id");
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));
        KycExchangeResult kycExchangeResult = new KycExchangeResult();
        kycExchangeResult.setEncryptedKyc("encrypted-kyc");

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        Mockito.when(authenticationWrapper.doKycExchange(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(kycExchangeResult);
        Mockito.when(tokenService.getAccessToken(Mockito.any(), Mockito.any())).thenReturn("test-access-token");
        Mockito.when(tokenService.getIDToken(Mockito.any())).thenReturn("test-id-token");
        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest,false);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNotNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertEquals(kycExchangeResult.getEncryptedKyc(), oidcTransaction.getEncryptedKyc());
    }

    @Test
    public void getTokens_withEmptyClientIdInRequest_thenPass() throws KycExchangeException {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("  ");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test-page");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client-assertion");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        oidcTransaction.setIndividualId("individual-id");
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));
        KycExchangeResult kycExchangeResult = new KycExchangeResult();
        kycExchangeResult.setEncryptedKyc("encrypted-kyc");

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        Mockito.when(authenticationWrapper.doKycExchange(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(kycExchangeResult);
        Mockito.when(tokenService.getAccessToken(Mockito.any(), Mockito.any())).thenReturn("test-access-token");
        Mockito.when(tokenService.getIDToken(Mockito.any())).thenReturn("test-id-token");
        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest,false);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNotNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertEquals(kycExchangeResult.getEncryptedKyc(), oidcTransaction.getEncryptedKyc());
    }

    @Test
    public void getTokens_withInvalidClientIdInRequest_thenFail() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("t");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setClientId("test-test");
        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        try {
            oAuthService.getTokens(tokenRequest,false);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_CLIENT_ID, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withInvalidRedirectUri_thenFail() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test/test-page");
        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        try {
            oAuthService.getTokens(tokenRequest,false);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_REDIRECT_URI, ex.getErrorCode());
        }

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri1/**", "http://test-redirect-uri-2"));
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test/test-page");
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        try {
            oAuthService.getTokens(tokenRequest,false);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_REDIRECT_URI, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withInvalidAssertionType_thenFail() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE+1);

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test/test-page");
        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test/test-page");
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        try {
            oAuthService.getTokens(tokenRequest,false);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_ASSERTION_TYPE, ex.getErrorCode());
        }

        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        try {
            oAuthService.getTokens(tokenRequest,false);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_ASSERTION, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withFailedDataExchange_thenFail() throws KycExchangeException {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client_assertion");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test/test-page");
        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test/test-page");
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);

        KycExchangeResult kycExchangeResult = new KycExchangeResult();
        kycExchangeResult.setEncryptedKyc(null);
        Mockito.when(authenticationWrapper.doKycExchange(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(null, kycExchangeResult);
        try {
            oAuthService.getTokens(tokenRequest,false);
        } catch (EsignetException ex) {
            Assert.assertEquals(DATA_EXCHANGE_FAILED, ex.getErrorCode());
        }

        try {
            oAuthService.getTokens(tokenRequest,false);
        } catch (EsignetException ex) {
            Assert.assertEquals(DATA_EXCHANGE_FAILED, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_dataExchangeRuntimeException_thenFail() throws KycExchangeException {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client_assertion");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test/test-page");
        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test/test-page");
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        Mockito.when(authenticationWrapper.doKycExchange(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenThrow(new KycExchangeException("test-err-1"));
        try {
            oAuthService.getTokens(tokenRequest,false);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals("test-err-1", ex.getErrorCode());
        }
    }

    @Test
    public void getJWKS_test() throws KycSigningCertificateException {
        String pemCert = "-----BEGIN CERTIFICATE-----\n" +
                "MIIC6jCCAdKgAwIBAgIGAYZGtqIKMA0GCSqGSIb3DQEBCwUAMDYxNDAyBgNVBAMM\n" +
                "K25pZ3RyQlo5M1hJNlRpUWZfQ3V3a3FCbGVvOHFhQU5ObjlycWpJNWlIQmMwHhcN\n" +
                "MjMwMjEyMTc0MDE5WhcNMjMxMjA5MTc0MDE5WjA2MTQwMgYDVQQDDCtuaWd0ckJa\n" +
                "OTNYSTZUaVFmX0N1d2txQmxlbzhxYUFOTm45cnFqSTVpSEJjMIIBIjANBgkqhkiG\n" +
                "9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiJpQSIajCvz1AI9bGhT6MuuboJr/dfgz/Ndk\n" +
                "CVbA6CpntZ14tRmTqs2aBhpMovIkF6Y7Az+7W+jBTze68GavFRQ8Epdn4ucbDGMe\n" +
                "kaOOjgYsaIlno1A/AVnieqTMdl31jrTAiwxtPcSVlp+23UfQwi8TUXpMfqbbI5kW\n" +
                "3uXDfAjSLBTa16XStOD93ONNFKPzmdlr2SfL7ppZAUnVMeXHEnVms5EygqANoSF3\n" +
                "9jQ8SOlGb+/8BYapw2AVaa/hDg3aEWzduAckwJGmyByiR/fndVfSWtNKLp1m3K17\n" +
                "dyaepYGWT3V7esPJuPSMa2IAMqvnrBlfXOhu2qDtqVXu30yEdwIDAQABMA0GCSqG\n" +
                "SIb3DQEBCwUAA4IBAQBEL88AOSksOBy2TUlKJpQpG726e9jWWiDxQuVM+Weqp9t4\n" +
                "zSiXr9BAIJcfEYOj3WW++ebDdDFAyasF8dcB8UY9/XAmPQCyGt70+jf0LJBC5/XY\n" +
                "Xux73fXDYQPISSBALAC1+oPF8Bd1/u0Vjpj2w0vM8WkRp058Xkhx0Vt5JH44uhGd\n" +
                "xakYQiHDMzDGq2rmJQyb2+53G7J9i19YYXhXHx7OBAo2rkNI2HZox6eLFz0dZZrr\n" +
                "KJQ4dvvNHyRDpFY6+1QKoTLhrKo3vYpF68FQ1qCJ7zZH1nPJJiaDRxCtO0otJquO\n" +
                "qVXwweiWny07Mgw3EEviLjWTs8p+U36RzzWwvk6k\n" +
                "-----END CERTIFICATE-----";

        CertificateDataResponseDto certificateDataResponseDto = new CertificateDataResponseDto();
        certificateDataResponseDto.setCertificateData(pemCert);
        certificateDataResponseDto.setKeyId("test-key-1");
        certificateDataResponseDto.setExpiryAt(LocalDateTime.now());
        AllCertificatesDataResponseDto allCertificatesDataResponseDto = new AllCertificatesDataResponseDto();
        allCertificatesDataResponseDto.setAllCertificates(new CertificateDataResponseDto[]{certificateDataResponseDto});
        Mockito.when(keymanagerService.getAllCertificates(Constants.OIDC_SERVICE_APP_ID, Optional.empty())).thenReturn(allCertificatesDataResponseDto);

        List<KycSigningCertificateData> allAuthCerts = new ArrayList<>();
        KycSigningCertificateData kycSigningCertificateData = new KycSigningCertificateData();
        kycSigningCertificateData.setCertificateData(pemCert);
        kycSigningCertificateData.setKeyId("test-key-2");
        kycSigningCertificateData.setExpiryAt(LocalDateTime.now());
        allAuthCerts.add(kycSigningCertificateData);
        Mockito.when(authenticationWrapper.getAllKycSigningCertificates()).thenReturn(allAuthCerts);

        Map<String, Object> maps = oAuthService.getJwks();
        Assert.assertNotNull(maps);
        Assert.assertTrue(!maps.isEmpty());
    }

    @Test
    public void getTokens_withInvalidPKCE_thenFail() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test-page");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client-assertion");
        tokenRequest.setCode_verifier("eyIxIjoxNzYsIjIiOjEzOCwiMyI6MiwiNCI6NTd91");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        oidcTransaction.setIndividualId("individual-id");
        oidcTransaction.setProofKeyCodeExchange(ProofKeyCodeExchange.getInstance("KgFzotzIWt3ZMFusBrpCIyWTP-F9QJdtM4Qb8m3I-4Q",
                "S256"));

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        try {
            oAuthService.getTokens(tokenRequest,true);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(PKCE_FAILED, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withInvalidChallengeMethod_thenFail() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test-page");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client-assertion");
        tokenRequest.setCode_verifier("eyIxIjoxNzYsIjIiOjEzOCwiMyI6MiwiNCI6NTd91");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        oidcTransaction.setIndividualId("individual-id");
        ProofKeyCodeExchange proofKeyCodeExchange = mock(ProofKeyCodeExchange.class);
        oidcTransaction.setProofKeyCodeExchange(proofKeyCodeExchange);
        Mockito.when(proofKeyCodeExchange.getCodeChallengeMethod()).thenReturn("Plaon");

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        try {
            oAuthService.getTokens(tokenRequest,true);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(UNSUPPORTED_PKCE_CHALLENGE_METHOD, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withVCScopedTransaction_thenPass() throws KycExchangeException {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test-page");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client-assertion");
        tokenRequest.setCode_verifier("eyIxIjoxNzYsIjIiOjEzOCwiMyI6MiwiNCI6NTd9");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        oidcTransaction.setIndividualId("individual-id");
        oidcTransaction.setRequestedCredentialScopes(Arrays.asList("sample_vc_ldp"));
        oidcTransaction.setPermittedScopes(Arrays.asList("sample_vc_ldp"));
        oidcTransaction.setProofKeyCodeExchange(ProofKeyCodeExchange.getInstance("KgFzotzIWt3ZMFusBrpCIyWTP-F9QJdtM4Qb8m3I-4Q",
                "S256"));
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        Mockito.when(securityHelperService.generateSecureRandomString(20)).thenReturn("test-nonce");
        Mockito.when(tokenService.getAccessToken(Mockito.any(),Mockito.any())).thenReturn("test-access-token");

        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest,false);
        Mockito.verifyNoInteractions(authenticationWrapper);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertNotNull(tokenResponse.getC_nonce());
        Assert.assertNotNull(tokenResponse.getC_nonce_expires_in());
    }
}
