/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.Base64URL;
import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.spi.ConsentService;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.jose4j.keys.X509Util;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;


@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class ConsentHelperServiceTest {


    @Mock
    ConsentService consentService;

    @Mock
    KeyBindingHelperService keyBindingHelperService;

    @InjectMocks
    ConsentHelperService consentHelperService;

    private static final String certificate;
    private static final String thumbprint;

    private static final Certificate certi;
    private static final PrivateKey privateKey;
    private static  String jwksString;

    static {
        try {
            jwksString="{ \"keys\": [ { \"p\": \"vwr964uY-ffRVDKiguaQWuKIwqXZWRnAi2uROVKrE1eM1IGtdbUR9DabYr7SaUJMiNygPdcKzsQHzUL-HeZ265VHhSgUPoz9vYyxYwV8JHwu5u5AaT8HET1ahIYYVCIptmgW3C9r8nCKefDj_rtUW_g2ugVSNSN2GcdVF8Kh_CM\", \"kty\": \"RSA\", \"q\": \"rZ_YY8zsAbwC-gD-HwZOipJ7nDUWAClv6CZJDYvO6hoT0Ie4gDlppQkHr8ma5xY-rSycGzfJTDz26FeTdGOKHUfIxgrKmhLf53Z4XmNbCpJlzv-YYtLJSvDIyzHveBmDh44cILhfsKUe6hYf9puSyRhrHhxAOp5dkO0Nn5zBon0\", \"d\": \"bXn_PyzoO-GrBz6yZihby5506eCQURuJcKH4Z0Ye_AYTS6UcwBX0HqNO9F5RZgcvNxkvmjsN0gldnw6w1lCpBv35CPIjoHAUIbCVRxbZbJx81FAz-52uAxr4kHsaXgipygp7b4uOzpo3PJwWFruFX5YwO7ixpGfYfuQyzEBuKy-uB00_cD4SWdFkMqb0HpjYusknxHBdEVoMKteva58a-6VhEfd1mukA3yYLZ2XVoLdGmiW586noqmqG1ykBxB9SNFolPSKTcYqKzzBNj7r_ueS1L9HxfAd5S91s1nGQPCUL63J2UMjE2WZ6u-mE4gdm-VtLEGi5nFYQbQEI_qxckQ\", \"e\": \"AQAB\", \"use\": \"sig\", \"kid\": \"4iY9qs5wtSf4NXbL3SWgV-MGuvTF6-VgKqbCELkI5n8\", \"qi\": \"LbtqYfYomhY3-8ai2Ysc5H1XVvrzLYGvw0OAcPX2B2OEkoXMzjntmXRUsak28o5eQEt3QdSMk3w_1K80tjuHGxn1sW5MttNMEm3lGF4OETLCg-jPVQmqyXvz5aTN1ETJydWTFI3k704Vzk6OhUAeBjlkEg-XGeAkDMwR4jPzl6E\", \"dp\": \"dXzdC66eNZwiMBWzu6zvufT3Bj3YnOMpdpSAizA75XlCMq5NbsYcdIPgq6mO3QzY5JJKOb2199K2uZUpklnZaKg1g75SNOWgZqHPtYX6ArYcYgjDs_X-8qs4r6eH7rXT0UnSRTcku8RaZQOwM0ghaS4M-fmrxOI6D0B3JFWeKOE\", \"alg\": \"RS256\", \"dq\": \"Eqqq0yAHB7C1CTfuGlvNOezByWuTr_TEiUsEc6ZiWpzvIG5XEcIab5nm76lXNB0aI_g12F9JDx9G1HgF7G9_O-Kp3VDvs1zwIayFCHDaKurOc1Dbi1RqO9pjXCVEP79OetZ2g7YO46j9B-HVEehsAZ7UdWpIJYU9PgWef1iVIOk\", \"n\": \"gZG256MN5pTqHlErv2_qfkIc57xKdVC41SjYZ3oR2ui5YEhhlxFkoRp6siAABSTCa-e7XzO7mf3yHTgwU0v92m_vUJhvjurbReCAqzjWwAjFppzDAHqvs0LNaVJ2ML3XrjWPmAfoOo1A8wGFBRVnzZso8RvPRwup5LVYOGE-kUDxPn7yJD01I7K2y4n0gqnzCcBNbJm3mayd3bU6fDNvV5w_LWfO1L6A1HyhEeyJi6bdBSQkSXVJfHJbja2_uu7gZowZRAySR6EL5yuzX7n0Ojc-4vEJmmAujmwQWpvJX-snrjzVg1l9pcBqGEBnkKyx4Q1qrE0gg0GUgmhF9QlDFw\" } ] }";
            certificate="-----BEGIN CERTIFICATE-----\n" +
                    "MIICrzCCAZegAwIBAgIGAYlJs0o1MA0GCSqGSIb3DQEBCwUAMBMxETAPBgNVBAMT\n" +
                    "CE1vY2stSURBMB4XDTIzMDcxMjA1MTM1NFoXDTIzMDcyMjA1MTM1NFowHjEcMBoG\n" +
                    "A1UEAxMTU2lkZGhhcnRoIEsgTWFuc291cjCCASIwDQYJKoZIhvcNAQEBBQADggEP\n" +
                    "ADCCAQoCggEBAIGRtuejDeaU6h5RK79v6n5CHOe8SnVQuNUo2Gd6EdrouWBIYZcR\n" +
                    "ZKEaerIgAAUkwmvnu18zu5n98h04MFNL/dpv71CYb47q20XggKs41sAIxaacwwB6\n" +
                    "r7NCzWlSdjC91641j5gH6DqNQPMBhQUVZ82bKPEbz0cLqeS1WDhhPpFA8T5+8iQ9\n" +
                    "NSOytsuJ9IKp8wnATWyZt5msnd21Onwzb1ecPy1nztS+gNR8oRHsiYum3QUkJEl1\n" +
                    "SXxyW42tv7ru4GaMGUQMkkehC+crs1+59Do3PuLxCZpgLo5sEFqbyV/rJ6481YNZ\n" +
                    "faXAahhAZ5CsseENaqxNIINBlIJoRfUJQxcCAwEAATANBgkqhkiG9w0BAQsFAAOC\n" +
                    "AQEAnYL76cc5PFNjxFyMef1se1CUByukky4IOm21JiTY+nUuIJolasipYVL6Bo7Y\n" +
                    "IObDEV7AW1hABXnUqQxFh2JUDnHrmQq0PbRIeWYItHGtvYBXBB8BY8xYZr93Rl+h\n" +
                    "LvXgFyXROZt9POT074EzCQZgxdMedMLg5GqouBbZKH3V5elEo0w4Wa5A8QrVF8dN\n" +
                    "qsKI1btm2vo0OMiP3bipyKvRVFyxZaGnVg7gSkRkNWIt+r6S+IJZ52v3TFSI5Yoo\n" +
                    "RLQHoQpcdOIqM/FcL5w3RBy5SACe7dipcWw5WA0m5MdWNBmTZPWcHPZmVixPGNpz\n" +
                    "NKM5jgxACiI4SDCEtTm1Dh7RiQ==\n" +
                    "-----END CERTIFICATE-----\n";

            thumbprint=generateThumbprintByCertificate(certificate);
            JWKSet jwkSet = JWKSet.parse(jwksString);
            // Find the private key JWK for signing
            JWK jwk = jwkSet.getKeys().get(0);
            privateKey = (RSAPrivateKey) jwk.toRSAKey().toPrivateKey();
            certi=convertToCertificate(certificate);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    public void addUserConsent_withValidLinkedTransaction_thenPass() throws Exception {
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
        oidcTransaction.setRequestedClaims(claims);



        List<String> acceptedClaims =oidcTransaction.getAcceptedClaims();
        List<String> permittedScopes =oidcTransaction.getPermittedScopes();
        Collections.sort(acceptedClaims);
        Collections.sort(permittedScopes);
        Map<String,Object> payLoadMap = new HashMap<>();
        payLoadMap.put("accepted_claims",acceptedClaims);
        payLoadMap.put("permitted_scopes",permittedScopes);
        String signature = generateSignature(payLoadMap);



        Mockito.when(keyBindingHelperService.getCertificate(Mockito.any(),Mockito.any())).thenReturn(certi);

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
    public void processConsent_withLinkedFlowAndValidConsentAndGetConsentActionAsNoCapture_thenPass() throws Exception {

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

        Mockito.when(keyBindingHelperService.getCertificate(Mockito.any(),Mockito.any())).thenReturn(certi);
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
        //payLoadMap.put("permitted_scopes",permittedScopes);

        String signature = generateSignature(payLoadMap);

        log.info("Signature for accepted claims and permitted scope is {}",signature);
        log.info("The thumbprint is {}",thumbprint);
        consentDetail.setSignature(signature);
        consentDetail.setPsuToken("psutoken");

       Mockito.when(keyBindingHelperService.getCertificate(Mockito.any(),Mockito.any())).thenReturn(certi);

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

    @Test
    public void toGenerateTestingSignatrue() throws Exception {
       // Payload payload = new Payload();
        List<String> acceptedClaims =Arrays.asList("name","email","gender");
        List<String> permittedScopes =new ArrayList<>();
        Collections.sort(acceptedClaims);
        //Collections.sort(permittedScopes);
        Map<String,Object> payLoadMap = new HashMap<>();
        payLoadMap.put("accepted_claims",acceptedClaims);
        payLoadMap.put("permitted_scopes",permittedScopes);
        String signature = generateSignature(payLoadMap);
        log.info("Signature for accepted claims and permitted scope is {}",signature);
    }

    private String generateSignature(Map<String,Object> payloadMap) throws Exception {

        // Define the header and payload
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .x509CertSHA256Thumbprint(new Base64URL(thumbprint))
                .build();

        JSONObject payloadJson = new JSONObject(payloadMap);
        Payload payload = new Payload(payloadJson.toJSONString());

        // Generate the JWT with a private key
        //privateKey=convertToPrivateKey(privateKeyString);
        JWSSigner signer = new RSASSASigner(privateKey);
        JWSObject jwsObject = new JWSObject(header,payload);
        jwsObject.sign(signer);
        String token = jwsObject.serialize();
        log.info("The token is {}",token);
        String parts[]=token.split("\\.");
        return parts[0]+"."+parts[2];
    }

    public static Certificate convertToCertificate(String certData) {
        try {
            StringReader strReader = new StringReader(certData);
            PemReader pemReader = new PemReader(strReader);
            PemObject pemObject = pemReader.readPemObject();
            if (Objects.isNull(pemObject)) {
                return null;
            }
            byte[] certBytes = pemObject.getContent();
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (IOException | CertificateException e) {
            return null;
        }
    }
    public static String generateThumbprintByCertificate(String cerifacate)
    {
        X509Certificate certificate = (X509Certificate) convertToCertificate(cerifacate);
        return X509Util.x5tS256(certificate);
    }
}