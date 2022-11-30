/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.authwrapper.service.MockAuthenticationService;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidClientException;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.repository.ClientDetailRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.*;

import static io.mosip.idp.core.spi.TokenService.ACR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationServiceTest {

    @Mock
    ClientDetailRepository clientDetailRepository;

    @Mock
    ClientManagementServiceImpl clientManagementService;

    @Mock
    AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Mock
    CacheUtilService cacheUtilService;

    @Mock
    MockAuthenticationService authenticationWrapper;

    @InjectMocks
    AuthorizationServiceImpl authorizationServiceImpl;

    @Mock
    TokenServiceImpl tokenGeneratorServiceService;

    @Mock
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    Resource mappingFile;



    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        Map<String, List<String>> claims = new HashMap<>();
        claims.put("profile", Arrays.asList("given_name", "profile_picture", "name", "phone_number", "email"));
        claims.put("email", Arrays.asList("email","email_verified"));
        claims.put("phone", Arrays.asList("phone_number","phone_number_verified"));
        AuthorizationHelperService authorizationHelperService = new AuthorizationHelperService();
        ReflectionTestUtils.setField(authorizationHelperService, "claims", claims);
        ReflectionTestUtils.setField(authorizationHelperService, "authorizeScopes", Arrays.asList("resident-service"));
        ReflectionTestUtils.setField(authorizationHelperService, "authenticationContextClassRefUtil", authenticationContextClassRefUtil);
        ReflectionTestUtils.setField(authorizationHelperService, "authenticationWrapper", authenticationWrapper);

        ReflectionTestUtils.setField(authorizationServiceImpl, "claims", claims);
        ReflectionTestUtils.setField(authorizationServiceImpl, "authorizationHelperService", authorizationHelperService);
    }


    @Test(expected = InvalidClientException.class)
    public void getOauthDetails_withInvalidClientId_throwsException() throws IdPException {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setNonce("test-nonce");
        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenThrow(InvalidClientException.class);
        authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
    }

    @Test
    public void getOauthDetails_withInvalidRedirectUri_throwsException() throws IdPException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v2/idp");
        oauthDetailRequest.setNonce("test-nonce");
        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);

        try {
            authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
            Assert.fail();
        } catch (IdPException e) {
            Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_REDIRECT_URI));
        }
    }

    @Test
    public void getOauthDetails_withNullClaimsInDbAndNullClaimsInReq_thenPass() throws IdPException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(null);
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:static-code"));

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:static-code"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponse.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withNullClaimsInDbAndValidClaimsInReq_thenPass() throws IdPException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(null);
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:static-code"));

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setNonce("test-nonce");
        oauthDetailRequest.setScope("openid test-scope");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        Claims claims = new Claims();
        Map<String, ClaimDetail> userClaims = new HashMap<>();
        userClaims.put("given_name", new ClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("level4");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:static-code"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponse.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withValidClaimsInDbAndValidClaimsInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:static-code"));

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setScope("openid");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setNonce("test-nonce");
        Claims claims = new Claims();
        Map<String, ClaimDetail> userClaims = new HashMap<>();
        userClaims.put("given_name", new ClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:static-code"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().size() == 1);
        Assert.assertTrue(oauthDetailResponse.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withValidClaimsInDbAndInValidClaimsInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code"));

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setScope("test-scope openid resident");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setNonce("test-nonce");
        Claims claims = new Claims();
        Map<String, ClaimDetail> userClaims = new HashMap<>();
        userClaims.put("phone", new ClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:generated-code");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponse.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withNullAcrInDB_thenFail() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(null);

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:generated-code mosip:idp:acr:static-code");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);

        try {
            authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
            Assert.fail();
        } catch (IdPException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.NO_ACR_REGISTERED));
        }
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndNullAcrInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code","mosip:idp:acr:linked-wallet"));

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues(null);
        oauthDetailRequest.setNonce("test-nonce");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        List<List<AuthenticationFactor>> authFactors = new ArrayList<>();
        authFactors.add(Collections.emptyList());
        authFactors.add(Collections.emptyList());
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code",
                "mosip:idp:acr:linked-wallet"})).thenReturn(authFactors);

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getAuthFactors().size() == 2);
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndValidAcrInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code","mosip:idp:acr:linked-wallet"));

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("level21 mosip:idp:acr:linked-wallet");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        List<List<AuthenticationFactor>> authFactors = new ArrayList<>();
        authFactors.add(Collections.emptyList());
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:linked-wallet"})).thenReturn(authFactors);

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getAuthFactors().size() == 1);
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndValidAcrInReq_orderOfPrecedencePreserved() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code","mosip:idp:acr:linked-wallet"));

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:linked-wallet mosip:idp:acr:generated-code");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        //NOTE: if order differs then below mock will not be used, hence will not return null
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:linked-wallet",
                "mosip:idp:acr:generated-code"})).thenReturn(null);

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertNull(oauthDetailResponse.getAuthFactors());
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndValidAcrClaimInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code", "mosip:idp:acr:wallet"));

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setNonce("test-nonce");
        Claims claims = new Claims();
        claims.setId_token(new HashMap<>());
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setValues(new String[]{"mosip:idp:acr:wallet", "mosip:idp:acr:webauthn"});
        claims.getId_token().put("acr", claimDetail);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:biometrics mosip:idp:acr:generated-code");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        List<List<AuthenticationFactor>> authFactors = new ArrayList<>();
        authFactors.add(Collections.emptyList());
        //Highest priority is given to ACR in claims request parameter
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:wallet"})).thenReturn(authFactors);

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getAuthFactors().size() == 1);
    }

    @Test
    public void getOauthDetails_withValidClaimsInDbAndValidClaimsInReqAndNoOPENIDScope_thenFail() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:wallet"));

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setScope("resident service");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setNonce("test-nonce");
        Claims claims = new Claims();
        Map<String, ClaimDetail> userClaims = new HashMap<>();
        userClaims.put("given_name", new ClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:wallet");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);

        try {
            authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
            Assert.fail();
        } catch (IdPException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.INVALID_SCOPE));
        }
    }

    @Test
    public void authenticate_withInvalidTransaction_thenFail() {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(null);

        KycAuthRequest kycAuthRequest = new KycAuthRequest();
        kycAuthRequest.setTransactionId(transactionId);
        try {
            authorizationServiceImpl.authenticateUser(kycAuthRequest);
            Assert.fail();
        } catch (IdPException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.INVALID_TRANSACTION));
        }
    }

    @Test
    public void authenticate_multipleRegisteredAcrsWithSingleFactor_thenPass() throws IdPException, KycAuthException {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken("test-kyc-token");
        kycAuthResult.setPartnerSpecificUserToken("test-psut");
        when(authenticationWrapper.doKycAuth(anyString(), anyString(), any())).thenReturn(kycAuthResult);

        KycAuthRequest kycAuthRequest = new KycAuthRequest();
        kycAuthRequest.setTransactionId(transactionId);
        kycAuthRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("OTP"));
        kycAuthRequest.setChallengeList(authChallenges);

        AuthResponse authResponse = authorizationServiceImpl.authenticateUser(kycAuthRequest);
        Assert.assertNotNull(authResponse);
        Assert.assertEquals(transactionId, authResponse.getTransactionId());
    }

    @Test
    public void authenticate_multipleRegisteredAcrsWithInvalidSingleFactor_thenFail() throws IdPException {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        KycAuthRequest kycAuthRequest = new KycAuthRequest();
        kycAuthRequest.setTransactionId(transactionId);
        kycAuthRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("BIO"));
        kycAuthRequest.setChallengeList(authChallenges);

        try {
            authorizationServiceImpl.authenticateUser(kycAuthRequest);
            Assert.fail();
        } catch (IdPException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.AUTH_FACTOR_MISMATCH));
        }
    }

    @Test
    public void authenticate_multipleRegisteredAcrsWithMultiFactor_thenPass() throws IdPException, KycAuthException {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:biometrics-generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:biometrics-generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken("test-kyc-token");
        kycAuthResult.setPartnerSpecificUserToken("test-psut");
        when(authenticationWrapper.doKycAuth(anyString(), anyString(), any())).thenReturn(kycAuthResult);

        KycAuthRequest kycAuthRequest = new KycAuthRequest();
        kycAuthRequest.setTransactionId(transactionId);
        kycAuthRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("OTP"));
        authChallenges.add(getAuthChallengeDto("BIO"));
        kycAuthRequest.setChallengeList(authChallenges);

        AuthResponse authResponse = authorizationServiceImpl.authenticateUser(kycAuthRequest);
        Assert.assertNotNull(authResponse);
        Assert.assertEquals(transactionId, authResponse.getTransactionId());
    }

    @Test
    public void authenticate_multipleRegisteredAcrsWithInvalidMultiFactor_thenPass() throws IdPException {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:linked-wallet"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:biometrics-generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:linked-wallet"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:biometrics-generated-code",
                "mosip:idp:acr:linked-wallet"})).thenReturn(allAuthFactors);

        KycAuthRequest kycAuthRequest = new KycAuthRequest();
        kycAuthRequest.setTransactionId(transactionId);
        kycAuthRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("OTP"));
        authChallenges.add(getAuthChallengeDto("PIN"));
        kycAuthRequest.setChallengeList(authChallenges);

        try {
            authorizationServiceImpl.authenticateUser(kycAuthRequest);
            Assert.fail();
        } catch (IdPException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.AUTH_FACTOR_MISMATCH));
        }
    }

    private IdPTransaction createIdpTransaction(String[] acrs) {
        IdPTransaction idPTransaction = new IdPTransaction();
        Map<String, ClaimDetail> idClaims = new HashMap<>();
        idClaims.put(ACR, new ClaimDetail(null, acrs, false));
        Claims requestedClaims = new Claims();
        requestedClaims.setId_token(idClaims);
        idPTransaction.setRequestedClaims(requestedClaims);
        idPTransaction.setClientId("test-client");
        idPTransaction.setRelyingPartyId("test-rp-client");
        return idPTransaction;
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


}
