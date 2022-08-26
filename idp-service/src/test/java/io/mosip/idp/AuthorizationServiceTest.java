package io.mosip.idp;

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
import io.mosip.idp.services.TokenGeneratorServiceServiceImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.Resource;

import java.util.*;

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
    TokenGeneratorServiceServiceImpl tokenGeneratorServiceService;

    @Mock
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    Resource mappingFile;

    private static final String amr_acr_mapping = "{\n" +
            "  \"amr_values\" : {\n" +
            "    \"only_otp\" :  [{ \"name\": \"otp\" }],\n" +
            "    \"only_finger\" :  [{ \"name\": \"fpt\", \"count\": 1, \"bioSubTypes\" : [\"leftThumb\"] }],\n" +
            "    \"only_iris\" :  [{ \"name\": \"iris\", \"count\": 2 }],\n" +
            "    \"five_fingers\" : [{ \"name\": \"fpt\", \"count\": 4 , \"bioSubTypes\" : [\"unknown\", \"unknown\", \"unknown\", \"unknown\"]}],\n" +
            "    \"otp_one_finger\" : [{ \"name\": \"otp\" },{ \"name\": \"fpt\", \"count\": 1 , \"bioSubTypes\" : [\"rightThumb\"]}],\n" +
            "    \"otp_all_fingers\" : [{ \"name\": \"otp\" },{ \"name\": \"fpt\", \"count\": 10 }],\n" +
            "    \"iris_otp\" :  [{ \"name\": \"iris\", \"count\": 2 }, { \"name\": \"otp\" }]\n" +
            "  },\n" +
            "  \"acr_values\" : {\n" +
            "    \"level1\" : \"1\",\n" +
            "    \"level2\" : \"2\",\n" +
            "    \"level3\" : \"3\",\n" +
            "    \"level4\" : \"4\",\n" +
            "    \"level5\" : \"5\"\n" +
            "  },\n" +
            "  \"acr_amr\" : {\n" +
            "                \"1\" :  [\"only_otp\"],\n" +
            "                \"2\" :  [\"only_otp\", \"only_finger\"],\n" +
            "                \"3\" :  [\"five_fingers\", \"otp_one_finger\"],\n" +
            "                \"4\" :  [\"otp_all_fingers\", \"only_iris\"],\n" +
            "                \"5\" :  [\"iris_otp\"]\n" +
            "              }\n" +
            "}";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(tokenGeneratorServiceService.getOptionalIdTokenClaims()).thenReturn(Arrays.asList("nonce", "acr", "at_hash", "auth_time"));
    }


    @Test(expected = InvalidClientException.class)
    public void getOauthDetails_withInvalidClientId_throwsException() throws IdPException {
        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.empty());
        authorizationServiceImpl.getOauthDetails("test-nonce", oauthDetailRequest);
    }

    @Test
    public void getOauthDetails_withInvalidRedirectUri_throwsException() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v2/idp");
        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));

        try {
            authorizationServiceImpl.getOauthDetails("test-nonce", oauthDetailRequest);
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
        clientDetail.setAcrValues("level4");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("level4");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"level4"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails("test-nonce", oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponse.getOptionalClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withNullClaimsInDbAndValidClaimsInReq() throws IdPException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims(null);
        clientDetail.setAcrValues("level4");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        Claims claims = new Claims();
        Map<String, ClaimDetail> userClaims = new HashMap<>();
        userClaims.put("given_name", new ClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("level4");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"level4"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails("test-nonce", oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponse.getOptionalClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withValidClaimsInDbAndValidClaimsInReq() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues("level4");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        Claims claims = new Claims();
        Map<String, ClaimDetail> userClaims = new HashMap<>();
        userClaims.put("given_name", new ClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("level4");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"level4"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails("test-nonce", oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().size() == 1);
        Assert.assertTrue(oauthDetailResponse.getOptionalClaims().isEmpty());
    }

    @Test
    public void getOauthDetails_withValidClaimsInDbAndInValidClaimsInReq() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues("level4");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        Claims claims = new Claims();
        Map<String, ClaimDetail> userClaims = new HashMap<>();
        userClaims.put("phone", new ClaimDetail(null, null, true));
        claims.setUserinfo(userClaims);
        oauthDetailRequest.setClaims(claims);
        oauthDetailRequest.setAcrValues("level4");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"level4"})).thenReturn(new ArrayList<>());

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails("test-nonce", oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getEssentialClaims().isEmpty());
        Assert.assertTrue(oauthDetailResponse.getOptionalClaims().isEmpty());
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
        oauthDetailRequest.setAcrValues("level4 level1");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));

        try {
            authorizationServiceImpl.getOauthDetails("test-nonce", oauthDetailRequest);
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
        clientDetail.setAcrValues("level1");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues(null);

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));

        try {
            authorizationServiceImpl.getOauthDetails("test-nonce", oauthDetailRequest);
            Assert.fail();
        } catch (IdPException ex) {
            Assert.assertTrue(ex.getErrorCode().equals(ErrorConstants.INVALID_ACR));
        }
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndValidAcrInReq() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues("level1,level2,level5");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("level21 level1 level5");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        List<List<AuthenticationFactor>> authFactors = new ArrayList<>();
        authFactors.add(Collections.emptyList());
        authFactors.add(Collections.emptyList());
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"level1","level5"})).thenReturn(authFactors);

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails("test-nonce", oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getAuthFactors().size() == 2);
    }

    @Test
    public void getOauthDetails_withValidAcrInDBAndValidAcrInReq_orderOfPrecedencePreserved() throws Exception {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("34567");
        clientDetail.setRedirectUris("https://localshot:3044/logo.png,http://localhost:8088/v1/idp,/v1/idp");
        clientDetail.setClaims("email,given_name");
        clientDetail.setAcrValues("level1,level2,level5");

        OAuthDetailRequest oauthDetailRequest = new OAuthDetailRequest();
        oauthDetailRequest.setClientId("34567");
        oauthDetailRequest.setRedirectUri("http://localhost:8088/v1/idp");
        oauthDetailRequest.setClaims(null);
        oauthDetailRequest.setAcrValues("level21 level1 level5");

        when(clientDetailRepository.findByIdAndStatus(oauthDetailRequest.getClientId(), "ACTIVE")).thenReturn(Optional.of(clientDetail));
        List<List<AuthenticationFactor>> authFactors = new ArrayList<>();
        authFactors.add(Collections.emptyList());
        authFactors.add(Collections.emptyList());
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"level1","level5"})).thenReturn(null);

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails("test-nonce", oauthDetailRequest);
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
        when(authenticationContextClassRefUtil.getAuthFactors(new String[]{"level5"})).thenReturn(authFactors);

        OAuthDetailResponse oauthDetailResponse = authorizationServiceImpl.getOauthDetails("test-nonce", oauthDetailRequest);
        Assert.assertNotNull(oauthDetailResponse);
        Assert.assertTrue(oauthDetailResponse.getAuthFactors().size() == 1);
    }
}
