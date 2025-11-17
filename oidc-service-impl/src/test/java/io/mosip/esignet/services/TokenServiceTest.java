package io.mosip.esignet.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.esignet.core.constants.ErrorConstants;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static io.mosip.esignet.core.spi.TokenService.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class TokenServiceTest {

    @InjectMocks
    private TokenServiceImpl tokenService;

    @Mock
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Mock
    private CacheUtilService cacheUtilService;

    private static final RSAKey RSA_JWK;

    static {
        try {
            RSA_JWK = new RSAKeyGenerator(2048).generate();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    public void setup() {
        Map<String, Object> mockDiscoveryMap = new HashMap<>();
        mockDiscoveryMap.put("token_endpoint_auth_signing_alg_values_supported", Arrays.asList("RS256", "PS256", "ES256"));
        mockDiscoveryMap.put("issuer", "client-id");
        ReflectionTestUtils.setField(tokenService, "signatureService", getSignatureService());
        ReflectionTestUtils.setField(tokenService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(tokenService, "issuerId", "test-issuer");
        ReflectionTestUtils.setField(tokenService, "maxClockSkew", 5);
        ReflectionTestUtils.setField(tokenService, "discoveryMap", mockDiscoveryMap);
        ReflectionTestUtils.setField(tokenService, "uniqueJtiRequired", true);
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
        Assertions.assertNotNull(token);
        JSONObject jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assertions.assertEquals(transaction.getClientId(), jsonObject.get(AUD));
        Assertions.assertEquals(transaction.getPartnerSpecificUserToken(), jsonObject.get(SUB));
        Assertions.assertEquals(transaction.getAuthTimeInSeconds(), jsonObject.getLong(AUTH_TIME));
        Assertions.assertEquals(transaction.getNonce(), jsonObject.get(NONCE));
        Assertions.assertEquals("generated-code static-code", jsonObject.get(ACR));
        Assertions.assertEquals("test-issuer", jsonObject.get(ISS));

        token = tokenService.getIDToken("subject", "audience", 30, transaction, transaction.getServerNonce());
        Assertions.assertNotNull(token);
        jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assertions.assertEquals("audience", jsonObject.get(AUD));
        Assertions.assertEquals("subject", jsonObject.get(SUB));
        Assertions.assertEquals(transaction.getAuthTimeInSeconds(), jsonObject.getLong(AUTH_TIME));
        Assertions.assertEquals(transaction.getServerNonce(), jsonObject.get(NONCE));
        Assertions.assertEquals("generated-code static-code", jsonObject.get(ACR));
        Assertions.assertEquals("test-issuer", jsonObject.get(ISS));
    }

    @Test
    public void getAccessToken_test() throws JSONException {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setClientId("client-id");
        transaction.setPartnerSpecificUserToken("psut");
        transaction.setPermittedScopes(Arrays.asList("read", "write"));
        String token = tokenService.getAccessToken(transaction, null);
        Assertions.assertNotNull(token);
        JSONObject jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assertions.assertEquals(transaction.getClientId(), jsonObject.get(AUD));
        Assertions.assertEquals(transaction.getPartnerSpecificUserToken(), jsonObject.get(SUB));
        Assertions.assertEquals("read write", jsonObject.get(SCOPE));
        Assertions.assertEquals("test-issuer", jsonObject.get(ISS));
    }

    @Test
    public void getAccessTokenWithNonce_test() throws JSONException {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setClientId("client-id");
        transaction.setPartnerSpecificUserToken("psut");
        transaction.setPermittedScopes(Arrays.asList("read", "write"));
        String token = tokenService.getAccessToken(transaction, "test_cnonce");
        Assertions.assertNotNull(token);
        JSONObject jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assertions.assertEquals(transaction.getClientId(), jsonObject.get(AUD));
        Assertions.assertEquals(transaction.getPartnerSpecificUserToken(), jsonObject.get(SUB));
        Assertions.assertEquals("read write", jsonObject.get(SCOPE));
        Assertions.assertEquals("test-issuer", jsonObject.get(ISS));
        Assertions.assertEquals("test_cnonce", jsonObject.get(C_NONCE));
        Assertions.assertNotNull(jsonObject.get(C_NONCE_EXPIRES_IN));
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
        Assertions.assertNotNull(token);
        JSONObject jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assertions.assertTrue(jsonObject.getInt(EXP) > IdentityProviderUtil.getEpochSeconds());

        //consent expire is greater than access token expire datetime
        transaction.setConsentExpireMinutes(2);
        token = tokenService.getAccessToken(transaction, null);
        Assertions.assertNotNull(token);
        jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        long diff = jsonObject.getInt(EXP) - IdentityProviderUtil.getEpochSeconds();
        Assertions.assertTrue(diff == 60);

        //consent expire is less than access token expire datetime
        ReflectionTestUtils.setField(tokenService, "accessTokenExpireSeconds", 180);
        transaction.setConsentExpireMinutes(2);
        token = tokenService.getAccessToken(transaction, null);
        Assertions.assertNotNull(token);
        jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        diff = jsonObject.getInt(EXP) - IdentityProviderUtil.getEpochSeconds();
        Assertions.assertTrue(diff == 120);
    }

    @Test
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
        EsignetException ex = Assertions.assertThrows(EsignetException.class, () -> tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), jwt.serialize(), List.of("audience")));
        Assertions.assertEquals(ErrorConstants.INVALID_CLIENT, ex.getErrorCode());
    }

    @Test
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
        EsignetException ex = Assertions.assertThrows(EsignetException.class, () -> tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), jwt.serialize(), List.of("audience")));
        Assertions.assertEquals(ErrorConstants.INVALID_CLIENT, ex.getErrorCode());
    }

    @Test
    public void verifyClientAssertionToken_withExpiredTokenWithinClockSkew_thenPass() throws JOSEException {
        long now = System.currentTimeMillis();
        JWSSigner signer = new RSASSASigner(RSA_JWK.toRSAPrivateKey());
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("client-id")
                .audience("audience")
                .issueTime(new Date(now - 4000))
                .expirationTime(new Date(now - 3000))
                .issuer("client-id")
                .jwtID(IdentityProviderUtil.createTransactionId(null))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        jwt.sign(signer);
        tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), jwt.serialize(),List.of("audience"));
    }

    @Test
    public void verifyClientAssertionToken_withExactAudienceMatch_thenPass() throws JOSEException {
        long now = System.currentTimeMillis();

        JWSSigner signer = new RSASSASigner(RSA_JWK.toRSAPrivateKey());
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("client-id")
                .audience("issuer")
                .issueTime(new Date(now))
                .expirationTime(new Date(now + 4000))
                .issuer("client-id")
                .jwtID(IdentityProviderUtil.createTransactionId(null))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        jwt.sign(signer);

        tokenService.verifyClientAssertionToken(
                "client-id",
                RSA_JWK.toPublicJWK().toJSONString(),
                jwt.serialize(),
                List.of("issuer")
        );
    }

    @Test
    public void verifyClientAssertionToken_withGarbageAudience_thenFail() throws JOSEException {
        long now = System.currentTimeMillis();

        JWSSigner signer = new RSASSASigner(RSA_JWK.toRSAPrivateKey());
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("client-id")
                .audience("random")
                .issueTime(new Date(now))
                .expirationTime(new Date(now + 3000))
                .issuer("client-id")
                .jwtID(IdentityProviderUtil.createTransactionId(null))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        jwt.sign(signer);

        EsignetException ex = Assertions.assertThrows(EsignetException.class, () -> tokenService.verifyClientAssertionToken(
                "client-id",
                RSA_JWK.toPublicJWK().toJSONString(),
                jwt.serialize(),
                List.of("tokenendpoint")
        ));
        Assertions.assertEquals(ErrorConstants.INVALID_CLIENT, ex.getErrorCode());
    }

    @Test
    public void verifyClientAssertionToken_withNullAssertion_thenFail() {
        assertThrows(EsignetException.class, () ->
                tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), null, List.of("audience")));
    }

    @Test
    public void verifyClientAssertionToken_withInvalidToken_thenFail() {
        EsignetException ex = Assertions.assertThrows(EsignetException.class, () -> tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), "client-assertion", List.of("audience")));
        Assertions.assertEquals(ErrorConstants.INVALID_CLIENT, ex.getErrorCode());
    }

    @Test
    public void verifyAccessToken_withNullToken_thenFail() {
        assertThrows(NotAuthenticatedException.class, () ->
                tokenService.verifyAccessToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), null));
    }

    @Test
    public void verifyAccessToken_withInvalidToken_thenFail() {
        assertThrows(NotAuthenticatedException.class, () ->
                tokenService.verifyAccessToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), "access_token"));
    }

    @Test
    public void verifyClientAssertion_withDuplicateJTI_thenFail() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048)
                .algorithm(JWSAlgorithm.PS256)
                .keyID("rsa-ps256")
                .generate();

        JWSSigner signer = new RSASSASigner(rsaKey);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("client-id")
                .issuer("client-id")
                .audience("audience")
                .issueTime(new Date(123000L))
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .jwtID(IdentityProviderUtil.createTransactionId(null))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.PS256).keyID(rsaKey.getKeyID()).build(), claimsSet);
        signedJWT.sign(signer);
        Mockito.when(cacheUtilService.checkAndMarkJti(Mockito.anyString())).thenReturn(true);
        EsignetException ex = Assertions.assertThrows(EsignetException.class, () -> tokenService.verifyClientAssertionToken("client-id", rsaKey.toJSONString(), signedJWT.serialize(), List.of("audience")));
        Assertions.assertEquals(ErrorConstants.INVALID_CLIENT, ex.getErrorCode());
    }

    @Test
    public void verifyClientAssertion_withRSAPSSKey_thenPass() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048)
                .algorithm(JWSAlgorithm.PS256)
                .keyID("rsa-ps256")
                .generate();

        JWSSigner signer = new RSASSASigner(rsaKey);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("client-id")
                .issuer("client-id")
                .audience("audience")
                .issueTime(new Date(123000L))
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .jwtID(IdentityProviderUtil.createTransactionId(null))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.PS256).keyID(rsaKey.getKeyID()).build(), claimsSet);
        signedJWT.sign(signer);

        tokenService.verifyClientAssertionToken("client-id", rsaKey.toJSONString(), signedJWT.serialize(), List.of("audience"));
    }

    @Test
    public void verifyClientAssertion_withECKey_thenPass() throws Exception {
        ECKey ecKey = new ECKeyGenerator(Curve.P_256)
                .algorithm(JWSAlgorithm.ES256)
                .keyID("ec-es256")
                .generate();

        JWSSigner signer = new ECDSASigner(ecKey);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("client-id")
                .issuer("client-id")
                .audience("audience")
                .issueTime(new Date(123000L))
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .jwtID(IdentityProviderUtil.createTransactionId(null))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecKey.getKeyID()).build(), claimsSet);
        signedJWT.sign(signer);

        tokenService.verifyClientAssertionToken("client-id", ecKey.toJSONString(), signedJWT.serialize(), List.of("audience"));
    }

    @Test
    public void verifyAccessToken_withUnsupportedAlg_thenFail() throws JOSEException {
        JWSSigner signer = new RSASSASigner(RSA_JWK.toRSAPrivateKey());
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .audience("audience")
                .issueTime(new Date(123000L))
                .expirationTime(new Date(System.currentTimeMillis()))
                .issuer("test-issuer")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.PS384), claimsSet);
        jwt.sign(signer);
        EsignetException ex = Assertions.assertThrows(EsignetException.class, () -> tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), jwt.serialize(), List.of("audience")));
        Assertions.assertEquals(ErrorConstants.INVALID_CLIENT, ex.getErrorCode());
    }

    @Test
    public void verifyAccessToken_withNullAlg_thenFail() {
        String headerJson = "{\"typ\":\"JWT\"}";
        String payloadJson = "{"
                + "\"sub\":\"alice\","
                + "\"aud\":\"audience\","
                + "\"iat\":123000,"
                + "\"exp\":" + (System.currentTimeMillis() / 1000) + ","
                + "\"iss\":\"test-issuer\""
                + "}";
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String malformedJwt = encodedHeader + "." + encodedPayload + "." + "dummySignature";
        EsignetException ex = Assertions.assertThrows(EsignetException.class, () -> tokenService.verifyClientAssertionToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), malformedJwt, List.of("audience")));
        Assertions.assertEquals(ErrorConstants.INVALID_CLIENT, ex.getErrorCode());
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

    @Test
    public void verifyAccessToken_withInvalidDataToken_thenFail() {
        assertThrows(NotAuthenticatedException.class, () ->
                tokenService.verifyAccessToken("client-id", RSA_JWK.toPublicJWK().toJSONString(), "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkJhZWxkdW5nIFVzZXIiLCJpYXQiOjE1MTYyMzkwMjJ9.qH7Zj_m3kY69kxhaQXTa-ivIpytKXXjZc1ZSmapZnGE"));
    }

    @Test
    public void verifyIdTokenHint_withNullToken_thenFail() {
        assertThrows(NotAuthenticatedException.class, () ->
                tokenService.verifyIdToken(null, "client-id"));
    }

    @Test
    public void verifyTokenHint_withInvalidToken_thenFail() {
        assertThrows(NotAuthenticatedException.class, () ->
                tokenService.verifyIdToken("id_token_hint", "client-id"));
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

        tokenService.verifyIdToken(jwt.serialize(), "audience");

        JWTClaimsSet claimsSetWithoutExp = new JWTClaimsSet.Builder()
                .subject("alice")
                .audience("audience")
                .issueTime(new Date(123000L))
                .issuer("test-issuer")
                .build();
        jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSetWithoutExp);
        jwt.sign(signer);
        try {
            tokenService.verifyIdToken(jwt.serialize(), "audience");
            Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals("invalid_token", e.getErrorCode());
        }
    }

    @Test
    public void isValidDpopServerNonce_withValidNonce_thenReturnTrue() throws Exception {
        OIDCTransaction transaction = new OIDCTransaction();
        String nonce = "validNonce";
        transaction.setDpopServerNonce(nonce);
        transaction.setDpopServerNonceTTL(System.currentTimeMillis() + 15 * 10000);
        String dpopHeader = createDpopHeader(nonce);
        Assertions.assertTrue(tokenService.isValidDpopServerNonce(dpopHeader, transaction));
    }

    @Test
    public void isValidDpopServerNonce_withExpiredNonce_thenReturnFalse() throws Exception {
        OIDCTransaction transaction = new OIDCTransaction();
        String nonce = "validNonce";
        transaction.setDpopServerNonce(nonce);
        transaction.setDpopServerNonceTTL(System.currentTimeMillis() - 15 * 10000);
        String dpopHeader = createDpopHeader(nonce);
        Assertions.assertFalse(tokenService.isValidDpopServerNonce(dpopHeader, transaction));
    }

    @Test
    public void isValidDpopServerNonce_withInvalidNonce_thenReturnFalse() throws Exception {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setDpopServerNonce("validNonce");
        transaction.setDpopServerNonceTTL(System.currentTimeMillis() + 15 * 10000);
        String dpopHeader = createDpopHeader("invalidNonce");
        Assertions.assertFalse(tokenService.isValidDpopServerNonce(dpopHeader, transaction));
    }

    @Test
    public void isValidDpopServerNonce_withMissingNonceInPayload_thenReturnFalse() throws Exception {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setDpopServerNonce("validNonce");
        transaction.setDpopServerNonceTTL(System.currentTimeMillis() + 15 * 10000);
        String dpopHeader = createDpopHeader(null);
        Assertions.assertFalse(tokenService.isValidDpopServerNonce(dpopHeader, transaction));
    }

    @Test
    public void isValidDpopServerNonce_withMissingNonceInPayloadAndTransaction_thenReturnFalse() throws Exception {
        OIDCTransaction transaction = new OIDCTransaction();
        String dpopHeader = createDpopHeader(null);
        Assertions.assertFalse(tokenService.isValidDpopServerNonce(dpopHeader, transaction));
    }

    private String createDpopHeader(String nonce) throws Exception {
        JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("dpop+jwt"))
                .jwk(RSA_JWK.toPublicJWK());

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issueTime(Date.from(Instant.now()));

        if(nonce != null) {
            claimsBuilder.claim("nonce", nonce);
        }

        SignedJWT signedJWT = new SignedJWT(headerBuilder.build(), claimsBuilder.build());

        JWSSigner signer = new RSASSASigner(RSA_JWK);
        signedJWT.sign(signer);

        return signedJWT.serialize();
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
                if(jwtSignatureVerifyRequestDto.getJwtSignatureData() == null) {
                    jwtSignatureVerifyResponseDto.setSignatureValid(false);
                    return jwtSignatureVerifyResponseDto;
                }
                jwtSignatureVerifyResponseDto.setSignatureValid(true);
                return jwtSignatureVerifyResponseDto;
            }

            @Override
            public JWTSignatureResponseDto jwsSign(JWSSignatureRequestDto jwsSignRequestDto) {
                JWTSignatureResponseDto responseDto = new JWTSignatureResponseDto();
                responseDto.setJwtSignedData(jwsSignRequestDto.getDataToSign());
                return responseDto;
            }

            @Override
            public JWTSignatureResponseDto jwtSignV2(JWTSignatureRequestDtoV2 jwtSignatureRequestDtoV2) {
                return null;
            }

            @Override
            public JWTSignatureResponseDto jwsSignV2(JWSSignatureRequestDtoV2 jwsSignatureRequestDtoV2) {
                return null;
            }

            @Override
            public JWTSignatureVerifyResponseDto jwtVerifyV2(JWTSignatureVerifyRequestDto jwtSignatureVerifyRequestDto) {
                return null;
            }

        };
    }
}
