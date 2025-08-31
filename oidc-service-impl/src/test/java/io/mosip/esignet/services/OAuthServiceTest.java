/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.esignet.api.dto.KycExchangeResult;
import io.mosip.esignet.api.dto.KycSigningCertificateData;
import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.exception.KycSigningCertificateException;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
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
import org.junit.Before;
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
import static org.mockito.ArgumentMatchers.any;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_DPOP_PROOF;

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

    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setup() {
        ReflectionTestUtils.setField(oAuthService, "objectMapper", objectMapper);
    }

    @Test
    public void getTokens_withValidRequest_thenPass() throws KycExchangeException {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
        oidcTransaction.setUserInfoResponseType("JWS");
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
        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest, null,false);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNotNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertEquals(kycExchangeResult.getEncryptedKyc(), oidcTransaction.getEncryptedKyc());
    }

    @Test
    public void getTokens_withValidRequestWithPKCE_thenPass() throws KycExchangeException {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest, null,false);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNotNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertEquals(kycExchangeResult.getEncryptedKyc(), oidcTransaction.getEncryptedKyc());
    }

    @Test
    public void getTokens_withValidVerifiedClaimRequest_thenPass() throws KycExchangeException, JsonProcessingException {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
        oidcTransaction.setAcceptedClaims(Arrays.asList("name", "email"));
        oidcTransaction.setUserInfoResponseType("JWS");

        Claims claims = new Claims();
        claims.setUserinfo(new HashMap<>());
        Map<String, Object> map = new HashMap<>();
        map.put("essential", true);
        map.put("verification", new HashMap<>());
        ((Map)map.get("verification")).put("trust_framework", null);
        claims.getUserinfo().put("name", Arrays.asList(map));
        oidcTransaction.setResolvedClaims(claims);

        Map<String, JsonNode> requestedClaimDetail = new HashMap<>();
        requestedClaimDetail.put("name", null);
        requestedClaimDetail.put("email", objectMapper.readTree("{\"essential\":false}"));
        requestedClaimDetail.put("phone_number", objectMapper.readTree("{\"essential\":true}"));
        requestedClaimDetail.put("verified_claims", objectMapper.readTree("{\"verification\":{\"trust_framework\":null}, \"claims\":{\"email\":{\"essential\":true},\"address\":{\"essential\":true}}}"));
        oidcTransaction.setRequestedClaimDetails(requestedClaimDetail);

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));
        KycExchangeResult kycExchangeResult = new KycExchangeResult();
        kycExchangeResult.setEncryptedKyc("encrypted-kyc");

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        Mockito.when(authenticationWrapper.doVerifiedKycExchange(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(kycExchangeResult);
        Mockito.when(tokenService.getAccessToken(Mockito.any(),Mockito.any())).thenReturn("test-access-token");
        Mockito.when(tokenService.getIDToken(Mockito.any())).thenReturn("test-id-token");
        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest, null,false);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNotNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertEquals(kycExchangeResult.getEncryptedKyc(), oidcTransaction.getEncryptedKyc());
    }

    @Test
    public void getTokens_withListOfVerifiedClaimRequest_thenPass() throws KycExchangeException, JsonProcessingException {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
        oidcTransaction.setAcceptedClaims(Arrays.asList("name", "email"));

        Claims claims = new Claims();
        claims.setUserinfo(new HashMap<>());
        Map<String, Object> map = new HashMap<>();
        map.put("essential", true);
        map.put("verification", new HashMap<>());
        ((Map)map.get("verification")).put("trust_framework", null);
        claims.getUserinfo().put("name", Arrays.asList(map));
        oidcTransaction.setResolvedClaims(claims);

        Map<String, JsonNode> requestedClaimDetail = new HashMap<>();
        requestedClaimDetail.put("name", null);
        requestedClaimDetail.put("email", objectMapper.readTree("{\"essential\":false}"));
        requestedClaimDetail.put("phone_number", objectMapper.readTree("{\"essential\":true}"));
        requestedClaimDetail.put("verified_claims", objectMapper.readTree("[{\"verification\":{\"trust_framework\":null}, \"claims\":{\"email\":{\"essential\":true},\"address\":{\"essential\":true}}}," +
                "{\"verification\":{\"trust_framework\":\"Test\"}, \"claims\":{\"phone_number\":{\"essential\":true},\"name\":{\"essential\":true}}}," +
                "{\"verification\":{\"trust_framework\":\"Test\"}}," +
                "{\"verification\":{\"trust_framework\":\"Test\"}, \"claims\":{\"phone_number\":{\"essential\":true},\"address\":{\"essential\":true}}}]"));
        oidcTransaction.setRequestedClaimDetails(requestedClaimDetail);

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));
        KycExchangeResult kycExchangeResult = new KycExchangeResult();
        kycExchangeResult.setEncryptedKyc("encrypted-kyc");

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        Mockito.when(authenticationWrapper.doVerifiedKycExchange(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(kycExchangeResult);
        Mockito.when(tokenService.getAccessToken(Mockito.any(),Mockito.any())).thenReturn("test-access-token");
        Mockito.when(tokenService.getIDToken(Mockito.any())).thenReturn("test-id-token");
        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest, null,false);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNotNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertEquals(kycExchangeResult.getEncryptedKyc(), oidcTransaction.getEncryptedKyc());
    }

    @Test
    public void getTokens_withInternalKycExchange_thenPass() {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
        oidcTransaction.setInternalAuthSuccess(true);
        oidcTransaction.setProofKeyCodeExchange(ProofKeyCodeExchange.getInstance("KgFzotzIWt3ZMFusBrpCIyWTP-F9QJdtM4Qb8m3I-4Q",
                "S256"));
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        Mockito.when(tokenService.getAccessToken(Mockito.any(),Mockito.any())).thenReturn("test-access-token");
        Mockito.when(tokenService.getIDToken(Mockito.any())).thenReturn("test-id-token");
        Mockito.when(tokenService.getSignedJWT(Mockito.anyString(), Mockito.any())).thenReturn("encrypted-kyc");
        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest, null,false);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNotNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertEquals("encrypted-kyc", oidcTransaction.getEncryptedKyc());
    }

    @Test
    public void getTokens_withInvalidAuthCode_thenFail() {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
        try {
            oAuthService.getTokens(tokenRequest, null,false);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_TRANSACTION, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withNullClientIdInRequest_thenPass() throws KycExchangeException {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest, null,false);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNotNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertEquals(kycExchangeResult.getEncryptedKyc(), oidcTransaction.getEncryptedKyc());
    }

    @Test
    public void getTokens_withEmptyClientIdInRequest_thenPass() throws KycExchangeException {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest, null,false);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNotNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertEquals(kycExchangeResult.getEncryptedKyc(), oidcTransaction.getEncryptedKyc());
    }

    @Test
    public void getTokens_withInvalidClientIdInRequest_thenFail() {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("t");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setClientId("test-test");
        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        try {
            oAuthService.getTokens(tokenRequest, null,false);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_CLIENT_ID, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withInvalidRedirectUri_thenFail() {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test/test-page");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        try {
            oAuthService.getTokens(tokenRequest, null, false);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_REDIRECT_URI, ex.getErrorCode());
        }
    }

    @Test
    public void getTokensV2_withInvalidRedirectUri_thenFail() {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test/test-page");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        try {
            oAuthService.getTokens(tokenRequest, null, true);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_REDIRECT_URI, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withEmptyCodeVerifier_thenFail() {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test-page");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client-assertion");
        tokenRequest.setCode_verifier("");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        oidcTransaction.setIndividualId("individual-id");
        oidcTransaction.setProofKeyCodeExchange(ProofKeyCodeExchange.getInstance("test", "S256"));
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**", "http://test-redirect-uri-2"));

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        try {
            oAuthService.getTokens(tokenRequest, null, false);
        } catch (EsignetException ex) {
            Assert.assertEquals(INVALID_PKCE_CODE_VERFIER, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withInvalidAssertionType_thenFail() {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
            oAuthService.getTokens(tokenRequest, null, false);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_ASSERTION_TYPE, ex.getErrorCode());
        }

        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        try {
            oAuthService.getTokens(tokenRequest, null, false);
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(INVALID_ASSERTION, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withFailedDataExchange_thenFail() throws KycExchangeException {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
            oAuthService.getTokens(tokenRequest, null, false);
        } catch (EsignetException ex) {
            Assert.assertEquals(DATA_EXCHANGE_FAILED, ex.getErrorCode());
        }

        try {
            oAuthService.getTokens(tokenRequest, null, false);
        } catch (EsignetException ex) {
            Assert.assertEquals(DATA_EXCHANGE_FAILED, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_dataExchangeRuntimeException_thenFail() throws KycExchangeException {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
            oAuthService.getTokens(tokenRequest, null, false);
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
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
            oAuthService.getTokens(tokenRequest, null, true);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(PKCE_FAILED, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withInvalidChallengeMethod_thenFail() {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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
            oAuthService.getTokens(tokenRequest, null, true);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(UNSUPPORTED_PKCE_CHALLENGE_METHOD, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withVCScopedTransaction_thenPass() throws KycExchangeException {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
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

        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest, null, false);
        Mockito.verifyNoInteractions(authenticationWrapper);
        Assert.assertNotNull(tokenResponse);
        Assert.assertNull(tokenResponse.getId_token());
        Assert.assertNotNull(tokenResponse.getAccess_token());
        Assert.assertEquals(BEARER, tokenResponse.getToken_type());
        Assert.assertNotNull(tokenResponse.getC_nonce());
        Assert.assertNotNull(tokenResponse.getC_nonce_expires_in());
    }

    @Test
    public void getOAuthServerDiscoveryInfo_test() {
        ReflectionTestUtils.setField(oAuthService, "oauthServerDiscoveryMap", new HashMap<>());
        Assert.assertNotNull(oAuthService.getOAuthServerDiscoveryInfo());
    }

    @Test
    public void authorize_withValidInput_thenPass() {
        PushedAuthorizationRequest request = new PushedAuthorizationRequest();
        request.setClient_id("34567");
        request.setClient_assertion_type("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        request.setClient_assertion("valid-jwt");
        request.setRedirect_uri("http://localhost:8088/v1/idp");
        request.setAcr_values("mosip:idp:acr:static-code");
        request.setNonce("test-nonce");

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("test-client");
        clientDetail.setPublicKey("public-key");
        clientDetail.setRedirectUris(List.of("http://localhost:8088/v1/idp"));

        Mockito.when(clientManagementService.getClientDetails("34567")).thenReturn(clientDetail);

        PushedAuthorizationResponse response = oAuthService.authorize(request, null);

        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getRequest_uri());
    }

    @Test
    public void authorize_withUnsupportedAssertionType_thenFail() {
        PushedAuthorizationRequest request = new PushedAuthorizationRequest();
        request.setClient_id("34567");
        request.setClient_assertion_type("unsupported-type");
        request.setClient_assertion("dummy");
        request.setRedirect_uri("http://localhost:8088/v1/idp");
        request.setScope("openid");
        request.setNonce("test-nonce");

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setPublicKey("dummy-key");
        clientDetail.setRedirectUris(List.of("http://localhost:8088/v1/idp"));

        Mockito.when(clientManagementService.getClientDetails("34567")).thenReturn(clientDetail);

        try {
            oAuthService.authorize(request, null);
            Assert.fail();
        } catch (InvalidRequestException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_ASSERTION_TYPE, ex.getMessage());
        }
    }

    @Test
    public void authorize_withValidDpopProof_thenPass() throws Exception {
        PushedAuthorizationRequest request = new PushedAuthorizationRequest();
        request.setClient_id("34567");
        request.setClient_assertion_type("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        request.setClient_assertion("valid-jwt");
        request.setRedirect_uri("http://localhost:8088/v1/idp");
        request.setAcr_values("mosip:idp:acr:static-code");
        request.setNonce("test-nonce");

        RSAKey rsaKey = new RSAKeyGenerator(2048).generate();
        String thumbprint = "test-thumbprint";
        SignedJWT dpopJwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).jwk(rsaKey.toPublicJWK()).build(),
            new JWTClaimsSet.Builder().build()
        );
        dpopJwt.sign(new RSASSASigner(rsaKey));

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("test-client");
        clientDetail.setPublicKey("public-key");
        clientDetail.setRedirectUris(List.of("http://localhost:8088/v1/idp"));

        Mockito.when(clientManagementService.getClientDetails("34567")).thenReturn(clientDetail);
        Mockito.when(securityHelperService.computeJwkThumbprint(any())).thenReturn(thumbprint);

        PushedAuthorizationResponse response = oAuthService.authorize(request, dpopJwt.serialize());

        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getRequest_uri());
        Assert.assertEquals(thumbprint, request.getDpop_jkt());
    }

    @Test
    public void authorize_withMismatchedDpopJkt_thenFail() throws Exception {
        PushedAuthorizationRequest request = new PushedAuthorizationRequest();
        request.setClient_id("34567");
        request.setClient_assertion_type("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        request.setClient_assertion("valid-jwt");
        request.setRedirect_uri("http://localhost:8088/v1/idp");
        request.setNonce("test-nonce");
        request.setDpop_jkt("different-thumbprint");

        RSAKey rsaKey = new RSAKeyGenerator(2048).generate();
        String thumbprint = "test-thumbprint";
        SignedJWT dpopJwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).jwk(rsaKey.toPublicJWK()).build(),
            new JWTClaimsSet.Builder().build()
        );
        dpopJwt.sign(new RSASSASigner(rsaKey));

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("test-client");
        clientDetail.setPublicKey("public-key");
        clientDetail.setRedirectUris(List.of("http://localhost:8088/v1/idp"));

        Mockito.when(clientManagementService.getClientDetails("34567")).thenReturn(clientDetail);
        Mockito.when(securityHelperService.computeJwkThumbprint(any())).thenReturn(thumbprint);

        try {
            oAuthService.authorize(request, dpopJwt.serialize());
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_DPOP_PROOF, ex.getErrorCode());
        }
    }

    @Test
    public void getTokens_withValidDpopProof_thenPass() throws Exception {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test-page");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client-assertion");

        RSAKey rsaKey = new RSAKeyGenerator(2048).generate();
        String thumbprint = "test-thumbprint";
        SignedJWT dpopJwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).jwk(rsaKey.toPublicJWK()).build(),
            new JWTClaimsSet.Builder().build()
        );
        dpopJwt.sign(new RSASSASigner(rsaKey));

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setAuthTransactionId("auth-transaction-id");
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        oidcTransaction.setIndividualId("individual-id");
        oidcTransaction.setDpopJkt(thumbprint);
        oidcTransaction.setDpopBoundAccessToken(true);

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**"));
        clientDetail.setAdditionalConfig(objectMapper.readTree("{\"dpop_bound_access_tokens\": true}"));

        KycExchangeResult kycExchangeResult = new KycExchangeResult();
        kycExchangeResult.setEncryptedKyc("encrypted-kyc");

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        Mockito.when(authorizationHelperService.getIndividualId(oidcTransaction)).thenReturn(oidcTransaction.getIndividualId());
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(authenticationWrapper.doKycExchange(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(kycExchangeResult);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        Mockito.when(securityHelperService.computeJwkThumbprint(any())).thenReturn(thumbprint);
        Mockito.when(tokenService.getAccessToken(Mockito.any(), Mockito.any())).thenReturn("test-access-token");
        Mockito.when(tokenService.getIDToken(Mockito.any())).thenReturn("test-id-token");

        TokenResponse tokenResponse = oAuthService.getTokens(tokenRequest, dpopJwt.serialize(), false);
        
        Assert.assertNotNull(tokenResponse);
        Assert.assertEquals(Constants.DPOP, tokenResponse.getToken_type());
    }

    @Test
    public void getTokens_withMismatchedDpopJkt_thenFail() throws Exception {
        TokenRequestV2 tokenRequest = new TokenRequestV2();
        tokenRequest.setCode("test-code");
        tokenRequest.setClient_id("client-id");
        tokenRequest.setRedirect_uri("https://test-redirect-uri/test-page");
        tokenRequest.setClient_assertion_type(JWT_BEARER_TYPE);
        tokenRequest.setClient_assertion("client-assertion");

        RSAKey rsaKey = new RSAKeyGenerator(2048).generate();
        String thumbprint = "test-thumbprint";
        String differentThumbprint = "different-thumbprint";
        SignedJWT dpopJwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).jwk(rsaKey.toPublicJWK()).build(),
            new JWTClaimsSet.Builder().build()
        );
        dpopJwt.sign(new RSASSASigner(rsaKey));

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setRelyingPartyId("rp-id");
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setKycToken("kyc-token");
        oidcTransaction.setRedirectUri("https://test-redirect-uri/test-page");
        oidcTransaction.setDpopJkt(differentThumbprint);
        oidcTransaction.setDpopBoundAccessToken(true);

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setRedirectUris(Arrays.asList("https://test-redirect-uri/**"));
        clientDetail.setAdditionalConfig(objectMapper.readTree("{\"dpop_bound_access_tokens\": true}"));

        Mockito.when(authorizationHelperService.getKeyHash(Mockito.anyString())).thenReturn("code-hash");
        ReflectionTestUtils.setField(authorizationHelperService, "secureIndividualId", false);
        Mockito.when(cacheUtilService.getAuthCodeTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(clientManagementService.getClientDetails(Mockito.anyString())).thenReturn(clientDetail);
        Mockito.when(securityHelperService.computeJwkThumbprint(any())).thenReturn(thumbprint);

        try {
            oAuthService.getTokens(tokenRequest, dpopJwt.serialize(), false);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(INVALID_DPOP_PROOF, ex.getErrorCode());
        }
    }

}
