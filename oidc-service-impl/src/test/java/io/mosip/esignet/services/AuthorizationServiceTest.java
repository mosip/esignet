/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.api.dto.KycAuthResult;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidClientException;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.constants.ErrorConstants;
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

import java.util.*;

import static io.mosip.esignet.core.spi.TokenService.ACR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationServiceTest {

    @Mock
    ClientManagementService clientManagementService;

    @Mock
    AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Mock
    CacheUtilService cacheUtilService;

    @Mock
    Authenticator authenticationWrapper;

    @InjectMocks
    AuthorizationServiceImpl authorizationServiceImpl;

    @Mock
    AuditPlugin auditWrapper;

    @Mock
    ConsentHelperService consentHelperService;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Map<String, List<String>> claims = new HashMap<>();
        claims.put("profile", Arrays.asList("given_name", "profile_picture", "name", "phone_number", "email"));
        claims.put("email", Arrays.asList("email","email_verified"));
        claims.put("phone", Arrays.asList("phone_number","phone_number_verified"));
        AuthorizationHelperService authorizationHelperService = new AuthorizationHelperService();
        ReflectionTestUtils.setField(authorizationHelperService, "credentialScopes", Arrays.asList("sample_ldp_vc"));
        ReflectionTestUtils.setField(authorizationHelperService, "authorizeScopes", Arrays.asList("resident-service"));
        ReflectionTestUtils.setField(authorizationHelperService, "authenticationContextClassRefUtil", authenticationContextClassRefUtil);
        ReflectionTestUtils.setField(authorizationHelperService, "authenticationWrapper", authenticationWrapper);
        ReflectionTestUtils.setField(authorizationHelperService, "auditWrapper", auditWrapper);

        ReflectionTestUtils.setField(authorizationServiceImpl, "claims", claims);
        ReflectionTestUtils.setField(authorizationServiceImpl, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(authorizationServiceImpl, "authorizationHelperService", authorizationHelperService);
    }


    @Test(expected = InvalidClientException.class)
    public void getOauthDetails_withInvalidClientId_throwsException() throws EsignetException {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setNonce("test-nonce");
        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenThrow(InvalidClientException.class);
        authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
    }

    @Test
    public void getOauthDetails_withInvalidRedirectUri_throwsException() throws EsignetException {
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
        } catch (EsignetException e) {
            Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_REDIRECT_URI));
        }
    }

    @Test
    public void getOauthDetails_withNullClaimsInDbAndNullClaimsInReq_thenPass() throws EsignetException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
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

        OAuthDetailResponseV1 oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponse.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withNullClaimsInDbAndValidClaimsInReq_thenPass() throws EsignetException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
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

        OAuthDetailResponseV1 oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponse.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withValidClaimsInDbAndValidClaimsInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
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

        OAuthDetailResponseV1 oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().size() == 1);
        Assert.assertTrue(oauthDetailResponse.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withValidClaimsInDbAndInValidClaimsInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
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

        OAuthDetailResponseV1 oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
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
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.NO_ACR_REGISTERED));
        }
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndNullAcrInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
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

        OAuthDetailResponseV1 oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getAuthFactors().size() == 2);
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndValidAcrInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
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

        OAuthDetailResponseV1 oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getAuthFactors().size() == 1);
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndValidAcrInReq_orderOfPrecedencePreserved() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
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

        OAuthDetailResponseV1 oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertNull(oauthDetailResponse.getAuthFactors());
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndValidAcrClaimInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
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

        OAuthDetailResponseV1 oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
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
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.INVALID_SCOPE));
        }
    }

    @Test(expected = InvalidClientException.class)
    public void getOauthDetailsV2_withInvalidClientId_throwsException() throws EsignetException {
        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setNonce("test-nonce");
        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenThrow(InvalidClientException.class);
        authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
    }

    @Test
    public void getOauthDetailsV2_withInvalidRedirectUri_throwsException() throws EsignetException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));

        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v2/idp");
        oauthDetailRequest.setNonce("test-nonce");
        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);

        try {
            authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_REDIRECT_URI));
        }
    }

    @Test
    public void getOauthDetailsV2_withNullClaimsInDbAndNullClaimsInReq_thenPass() throws EsignetException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(null);
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:static-code"));

        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:static-code"})).thenReturn(new ArrayList<>());

        OAuthDetailResponseV2 oauthDetailResponseV2 = authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponseV2);
        Assert.assertTrue(oauthDetailResponseV2.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponseV2.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetailsV2_withNullClaimsInDbAndValidClaimsInReq_thenPass() throws EsignetException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(null);
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:static-code"));

        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
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

        OAuthDetailResponseV2 oauthDetailResponseV2 = authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponseV2);
        Assert.assertTrue(oauthDetailResponseV2.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponseV2.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetailsV2_withValidClaimsInDbAndValidClaimsInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:static-code"));

        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
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

        OAuthDetailResponseV2 oauthDetailResponseV2 = authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponseV2);
        Assert.assertTrue(oauthDetailResponseV2.getEssentialClaims().size() == 1);
        Assert.assertTrue(oauthDetailResponseV2.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetailsV2_withValidClaimsInDbAndInValidClaimsInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code"));

        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
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

        OAuthDetailResponseV2 oauthDetailResponseV2 = authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponseV2);
        Assert.assertTrue(oauthDetailResponseV2.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponseV2.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetailsV2_withNullAcrInDB_thenFail() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(null);

        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:generated-code mosip:idp:acr:static-code");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);

        try {
            authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.NO_ACR_REGISTERED));
        }
    }

    @Test
    public void getOauthDetailsV2_withValidAcrInDBAndNullAcrInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code","mosip:idp:acr:linked-wallet"));

        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
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

        OAuthDetailResponseV2 oauthDetailResponseV2 = authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponseV2);
        Assert.assertTrue(oauthDetailResponseV2.getAuthFactors().size() == 2);
    }

    @Test
    public void getOauthDetailsV2_withValidAcrInDBAndValidAcrInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code","mosip:idp:acr:linked-wallet"));

        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("level21 mosip:idp:acr:linked-wallet");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        List<List<AuthenticationFactor>> authFactors = new ArrayList<>();
        authFactors.add(Collections.emptyList());
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:linked-wallet"})).thenReturn(authFactors);

        OAuthDetailResponseV2 oauthDetailResponseV2 = authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponseV2);
        Assert.assertTrue(oauthDetailResponseV2.getAuthFactors().size() == 1);
    }

    @Test
    public void getOauthDetailsV2_withValidAcrInDBAndValidAcrInReq_orderOfPrecedencePreserved() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code","mosip:idp:acr:linked-wallet"));

        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:linked-wallet mosip:idp:acr:generated-code");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        //NOTE: if order differs then below mock will not be used, hence will not return null
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:linked-wallet",
                "mosip:idp:acr:generated-code"})).thenReturn(null);

        OAuthDetailResponseV2 oauthDetailResponseV2 = authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponseV2);
        Assert.assertNull(oauthDetailResponseV2.getAuthFactors());
    }

    @Test
    public void getOauthDetailsV2_withValidAcrInDBAndValidAcrClaimInReq_thenPass() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code", "mosip:idp:acr:wallet"));

        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
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

        OAuthDetailResponseV2 oauthDetailResponseV2 = authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponseV2);
        Assert.assertTrue(oauthDetailResponseV2.getAuthFactors().size() == 1);
    }

    @Test
    public void getOauthDetailsV2_withValidClaimsInDbAndValidClaimsInReqAndNoOPENIDScope_thenFail() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:wallet"));

        OAuthDetailRequestV2 oauthDetailRequest = new OAuthDetailRequestV2();
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
            authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.INVALID_SCOPE));
        }
    }

    @Test
    public void authenticate_withInvalidTransaction_thenFail() {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(null);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        try {
            authorizationServiceImpl.authenticateUser(authRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.INVALID_TRANSACTION));
        }
    }

    @Test
    public void authenticate_multipleRegisteredAcrsWithSingleFactor_thenPass() throws EsignetException, KycAuthException {
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

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("OTP"));
        authRequest.setChallengeList(authChallenges);

        AuthResponse authResponse = authorizationServiceImpl.authenticateUser(authRequest);
        Assert.assertNotNull(authResponse);
        Assert.assertEquals(transactionId, authResponse.getTransactionId());
    }

    @Test
    public void authenticate_multipleRegisteredAcrsWithInvalidSingleFactor_thenFail() throws EsignetException {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("BIO"));
        authRequest.setChallengeList(authChallenges);

        try {
            authorizationServiceImpl.authenticateUser(authRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.AUTH_FACTOR_MISMATCH));
        }
    }

    @Test
    public void authenticate_multipleRegisteredAcrsWithMultiFactor_thenPass() throws EsignetException, KycAuthException {
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

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("OTP"));
        authChallenges.add(getAuthChallengeDto("BIO"));
        authRequest.setChallengeList(authChallenges);

        AuthResponse authResponse = authorizationServiceImpl.authenticateUser(authRequest);
        Assert.assertNotNull(authResponse);
        Assert.assertEquals(transactionId, authResponse.getTransactionId());
    }

    @Test
    public void authenticate_multipleRegisteredAcrsWithInvalidMultiFactor_thenPass() throws EsignetException {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:linked-wallet"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:biometrics-generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:linked-wallet"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:biometrics-generated-code",
                "mosip:idp:acr:linked-wallet"})).thenReturn(allAuthFactors);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("OTP"));
        authChallenges.add(getAuthChallengeDto("PIN"));
        authRequest.setChallengeList(authChallenges);

        try {
            authorizationServiceImpl.authenticateUser(authRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.AUTH_FACTOR_MISMATCH));
        }
    }

    @Test
    public void authenticateV2_withInvalidTransaction_thenFail() {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(null);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        try {
            authorizationServiceImpl.authenticateUserV2(authRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.INVALID_TRANSACTION));
        }
    }

    @Test
    public void authenticateV2_multipleRegisteredAcrsWithSingleFactor_thenPass() throws EsignetException, KycAuthException {
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

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("OTP"));
        authRequest.setChallengeList(authChallenges);

        AuthResponseV2 authResponseV2 = authorizationServiceImpl.authenticateUserV2(authRequest);
        Assert.assertNotNull(authResponseV2);
        Assert.assertEquals(transactionId, authResponseV2.getTransactionId());
    }

    @Test
    public void authenticateV2_multipleRegisteredAcrsWithInvalidSingleFactor_thenFail() throws EsignetException {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("BIO"));
        authRequest.setChallengeList(authChallenges);

        try {
            authorizationServiceImpl.authenticateUserV2(authRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.AUTH_FACTOR_MISMATCH));
        }
    }

    @Test
    public void authenticateV2_multipleRegisteredAcrsWithMultiFactor_thenPass() throws EsignetException, KycAuthException {
        String transactionId = "test-transaction";
        String consentAction="Capture";
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

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("OTP"));
        authChallenges.add(getAuthChallengeDto("BIO"));
        authRequest.setChallengeList(authChallenges);

        AuthResponseV2 authResponseV2 = authorizationServiceImpl.authenticateUserV2(authRequest);
        Assert.assertNotNull(authResponseV2);
        Assert.assertEquals(transactionId, authResponseV2.getTransactionId());
        //Assert.assertEquals(consentAction,authResponseV2.getConsentAction());
    }

    @Test
    public void authenticateV2_multipleRegisteredAcrsWithInvalidMultiFactor_thenFail() throws EsignetException {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:linked-wallet"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:biometrics-generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:linked-wallet"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:biometrics-generated-code",
                "mosip:idp:acr:linked-wallet"})).thenReturn(allAuthFactors);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId("23423434234");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        authChallenges.add(getAuthChallengeDto("OTP"));
        authChallenges.add(getAuthChallengeDto("PIN"));
        authRequest.setChallengeList(authChallenges);

        try {
            authorizationServiceImpl.authenticateUserV2(authRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.AUTH_FACTOR_MISMATCH));
        }
    }
    
    @Test
    public void getAuthCode_withValidInput_thenPass() {
    	AuthCodeRequest authCodeRequest = new AuthCodeRequest();
    	authCodeRequest.setTransactionId("987654321");
    	authCodeRequest.setAcceptedClaims(Arrays.asList("fullName"));
    	authCodeRequest.setPermittedAuthorizeScopes(Arrays.asList("test-scope"));
    	OIDCTransaction transaction = new OIDCTransaction();
    	transaction.setAuthTransactionId("987654321");
    	transaction.setRequestedAuthorizeScopes(Arrays.asList("test-scope"));
    	transaction.setRedirectUri("http://www.test.com");
    	transaction.setNonce("test-nonce");
    	transaction.setState("test-state");
    	Claims requestedClaims = new Claims();
    	Map<String, ClaimDetail> userinfo = new HashMap<>();
    	userinfo.put("fullName", new ClaimDetail("test", new String[] {"test"}, true));
		requestedClaims.setUserinfo(userinfo);
		transaction.setRequestedClaims(requestedClaims);
		Mockito.when(cacheUtilService.getAuthenticatedTransaction(Mockito.anyString())).thenReturn(transaction);
		Mockito.when(cacheUtilService.setAuthCodeGeneratedTransaction(Mockito.anyString(), Mockito.any())).thenReturn(transaction);
		Assert.assertEquals(authorizationServiceImpl.getAuthCode(authCodeRequest).getNonce(), "test-nonce");
		Assert.assertEquals(authorizationServiceImpl.getAuthCode(authCodeRequest).getState(), "test-state");
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


}
