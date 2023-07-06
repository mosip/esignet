/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64URL;
import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.spi.ConsentService;
import net.minidev.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;


@RunWith(MockitoJUnitRunner.class)
public class ConsentHelperServiceTest {


    @Mock
    ConsentService consentService;

    @Mock
    KeyBindingHelperService keyBindingHelperService;

    @InjectMocks
    ConsentHelperService consentHelperService;


    public static final String X5T_S256 = "x5t#S256";

    private  static  final  KeyPairGenerator keyPairGenerator;
    private  static  final  KeyPair keyPair;
    private  static  final  RSAPrivateKey privateKey;
    private  static  final  RSAPublicKey publicKey;

    static {
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPair = keyPairGenerator.generateKeyPair();
            publicKey = (RSAPublicKey) keyPair.getPublic();
            privateKey = (RSAPrivateKey) keyPair.getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }


    @Test
    public void addUserConsent_withValidLinkedTransaction_thenPass() throws NoSuchAlgorithmException, JOSEException {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("123");
        oidcTransaction.setAcceptedClaims(List.of("name","email"));
        oidcTransaction.setPermittedScopes(null);
        oidcTransaction.setConsentAction(ConsentAction.CAPTURE);

        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoNameClaimDetail = new ClaimDetail("name", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("token", new String[]{"value2a", "value2b"}, false);
        userinfo.put("name", userinfoNameClaimDetail);
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setAcceptedClaims(Arrays.asList("name","email","gender"));
        oidcTransaction.setPermittedScopes(Arrays.asList("openid","profile","email"));
        oidcTransaction.setRequestedAuthorizeScopes(Arrays.asList("openid","profile","email"));



        List<String> acceptedClaims =oidcTransaction.getAcceptedClaims();
        List<String> permittedScopes = oidcTransaction.getPermittedScopes();
        Collections.sort(acceptedClaims);
        Collections.sort(permittedScopes);
        Map<String,Object> payLoadMap = new HashMap<>();
        payLoadMap.put("accepted_claims",acceptedClaims);
        payLoadMap.put("permitted_scopes",permittedScopes);
        String signature = generateSignature(payLoadMap);


        byte[] publicKeyBytes = publicKey.getEncoded();
        String publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes);

        Mockito.when(keyBindingHelperService.getPublicKey(Mockito.any(),Mockito.any())).thenReturn(publicKeyString);

        oidcTransaction.setRequestedClaims(claims);
        consentHelperService.addUserConsent(oidcTransaction, true, signature);

    }

