package io.mosip.esignet.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.kernel.core.signatureutil.model.SignatureResponse;
import io.mosip.kernel.signature.dto.*;
import io.mosip.kernel.signature.service.SignatureService;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import static io.mosip.esignet.core.spi.TokenService.*;

@RunWith(MockitoJUnitRunner.class)
public class TokenServiceTest {

    @InjectMocks
    private TokenServiceImpl tokenService;

    @Mock
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    private static final RSAKey RSA_JWK;

    static {
        try {
            RSA_JWK = new RSAKeyGenerator(2048).generate();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setup() {
        ReflectionTestUtils.setField(tokenService, "signatureService", getSignatureService());
        ReflectionTestUtils.setField(tokenService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(tokenService, "issuerId", "test-issuer");
        ReflectionTestUtils.setField(tokenService, "maxClockSkew", 5);
    }

    @Test
    public void getIDToken_test() throws JSONException {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setClientId("client-id");
        transaction.setPartnerSpecificUserToken("psut");
        transaction.setNonce("nonce");
        transaction.setAuthTimeInSeconds(22);
        transaction.setServerNonce("server-nonce");
        transaction.setAHash("access-token-hash");
        transaction.setProvidedAuthFactors(new HashSet<>());
        Mockito.when(authenticationContextClassRefUtil.getACRs(Mockito.any())).thenReturn(Arrays.asList("generated-code", "static-code"));

        String token = tokenService.getIDToken(transaction);
        Assert.assertNotNull(token);
        JSONObject jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assert.assertEquals(transaction.getClientId(), jsonObject.get(AUD));
        Assert.assertEquals(transaction.getPartnerSpecificUserToken(), jsonObject.get(SUB));
        Assert.assertEquals(transaction.getAuthTimeInSeconds(), jsonObject.getLong(AUTH_TIME));
        Assert.assertEquals(transaction.getNonce(), jsonObject.get(NONCE));
        Assert.assertEquals("generated-code static-code", jsonObject.get(ACR));
        Assert.assertEquals("test-issuer", jsonObject.get(ISS));

        token = tokenService.getIDToken("subject", "audience",30, transaction, transaction.getServerNonce());
        Assert.assertNotNull(token);
        jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assert.assertEquals("audience", jsonObject.get(AUD));
        Assert.assertEquals("subject", jsonObject.get(SUB));
        Assert.assertEquals(transaction.getAuthTimeInSeconds(), jsonObject.getLong(AUTH_TIME));
        Assert.assertEquals(transaction.getServerNonce(), jsonObject.get(NONCE));
        Assert.assertEquals("generated-code static-code", jsonObject.get(ACR));
        Assert.assertEquals("test-issuer", jsonObject.get(ISS));
    }

    @Test
    public void getAccessToken_test() throws JSONException {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setClientId("client-id");
        transaction.setPartnerSpecificUserToken("psut");
        transaction.setPermittedScopes(Arrays.asList("read", "write"));
        String token = tokenService.getAccessToken(transaction, null);
        Assert.assertNotNull(token);
        JSONObject jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assert.assertEquals(transaction.getClientId(), jsonObject.get(AUD));
        Assert.assertEquals(transaction.getPartnerSpecificUserToken(), jsonObject.get(SUB));
        Assert.assertEquals("read write", jsonObject.get(SCOPE));
        Assert.assertEquals("test-issuer", jsonObject.get(ISS));
    }

    @Test
    public void getAccessTokenWithNonce_test() throws JSONException {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setClientId("client-id");
        transaction.setPartnerSpecificUserToken("psut");
        transaction.setPermittedScopes(Arrays.asList("read", "write"));
        String token = tokenService.getAccessToken(transaction, "test_cnonce");
        Assert.assertNotNull(token);
        JSONObject jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assert.assertEquals(transaction.getClientId(), jsonObject.get(AUD));
        Assert.assertEquals(transaction.getPartnerSpecificUserToken(), jsonObject.get(SUB));
        Assert.assertEquals("read write", jsonObject.get(SCOPE));
        Assert.assertEquals("test-issuer", jsonObject.get(ISS));
        Assert.assertEquals("test_cnonce", jsonObject.get(C_NONCE));
        Assert.assertNotNull(jsonObject.get(C_NONCE_EXPIRES_IN));
    }

    @Test
    public void getAccessToken_withConsentExpiryLessThanConfiguredTokenValidity_thenPass() throws JSONException {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setClientId("client-id");
        transaction.setPartnerSpecificUserToken("psut");
        transaction.setPermittedScopes(Arrays.asList("read", "write"));

        //Consent expire is set to 0, equivalent to not set
        transaction.setConsentExpireMinutes(0);
        String token = tokenService.getAccessToken(transaction, null);
        Assert.assertNotNull(token);
        JSONObject jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assert.assertTrue( jsonObject.getInt(EXP) > IdentityProviderUtil.getEpochSeconds());

        //consent expire is greater than access token expire datetime
        transaction.setConsentExpireMinutes(2);
        token = tokenService.getAccessToken(transaction, null);
        Assert.assertNotNull(token);
        jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        long diff = jsonObject.getInt(EXP) - IdentityProviderUtil.getEpochSeconds();
        Assert.assertTrue( diff == 60);

        //consent expire is less than access token expire datetime
        ReflectionTestUtils.setField(tokenService, "accessTokenExpireSeconds", 180);
        transaction.setConsentExpireMinutes(2);
        token = tokenService.getAccessToken(transaction, null);
        Assert.assertNotNull(token);
        jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        diff = jsonObject.getInt(EXP) - IdentityProviderUtil.getEpochSeconds();
        Assert.assertTrue( diff == 120);
    }

    @Test(expected = InvalidRequestException.class)
    public void verifyClientAssertionToken_withExpiredTokenNotWithinClockSkew_thenException() throws JOSEException {
        ReflectionTestUtils.setField(tokenService, "maxClockSkew", 0);
        JWSSigner signer = new RSASSASigner(RSA_JWK.toRSAPrivateKey());
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("client-id")
                .audience("audience")
                .issueTime(new Date(System.currentTimeMillis()))
                .expirationTime(new Date(System.currentTimeMillis() - 3000))
                .issuer("client-id")
                .jwtID(IdentityProviderUtil.createTransactionId(null))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        jwt.sign(signer);
        tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), jwt.serialize(),"audience");
    }

    @Test(expected = InvalidRequestException.class)
    public void verifyClientAssertionToken_withTokenWithoutJTI_thenException() throws JOSEException {
        JWSSigner signer = new RSASSASigner(RSA_JWK.toRSAPrivateKey());
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("client-id")
                .audience("audience")
                .issueTime(new Date(System.currentTimeMillis()))
                .expirationTime(new Date(System.currentTimeMillis()))
                .issuer("client-id")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        jwt.sign(signer);
        tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), jwt.serialize(),"audience");
    }

    @Test
    public void verifyClientAssertionToken_withExpiredTokenWithinClockSkew_thenPass() throws JOSEException {
        JWSSigner signer = new RSASSASigner(RSA_JWK.toRSAPrivateKey());
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("client-id")
                .audience("audience")
                .issueTime(new Date(System.currentTimeMillis()))
                .expirationTime(new Date(System.currentTimeMillis() - 3000))
                .issuer("client-id")
                .jwtID(IdentityProviderUtil.createTransactionId(null))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        jwt.sign(signer);
        tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), jwt.serialize(),"audience");
    }

    @Test(expected = EsignetException.class)
    public void verifyClientAssertionToken_withNullAssertion_thenFail() {
        tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), null,"audience");
    }

    @Test(expected = InvalidRequestException.class)
    public void verifyClientAssertionToken_withInvalidToken_thenFail() {
        tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), "client-assertion","audience");
    }

    @Test(expected = NotAuthenticatedException.class)
    public void verifyAccessToken_withNullToken_thenFail() {
        tokenService.verifyAccessToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), null);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void verifyAccessToken_withInvalidToken_thenFail() {
        tokenService.verifyAccessToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), "access_token");
    }

    @Test
    public void verifyAccessToken_withValidToken_thenPass() throws JOSEException {
        JWSSigner signer = new RSASSASigner(RSA_JWK.toRSAPrivateKey());
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .audience("audience")
                .issueTime(new Date(123000L))
                .expirationTime(new Date(System.currentTimeMillis()))
                .issuer("test-issuer")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        jwt.sign(signer);
        tokenService.verifyAccessToken("audience", "alice", jwt.serialize());
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void verifyAccessToken_withInvalidDataToken_thenFail() {
        tokenService.verifyAccessToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkJhZWxkdW5nIFVzZXIiLCJpYXQiOjE1MTYyMzkwMjJ9.qH7Zj_m3kY69kxhaQXTa-ivIpytKXXjZc1ZSmapZnGE");
    }

    @Test(expected = NotAuthenticatedException.class)
    public void verifyIdTokenHint_withNullToken_thenFail() {
        tokenService.verifyIdToken(null,"client-id");
    }

    @Test(expected = NotAuthenticatedException.class)
    public void verifyTokenHint_withInvalidToken_thenFail() {
        tokenService.verifyIdToken("id_token_hint","client-id");
    }

    @Test
    public void verifyTokenHint_withValidToken_thenPass() throws JOSEException {
        JWSSigner signer = new RSASSASigner(RSA_JWK.toRSAPrivateKey());

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .audience("audience")
                .issueTime(new Date(123000L))
                .expirationTime(new Date(System.currentTimeMillis()))
                .issuer("test-issuer")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        jwt.sign(signer);

        tokenService.verifyIdToken(jwt.serialize(),"audience");

        JWTClaimsSet claimsSetWithoutExp = new JWTClaimsSet.Builder()
                .subject("alice")
                .audience("audience")
                .issueTime(new Date(123000L))
                .issuer("test-issuer")
                .build();
        jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSetWithoutExp);
        jwt.sign(signer);
        try {
            tokenService.verifyIdToken(jwt.serialize(),"audience");
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals("invalid_token", e.getErrorCode());
        }
    }

    private SignatureService getSignatureService() {
        return new SignatureService() {

            @Override
            public ValidatorResponseDto validate(TimestampRequestDto timestampRequestDto) {
                return null;
            }

            @Override
            public SignatureResponse sign(SignRequestDto signRequestDto) {
                return null;
            }

            @Override
            public SignatureResponseDto signPDF(PDFSignatureRequestDto request) {
                return null;
            }

            @Override
            public JWTSignatureResponseDto jwtSign(JWTSignatureRequestDto jwtSignRequestDto) {
                JWTSignatureResponseDto responseDto = new JWTSignatureResponseDto();
                responseDto.setJwtSignedData(jwtSignRequestDto.getDataToSign());
                return responseDto;
            }

            @Override
            public JWTSignatureVerifyResponseDto jwtVerify(JWTSignatureVerifyRequestDto jwtSignatureVerifyRequestDto) {
                JWTSignatureVerifyResponseDto jwtSignatureVerifyResponseDto = new JWTSignatureVerifyResponseDto();
                if(jwtSignatureVerifyRequestDto.getJwtSignatureData()==null) {
                    jwtSignatureVerifyResponseDto.setSignatureValid(false);
                    return jwtSignatureVerifyResponseDto;
                }
                jwtSignatureVerifyResponseDto.setSignatureValid(true);
                return jwtSignatureVerifyResponseDto;
            }

            @Override
            public JWTSignatureResponseDto jwsSign(JWSSignatureRequestDto jwsSignRequestDto) {
                return null;
            }
        };
    }
}
