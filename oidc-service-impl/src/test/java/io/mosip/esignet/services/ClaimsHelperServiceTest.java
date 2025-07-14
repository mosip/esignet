/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.api.dto.claim.ClaimsV2;
import io.mosip.esignet.api.util.FilterCriteriaMatcher;
import io.mosip.esignet.core.dto.ClaimStatus;
import io.mosip.esignet.core.dto.ClientDetail;
import io.mosip.esignet.core.dto.OAuthDetailRequest;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static io.mosip.esignet.core.constants.Constants.ESSENTIAL;
import static io.mosip.esignet.core.constants.Constants.VOLUNTARY;
import static io.mosip.esignet.core.constants.ErrorConstants.*;


@ExtendWith(MockitoExtension.class)
public class ClaimsHelperServiceTest {

    @InjectMocks
    private ClaimsHelperService claimsHelperService;

    private ObjectMapper objectMapper = new ObjectMapper();


    @BeforeEach
    public void setup() {
        Map<String, List<String>> scopeClaimsMapping = new HashMap<>();
        scopeClaimsMapping.put("profile", Arrays.asList("name", "gender", "email"));
        scopeClaimsMapping.put("email", Arrays.asList("email"));

        FilterCriteriaMatcher filterCriteriaMatcher = new FilterCriteriaMatcher();
        ReflectionTestUtils.setField(filterCriteriaMatcher, "objectMapper", objectMapper);

        ReflectionTestUtils.setField(claimsHelperService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(claimsHelperService, "claims", scopeClaimsMapping);
        ReflectionTestUtils.setField(claimsHelperService, "filterCriteriaMatcher", filterCriteriaMatcher);
    }


    @Test
    public void getClaimNames_test() {
        Claims resolvedClaims = new Claims();
        Map<String, List<Map<String, Object>>> userinfoClaims = new HashMap<>();
        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("essential", true);
        Map<String, Object> dateMap = new HashMap<>();
        dateMap.put("essential", true);
        userinfoClaims.put("name", Arrays.asList(nameMap));
        userinfoClaims.put("birthdate", Arrays.asList(dateMap));
        userinfoClaims.put("address", Arrays.asList(new HashMap<>()));
        userinfoClaims.put("gender", null);
        resolvedClaims.setUserinfo(userinfoClaims);
        Map<String, List<String>> result = claimsHelperService.getClaimNames(resolvedClaims);
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.get(ESSENTIAL));
        Assertions.assertTrue(result.get(ESSENTIAL).containsAll(Arrays.asList("name", "birthdate")));
        Assertions.assertNotNull(result.get(VOLUNTARY));
        Assertions.assertTrue(result.get(VOLUNTARY).containsAll(Arrays.asList("address", "gender")));
    }

    @Test
    public void validateAcceptedClaims_withEmptyAcceptedClaims_thenPass() {
        claimsHelperService.validateAcceptedClaims(new OIDCTransaction(), new ArrayList<>());
    }

    @Test
    public void validateAcceptedClaims_withNullRequestedClaims_thenFail() {

        try {
            claimsHelperService.validateAcceptedClaims(
                    new OIDCTransaction(), Arrays.asList("name", "gender")
            );
            Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals(INVALID_ACCEPTED_CLAIM, e.getErrorCode());
        }
    }
    @Test
    public void validateAcceptedClaims_withEmptyRequestedClaims_thenFail() {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setResolvedClaims(resolvedClaims);
        try {
            claimsHelperService.validateAcceptedClaims(oidcTransaction, Arrays.asList("name", "gender"));
            Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals(INVALID_ACCEPTED_CLAIM, e.getErrorCode());
        }
    }