    @Test
    public void addUserConsent_withValidWebTransaction_thenPass()
    {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("123");
        oidcTransaction.setAcceptedClaims(List.of("name","email"));
        oidcTransaction.setPermittedScopes(null);
        oidcTransaction.setConsentAction(ConsentAction.CAPTURE);

        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoClaimDetail = new ClaimDetail("value1", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("value2", new String[]{"value2a", "value2b"}, false);
        userinfo.put("userinfoKey", userinfoClaimDetail);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setRequestedClaims(claims);

        Mockito.when(consentService.saveUserConsent(Mockito.any())).thenReturn(new ConsentDetail());

        consentHelperService.addUserConsent(oidcTransaction, false, "");
    }

    @Test
    public void processConsent_withWebFlowAndValidConsentAndGetConsentActionAsNoCapture_thenPass() throws JsonProcessingException {

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));
        oidcTransaction.setPermittedScopes(List.of("openid","profile"));

        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoNameClaimDetail = new ClaimDetail("name", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("token", new String[]{"value2a", "value2b"}, false);
        userinfo.put("name", userinfoNameClaimDetail);
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setRequestedClaims(claims);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setClientId("123");
        consentDetail.setSignature("signature");
        consentDetail.setAuthorizationScopes(Map.of("openid",true,"profile",true));
        consentDetail.setClaims(claims);
        Claims normalizedClaims = new Claims();
        normalizedClaims.setUserinfo(consentHelperService.normalizeClaims(claims.getUserinfo()));
        normalizedClaims.setId_token(consentHelperService.normalizeClaims(claims.getId_token()));
        String hashCode =consentHelperService.hashUserConsent(normalizedClaims,consentDetail.getAuthorizationScopes());
        consentDetail.setHash(hashCode);


        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));

        consentHelperService.processConsent(oidcTransaction,false);

        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.NOCAPTURE);
        Assert.assertEquals(oidcTransaction.getAcceptedClaims(),consentDetail.getAcceptedClaims());
        Assert.assertEquals(oidcTransaction.getPermittedScopes(),consentDetail.getPermittedScopes());
    }

    @Test
    public void processConsent_withWebFlowAndValidConsentAndGetConsentActionAsCapture_thenPass() throws JsonProcessingException {

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));
        oidcTransaction.setPermittedScopes(List.of("openid","profile"));

        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoNameClaimDetail = new ClaimDetail("name", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("token", new String[]{"value2a", "value2b"}, false);
        userinfo.put("name", userinfoNameClaimDetail);
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setRequestedClaims(claims);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setClientId("123");
        consentDetail.setSignature("signature");
        consentDetail.setAuthorizationScopes(Map.of("openid",true,"profile",true));

        Claims consentClaims = new Claims();
        userinfo = new HashMap<>();
        id_token = new HashMap<>();
        userinfoNameClaimDetail = new ClaimDetail("gender", new String[]{"value1a", "value1b"}, false);
        idTokenClaimDetail = new ClaimDetail("token", new String[]{"value1a", "value2b"}, false);
        userinfo.put("gender", userinfoNameClaimDetail);
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenClaimDetail);
        consentClaims.setUserinfo(userinfo);
        consentClaims.setId_token(id_token);

        consentDetail.setClaims(consentClaims);
        String hashCode =consentHelperService.hashUserConsent(consentClaims,consentDetail.getAuthorizationScopes());
        consentDetail.setHash(hashCode);
        consentDetail.setAcceptedClaims(Arrays.asList("name","email","gender"));
        consentDetail.setPermittedScopes(Arrays.asList("openid","profile","email"));

        List<String> acceptedClaims = consentDetail.getAcceptedClaims();
        List<String> permittedScopes = consentDetail.getPermittedScopes();
        Collections.sort(acceptedClaims);
        Collections.sort(permittedScopes);
        Map<String,Object> payLoadMap = new HashMap<>();
        payLoadMap.put("accepted_claims",acceptedClaims);
        payLoadMap.put("permitted_scopes",permittedScopes);

        consentDetail.setPsuToken("psutoken");

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));
        consentHelperService.processConsent(oidcTransaction,false);

        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.CAPTURE);

    }

    @Test
    public void processConsent_withLinkedFlowAndValidConsentAndGetConsentActionAsNoCapture_thenPass() throws JsonProcessingException, NoSuchAlgorithmException, JOSEException {

        byte[] publicKeyBytes = publicKey.getEncoded();
        String publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes);
        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));
        oidcTransaction.setPermittedScopes(List.of("openid","profile"));

        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoNameClaimDetail = new ClaimDetail("name", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("token", new String[]{"value2a", "value2b"}, false);
        userinfo.put("name", userinfoNameClaimDetail);
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setRequestedClaims(claims);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setClientId("123");
        consentDetail.setSignature("signature");
        consentDetail.setAuthorizationScopes(Map.of("openid",true,"profile",true));
        consentDetail.setClaims(claims);
        Claims normalizedClaims = new Claims();
        normalizedClaims.setUserinfo(consentHelperService.normalizeClaims(claims.getUserinfo()));
        normalizedClaims.setId_token(consentHelperService.normalizeClaims(claims.getId_token()));
        String hashCode =consentHelperService.hashUserConsent(normalizedClaims,consentDetail.getAuthorizationScopes());
        consentDetail.setHash(hashCode);


        consentDetail.setAcceptedClaims(Arrays.asList("name","email","gender"));
        consentDetail.setPermittedScopes(Arrays.asList("openid","profile","email"));

        List<String> acceptedClaims = consentDetail.getAcceptedClaims();
        List<String> permittedScopes = consentDetail.getPermittedScopes();
        Collections.sort(acceptedClaims);
        Collections.sort(permittedScopes);

        Map<String,Object> payLoadMap = new HashMap<>();
        payLoadMap.put("accepted_claims",acceptedClaims);
        payLoadMap.put("permitted_scopes",permittedScopes);
        String signature = generateSignature(payLoadMap);

        consentDetail.setSignature(signature);
        consentDetail.setPsuToken("psutoken");

        Mockito.when(keyBindingHelperService.getPublicKey(Mockito.any(),Mockito.any())).thenReturn(publicKeyString);
        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));

        consentHelperService.processConsent(oidcTransaction,true);

        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.NOCAPTURE);
        Assert.assertEquals(oidcTransaction.getAcceptedClaims(),consentDetail.getAcceptedClaims());
        Assert.assertEquals(oidcTransaction.getPermittedScopes(),consentDetail.getPermittedScopes());
    }

    @Test
    public void processConsent_withLinkedFlowAndValidConsentAndGetConsentActionAsCapture_thenPass() throws JsonProcessingException, NoSuchAlgorithmException, JOSEException {

        byte[] publicKeyBytes = publicKey.getEncoded();
        String publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes);

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));
        oidcTransaction.setPermittedScopes(List.of("openid","profile"));

        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoNameClaimDetail = new ClaimDetail("name", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("token", new String[]{"value2a", "value2b"}, false);
        userinfo.put("name", userinfoNameClaimDetail);
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setRequestedClaims(claims);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setClientId("123");
        consentDetail.setSignature("signature");
        consentDetail.setAuthorizationScopes(Map.of("openid",true,"profile",true));

        Claims consentClaims = new Claims();
        userinfo = new HashMap<>();
        id_token = new HashMap<>();
        userinfoNameClaimDetail = new ClaimDetail("gender", new String[]{"value1a", "value1b"}, false);
        idTokenClaimDetail = new ClaimDetail("token", new String[]{"value1a", "value2b"}, false);
        userinfo.put("gender", userinfoNameClaimDetail);
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenClaimDetail);
        consentClaims.setUserinfo(userinfo);
        consentClaims.setId_token(id_token);

        consentDetail.setClaims(consentClaims);
        String hashCode =consentHelperService.hashUserConsent(consentClaims,consentDetail.getAuthorizationScopes());
        consentDetail.setHash(hashCode);
        consentDetail.setAcceptedClaims(Arrays.asList("name","email","gender"));
        consentDetail.setPermittedScopes(Arrays.asList("openid","profile","email"));

        List<String> acceptedClaims = consentDetail.getAcceptedClaims();
        List<String> permittedScopes = consentDetail.getPermittedScopes();
        String jws = consentDetail.getSignature();
        Collections.sort(acceptedClaims);
        Collections.sort(permittedScopes);
        Map<String,Object> payLoadMap = new HashMap<>();
        payLoadMap.put("accepted_claims",acceptedClaims);
        payLoadMap.put("permitted_scopes",permittedScopes);
        String signature = generateSignature(payLoadMap);

        consentDetail.setSignature(signature);
        consentDetail.setPsuToken("psutoken");

        Mockito.when(keyBindingHelperService.getPublicKey(Mockito.any(),Mockito.any())).thenReturn(publicKeyString);

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));
        consentHelperService.processConsent(oidcTransaction,true);

        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.CAPTURE);

    }


    @Test
    public void processConsent_withEmptyConsent_thenPass(){

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.empty());

        consentHelperService.processConsent(oidcTransaction,true);
        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.CAPTURE);

    }

    private String generateSignature(Map<String,Object> payloadMap) throws JOSEException, NoSuchAlgorithmException {

        // Define the header and payload
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID("123")
                .x509CertSHA256Thumbprint(new Base64URL("thumbprint"))
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
}