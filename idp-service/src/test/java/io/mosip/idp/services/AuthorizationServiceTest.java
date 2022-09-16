package io.mosip.idp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidClientException;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.entity.ClientDetail;
import io.mosip.idp.repository.ClientDetailRepository;
import io.mosip.idp.services.AuthorizationServiceImpl;
import io.mosip.idp.services.CacheUtilService;
import io.mosip.idp.services.TokenServiceImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationServiceTest {

    @Mock
    ClientDetailRepository clientDetailRepository;

    @Mock
    AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Mock
    CacheUtilService cacheUtilService;

    @InjectMocks
    AuthorizationServiceImpl authorizationServiceImpl;

    @Mock
    TokenServiceImpl tokenGeneratorServiceService;

    @Mock
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    Resource mappingFile;


    private static final String amr_acr_mapping = "{\n" +
            "\t\"locales\": {\"en\" :  \"eng\", \"fr\":  \"fra\", \"ar\":  \"ara\" },\n" +
            "\t\"attributes\" : {\n" +
            "\t\t\t\"fullName\" : { \"path\": \"$.identity.fullName[?(@.language=='_LOCALE_')].value\", \"defaultLocale\" : \"en\" },\n" +
            "\t\t\t\"dateOfBirth\" : { \"path\": \"$.identity.dateOfBirth\"},\n" +
            "\t\t\t\"gender\" : { \"path\": \"$.identity.gender[?(@.language=='_LOCALE_')].value\", \"defaultLocale\" : \"en\" },\n" +
            "\t\t\t\"email\" : { \"path\": \"$.identity.email\"},\n" +
            "\t\t\t\"phone\" : { \"path\": \"$.identity.phone\"},\n" +
            "\t\t\t\"addressLine1\" : { \"path\": \"$.identity.addressLine1[?(@.language=='_LOCALE_')].value\", \"defaultLocale\" : \"en\" },\n" +
            "\t\t\t\"addressLine2\" : { \"path\": \"$.identity.addressLine2[?(@.language=='_LOCALE_')].value\", \"defaultLocale\" : \"en\" },\n" +
            "\t\t\t\"addressLine3\" : { \"path\": \"$.identity.addressLine3[?(@.language=='_LOCALE_')].value\", \"defaultLocale\" : \"en\" },\n" +
            "\t\t\t\"province\" : { \"path\": \"$.identity.province[?(@.language=='_LOCALE_')].value\", \"defaultLocale\" : \"en\" },\n" +
            "\t\t\t\"region\" : { \"path\": \"$.identity.region[?(@.language=='_LOCALE_')].value\", \"defaultLocale\" : \"en\" },\n" +
            "\t\t\t\"postal_code\" : { \"path\": \"$.identity.postalCode\" },\n" +
            "\t\t\t\"zone\" : { \"path\": \"$.identity.zone[?(@.language=='_LOCALE_')].value\", \"defaultLocale\" : \"en\" },\n" +
            "\t\t\t\"encodedPhoto\" : { \"path\": \"$.identity.encodedPhoto\"}\n" +
            "\t},\n" +
            "\t\"claims\" : {\n" +
            "\t\t\t\"given_name\" : \"fullName\",\n" +
            "\t\t\t\"name\" : \"fullName\",\n" +
            "\t\t\t\"middle_name\" : \"\",\n" +
            "\t\t\t\"preferred_username\" : \"fullName\",\n" +
            "\t\t\t\"nickname\" : \"\",\n" +
            "\t\t\t\"family_name\" : \"\",\n" +
            "\t\t\t\"gender\" : \"gender\",\n" +
            "\t\t\t\"birthdate\" : \"dateOfBirth\",\t\t\t\n" +
            "\t\t\t\"email\" : \"email\",\n" +
            "\t\t\t\"phone_number\" : \"phone\",\n" +
            "\t\t\t\"locale\" : \"\",\n" +
            "\t\t\t\"email_verified\" : \"\",\t\n" +
            "\t\t\t\"phone_number_verified\" : \"\",\n" +
            "\t\t\t\"picture\": \"encodedPhoto\",\n" +
            "\t\t\t\"zoneinfo\" : \"\",\n" +
            "\t\t\t\"updated_at\" : \"\",\n" +
            "\t\t\t\"address\" : { \"street_address\" : \"\",  \"locality\" : \"province\", \"region\" : \"region\",\n" +
            "\t\t\t\t\"postal_code\": \"postalCode\", \"country\" : \"\",\n" +
            "\t\t\t\t\"formatted\" : [\"addressLine1\", \"addressLine2\", \"addressLine3\"] }\n" +
            "\t}\t\n" +
            "}";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Map<String, List<String>> claims = new HashMap<>();
        claims.put("profile", Arrays.asList("given_name", "profile_picture", "name", "phone_number", "email"));
        claims.put("email", Arrays.asList("email","email_verified"));
        claims.put("phone", Arrays.asList("phone_number","phone_number_verified"));
        ReflectionTestUtils.setField(authorizationServiceImpl, "claims", claims);
        ReflectionTestUtils.setField(authorizationServiceImpl, "authorizeScopes", Arrays.asList("resident-service"));
    }


    @Test(expected = InvalidClientException.class)
    public void getOauthDetails_withInvalidClientId_throwsException() throws IdPException {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setNonce("test-nonce");
        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.empty());
        authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
    }

    @Test
    public void getOauthDetails_withInvalidRedirectUri_throwsException() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v2/idp");
        oauthDetailRequest.setNonce("test-nonce");
        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));

        try {
            authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
            Assert.fail();
        } catch (IdPException e) {
            Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_REDIRECT_URI));
        }
    }

    @Test
    public void getOauthDetails_withNullClaimsInDbAndNullClaimsInReq() throws IdPException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims(null);
        clientDetail.setAcrValues("mosip:idp:acr:static-code");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:static-code");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:static-code"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponse.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withNullClaimsInDbAndValidClaimsInReq() throws IdPException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims(null);
        clientDetail.setAcrValues("mosip:idp:acr:static-code");

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

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:static-code"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponse.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withValidClaimsInDbAndValidClaimsInReq() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues("mosip:idp:acr:static-code");

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

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:static-code"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().size() == 1);
        Assert.assertTrue(oauthDetailResponse.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withValidClaimsInDbAndInValidClaimsInReq() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues("mosip:idp:acr:generated-code");

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

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:generated-code"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponse.getVoluntaryClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withNullAcrInDB() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues(null);

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("mosip:idp:acr:generated-code mosip:idp:acr:static-code");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));

        try {
            authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
            Assert.fail();
        } catch (IdPException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.NO_ACR_REGISTERED));
        }
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndNullAcrInReq() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues("mosip:idp:acr:generated-code,mosip:idp:acr:linked-wallet");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues(null);
        oauthDetailRequest.setNonce("test-nonce");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
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
    public void getOauthDetails_withValidAcrInDBAndValidAcrInReq() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues("mosip:idp:acr:generated-code,mosip:idp:acr:linked-wallet");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("level21 mosip:idp:acr:linked-wallet");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
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
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues("mosip:idp:acr:generated-code,mosip:idp:acr:linked-wallet");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("level21 mosip:idp:acr:linked-wallet mosip:idp:acr:generated-code");
        oauthDetailRequest.setNonce("test-nonce");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        //NOTE: if order differs then below mock will not be used, hence will not return null
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"mosip:idp:acr:linked-wallet",
                "mosip:idp:acr:generated-code"})).thenReturn(null);

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertNull(oauthDetailResponse.getAuthFactors());
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndValidAcrClaimInReq() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues("level2,level5");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setNonce("test-nonce");
        Claims claims = new Claims();
        claims.setId_token(new HashMap<>());
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setValues(new String[]{"level2", "level1"});
        claims.getId_token().put("acr", claimDetail);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("level21 level1 level5");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        List<List<AuthenticationFactor>> authFactors = new ArrayList<>();
        authFactors.add(Collections.emptyList());
        //Highest priority is given to ACR in claims request parameter
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"level2"})).thenReturn(authFactors);

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getAuthFactors().size() == 1);
    }

    @Test
    public void getOauthDetails_withValidClaimsInDbAndValidClaimsInReqAndNoOPENIDScope() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues("level4");

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
        oauthDetailRequest.setAcrValues("level4");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));

        try {
            authorizationServiceImpl.getOauthDetails(oauthDetailRequest);
            Assert.fail();
        } catch (IdPException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.INVALID_SCOPE));
        }
    }
}