    @Test
    public void validateAcceptedClaims_withInvalidAcceptedClaims_thenFail() {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        Map<String, List<Map<String, Object>>> userinfoClaims = new HashMap<>();
        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("essential", true);
        Map<String, Object> dateMap = new HashMap<>();
        dateMap.put("essential", true);
        userinfoClaims.put("name", Arrays.asList(nameMap));
        userinfoClaims.put("birthdate", Arrays.asList(dateMap));
        userinfoClaims.put("address", Arrays.asList(new HashMap<>()));
        userinfoClaims.put("gender", null);
        resolvedClaims.setUserinfo(userinfoClaims);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setResolvedClaims(resolvedClaims);
        try {
            claimsHelperService.validateAcceptedClaims(oidcTransaction, Arrays.asList("email", "phone_number"));
            Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals(INVALID_ACCEPTED_CLAIM, e.getErrorCode());
        }
    }

    @Test
    public void validateAcceptedClaims_withValidAcceptedEssentialClaims_thenPass() {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        Map<String, List<Map<String, Object>>> userinfoClaims = new HashMap<>();
        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("essential", true);
        Map<String, Object> dateMap = new HashMap<>();
        dateMap.put("essential", true);
        userinfoClaims.put("name", Arrays.asList(nameMap));
        userinfoClaims.put("birthdate", Arrays.asList(dateMap));
        userinfoClaims.put("address", Arrays.asList(new HashMap<>()));
        userinfoClaims.put("gender", null);
        resolvedClaims.setUserinfo(userinfoClaims);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setResolvedClaims(resolvedClaims);
        claimsHelperService.validateAcceptedClaims(oidcTransaction, Arrays.asList("name", "birthdate"));
    }

    @Test
    public void validateAcceptedClaims_withAllOptionalClaimsNotAccepted_thenPass() {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        Map<String, List<Map<String, Object>>> userinfoClaims = new HashMap<>();
        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("essential", true);
        Map<String, Object> dateMap = new HashMap<>();
        dateMap.put("essential", true);
        userinfoClaims.put("name", Arrays.asList(nameMap));
        userinfoClaims.put("birthdate", Arrays.asList(dateMap));
        userinfoClaims.put("address", Arrays.asList(new HashMap<>()));
        userinfoClaims.put("gender", null);
        resolvedClaims.setUserinfo(userinfoClaims);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setResolvedClaims(resolvedClaims);
        claimsHelperService.validateAcceptedClaims(oidcTransaction, List.of());
    }

    @Test
    public void validateAcceptedClaims_withSomeValidAcceptedEssentialClaims_thenFail() {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        Map<String, List<Map<String, Object>>> userinfoClaims = new HashMap<>();
        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("essential", true);
        Map<String, Object> dateMap = new HashMap<>();
        dateMap.put("essential", true);
        userinfoClaims.put("name", Arrays.asList(nameMap));
        userinfoClaims.put("birthdate", Arrays.asList(dateMap));
        userinfoClaims.put("address", Arrays.asList(new HashMap<>()));
        userinfoClaims.put("gender", null);
        resolvedClaims.setUserinfo(userinfoClaims);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setResolvedClaims(resolvedClaims);
        oidcTransaction.setEssentialClaims(Arrays.asList("name", "birthdate"));
        try {
            claimsHelperService.validateAcceptedClaims(oidcTransaction, Arrays.asList("name", "address"));
            Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals(INVALID_ACCEPTED_CLAIM, e.getErrorCode());
        }
    }

    @Test
    public void validateAcceptedClaims_withAllOptionalClaims_thenFail() {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        Map<String, List<Map<String, Object>>> userinfoClaims = new HashMap<>();
        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("essential", true);
        Map<String, Object> dateMap = new HashMap<>();
        dateMap.put("essential", true);
        userinfoClaims.put("name", Arrays.asList(nameMap));
        userinfoClaims.put("birthdate", Arrays.asList(dateMap));
        userinfoClaims.put("address", Arrays.asList(new HashMap<>()));
        userinfoClaims.put("gender", null);
        resolvedClaims.setUserinfo(userinfoClaims);
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setResolvedClaims(resolvedClaims);
        try {
            claimsHelperService.validateAcceptedClaims(oidcTransaction, Arrays.asList("email", "phone_number"));
            Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals(INVALID_ACCEPTED_CLAIM, e.getErrorCode());
        }
    }

