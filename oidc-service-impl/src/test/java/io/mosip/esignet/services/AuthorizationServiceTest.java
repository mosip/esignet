/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.SendOtpDto;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.dto.claim.ClaimDetail;
import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.api.dto.KycAuthResult;
import io.mosip.esignet.api.dto.claim.ClaimsV2;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.api.util.FilterCriteriaMatcher;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidClientException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.util.CaptchaHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static io.mosip.esignet.core.constants.Constants.SERVER_NONCE_SEPARATOR;
import static io.mosip.esignet.core.constants.Constants.VERIFICATION_COMPLETE;
import static io.mosip.esignet.core.spi.TokenService.ACR;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationServiceTest {

    @Mock
    ClientManagementService clientManagementService;

    @Mock
    AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Mock
    CacheUtilService cacheUtilService;
    
    @Mock
    TokenService tokenService;

    @Mock
    Authenticator authenticationWrapper;

    @Mock
    Environment environment;

    @InjectMocks
    AuthorizationServiceImpl authorizationServiceImpl;

    @Mock
    AuditPlugin auditWrapper;

    @Mock
    ConsentHelperService consentHelperService;
    
    @Mock
    HttpServletResponse httpServletResponse;

    @Mock
    HttpServletRequest httpServletRequest;

    @InjectMocks
    AuthorizationHelperService authorizationHelperService;

    @Mock
    CaptchaHelper captchaHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Map<String, List<String>> claims = new HashMap<>();
        claims.put("profile", Arrays.asList("given_name", "profile_picture", "name", "phone_number", "email"));
        claims.put("email", Arrays.asList("email","email_verified"));
        claims.put("phone", Arrays.asList("phone_number","phone_number_verified"));

        FilterCriteriaMatcher filterCriteriaMatcher = new FilterCriteriaMatcher();
        ReflectionTestUtils.setField(filterCriteriaMatcher,"objectMapper", new ObjectMapper());

        ClaimsHelperService claimsHelperService = new ClaimsHelperService();
        ReflectionTestUtils.setField(claimsHelperService,"claims", claims);
        ReflectionTestUtils.setField(claimsHelperService,"objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(claimsHelperService,"filterCriteriaMatcher", filterCriteriaMatcher);

        ReflectionTestUtils.setField(authorizationHelperService, "credentialScopes", Arrays.asList("sample_ldp_vc"));
        ReflectionTestUtils.setField(authorizationHelperService, "authorizeScopes", Arrays.asList("resident-service"));
        ReflectionTestUtils.setField(authorizationHelperService,"captchaRequired",Arrays.asList("bio","pwd"));
        ReflectionTestUtils.setField(authorizationHelperService, "claimsHelperService", claimsHelperService);
        ReflectionTestUtils.setField(authorizationHelperService, "signupIDTokenAudience", "mosip-signup-oauth-client");
        ReflectionTestUtils.setField(authorizationHelperService, "captchaHelper", captchaHelper);
        ReflectionTestUtils.setField(authorizationHelperService, "objectMapper", new ObjectMapper());
        
        ReflectionTestUtils.setField(authorizationServiceImpl, "claimsHelperService", claimsHelperService);
        ReflectionTestUtils.setField(authorizationServiceImpl, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(authorizationServiceImpl, "authorizationHelperService", authorizationHelperService);
        ReflectionTestUtils.setField(authorizationServiceImpl,"captchaRequired",Arrays.asList("bio","pwd"));
        ReflectionTestUtils.setField(authorizationServiceImpl, "uiConfigMap", new HashMap<String, Object>());

        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
    }

    
    @Test(expected = InvalidTransactionException.class)
    public void prepareSignupRedirect_withInvalidTransactionId_throwsException() {
    	SignupRedirectRequest signupRedirectRequest = new SignupRedirectRequest();
    	signupRedirectRequest.setTransactionId("transactionId");
    	signupRedirectRequest.setPathFragment("pathFragment");
    	when(cacheUtilService.getAuthenticatedTransaction(Mockito.anyString())).thenReturn(null);
    	authorizationServiceImpl.prepareSignupRedirect(signupRedirectRequest, httpServletResponse);
    }
    
    @Test
    public void prepareSignupRedirect_withValidInput_thenPass() {
    	SignupRedirectRequest signupRedirectRequest = new SignupRedirectRequest();
    	signupRedirectRequest.setTransactionId("transactionId");
    	signupRedirectRequest.setPathFragment("pathFragment");
    	OIDCTransaction oidcTransaction = new OIDCTransaction();
    	oidcTransaction.setServerNonce("secretCode");
    	when(cacheUtilService.getAuthenticatedTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
    	SignupRedirectResponse signupRedirectResponse = authorizationServiceImpl.prepareSignupRedirect(signupRedirectRequest, httpServletResponse);
    	Assert.assertEquals(signupRedirectResponse.getTransactionId(), "transactionId");
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
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        ClaimsV2 claims = new ClaimsV2();
        Map<String, JsonNode> userClaims = new HashMap<>();

        userClaims.put("given_name",  getClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("level4");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        ClaimsV2 claims = new ClaimsV2();
        Map<String, JsonNode> userClaims = new HashMap<>();
        userClaims.put("given_name",  getClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:static-code"})).thenReturn(new ArrayList<>());
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);

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
        ClaimsV2 claims = new ClaimsV2();
        Map<String, JsonNode> userClaims = new HashMap<>();
        userClaims.put("phone", getClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:generated-code");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);

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
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);

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
        ClaimsV2 claims = new ClaimsV2();
        claims.setId_token(new HashMap<>());
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setValues(new String[]{"mosip:idp:acr:wallet", "mosip:idp:acr:webauthn"});
        claims.getId_token().put("acr", claimDetail);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:biometrics mosip:idp:acr:generated-code");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        ClaimsV2 claims = new ClaimsV2();
        Map<String, JsonNode> userClaims = new HashMap<>();
        userClaims.put("given_name", getClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:wallet");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);

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
    public void testGetOauthDetailsV3_WithInvalidIdTokenHint_ShouldThrowEsignetException() {
        OAuthDetailRequestV3 oauthDetailReqDto = new OAuthDetailRequestV3();
        oauthDetailReqDto.setIdTokenHint("invalid_id_token_hint");
        try {
            authorizationServiceImpl.getOauthDetailsV3(oauthDetailReqDto, httpServletRequest);
            Assert.fail();
        }catch (EsignetException e){
            Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_ID_TOKEN_HINT));
        }
    }

    @Test
    public void getOauthDetailsV3_WithNoCookie_ThrowsEsignetException() {
        OAuthDetailRequestV3 oauthDetailReqDto = new OAuthDetailRequestV3();
        oauthDetailReqDto.setIdTokenHint("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.3RJf1g9bKzRC-dEj4b2Jx2yCk7Mz4oG1bZbDqGt8QxE");

        try {
            authorizationServiceImpl.getOauthDetailsV3(oauthDetailReqDto, httpServletRequest);
            Assert.fail();
        }catch (EsignetException e){
            Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_ID_TOKEN_HINT));
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
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        ClaimsV2 claims = new ClaimsV2();
        Map<String, JsonNode> userClaims = new HashMap<>();
        userClaims.put("given_name",  getClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("level4");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        ClaimsV2 claims = new ClaimsV2();
        Map<String, JsonNode> userClaims = new HashMap<>();
        userClaims.put("given_name", getClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        ClaimsV2 claims = new ClaimsV2();
        Map<String, JsonNode> userClaims = new HashMap<>();
        userClaims.put("phone",  getClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:generated-code");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);

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
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        ClaimsV2 claims = new ClaimsV2();
        claims.setId_token(new HashMap<>());
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setValues(new String[]{"mosip:idp:acr:wallet", "mosip:idp:acr:webauthn"});
        claims.getId_token().put("acr", claimDetail);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:biometrics mosip:idp:acr:generated-code");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
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
        ClaimsV2 claims = new ClaimsV2();
        Map<String, JsonNode> userClaims = new HashMap<>();
        userClaims.put("given_name",  getClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:wallet");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);

        try {
            authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.INVALID_SCOPE));
        }
    }

    @Test
    public void getOauthDetailsV2_withoutPKCE_thenFail() {
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
        ClaimsV2 claims = new ClaimsV2();
        claims.setId_token(new HashMap<>());
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setValues(new String[]{"mosip:idp:acr:wallet", "mosip:idp:acr:webauthn"});
        claims.getId_token().put("acr", claimDetail);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:biometrics mosip:idp:acr:generated-code");
        oauthDetailRequest.setScope("sample_ldp_vc");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
        List<List<AuthenticationFactor>> authFactors = new ArrayList<>();
        authFactors.add(Collections.emptyList());
        //Highest priority is given to ACR in claims request parameter
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:wallet"})).thenReturn(authFactors);

        try {
            ReflectionTestUtils.setField(authorizationServiceImpl, "mandatePKCEForVC", true);
            authorizationServiceImpl.getOauthDetailsV2(oauthDetailRequest);
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(ErrorConstants.INVALID_PKCE_CHALLENGE, e.getErrorCode());
        }
    }

    @Test
    public void getOauthDetailsV3_withValidIDTokenHint_thenPass() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setId("mosip-signup-oauth-client");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code", "mosip:idp:acr:wallet"));

        OAuthDetailRequestV3 oauthDetailRequest = new OAuthDetailRequestV3();
        oauthDetailRequest.setClientId("mosip-signup-oauth-client");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setNonce("test-nonce");
        ClaimsV2 claims = new ClaimsV2();
        claims.setId_token(new HashMap<>());
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setValues(new String[]{"mosip:idp:acr:wallet", "mosip:idp:acr:webauthn"});
        claims.getId_token().put("acr", claimDetail);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:biometrics mosip:idp:acr:generated-code");

        when(clientManagementService.getClientDetails(oauthDetailRequest.getClientId())).thenReturn(clientDetail);
        when(cacheUtilService.checkNonce(anyString())).thenReturn(1L);
        List<List<AuthenticationFactor>> authFactors = new ArrayList<>();
        authFactors.add(Collections.emptyList());
        //Highest priority is given to ACR in claims request parameter
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:wallet"})).thenReturn(authFactors);

        oauthDetailRequest.setIdTokenHint("eyJraWQiOiJtbG02RVNRaFB5dVVsWmY0dnBZbGJTVWlSMXBXcG5jdW9kamtnRjNaNU5nIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJxWS0tNVk0VG9Ga1dUb1hKclJGbVBXUEhEWkxrY2lNTDQtX2cxTDJBNXhJIiwiYXVkIjoibW9zaXAtc2lnbnVwLW9hdXRoLWNsaWVudCIsImFjciI6Im1vc2lwOmlkcDphY3I6Z2VuZXJhdGVkLWNvZGUiLCJhdXRoX3RpbWUiOjE3MjUyNjk4ODUsImlzcyI6Imh0dHBzOlwvXC9lc2lnbmV0bDIuY2FtZGdjLXFhLm1vc2lwLm5ldFwvdjFcL2VzaWduZXQiLCJleHAiOjE3MjUyNzAwNzMsImlhdCI6MTcyNTI2OTg5Mywibm9uY2UiOiI5NzNlaWVsanpuZyJ9.VMMn92CFzGkVyx8Jwrq03KhuXOXj3wRlUoxZQQBN7MxlfIxGSX_yE7iw3JWxohzQuHticndtQX2LELcGTPhclzRop3skHCeo6ZPGJklCiRA3F5SyfCYLvDprgE_-pQhLWeECqRtW_8jFFgZSORMoxy8eBj5Vvc8q2zcoDjE-JiLZvqE9UWDRpAKzumJcD3iJvBwE-9jkzQtWZbp-tZrpPrm-KCZU6-Q3qhWU23E9DSMg_6byq4iH51TFwO0nHW1kaxhsqHvCsTX7YTvmfWXUwPVRLNZh5Uszt8EIsgpKIUDkRImqmCUbP1LwoFG55MsW67QzHNTFuR6H-4LidSKnnA");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("qY--5Y4ToFkWToXJrRFmPWPHDZLkciML4-_g1L2A5xI", "5Y4ToFkWToXJrRFmPWPHDZLkciML4"+SERVER_NONCE_SEPARATOR+"test-state"));
        OAuthDetailResponseV2 oauthDetailResponseV2 = authorizationServiceImpl.getOauthDetailsV3(oauthDetailRequest, request);
        Assert.assertNotNull(oauthDetailResponseV2);
    }

    @Test
    public void getOauthDetailsV3_withValidIDTokenHintClientIdAndAUDMismatch_thenFail() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName(new HashMap<>());
        clientDetail.getName().put(Constants.NONE_LANG_KEY, "clientName");
        clientDetail.setId("34567");
        clientDetail.setRedirectUris(Arrays.asList("https://localshot:3044/logo.png","http://localhost:8088/v1/idp","/v1/idp"));
        clientDetail.setClaims(Arrays.asList("email","given_name"));
        clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:generated-code", "mosip:idp:acr:wallet"));

        OAuthDetailRequestV3 oauthDetailRequest = new OAuthDetailRequestV3();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setNonce("test-nonce");
        ClaimsV2 claims = new ClaimsV2();
        claims.setId_token(new HashMap<>());
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setValues(new String[]{"mosip:idp:acr:wallet", "mosip:idp:acr:webauthn"});
        claims.getId_token().put("acr", claimDetail);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:biometrics mosip:idp:acr:generated-code");
        oauthDetailRequest.setIdTokenHint("eyJraWQiOiJtbG02RVNRaFB5dVVsWmY0dnBZbGJTVWlSMXBXcG5jdW9kamtnRjNaNU5nIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJxWS0tNVk0VG9Ga1dUb1hKclJGbVBXUEhEWkxrY2lNTDQtX2cxTDJBNXhJIiwiYXVkIjoibW9zaXAtc2lnbnVwLW9hdXRoLWNsaWVudCIsImFjciI6Im1vc2lwOmlkcDphY3I6Z2VuZXJhdGVkLWNvZGUiLCJhdXRoX3RpbWUiOjE3MjUyNjk4ODUsImlzcyI6Imh0dHBzOlwvXC9lc2lnbmV0bDIuY2FtZGdjLXFhLm1vc2lwLm5ldFwvdjFcL2VzaWduZXQiLCJleHAiOjE3MjUyNzAwNzMsImlhdCI6MTcyNTI2OTg5Mywibm9uY2UiOiI5NzNlaWVsanpuZyJ9.VMMn92CFzGkVyx8Jwrq03KhuXOXj3wRlUoxZQQBN7MxlfIxGSX_yE7iw3JWxohzQuHticndtQX2LELcGTPhclzRop3skHCeo6ZPGJklCiRA3F5SyfCYLvDprgE_-pQhLWeECqRtW_8jFFgZSORMoxy8eBj5Vvc8q2zcoDjE-JiLZvqE9UWDRpAKzumJcD3iJvBwE-9jkzQtWZbp-tZrpPrm-KCZU6-Q3qhWU23E9DSMg_6byq4iH51TFwO0nHW1kaxhsqHvCsTX7YTvmfWXUwPVRLNZh5Uszt8EIsgpKIUDkRImqmCUbP1LwoFG55MsW67QzHNTFuR6H-4LidSKnnA");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("qY--5Y4ToFkWToXJrRFmPWPHDZLkciML4-_g1L2A5xI", "5Y4ToFkWToXJrRFmPWPHDZLkciML4"+SERVER_NONCE_SEPARATOR+"test-state"));

        try {
            OAuthDetailResponseV2 oauthDetailResponseV2 = authorizationServiceImpl.getOauthDetailsV3(oauthDetailRequest, request);
            Assert.assertNotNull(oauthDetailResponseV2);
        } catch (EsignetException e) {
            Assert.assertEquals(ErrorConstants.INVALID_ID_TOKEN_HINT, e.getErrorCode());
        }
    }

    @Test
    public void getOauthDetailsV3_withValidIDTokenHintNoCookie_thenFail() throws Exception {
        OAuthDetailRequestV3 oauthDetailRequest = new OAuthDetailRequestV3();
        oauthDetailRequest.setIdTokenHint("eyJraWQiOiJtbG02RVNRaFB5dVVsWmY0dnBZbGJTVWlSMXBXcG5jdW9kamtnRjNaNU5nIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJxWS0tNVk0VG9Ga1dUb1hKclJGbVBXUEhEWkxrY2lNTDQtX2cxTDJBNXhJIiwiYXVkIjoibW9zaXAtc2lnbnVwLW9hdXRoLWNsaWVudCIsImFjciI6Im1vc2lwOmlkcDphY3I6Z2VuZXJhdGVkLWNvZGUiLCJhdXRoX3RpbWUiOjE3MjUyNjk4ODUsImlzcyI6Imh0dHBzOlwvXC9lc2lnbmV0bDIuY2FtZGdjLXFhLm1vc2lwLm5ldFwvdjFcL2VzaWduZXQiLCJleHAiOjE3MjUyNzAwNzMsImlhdCI6MTcyNTI2OTg5Mywibm9uY2UiOiI5NzNlaWVsanpuZyJ9.VMMn92CFzGkVyx8Jwrq03KhuXOXj3wRlUoxZQQBN7MxlfIxGSX_yE7iw3JWxohzQuHticndtQX2LELcGTPhclzRop3skHCeo6ZPGJklCiRA3F5SyfCYLvDprgE_-pQhLWeECqRtW_8jFFgZSORMoxy8eBj5Vvc8q2zcoDjE-JiLZvqE9UWDRpAKzumJcD3iJvBwE-9jkzQtWZbp-tZrpPrm-KCZU6-Q3qhWU23E9DSMg_6byq4iH51TFwO0nHW1kaxhsqHvCsTX7YTvmfWXUwPVRLNZh5Uszt8EIsgpKIUDkRImqmCUbP1LwoFG55MsW67QzHNTFuR6H-4LidSKnnA");
        oauthDetailRequest.setClientId("mosip-signup-oauth-client");
        MockHttpServletRequest request = new MockHttpServletRequest();
        try {
            authorizationServiceImpl.getOauthDetailsV3(oauthDetailRequest, request);
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(ErrorConstants.INVALID_ID_TOKEN_HINT, e.getErrorCode());
        }
    }

    @Test
    public void getOauthDetailsV3_withValidIDTokenHintWrongAudience_thenFail() throws Exception {
        OAuthDetailRequestV3 oauthDetailRequest = new OAuthDetailRequestV3();
        oauthDetailRequest.setIdTokenHint("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
        MockHttpServletRequest request = new MockHttpServletRequest();

        //No audience claim
        try {
            authorizationServiceImpl.getOauthDetailsV3(oauthDetailRequest, request);
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(ErrorConstants.INVALID_ID_TOKEN_HINT, e.getErrorCode());
        }

        //wrong audience
        oauthDetailRequest.setIdTokenHint("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhbGljZSIsImF1ZCI6ImF1ZGllbmNlIiwiaXNzIjoidGVzdC1pc3N1ZXIiLCJleHAiOjE3MjUyNzA4OTgsImlhdCI6MTIzfQ.Z42f2G4xO7JKgKmA-JwCXOEDnXIGNwaB0Rksk0tkXrbfE2dtkASfGDej8FtQZlHsY1rdnjL7vP0NdoKmDUehYzhh-RESfqs6XdOCgNMS0NF5girKts0iAKSU4Exj3xjxpUsUOCmGU129m91WWYZZFTapByKf9UF4PGqiZEn_CIpojDv-D_qzH4XsU2oYy51PecNXF_KWL0Ix3IS8YaC0gTL5a7FZETQfao98vhZ88aWMqgVHVM_esXIpmAKYU-KiKGMW0zIVaoGX8gAV65XTlNGdPKSQUwrJ1hTmVXvWRLStyP8Bp9bjXMqCY1zFf2J-DpfrSnBhuGNIewrB4LHJ9A");
        try {
            authorizationServiceImpl.getOauthDetailsV3(oauthDetailRequest, request);
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(ErrorConstants.INVALID_ID_TOKEN_HINT, e.getErrorCode());
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
        String individualId = "23423434234";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"}));
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(transactionId, individualId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken("test-kyc-token");
        kycAuthResult.setPartnerSpecificUserToken("test-psut");
        when(authenticationWrapper.doKycAuth(anyString(), anyString(), anyBoolean(), any())).thenReturn(kycAuthResult);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId(individualId);
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
        String individualId = "23423434234";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"}));
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(transactionId, individualId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId(individualId);
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
        String individualId = "23423434234";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:static-code"}));
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(transactionId, individualId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:biometrics-generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:biometrics-generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken("test-kyc-token");
        kycAuthResult.setPartnerSpecificUserToken("test-psut");
        when(authenticationWrapper.doKycAuth(anyString(), anyString(), anyBoolean(), any())).thenReturn(kycAuthResult);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId(individualId);
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
        String individualId = "23423434234";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:linked-wallet"}));
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(transactionId, individualId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:linked-wallet"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:biometrics-generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:linked-wallet"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:biometrics-generated-code",
                "mosip:idp:acr:linked-wallet"})).thenReturn(allAuthFactors);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId(individualId);
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
        String individualId = "23423434234";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"}));
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(transactionId, individualId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken("test-kyc-token");
        kycAuthResult.setPartnerSpecificUserToken("test-psut");
        when(authenticationWrapper.doKycAuth(anyString(), anyString(), anyBoolean(), any())).thenReturn(kycAuthResult);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId(individualId);
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
        String individualId = "23423434234";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:generated-code", "mosip:idp:acr:static-code"}));
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(transactionId, individualId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId(individualId);
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
        String individualId = "23423434234";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:static-code"}));
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(transactionId, individualId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:biometrics-generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:static-code"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:biometrics-generated-code",
                "mosip:idp:acr:static-code"})).thenReturn(allAuthFactors);

        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken("test-kyc-token");
        kycAuthResult.setPartnerSpecificUserToken("test-psut");
        when(authenticationWrapper.doKycAuth(anyString(), anyString(), anyBoolean(), any())).thenReturn(kycAuthResult);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId(individualId);
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
        String individualId = "23423434234";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:linked-wallet"}));
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(transactionId, individualId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics-generated-code", "mosip:idp:acr:static-code"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:biometrics-generated-code"));
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:linked-wallet"));

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
    public void authenticateV3_withInvalidTransactionId_thenFail() {
        String transactionId = "test-transaction";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(null);

        AuthRequestV2 authRequest = new AuthRequestV2();
        authRequest.setTransactionId(transactionId);
        List<AuthChallenge> authChallenges = new ArrayList<>();
        AuthChallenge authChallenge = getAuthChallengeDto("WLA");
        authChallenges.add(authChallenge);
        authRequest.setChallengeList(authChallenges);

        try {
            authorizationServiceImpl.authenticateUserV3(authRequest,httpServletRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.INVALID_TRANSACTION));
        }
    }

    @Test
    public void authenticateV3_enableCaptcha_thenPass() throws KycAuthException {
        String transactionId = "test-transaction";
        String individualId = "23423434234";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics"}));
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(transactionId, individualId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:biometrics"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:biometrics"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:biometrics"})).thenReturn(allAuthFactors);

        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken("test-kyc-token");
        kycAuthResult.setPartnerSpecificUserToken("test-psut");
        when(authenticationWrapper.doKycAuth(anyString(), anyString(), anyBoolean(), any())).thenReturn(kycAuthResult);

        AuthRequestV2 authRequest = new AuthRequestV2();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId(individualId);
        authRequest.setCaptchaToken("captcha-token");
        List<AuthChallenge> authChallenges = new ArrayList<>();
        AuthChallenge authChallenge = getAuthChallengeDto("BIO");
        authChallenges.add(authChallenge);
        authRequest.setChallengeList(authChallenges);

        when(captchaHelper.validateCaptcha("captcha-token")).thenReturn(true);

        AuthResponseV2 authResponseV2 = authorizationServiceImpl.authenticateUserV3(authRequest, httpServletRequest);
        verify(captchaHelper, times(1)).validateCaptcha("captcha-token");
        Assert.assertNotNull(authResponseV2);
        Assert.assertEquals(transactionId, authResponseV2.getTransactionId());
    }

    @Test
    public void authenticateV3_withIDTokenInvalidIndividualId_thenFail() {
        String transactionId = "test-transaction";
        String individualId = "23423434234";
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:id-token"}));
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(transactionId, individualId)).thenReturn(createIdpTransaction(
                new String[]{"mosip:idp:acr:id-token"}));

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:id-token"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:id-token"})).thenReturn(allAuthFactors);

        AuthRequestV2 authRequest = new AuthRequestV2();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId(individualId);
        authRequest.setCaptchaToken("captcha-token");

        List<AuthChallenge> authChallenges = new ArrayList<>();
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("IDT");
        authChallenge.setChallenge("eyJ0b2tlbiI6ImV5SmhiR2NpT2lKSVV6STFOaUo5LmV5SnpkV0lpT2lKemRXSnFaV04wSW4wLjl0MG5GMkNtVWZaeTlCYlA3cjM4bElhSlJSeTNaSk41MnBRNlpLSl9qVWMifQ==");
        authChallenges.add(authChallenge);
        authRequest.setChallengeList(authChallenges);

        try{
            AuthResponseV2 authResponseV2 = authorizationServiceImpl.authenticateUserV3(authRequest, httpServletRequest);
            Assert.assertNotNull(authResponseV2);
        }catch (EsignetException ex){
            Assert.assertEquals(ErrorConstants.INVALID_INDIVIDUAL_ID,ex.getErrorCode());
        }
    }

    @Test
    public void authenticateV3_withIDToken_thenPass() {
        String transactionId = "test-transaction";
        String individualId = "subject";
        OIDCTransaction oidcTransaction = createIdpTransaction(new String[]{"mosip:idp:acr:id-token"});
        oidcTransaction.setNonce("server-nonce");
        when(cacheUtilService.getPreAuthTransaction(transactionId)).thenReturn(oidcTransaction);
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(transactionId, individualId)).thenReturn(oidcTransaction);

        List<List<AuthenticationFactor>> allAuthFactors=new ArrayList<>();
        allAuthFactors.add(getAuthFactors("mosip:idp:acr:id-token"));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:id-token"})).thenReturn(allAuthFactors);

        AuthRequestV2 authRequest = new AuthRequestV2();
        authRequest.setTransactionId(transactionId);
        authRequest.setIndividualId(individualId);
        authRequest.setCaptchaToken("captcha-token");

        List<AuthChallenge> authChallenges = new ArrayList<>();
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("IDT");
        authChallenge.setChallenge("eyJ0b2tlbiI6ImV5SmhiR2NpT2lKSVV6STFOaUo5LmV5SnpkV0lpT2lKemRXSnFaV04wSWl3aWJtOXVZMlVpT2lKelpYSjJaWEl0Ym05dVkyVWlmUS5CcU5FWF82YUhIc0J2MDVzc0ZqaXVjZ0dzQTZYSW1RWUxWaDZseXFXMXM0In0=");
        authChallenges.add(authChallenge);
        authRequest.setChallengeList(authChallenges);

        Mockito.when(httpServletRequest.getCookies()).thenReturn(new Cookie[]{new Cookie("subject",
                "server-nonce".concat(SERVER_NONCE_SEPARATOR).concat("sanitized-path-fragment"))});

        OIDCTransaction haltedTransaction = new OIDCTransaction();
        haltedTransaction.setIndividualId("individualId");
        haltedTransaction.setTransactionId("transactionId");
        haltedTransaction.setServerNonce("server-nonce");
        Mockito.when(cacheUtilService.getHaltedTransaction(Mockito.anyString())).thenReturn(haltedTransaction);

        AuthResponseV2 authResponseV2 = authorizationServiceImpl.authenticateUserV3(authRequest, httpServletRequest);
        verify(captchaHelper, times(0)).validateCaptcha("captcha-token");
        Assert.assertNotNull(authResponseV2);
    }

    @Test
    public void completeSignupRedirect_withValidTransactionId_thenPass() {
        String transactionId = "validTransactionId";
        CompleteSignupRedirectRequest completeSignupRedirectRequest = new CompleteSignupRedirectRequest();
        completeSignupRedirectRequest.setTransactionId(transactionId);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setVerificationStatus(VERIFICATION_COMPLETE);
        when(cacheUtilService.getHaltedTransaction(transactionId)).thenReturn(oidcTransaction);
        CompleteSignupRedirectResponse result = authorizationServiceImpl.completeSignupRedirect(completeSignupRedirectRequest);
        Assert.assertEquals(Constants.VERIFICATION_COMPLETE, result.getStatus());
    }

    @Test
    public void completeSignupRedirect_withInvalidTransactionId_thenFail() {
        String transactionId = "invalidTransactionId";
        CompleteSignupRedirectRequest completeSignupRedirectRequest = new CompleteSignupRedirectRequest();
        completeSignupRedirectRequest.setTransactionId(transactionId);
        when(cacheUtilService.getHaltedTransaction(transactionId)).thenReturn(null);
        assertThrows(InvalidTransactionException.class, () -> {
            authorizationServiceImpl.completeSignupRedirect(completeSignupRedirectRequest);
        });
    }

    @Test
    public void completeSignupRedirect_withStatusAsNotCompleted_thenFail() {
        String transactionId = "transactionId";
        CompleteSignupRedirectRequest completeSignupRedirectRequest = new CompleteSignupRedirectRequest();
        completeSignupRedirectRequest.setTransactionId(transactionId);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setVerificationStatus("FAILED");
        when(cacheUtilService.getHaltedTransaction(transactionId)).thenReturn(oidcTransaction);
        try{
            authorizationServiceImpl.completeSignupRedirect(completeSignupRedirectRequest);
        }catch (EsignetException ex){
            Assert.assertEquals(ErrorConstants.VERIFICATION_INCOMPLETE,ex.getErrorCode());
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
        transaction.setConsentAction(ConsentAction.NOCAPTURE);
    	Claims requestedClaims = new Claims();
    	Map<String, List<Map<String, Object>>> userinfo = new HashMap<>();
        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("value", "test");
        nameMap.put("values", new String[] {"test"});
        nameMap.put("essential", true);
    	userinfo.put("fullName", Arrays.asList(nameMap));
		requestedClaims.setUserinfo(userinfo);
		transaction.setResolvedClaims(requestedClaims);
		Mockito.when(cacheUtilService.getAuthenticatedTransaction(Mockito.anyString())).thenReturn(transaction);
		Mockito.when(cacheUtilService.setAuthCodeGeneratedTransaction(Mockito.anyString(), Mockito.any())).thenReturn(transaction);
		Assert.assertEquals(authorizationServiceImpl.getAuthCode(authCodeRequest).getNonce(), "test-nonce");
		Assert.assertEquals(authorizationServiceImpl.getAuthCode(authCodeRequest).getState(), "test-state");
    }

    @Test
    public void getAuthCode_withInValidTransactionId_thenFail() {
        AuthCodeRequest authCodeRequest = new AuthCodeRequest();
        authCodeRequest.setTransactionId("987654321");
        authCodeRequest.setAcceptedClaims(Arrays.asList("fullName"));
        authCodeRequest.setPermittedAuthorizeScopes(Arrays.asList("test-scope"));
        Mockito.when(cacheUtilService.getAuthenticatedTransaction(Mockito.anyString())).thenReturn(null);
        try{
            authorizationServiceImpl.getAuthCode(authCodeRequest);
            Assert.fail();
        }catch (EsignetException e){
            Assert.assertEquals("invalid_transaction",e.getErrorCode());
        }
    }

    @Test
    public void getClaimDetails_withUnVerifiedClaimsRequest_thenPass(){
        OIDCTransaction transaction=new OIDCTransaction();
	Claims resolvedClaims = new Claims();
	resolvedClaims.setUserinfo(new HashMap<>());
	Map<String, Object> map = new HashMap<>();
	map.put("essential", true);
    	Map<String, Object> requestedMetadata = new HashMap<>();
	requestedMetadata.put("trust_framework", objectMapper.readValue("{\"values\":[\"ABC TF\"]}", Map.class));
	map.put("verification", requestedMetadata);
	resolvedClaims.getUserinfo().put("name", Arrays.asList(map));
	resolvedClaims.getUserinfo().put("email", Arrays.asList(map));
	
	Map<String, Object> phoneClaimRequest = new HashMap<>();
	phoneClaimRequest.put("essential", false);
	resolvedClaims.getUserinfo().put("phone_number", Arrays.asList(phoneClaimRequest));
	
	transaction.setResolvedClaims(resolvedClaims);
	transaction.setEssentialClaims(List.of("name", "email"));
	transaction.setVoluntaryClaims(List.of("phone_number"));
	
	Map<String, List<JsonNode>> claimMetadata = new HashMap<>();
	claimMetadata.put("name", null);
	List<JsonNode> verificationList =  new ArrayList<>();
	verificationList.add(objectMapper.readTree("{\"trust_framework\":\"ABC TF\"}"));
	claimMetadata.put("email", verificationList);
	
	transaction.setClaimMetadata(claimMetadata);
	
	transaction.setConsentAction(ConsentAction.NOCAPTURE);
	Mockito.when(cacheUtilService.getAuthenticatedTransaction(Mockito.anyString())).thenReturn(transaction);
	
	ClaimDetailResponse claimDetailResponse = authorizationServiceImpl.getClaimDetails("transactionId");
	Assert.assertEquals(claimDetailResponse.getConsentAction(),ConsentAction.NOCAPTURE);
	Assert.assertEquals(claimDetailResponse.getTransactionId(),"transactionId");
	Assert.assertTrue(claimDetailResponse.isProfileUpdateRequired());
	Assert.assertNotNull(claimDetailResponse.getClaimStatus());
	for(ClaimStatus claimStatus : claimDetailResponse.getClaimStatus()) {
	        switch (claimStatus.getClaim()) {
	            case "email" :
	                Assert.assertTrue(claimStatus.isAvailable());
	                Assert.assertTrue(claimStatus.isVerified());
	                break;
	
	            case "name" :
	                Assert.assertTrue(claimStatus.isAvailable());
	                Assert.assertFalse(claimStatus.isVerified());
	                break;
	
	            case "phone_number" :
	                Assert.assertFalse(claimStatus.isAvailable());
	                Assert.assertFalse(claimStatus.isVerified());
	                break;
	        }
	}
    }

    @Test
    public void getClaimDetails_withVerifiedClaimsRequest_thenPass() throws JsonProcessingException {
        OIDCTransaction transaction=new OIDCTransaction();
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        Map<String, Object> map = new HashMap<>();
        map.put("essential", true);
        Map<String, Object> requestedVerification = new HashMap<>();
        requestedVerification.put("trust_framework", null);
        map.put("verification", requestedVerification);
        resolvedClaims.getUserinfo().put("name", Arrays.asList(map));
        transaction.setResolvedClaims(resolvedClaims);
        transaction.setEssentialClaims(List.of("name", "email"));
        transaction.setVoluntaryClaims(List.of("phone_number"));

        Map<String, List<JsonNode>> claimMetadata = new HashMap<>();
        transaction.setClaimMetadata(claimMetadata);
        transaction.setConsentAction(ConsentAction.CAPTURE);
        Mockito.when(cacheUtilService.getAuthenticatedTransaction(Mockito.anyString())).thenReturn(transaction);

        ClaimDetailResponse claimDetailResponse = authorizationServiceImpl.getClaimDetails("transactionId");
        Assert.assertEquals(claimDetailResponse.getConsentAction(),ConsentAction.CAPTURE);
        Assert.assertEquals(claimDetailResponse.getTransactionId(),"transactionId");
        Assert.assertTrue(claimDetailResponse.getClaimStatus().stream().allMatch(cs -> !cs.isVerified() && !cs.isAvailable()));
        Assert.assertTrue(claimDetailResponse.isProfileUpdateRequired());

        Map<String, Object> emailMap = new HashMap<>();
        emailMap.put("essential", true);
        resolvedClaims.getUserinfo().put("email", Arrays.asList(emailMap));
        Map<String, Object> phoneMap = new HashMap<>();
        phoneMap.put("essential", false);
        resolvedClaims.getUserinfo().put("phone_number", Arrays.asList(phoneMap));
        claimMetadata.put("name", Arrays.asList(objectMapper.readTree("{\"verification\": {\"trust_framework\": \"XYZ TF\"}}")));
        claimMetadata.put("phone_number", Arrays.asList());
        claimDetailResponse = authorizationServiceImpl.getClaimDetails("transactionId");
        Assert.assertTrue(claimDetailResponse.getClaimStatus().stream().anyMatch(cs -> cs.getClaim().equals("name") && cs.isVerified() && cs.isAvailable()));
        Assert.assertTrue(claimDetailResponse.getClaimStatus().stream().anyMatch(cs -> cs.getClaim().equals("email") && !cs.isVerified() && !cs.isAvailable()));
        Assert.assertTrue(claimDetailResponse.getClaimStatus().stream().anyMatch(cs -> cs.getClaim().equals("phone_number") && !cs.isVerified() && cs.isAvailable()));
        Assert.assertFalse(claimDetailResponse.isProfileUpdateRequired());
    }

    @Test
    public void getClaimDetails_withInvalidTransaction_thenFail(){
        Mockito.when(cacheUtilService.getAuthenticatedTransaction(Mockito.anyString())).thenReturn(null);
        try{
            authorizationServiceImpl.getClaimDetails("transactionId");
        }catch (InvalidTransactionException ex){
            Assert.assertEquals(ex.getErrorCode(),ErrorConstants.INVALID_TRANSACTION);
        }
    }

    @Test
    public void testSendOtp_ValidRequest_thenPass() throws Exception {
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setCaptchaToken("captchaToken");
        otpRequest.setTransactionId("transactionId");
        otpRequest.setIndividualId("individualId");
        ArrayList<String> otpChannels=new ArrayList<>();
        otpRequest.setOtpChannels(otpChannels);

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setTransactionId("transactionId");
        transaction.setIndividualIdHash("individualIdHash");
        transaction.setRelyingPartyId("relyingPartyId");
        transaction.setClientId("clientId");
        transaction.setAuthTransactionId("transactionId");
        when(cacheUtilService.getPreAuthTransaction(Mockito.anyString())).thenReturn(transaction);
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(Mockito.anyString(), Mockito.anyString())).thenReturn(transaction);
        when(cacheUtilService.isIndividualIdBlocked(Mockito.anyString())).thenReturn(false);

        SendOtpResult sendOtpResult = new SendOtpResult();
        sendOtpResult.setTransactionId("transactionId");
        sendOtpResult.setMaskedEmail("maskedEmail");
        sendOtpResult.setMaskedMobile("maskedMobile");

        SendOtpDto sendOtpDto=new SendOtpDto();
        sendOtpDto.setTransactionId("transactionId");
        sendOtpDto.setIndividualId("individualId");
        ArrayList<String> otpChannel=new ArrayList<>();
        sendOtpDto.setOtpChannels(otpChannel);

        Mockito.when(authenticationWrapper.sendOtp("relyingPartyId","clientId",sendOtpDto)).thenReturn(sendOtpResult);
        OtpResponse otpResponse = authorizationServiceImpl.sendOtp(otpRequest);
        Assert.assertNotNull(otpResponse);
        Assert.assertEquals("transactionId", otpResponse.getTransactionId());
        Assert.assertEquals("maskedEmail", otpResponse.getMaskedEmail());
        Assert.assertEquals("maskedMobile", otpResponse.getMaskedMobile());
    }

    @Test
    public void sendOtp_whenIndividualIdBlocked_thenFail() throws Exception {
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setCaptchaToken("captchaToken");
        otpRequest.setTransactionId("transactionId");
        otpRequest.setIndividualId("individualId");
        ArrayList<String> otpChannels=new ArrayList<>();
        otpRequest.setOtpChannels(otpChannels);

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setTransactionId("transactionId");
        transaction.setIndividualIdHash("individualIdHash");
        transaction.setRelyingPartyId("relyingPartyId");
        transaction.setClientId("clientId");
        transaction.setAuthTransactionId("transactionId");
        when(cacheUtilService.getPreAuthTransaction(Mockito.anyString())).thenReturn(transaction);
        when(cacheUtilService.updateIndividualIdHashInPreAuthCache(Mockito.anyString(), Mockito.anyString())).thenReturn(transaction);
        when(cacheUtilService.isIndividualIdBlocked(Mockito.anyString())).thenReturn(true);
        try {
            authorizationServiceImpl.sendOtp(otpRequest);
        }catch(EsignetException e)
        {
            Assert.assertEquals(ErrorConstants.INDIVIDUAL_ID_BLOCKED,e.getErrorCode());
        }
    }

    @Test
    public void sendOtp_invalidTransactionId_thenFail() throws Exception {
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setTransactionId("invalidTransactionId");
        when(cacheUtilService.getPreAuthTransaction("invalidTransactionId")).thenReturn(null);
        try{
            authorizationServiceImpl.sendOtp(otpRequest);
            Assert.fail();
        }catch(EsignetException e){
            Assert.assertEquals("invalid_transaction",e.getErrorCode());
        }
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
            case "mosip:idp:acr:id-token":
                acrAuthFactors.add(new AuthenticationFactor("IDT", 0, null));
                break;
        }
        return acrAuthFactors;
    }

    private JsonNode getClaimDetail(String value, String[] values, boolean essential) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("value", value);
        detail.put("values", values);
        detail.put("essential", essential);
        try {
            return objectMapper.readTree(objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


}
