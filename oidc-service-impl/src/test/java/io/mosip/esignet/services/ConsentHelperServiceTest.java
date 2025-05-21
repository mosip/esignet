/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.Base64URL;
import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.dto.PublicKeyRegistry;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.spi.ConsentService;
import io.mosip.esignet.core.spi.PublicKeyRegistryService;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.jose4j.keys.X509Util;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Arrays;
import java.util.Collections;


@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class ConsentHelperServiceTest {

    @Mock
    ConsentService consentService;

    @Mock
    PublicKeyRegistryService publicKeyRegistryService;

    @Mock
    AuthorizationHelperService authorizationHelperService;

    @Mock
    AuditPlugin auditPlugin;

    @InjectMocks
    ConsentHelperService consentHelperService;

    private static final String certificateString;
    private static final String thumbprint;

    private static final Certificate certificate;
    private static final PrivateKey privateKey;
    private static final String jwksString;

    static {
        try {
            jwksString="{ \"keys\": [ { \"p\": \"-lDLpWmbDNnAb6QEvY_1-WQLnAzJqjgAnCDIitaTSchJZWU6OHuGbLnwRGx-u86sPqk7V9KyudNxDTX9FCVNye2i5Rv4Ky0F29qiXT-xKNHa64xvFQ9imhFqZUL1-wQfJryVe_tR5Cxf45onFsT-BqeXLJqrgIpeCsHY1WOcxq0\", \"kty\": \"RSA\", \"q\": \"25GP6Hw-Xjj_A9M-dvKFTMPEI4rKUjznEAiro2TqSWM890XQiTuL92GCmTnDhG1RalTyQrED2pC0zwlhLnjuxPJTFjbxIoFfzWgf2o7sujmezDjahflB_1S2UmF2rc1HA0veCyb8d7rEcfX_D-gK8j2_7zcHKFUAY7amWQpFnQc\", \"d\": \"dj5vOzlQKJNQ_CKlhvbexsGA-GSyGFKkEJE9ZvwMFb1RkWq3PeImssKgigQgwUEcsAMDGBMDJBkjJXQ0w-DJB_nnnRqmtJpRESz-m118sHxgbOT1KMbd9mMWm5ElMm-gywD-rI7gCWStIbM9-X9K9HVpVRL9ZnR7Vq6mmOD3oCDDGNMXAEbPqhyeffCAzz4sCGm68W79xVf6N5zGNgpLwBx1U-ytoL49ljfO9CjMYHHLd9WIuFjRvebudAtmPL5dDuT6w3X81l8Uk0dvCYq9q0k6qbM9CgQuEVbEZYYB0clehcXDuMfotcq8dZTSh-x55Hr7WP3jP8Wmbq7Hb_9D-Q\", \"e\": \"AQAB\", \"use\": \"sig\", \"qi\": \"7UN1qEDk2-9vNglyoG9s3Y7HTXyJRQTd9gSF_xnlX9V2xwHFyzrBvQXEpmgcqUmd_d1Nwii0F6FIPDO2imewf05EDAzgQ0z7JfW5A28Cu7dgrTafw9wNwnhxX6XvQJN9d1mB1CGXvu6hjfmJf2d3w8Zx3S9ftHJfiJdYiovz2a4\", \"dp\": \"3Wb_aVSKVwh3RIfvxXeDGk5aUnGhIhUcvPvmRZ9bcaRc_vlVUMY5dQvVr7_DLPy1r0rieWCcwu55jTTWn64LKvMs5Lcjf5T4HKk6eX4vhapwl22Ehz0vepSy5dQfXCIeJ5YgJiR6H3b8bYVY07Pz-BDeDw7TaQN6AMvKrZvI_JU\", \"alg\": \"RS256\", \"dq\": \"fE2F-QoNjO4bgSDgZhqaWIEj0zNJoxEThsJB2TjSYkWqSBrgGjD55kzv0KshAyGYS-hXVmY8VkpB8kvPFq4kDp9ZZmQoU26GvBTMo8DyR-NDAT7Wh647LL_aj_zZYT-rijQzOoERwP6dJB8uDOVC_Sz0MgsnJDArDkhjGFl2W5U\", \"n\": \"1rFysE5-hwy2qvd3KBT6OzHzJdmIyLhR5bmO4L1qxkVQ78Danse9etQSY1c_v8jVvpA9IUVsAPdvIore4t5L2foqydn4H0VBQOcnX1f-6FcZ3_6nH5GFGQIVyuBPO7d7XM1vn8DEt3FY5-VGB0kpSHcxfLGVL0F7jPm31rDYaTROevkeIaMIib0tvMZpoxP7e9yfpix7L5p3-vLtcvpZk1hFiROa1m0nnpNg6k7-HunLMJV0UGOtYgDwmj_Fow56N26AGLzdVxpz-mjBu5RFZwCJba56mE7d77nndEUbiweYrOXIY04mUye55sU7-svOXprq925ckfRlAwUWLcKHuw\" } ] }";
            certificateString="-----BEGIN CERTIFICATE-----\n" +
                    "MIIC6jCCAdKgAwIBAgIGAYlTAszKMA0GCSqGSIb3DQEBCwUAMDYxNDAyBgNVBAMM\n" +
                    "K1c0bVhHakV4VU9YUl9Pc0dqUzRDUzR0N1VlRkZReE5yZXo1d05DRE1FY0UwHhcN\n" +
                    "MjMwNzE0MDYwNzE5WhcNMjQwNTA5MDYwNzE5WjA2MTQwMgYDVQQDDCtXNG1YR2pF\n" +
                    "eFVPWFJfT3NHalM0Q1M0dDdVZUZGUXhOcmV6NXdOQ0RNRWNFMIIBIjANBgkqhkiG\n" +
                    "9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1rFysE5+hwy2qvd3KBT6OzHzJdmIyLhR5bmO\n" +
                    "4L1qxkVQ78Danse9etQSY1c/v8jVvpA9IUVsAPdvIore4t5L2foqydn4H0VBQOcn\n" +
                    "X1f+6FcZ3/6nH5GFGQIVyuBPO7d7XM1vn8DEt3FY5+VGB0kpSHcxfLGVL0F7jPm3\n" +
                    "1rDYaTROevkeIaMIib0tvMZpoxP7e9yfpix7L5p3+vLtcvpZk1hFiROa1m0nnpNg\n" +
                    "6k7+HunLMJV0UGOtYgDwmj/Fow56N26AGLzdVxpz+mjBu5RFZwCJba56mE7d77nn\n" +
                    "dEUbiweYrOXIY04mUye55sU7+svOXprq925ckfRlAwUWLcKHuwIDAQABMA0GCSqG\n" +
                    "SIb3DQEBCwUAA4IBAQDEncAcwSkKRFClDkYw354hxbzc2zvtT9Foc4JNrKJhg+rI\n" +
                    "toHtrc/GLd6dPokrgIcfJs9ogTL9gGrO70X8CXBDPgFzKueeZOEFZSGd9wzVYHeZ\n" +
                    "aCQpv08+Xm9HGYJKN/V0SDU5n2K6qjIGc9vUa5wLYyngkK4akcOLDcs/JYUWmMFs\n" +
                    "u758AsRhM5nen6DTqiYf2VEoVP5QTtG5LKP8xirlfdTwtzPK2UFBut5JLsExDIoT\n" +
                    "e1dYNxoRM1wKr5rDMvuzu0+X2y5FRcT4qnxxi/BQfQ9pRbZGxJsNawsZf+IorpR8\n" +
                    "RjPbLKozzWxtGPTbpYWX5gqptSa1YCRuKHNm2g3N\n" +
                    "-----END CERTIFICATE-----";

            thumbprint=generateThumbprintByCertificate(certificateString);
            JWKSet jwkSet = JWKSet.parse(jwksString);
            // Find the private key JWK for signing
            JWK jwk = jwkSet.getKeys().get(0);
            privateKey = (RSAPrivateKey) jwk.toRSAKey().toPrivateKey();
            certificate=IdentityProviderUtil.convertToCertificate(certificateString);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setup()  {
        ReflectionTestUtils.setField(consentHelperService,"objectMapper", new ObjectMapper());
    }

    @Test
    public void addUserConsent_withValidLinkedTransaction_thenPass() throws Exception {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("123");
        oidcTransaction.setAcceptedClaims(List.of("name","email"));
        oidcTransaction.setPermittedScopes(null);
        oidcTransaction.setIndividualId("individualId");
        oidcTransaction.setConsentAction(ConsentAction.CAPTURE);

        Claims claims = new Claims();
        Map<String, List<Map<String, Object>>> userinfo = new HashMap<>();
        Map<String, Map<String, Object>> id_token = new HashMap<>();

        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("essential", true);
        nameMap.put("value", "name");
        nameMap.put("values", new String[]{"value1a", "value1b"});
        Map<String, Object> idTokenMap = new HashMap<>();
        idTokenMap.put("essential", false);
        idTokenMap.put("value", "token");
        idTokenMap.put("values", new String[]{"value2a", "value2b"});
        userinfo.put("name", Arrays.asList(nameMap));
        id_token.put("idTokenKey", idTokenMap);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setAcceptedClaims(Arrays.asList("name","email","gender"));
        oidcTransaction.setPermittedScopes(Arrays.asList("openid","profile","email"));
        oidcTransaction.setRequestedAuthorizeScopes(Arrays.asList("openid","profile","email"));
        oidcTransaction.setResolvedClaims(claims);

        List<String> acceptedClaims =oidcTransaction.getAcceptedClaims();
        List<String> permittedScopes =oidcTransaction.getPermittedScopes();
        Collections.sort(acceptedClaims);
        Collections.sort(permittedScopes);
        Map<String,Object> payLoadMap = new TreeMap<>();
        payLoadMap.put("accepted_claims",acceptedClaims);
        payLoadMap.put("permitted_authorized_scopes",permittedScopes);
        String signature = generateSignature(payLoadMap);
        consentHelperService.updateUserConsent(oidcTransaction, signature);
        UserConsent userConsent = new UserConsent();
        userConsent.setAuthorizationScopes(Map.of("openid",false,"profile",false,"email",false));
        userConsent.setHash("S91aCTTZ1NpJxXSvWf-wpW2xBjykRF77W3p0eHjmN1k");
        userConsent.setClaims(claims);
        userConsent.setAcceptedClaims(List.of("email","gender","name"));

        userConsent.setPermittedScopes(List.of("email","openid","profile"));
        userConsent.setSignature(signature);

        Mockito.verify(consentService).saveUserConsent(userConsent);

        PublicKeyRegistry publicKeyRegistry =new PublicKeyRegistry();
        publicKeyRegistry.setCertificate(certificateString);


    }

    @Test
    public void addUserConsent_withValidWebTransaction_thenPass()
    {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("123");
        oidcTransaction.setAcceptedClaims(List.of("name","email"));
        oidcTransaction.setPermittedScopes(null);
        oidcTransaction.setConsentAction(ConsentAction.CAPTURE);
        oidcTransaction.setConsentExpireMinutes(10);

        Claims claims = new Claims();
        Map<String, List<Map<String, Object>>> userinfo = new HashMap<>();
        Map<String, Map<String, Object>> id_token = new HashMap<>();
        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("essential", true);
        nameMap.put("value", "value1");
        nameMap.put("values", new String[]{"value1a", "value1b"});
        Map<String, Object> idTokenMap = new HashMap<>();
        idTokenMap.put("essential", false);
        idTokenMap.put("value", "value2");
        idTokenMap.put("values", new String[]{"value2a", "value2b"});
        userinfo.put("userinfoKey", Arrays.asList(nameMap));
        id_token.put("idTokenKey", idTokenMap);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setResolvedClaims(claims);

        Mockito.when(consentService.saveUserConsent(Mockito.any())).thenReturn(new ConsentDetail());

        consentHelperService.updateUserConsent(oidcTransaction, "");
        UserConsent userConsent = new UserConsent();
        userConsent.setHash("N54bQaclZC0w91p-z6CY6hynD7GIOypYGVgTthMQxyo");
        userConsent.setClaims(claims);
        userConsent.setAuthorizationScopes(Map.of());
        userConsent.setAcceptedClaims(List.of("name","email"));
        userConsent.setSignature("");
        userConsent.setExpiredtimes(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10).truncatedTo(ChronoUnit.SECONDS));
        Mockito.verify(consentService).saveUserConsent(userConsent);
    }

    @Test
    public void addUserConsent_withValidWebTransactionNoClaimsAndScopes_thenPass()
    {
        String clientId  = "clientId";
        String psuToken = "psuToken";
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("123");
        oidcTransaction.setAcceptedClaims(List.of());
        oidcTransaction.setRequestedAuthorizeScopes(List.of());
        oidcTransaction.setConsentAction(ConsentAction.NOCAPTURE);
        oidcTransaction.setVoluntaryClaims(List.of());
        oidcTransaction.setEssentialClaims(List.of());
        oidcTransaction.setAcceptedClaims(List.of());
        oidcTransaction.setPermittedScopes(List.of());
        oidcTransaction.setClientId(clientId);
        oidcTransaction.setPartnerSpecificUserToken(psuToken);
        consentHelperService.updateUserConsent(oidcTransaction, "");
        Mockito.verify(consentService).deleteUserConsent(clientId, psuToken);
    }

    @Test
    public void processConsent_withWebFlowAndValidConsentAndGetConsentActionAsNoCapture_thenPass() throws Exception {

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));
        oidcTransaction.setPermittedScopes(List.of("openid","profile"));
        oidcTransaction.setEssentialClaims(List.of("name"));
        oidcTransaction.setVoluntaryClaims(List.of("email"));
        oidcTransaction.setIndividualId("individualId");

        Claims claims = new Claims();
        Map<String, List<Map<String, Object>>> userinfo = new HashMap<>();
        Map<String, Map<String, Object>> id_token = new HashMap<>();
        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("essential", true);
        nameMap.put("value", "name");
        nameMap.put("values", new String[]{"value1a", "value1b"});
        Map<String, Object> idTokenMap = new HashMap<>();
        idTokenMap.put("essential", false);
        idTokenMap.put("value", "token");
        idTokenMap.put("values", new String[]{"value2a", "value2b"});
        userinfo.put("name", Arrays.asList(nameMap));
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenMap);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setResolvedClaims(claims);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setClientId("123");
        consentDetail.setSignature("signature");
        consentDetail.setAuthorizationScopes(Map.of("openid",false,"profile",false));
        Claims consentedClaims = new Claims();
        consentedClaims.setUserinfo(claims.getUserinfo());
        consentedClaims.setId_token(claims.getId_token());
        consentDetail.setClaims(consentedClaims);
        Map<String, Object> normalizedClaims = new HashMap<>();
        normalizedClaims.put("userinfo", consentHelperService.normalizeUserInfoClaims(claims.getUserinfo()));
        normalizedClaims.put("id_token", consentHelperService.normalizeIdTokenClaims(claims.getId_token()));
        String hashCode =consentHelperService.hashUserConsent(normalizedClaims,consentDetail.getAuthorizationScopes());
        consentDetail.setHash(hashCode);

        consentDetail.setAcceptedClaims(Arrays.asList("email","gender","name"));
        consentDetail.setPermittedScopes(Arrays.asList("email","openid","profile"));

        List<String> acceptedClaims = consentDetail.getAcceptedClaims();
        List<String> permittedScopes = consentDetail.getPermittedScopes();
        Collections.sort(acceptedClaims);
        Collections.sort(permittedScopes);
        Map<String,Object> payLoadMap = new TreeMap<>();
        payLoadMap.put("accepted_claims",acceptedClaims);
        payLoadMap.put("permitted_authorized_scopes",permittedScopes);

        String signature = generateSignature(payLoadMap);
        consentDetail.setSignature(signature);
        consentDetail.setPsuToken("psutoken");

        PublicKeyRegistry publicKeyRegistry =new PublicKeyRegistry();
        publicKeyRegistry.setCertificate(certificateString);
        Mockito.when(authorizationHelperService.getIndividualId(oidcTransaction)).thenReturn("individualId");
        Mockito.when(publicKeyRegistryService.findFirstByIdHashAndThumbprintAndExpiredtimes(Mockito.any(),Mockito.any())).thenReturn(Optional.of(publicKeyRegistry));

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));

        consentHelperService.processConsent(oidcTransaction,true);

        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.NOCAPTURE);
        Assert.assertEquals(oidcTransaction.getAcceptedClaims(),consentDetail.getAcceptedClaims());
        Assert.assertEquals(oidcTransaction.getPermittedScopes(),consentDetail.getPermittedScopes());
    }

    @Test
    public void processConsent_withLinkedFlowAndValidConsentAndGetConsentActionAsCapture_thenPass() throws Exception {

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));
        oidcTransaction.setIndividualId("individualId");
        oidcTransaction.setPermittedScopes(List.of("openid","profile"));
        oidcTransaction.setEssentialClaims(List.of("name"));
        oidcTransaction.setVoluntaryClaims(List.of("email"));
        Claims claims = new Claims();
        Map<String, List<Map<String, Object>>> userinfo = new HashMap<>();
        Map<String, Map<String, Object>> id_token = new HashMap<>();
        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("essential", true);
        nameMap.put("value", "name");
        nameMap.put("values", new String[]{"value1a", "value1b"});
        Map<String, Object> idTokenMap = new HashMap<>();
        idTokenMap.put("essential", false);
        idTokenMap.put("value", "token");
        idTokenMap.put("values", new String[]{"value2a", "value2b"});
        userinfo.put("name", Arrays.asList(nameMap));
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenMap);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setResolvedClaims(claims);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setClientId("123");
        consentDetail.setSignature("signature");
        consentDetail.setAuthorizationScopes(Map.of("openid",true,"profile",true));

        Map<String, Object> consentClaims = new HashMap<>();
        userinfo = new HashMap<>();
        id_token = new HashMap<>();
        Map<String, Object> genderMap = new HashMap<>();
        nameMap.put("essential", false);
        nameMap.put("value", "gender");
        nameMap.put("values", new String[]{"value1a", "value1b"});
        Map<String, Object> consentedIdTokenMap = new HashMap<>();
        idTokenMap.put("essential", false);
        idTokenMap.put("value", "token");
        idTokenMap.put("values", new String[]{"value2a", "value2b"});
        userinfo.put("gender", Arrays.asList(genderMap));
        userinfo.put("email",null);
        id_token.put("idTokenKey", consentedIdTokenMap);
        consentClaims.put("userinfo", userinfo);
        consentClaims.put("id_token", id_token);


        consentDetail.setClaims(new Claims());
        consentDetail.getClaims().setUserinfo(userinfo);
        consentDetail.getClaims().setId_token(id_token);
        String hashCode = consentHelperService.hashUserConsent(consentClaims,consentDetail.getAuthorizationScopes());
        consentDetail.setHash(hashCode);

        consentDetail.setAcceptedClaims(Arrays.asList("email","gender","name"));
        consentDetail.setPermittedScopes(Arrays.asList("email","openid","profile"));

        List<String> acceptedClaims = consentDetail.getAcceptedClaims();
        List<String> permittedScopes = consentDetail.getPermittedScopes();
        Collections.sort(acceptedClaims);
        Collections.sort(permittedScopes);
        Map<String,Object> payLoadMap = new TreeMap<>();
        payLoadMap.put("accepted_claims",acceptedClaims);
        payLoadMap.put("permitted_authorized_scopes",permittedScopes);

        String signature = generateSignature(payLoadMap);
        consentDetail.setSignature(signature);
        consentDetail.setPsuToken("psutoken");

        PublicKeyRegistry publicKeyRegistry =new PublicKeyRegistry();
        publicKeyRegistry.setCertificate(certificateString);
        Mockito.when(authorizationHelperService.getIndividualId(oidcTransaction)).thenReturn("individualId");
        Mockito.when(publicKeyRegistryService.findFirstByIdHashAndThumbprintAndExpiredtimes(Mockito.any(),Mockito.any())).thenReturn(Optional.of(publicKeyRegistry));

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));
        consentHelperService.processConsent(oidcTransaction,true);

        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.CAPTURE);

    }

    @Test
    public void processConsent_withEmptyConsent_thenPass(){

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setVoluntaryClaims(List.of("email"));
        oidcTransaction.setEssentialClaims(List.of());
        oidcTransaction.setRequestedAuthorizeScopes(List.of());
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.empty());

        consentHelperService.processConsent(oidcTransaction,true);
        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.CAPTURE);
    }

    @Test
    public void processConsent_withConsentPrompt_thenPass(){

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setVoluntaryClaims(List.of("email"));
        oidcTransaction.setEssentialClaims(List.of());
        oidcTransaction.setRequestedAuthorizeScopes(List.of());
        oidcTransaction.setPrompt(new String[] {"consent"});
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.empty());

        consentHelperService.processConsent(oidcTransaction,true);
        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.CAPTURE);
    }

    @Test
    public void processConsent_withValidConsentAndConsentPrompt_thenPass(){

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setVoluntaryClaims(List.of("email"));
        oidcTransaction.setEssentialClaims(List.of());
        oidcTransaction.setRequestedAuthorizeScopes(List.of());
        oidcTransaction.setPrompt(new String[] {"consent"});
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail = new ConsentDetail();
        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));

        consentHelperService.processConsent(oidcTransaction,false);
        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.CAPTURE);
    }

    @Test
    public void processConsent_withInvalidIdHashOrThumbPrint_thenPass() throws Exception {

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));
        oidcTransaction.setPermittedScopes(List.of("openid","profile"));
        oidcTransaction.setEssentialClaims(List.of("name"));
        oidcTransaction.setVoluntaryClaims(List.of("email"));
        oidcTransaction.setIndividualId("individualId");

        Claims claims = new Claims();
        Map<String, List<Map<String, Object>>> userinfo = new HashMap<>();
        Map<String, Map<String, Object>> id_token = new HashMap<>();
        Map<String, Object> nameMap = new HashMap<>();
        nameMap.put("essential", true);
        nameMap.put("value", "name");
        nameMap.put("values", new String[]{"value1a", "value1b"});
        Map<String, Object> idTokenMap = new HashMap<>();
        idTokenMap.put("essential", false);
        idTokenMap.put("value", "token");
        idTokenMap.put("values", new String[]{"value2a", "value2b"});
        userinfo.put("name", Arrays.asList(nameMap));
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenMap);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setResolvedClaims(claims);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setClientId("123");
        consentDetail.setSignature("signature");
        consentDetail.setAuthorizationScopes(Map.of("openid",false,"profile",false));
        Map<String, Object> consentedClaims = new HashMap<>();
        consentedClaims.put("userinfo", claims.getUserinfo());
        consentedClaims.put("id_token", claims.getId_token());
        consentDetail.setClaims(claims);
        Map<String, Object>  normalizedClaims = new HashMap<>();
        normalizedClaims.put("userinfo", consentHelperService.normalizeUserInfoClaims(claims.getUserinfo()));
        normalizedClaims.put("id_token", consentHelperService.normalizeIdTokenClaims(claims.getId_token()));
        String hashCode = consentHelperService.hashUserConsent(normalizedClaims,consentDetail.getAuthorizationScopes());
        consentDetail.setHash(hashCode);

        consentDetail.setAcceptedClaims(Arrays.asList("email","gender","name"));
        consentDetail.setPermittedScopes(Arrays.asList("email","openid","profile"));

        List<String> acceptedClaims = consentDetail.getAcceptedClaims();
        List<String> permittedScopes = consentDetail.getPermittedScopes();
        Collections.sort(acceptedClaims);
        Collections.sort(permittedScopes);
        Map<String,Object> payLoadMap = new TreeMap<>();
        payLoadMap.put("accepted_claims",acceptedClaims);
        payLoadMap.put("permitted_authorized_scopes",permittedScopes);

        String signature = generateSignature(payLoadMap);
        consentDetail.setSignature(signature);
        consentDetail.setPsuToken("psutoken");

        PublicKeyRegistry publicKeyRegistry =new PublicKeyRegistry();
        publicKeyRegistry.setCertificate(certificateString);
        Mockito.when(authorizationHelperService.getIndividualId(oidcTransaction)).thenReturn("individualId");
        Mockito.when(publicKeyRegistryService.findFirstByIdHashAndThumbprintAndExpiredtimes(Mockito.any(),Mockito.any())).thenReturn(Optional.empty());

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));

        consentHelperService.processConsent(oidcTransaction,true);

        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.CAPTURE);
    }

    @Test
    public void processConsent_withInvalidSignature_thenFail(){

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setVoluntaryClaims(List.of("email"));
        oidcTransaction.setEssentialClaims(List.of());
        oidcTransaction.setRequestedAuthorizeScopes(List.of());
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail=new ConsentDetail();
        consentDetail.setAcceptedClaims(Arrays.asList());
        consentDetail.setSignature("haa.naa");

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));
        try{
            consentHelperService.processConsent(oidcTransaction,true);
            Assert.fail();
        }catch (Exception e){
            Assert.assertEquals(e.getMessage(),ErrorConstants.INVALID_AUTH_TOKEN);
        }
    }

    @Test
    public void processConsent_withEmptyRequestedClaims_thenPass(){
        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setVoluntaryClaims(List.of());
        oidcTransaction.setEssentialClaims(List.of());
        oidcTransaction.setRequestedAuthorizeScopes(List.of());
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());
        consentHelperService.processConsent(oidcTransaction,true);
        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.NOCAPTURE);
    }

    @Test
    public void processConsent_withExpiredConsentAndGetConsentActionAsCapture_thenPass() {
        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));
        oidcTransaction.setPermittedScopes(List.of("openid","profile"));
        oidcTransaction.setEssentialClaims(List.of("name"));
        oidcTransaction.setVoluntaryClaims(List.of("email"));
        oidcTransaction.setIndividualId("individualId");

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setClientId("123");
        consentDetail.setSignature("signature");
        consentDetail.setAuthorizationScopes(Map.of("openid",false,"profile",false));
        consentDetail.setAcceptedClaims(Arrays.asList("email","gender","name"));
        consentDetail.setPermittedScopes(Arrays.asList("email","openid","profile"));
        consentDetail.setExpiredtimes(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2));
        consentDetail.setPsuToken("psutoken");

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));
        consentHelperService.processConsent(oidcTransaction,false);

        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.CAPTURE);
    }


    private String generateSignature(Map<String,Object> payloadMap) throws Exception {

        // Define the header and payload
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .x509CertSHA256Thumbprint(new Base64URL(thumbprint))
                .build();
        JSONObject payloadJson = new JSONObject(payloadMap);
        Payload payload = new Payload(payloadJson.toJSONString());

        // Generate the JWT with a private key
        JWSSigner signer = new RSASSASigner(privateKey);
        JWSObject jwsObject = new JWSObject(header,payload);
        jwsObject.sign(signer);
        String token = jwsObject.serialize();
        String parts[]=token.split("\\.");
        return parts[0]+"."+parts[2];
    }

    public static String generateThumbprintByCertificate(String cerifacate)
    {
        X509Certificate certificate = (X509Certificate) IdentityProviderUtil.convertToCertificate(cerifacate);// convertToCertificate(cerifacate);
        return X509Util.x5tS256(certificate);
    }
}