    @Test
    public void resolveRequestedClaims_withoutOpenidScope_thenFail() {
        OAuthDetailRequest oAuthDetailRequest = new OAuthDetailRequest();

        ClaimsV2 claimsV2 = new ClaimsV2();
        Map<String, JsonNode> userinfo = new HashMap<>();
        claimsV2.setUserinfo(userinfo);

        oAuthDetailRequest.setScope("profile");
        oAuthDetailRequest.setClaims(claimsV2);

        try {
            claimsHelperService.resolveRequestedClaims(oAuthDetailRequest, new ClientDetail());
            Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals(INVALID_SCOPE, e.getErrorCode());
        }
    }

    @Test
    public void resolveRequestedClaims_withValidVerifiedClaims_thenPass() throws JsonProcessingException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setClaims(Arrays.asList("name", "gender"));
        OAuthDetailRequest oAuthDetailRequest = new OAuthDetailRequest();

        ClaimsV2 claimsV2 = new ClaimsV2();
        Map<String, JsonNode> userinfo = new HashMap<>();
        userinfo.put("name", objectMapper.readTree("{\"essential\":false}"));
        userinfo.put("gender", objectMapper.nullNode());
        userinfo.put("verified_claims", objectMapper.readTree("{\"verification\":{\"trust_framework\":null},\"claims\":{\"name\":{\"essential\":true}}}"));
        claimsV2.setUserinfo(userinfo);

        oAuthDetailRequest.setScope("openid profile");
        oAuthDetailRequest.setClaims(claimsV2);
        Claims claims = claimsHelperService.resolveRequestedClaims(oAuthDetailRequest, clientDetail);
        Assertions.assertNotNull(claims);
        Assertions.assertEquals(2, claims.getUserinfo().size());
        Assertions.assertTrue(claims.getUserinfo().containsKey("name"));
        Assertions.assertTrue(claims.getUserinfo().containsKey("gender"));
        Assertions.assertEquals(1, claims.getUserinfo().get("name").size());
        Assertions.assertEquals(0, claims.getUserinfo().get("gender").size());
        Assertions.assertTrue((boolean)claims.getUserinfo().get("name").get(0).get("essential"));
        Assertions.assertNotNull(claims.getUserinfo().get("gender"));
    }

    @Test
    public void resolveRequestedClaims_withValidVerifiedClaimList_thenPass() throws JsonProcessingException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setClaims(Arrays.asList("name", "gender", "email"));
        OAuthDetailRequest oAuthDetailRequest = new OAuthDetailRequest();

        ClaimsV2 claimsV2 = new ClaimsV2();
        Map<String, JsonNode> userinfo = new HashMap<>();
        userinfo.put("name", objectMapper.readTree("{\"essential\":false}"));
        userinfo.put("gender", objectMapper.nullNode());
        userinfo.put("verified_claims", objectMapper.readTree("[{\"verification\":{\"trust_framework\":null},\"claims\":{\"name\":{\"essential\":true}}}," +
                "{\"verification\":{\"trust_framework\":{\"value\":\"GOI\"}},\"claims\":{\"name\":{\"essential\":true},\"gender\":{\"essential\":true}}}]"));
        claimsV2.setUserinfo(userinfo);

        oAuthDetailRequest.setScope("openid profile");
        oAuthDetailRequest.setClaims(claimsV2);
        Claims claims = claimsHelperService.resolveRequestedClaims(oAuthDetailRequest, clientDetail);
        Assertions.assertNotNull(claims);
        Assertions.assertEquals(3, claims.getUserinfo().size());
        Assertions.assertTrue(claims.getUserinfo().containsKey("name"));
        Assertions.assertTrue(claims.getUserinfo().containsKey("gender"));
        Assertions.assertTrue(claims.getUserinfo().containsKey("email"));
        Assertions.assertEquals(2, claims.getUserinfo().get("name").size());
        Assertions.assertEquals(1, claims.getUserinfo().get("gender").size());
        Assertions.assertEquals(0, claims.getUserinfo().get("email").size());
        Assertions.assertTrue((boolean)claims.getUserinfo().get("name").get(0).get("essential"));
        Assertions.assertTrue((boolean)claims.getUserinfo().get("gender").get(0).get("essential"));
    }

    @Test
    public void getClaimStatus_withNullStoredVerificationMetadata_thenPass() throws JsonProcessingException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setClaims(Arrays.asList("name", "gender", "email"));
        OAuthDetailRequest oAuthDetailRequest = new OAuthDetailRequest();

        ClaimsV2 claimsV2 = new ClaimsV2();
        Map<String, JsonNode> userinfo = new HashMap<>();
        userinfo.put("name", objectMapper.readTree("{\"essential\":false}"));
        userinfo.put("gender", objectMapper.nullNode());
        userinfo.put("verified_claims", objectMapper.readTree("[{\"verification\":{\"trust_framework\":null},\"claims\":{\"name\":{\"essential\":true}}}," +
                "{\"verification\":{\"trust_framework\":{\"value\":\"GOI\"}},\"claims\":{\"name\":{\"essential\":true},\"gender\":{\"essential\":true}}}]"));
        claimsV2.setUserinfo(userinfo);

        oAuthDetailRequest.setScope("openid profile");
        oAuthDetailRequest.setClaims(claimsV2);
        Claims claims = claimsHelperService.resolveRequestedClaims(oAuthDetailRequest, clientDetail);

        ClaimStatus nameClaimStatus = claimsHelperService.getClaimStatus("name", claims.getUserinfo().get("name"), null);
        Assertions.assertNotNull(nameClaimStatus);
        Assertions.assertNotNull(nameClaimStatus.getClaim());
        Assertions.assertFalse(nameClaimStatus.isAvailable());
        Assertions.assertFalse(nameClaimStatus.isVerified());

        Map<String, List<JsonNode>> storedVerificationMetadata = new HashMap<>();
        storedVerificationMetadata.put("gender", null);
        storedVerificationMetadata.put("email", Arrays.asList());
        ClaimStatus genderClaimStatus = claimsHelperService.getClaimStatus("gender", claims.getUserinfo().get("gender"), storedVerificationMetadata);
        Assertions.assertNotNull(genderClaimStatus);
        Assertions.assertNotNull(genderClaimStatus.getClaim());
        Assertions.assertTrue(genderClaimStatus.isAvailable());
        Assertions.assertFalse(genderClaimStatus.isVerified());
        ClaimStatus emailClaimStatus = claimsHelperService.getClaimStatus("email", claims.getUserinfo().get("email"), storedVerificationMetadata);
        Assertions.assertNotNull(emailClaimStatus);
        Assertions.assertNotNull(emailClaimStatus.getClaim());
        Assertions.assertTrue(emailClaimStatus.isAvailable());
        Assertions.assertFalse(emailClaimStatus.isVerified());
    }

    @Test
    public void getClaimStatus_withValidClaims_thenPass() throws JsonProcessingException {
        Map<String, List<JsonNode>> storedVerificationMetadata = new HashMap<>();
        ObjectNode verificationDetailA = objectMapper.createObjectNode();
        verificationDetailA.put("trust_framework", "GOI");
        verificationDetailA.put("time",IdentityProviderUtil.getUTCDateTime());
        ObjectNode verificationDetailB = objectMapper.createObjectNode();
        verificationDetailB.put("trust_framework", "GOK");
        verificationDetailB.put("time",IdentityProviderUtil.getUTCDateTime());
        storedVerificationMetadata.put("name", Arrays.asList(verificationDetailA, verificationDetailB));
        storedVerificationMetadata.put("gender", Arrays.asList());

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setClaims(Arrays.asList("name", "gender", "email"));
        OAuthDetailRequest oAuthDetailRequest = new OAuthDetailRequest();

        ClaimsV2 claimsV2 = new ClaimsV2();
        Map<String, JsonNode> userinfo = new HashMap<>();
        userinfo.put("name", objectMapper.readTree("{\"essential\":false}"));
        userinfo.put("gender", objectMapper.nullNode());
        userinfo.put("verified_claims", objectMapper.readTree("[{\"verification\":{\"trust_framework\":null},\"claims\":{\"name\":{\"essential\":true}}}," +
                "{\"verification\":{\"trust_framework\":{\"value\":\"GOI\"}},\"claims\":{\"name\":{\"essential\":true},\"gender\":{\"essential\":true}}}]"));
        claimsV2.setUserinfo(userinfo);

        oAuthDetailRequest.setScope("openid profile");
        oAuthDetailRequest.setClaims(claimsV2);
        Claims claims = claimsHelperService.resolveRequestedClaims(oAuthDetailRequest, clientDetail);

        ClaimStatus nameClaimStatus = claimsHelperService.getClaimStatus("name", claims.getUserinfo().get("name"), storedVerificationMetadata);
        Assertions.assertNotNull(nameClaimStatus);
        Assertions.assertNotNull(nameClaimStatus.getClaim());
        Assertions.assertTrue(nameClaimStatus.isAvailable());
        Assertions.assertTrue(nameClaimStatus.isVerified());

        ClaimStatus genderClaimStatus = claimsHelperService.getClaimStatus("gender", claims.getUserinfo().get("gender"), storedVerificationMetadata);
        Assertions.assertNotNull(genderClaimStatus);
        Assertions.assertNotNull(genderClaimStatus.getClaim());
        Assertions.assertTrue(genderClaimStatus.isAvailable());
        Assertions.assertFalse(genderClaimStatus.isVerified());

        ClaimStatus emailClaimStatus = claimsHelperService.getClaimStatus("email", claims.getUserinfo().get("email"), storedVerificationMetadata);
        Assertions.assertNotNull(emailClaimStatus);
        Assertions.assertNotNull(emailClaimStatus.getClaim());
        Assertions.assertFalse(emailClaimStatus.isAvailable());
        Assertions.assertFalse(emailClaimStatus.isVerified());

        ClaimStatus phoneClaimStatus = claimsHelperService.getClaimStatus("phone_number", claims.getUserinfo().get("phone_number"), storedVerificationMetadata);
        Assertions.assertNotNull(phoneClaimStatus);
        Assertions.assertNotNull(phoneClaimStatus.getClaim());
        Assertions.assertFalse(phoneClaimStatus.isAvailable());
        Assertions.assertFalse(phoneClaimStatus.isVerified());
    }

    @Test
    public void getClaimStatus_withDifferentStoredClaimMetadata_thenPass() throws JsonProcessingException {
        Map<String, List<JsonNode>> storedVerificationMetadata = new HashMap<>();
        ObjectNode verificationDetailA = objectMapper.createObjectNode();
        verificationDetailA.put("trust_framework","GOI");
        verificationDetailA.put("time",IdentityProviderUtil.getUTCDateTime());
        ObjectNode verificationDetailB = objectMapper.createObjectNode();
        verificationDetailB.put("trust_framework","GOK");
        verificationDetailB.put("time",IdentityProviderUtil.getUTCDateTime());
        storedVerificationMetadata.put("name", Arrays.asList(verificationDetailA, verificationDetailB));
        storedVerificationMetadata.put("gender", Arrays.asList());

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setClaims(Arrays.asList("name", "gender", "email"));
        OAuthDetailRequest oAuthDetailRequest = new OAuthDetailRequest();

        ClaimsV2 claimsV2 = new ClaimsV2();
        Map<String, JsonNode> userinfo = new HashMap<>();
        userinfo.put("name", objectMapper.readTree("{\"essential\":false}"));
        userinfo.put("gender", objectMapper.nullNode());
        userinfo.put("verified_claims", objectMapper.readTree("[{\"verification\":{\"trust_framework\":null},\"claims\":{\"email\":{\"essential\":true}}}," +
                "{\"verification\":{\"trust_framework\":{\"value\":\"GOM\"}},\"claims\":{\"name\":{\"essential\":true},\"gender\":{\"essential\":true}}}]"));
        claimsV2.setUserinfo(userinfo);

        oAuthDetailRequest.setScope("openid profile");
        oAuthDetailRequest.setClaims(claimsV2);
        Claims claims = claimsHelperService.resolveRequestedClaims(oAuthDetailRequest, clientDetail);

        ClaimStatus nameClaimStatus = claimsHelperService.getClaimStatus("name", claims.getUserinfo().get("name"), storedVerificationMetadata);
        Assertions.assertNotNull(nameClaimStatus);
        Assertions.assertNotNull(nameClaimStatus.getClaim());
        Assertions.assertTrue(nameClaimStatus.isAvailable());
        Assertions.assertFalse(nameClaimStatus.isVerified());
    }

    @Test
    public void isVerifiedClaimRequested_withVerificationMetadata_thenTrue() throws JsonProcessingException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setClaims(Arrays.asList("name", "gender", "email"));
        OAuthDetailRequest oAuthDetailRequest = new OAuthDetailRequest();

        ClaimsV2 claimsV2 = new ClaimsV2();
        Map<String, JsonNode> userinfo = new HashMap<>();
        userinfo.put("name", objectMapper.readTree("{\"essential\":false}"));
        userinfo.put("gender", objectMapper.nullNode());
        userinfo.put("verified_claims", objectMapper.readTree("[{\"verification\":{\"trust_framework\":null},\"claims\":{\"email\":{\"essential\":true}}}," +
                "{\"verification\":{\"trust_framework\":{\"value\":\"GOM\"}},\"claims\":{\"name\":{\"essential\":true},\"gender\":{\"essential\":true}}}]"));
        claimsV2.setUserinfo(userinfo);

        oAuthDetailRequest.setScope("openid profile");
        oAuthDetailRequest.setClaims(claimsV2);
        Claims claims = claimsHelperService.resolveRequestedClaims(oAuthDetailRequest, clientDetail);

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setResolvedClaims(claims);
        Assertions.assertTrue(claimsHelperService.isVerifiedClaimRequested(oidcTransaction));
    }

    @Test
    public void isVerifiedClaimRequested_withoutVerificationMetadata_thenFalse() throws JsonProcessingException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setClaims(Arrays.asList("name", "gender", "email"));
        OAuthDetailRequest oAuthDetailRequest = new OAuthDetailRequest();

        ClaimsV2 claimsV2 = new ClaimsV2();
        Map<String, JsonNode> userinfo = new HashMap<>();
        userinfo.put("name", objectMapper.readTree("{\"essential\":false}"));
        userinfo.put("gender", objectMapper.nullNode());
        claimsV2.setUserinfo(userinfo);

        oAuthDetailRequest.setScope("openid profile");
        oAuthDetailRequest.setClaims(claimsV2);
        Claims claims = claimsHelperService.resolveRequestedClaims(oAuthDetailRequest, clientDetail);

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setResolvedClaims(claims);
        Assertions.assertFalse(claimsHelperService.isVerifiedClaimRequested(oidcTransaction));
    }

    @Test
    public void resolveRequestedClaims_withInvalidVerifiedClaims_thenFail() throws JsonProcessingException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setClaims(Arrays.asList("name", "gender", "email"));
        OAuthDetailRequest oAuthDetailRequest = new OAuthDetailRequest();

        ClaimsV2 claimsV2 = new ClaimsV2();
        Map<String, JsonNode> userinfo = new HashMap<>();
        userinfo.put("name", objectMapper.readTree("{\"essential\":false}"));
        userinfo.put("gender", objectMapper.nullNode());
        userinfo.put("verified_claims", objectMapper.readTree("{\"verification\":{},\"claims\":{\"name\":null}}"));
        claimsV2.setUserinfo(userinfo);

        oAuthDetailRequest.setScope("openid profile");
        oAuthDetailRequest.setClaims(claimsV2);
        try {
            claimsHelperService.resolveRequestedClaims(oAuthDetailRequest, clientDetail);
            Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals(INVALID_VERIFICATION, e.getErrorCode());
        }
    }
}
