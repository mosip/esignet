/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.ClientDetail;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.kernel.cryptomanager.dto.JWTCipherResponseDto;
import io.mosip.kernel.cryptomanager.service.CryptomanagerService;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static io.mosip.esignet.api.util.ErrorConstants.DATA_EXCHANGE_FAILED;

@ExtendWith(MockitoExtension.class)
public class UserInfoResponseHelperTest {

    @InjectMocks
    private UserInfoResponseHelper userInfoResponseHelper;

    @Mock
    private TokenService tokenService;

    @Mock
    private ClientManagementService clientManagementService;

    @Mock
    private CryptomanagerService cryptomanagerService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private RsaJsonWebKey rsaJsonWebKey;
    private ClientDetail clientDetail;

    @BeforeEach
    public void setUp() throws Exception {
        rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
        rsaJsonWebKey.setKeyId("test-key-id");
        rsaJsonWebKey.setUse("enc");

        clientDetail = new ClientDetail();
        clientDetail.setId("test-client-id");

        clientDetail.setEncPublicKey(rsaJsonWebKey.toJson());

        ObjectNode additionalConfig = objectMapper.createObjectNode();
        additionalConfig.put("userinfo_response_type", "JWS");
        clientDetail.setAdditionalConfig(additionalConfig);
    }


    @Test
    public void processUserInfoResponse_withJWE_thenPass() throws Exception {
        String jwe = createTestJwe();

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(jwe);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWS");

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertEquals(jwe, result);
    }

    @Test
    public void processUserInfoResponse_withJWEInputAndJWEConfig_thenPass() throws Exception {
        String jwe = createTestJwe();

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(jwe);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWE");

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertEquals(jwe, result);
    }

    @Test
    public void processUserInfoResponse_withJWSInputAndJWSConfig_thenPass() throws Exception {
        String jws = createTestJws();

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(jws);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWS");

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertEquals(jws, result);
    }

    @Test
    public void processUserInfoResponse_withJWSInputAndJWEConfig_thenPass() throws Exception {
        String jws = createTestJws();
        String mockJwe = "header.encrypted_key.iv.ciphertext.auth_tag";

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(jws);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWE");

        Mockito.when(clientManagementService.getClientDetails("test-client-id")).thenReturn(clientDetail);

        JWTCipherResponseDto jweResponse = new JWTCipherResponseDto();
        jweResponse.setData(mockJwe);
        Mockito.when(cryptomanagerService.jwtEncrypt(Mockito.any())).thenReturn(jweResponse);

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertNotNull(result);
        String[] segments = result.split("\\.");
        Assertions.assertEquals(5, segments.length, "Result should be JWE with 5 segments");
    }


    @Test
    public void processUserInfoResponse_withPlainJsonAndJWSConfig_thenPass() throws Exception {
        String json = "{\"sub\":\"user123\",\"name\":\"Test User\"}";
        String base64Json = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(base64Json);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWS");

        Mockito.when(tokenService.getSignedJWT(Mockito.anyString(), Mockito.any()))
                .thenReturn(createTestJws());

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertNotNull(result);
        String[] segments = result.split("\\.");
        Assertions.assertEquals(3, segments.length, "Result should be JWS with 3 segments");

        Mockito.verify(tokenService, Mockito.times(1)).getSignedJWT(Mockito.anyString(), Mockito.any());
    }

    @Test
    public void processUserInfoResponse_withPlainJsonAndJWEConfig_thenPass() throws Exception {
        String json = "{\"sub\":\"user123\",\"name\":\"Test User\"}";
        String base64Json = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String mockJwe = "header.encrypted_key.iv.ciphertext.auth_tag";

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(base64Json);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWE");

        Mockito.when(tokenService.getSignedJWT(Mockito.anyString(), Mockito.any()))
                .thenReturn(createTestJws());
        Mockito.when(clientManagementService.getClientDetails("test-client-id")).thenReturn(clientDetail);

        JWTCipherResponseDto jweResponse = new JWTCipherResponseDto();
        jweResponse.setData(mockJwe);
        Mockito.when(cryptomanagerService.jwtEncrypt(Mockito.any())).thenReturn(jweResponse);

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertNotNull(result);
        String[] segments = result.split("\\.");
        Assertions.assertEquals(5, segments.length, "Result should be JWE with 5 segments");

        Mockito.verify(tokenService, Mockito.times(1)).getSignedJWT(Mockito.anyString(), Mockito.any());
    }

    @Test
    public void processUserInfoResponse_withRawJsonAndJWSConfig_thenPass() throws Exception {
        // Raw JSON (not base64 encoded)
        String rawJson = "{\"sub\":\"user123\",\"name\":\"Test User\"}";

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(rawJson);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWS");

        Mockito.when(tokenService.getSignedJWT(Mockito.anyString(), Mockito.any()))
                .thenReturn(createTestJws());

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertNotNull(result);
        String[] segments = result.split("\\.");
        Assertions.assertEquals(3, segments.length, "Result should be JWS with 3 segments");

        Mockito.verify(tokenService, Mockito.times(1)).getSignedJWT(Mockito.anyString(), Mockito.any());
    }

    @Test
    public void processUserInfoResponse_withRawJsonAndJWEConfig_thenPass() throws Exception {
        // Raw JSON (not base64 encoded)
        String rawJson = "{\"sub\":\"user123\",\"name\":\"Test User\"}";
        String mockJwe = "header.encrypted_key.iv.ciphertext.auth_tag";

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(rawJson);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWE");

        Mockito.when(tokenService.getSignedJWT(Mockito.anyString(), Mockito.any()))
                .thenReturn(createTestJws());
        Mockito.when(clientManagementService.getClientDetails("test-client-id")).thenReturn(clientDetail);

        JWTCipherResponseDto jweResponse = new JWTCipherResponseDto();
        jweResponse.setData(mockJwe);
        Mockito.when(cryptomanagerService.jwtEncrypt(Mockito.any())).thenReturn(jweResponse);

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertNotNull(result);
        String[] segments = result.split("\\.");
        Assertions.assertEquals(5, segments.length, "Result should be JWE with 5 segments");

        Mockito.verify(tokenService, Mockito.times(1)).getSignedJWT(Mockito.anyString(), Mockito.any());
    }


    @Test
    public void processUserInfoResponse_withNullInput_thenPass() {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(null);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWS");

        EsignetException exception = Assertions.assertThrows(EsignetException.class, () ->
            userInfoResponseHelper.processUserInfoResponse(transaction)
        );

        Assertions.assertEquals(DATA_EXCHANGE_FAILED, exception.getErrorCode());
    }

    @Test
    public void processUserInfoResponse_withEmptyInput_thenPass() {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc("");
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWS");

        EsignetException exception = Assertions.assertThrows(EsignetException.class, () ->
            userInfoResponseHelper.processUserInfoResponse(transaction)
        );

        Assertions.assertEquals(DATA_EXCHANGE_FAILED, exception.getErrorCode());
    }

    @Test
    public void processUserInfoResponse_withBlankInput_thenFail() {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc("   ");
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWS");

        EsignetException exception = Assertions.assertThrows(EsignetException.class, () ->
            userInfoResponseHelper.processUserInfoResponse(transaction)
        );

        Assertions.assertEquals(DATA_EXCHANGE_FAILED, exception.getErrorCode());
    }

    @Test
    public void processUserInfoResponse_withJWSInputAndJWEConfigButNoEncKey_thenFail() {
        clientDetail.setEncPublicKey(null);

        String jws = createTestJws();

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(jws);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWE");

        Mockito.when(clientManagementService.getClientDetails("test-client-id")).thenReturn(clientDetail);

        EsignetException exception = Assertions.assertThrows(EsignetException.class, () ->
            userInfoResponseHelper.processUserInfoResponse(transaction)
        );

        Assertions.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, exception.getErrorCode());
    }

    @Test
    public void processUserInfoResponse_withJWSInputAndJWEConfigButEmptyEncKey_thenFail() {
        clientDetail.setEncPublicKey("");

        String jws = createTestJws();

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(jws);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWE");

        Mockito.when(clientManagementService.getClientDetails("test-client-id")).thenReturn(clientDetail);

        EsignetException exception = Assertions.assertThrows(EsignetException.class, () ->
            userInfoResponseHelper.processUserInfoResponse(transaction)
        );

        Assertions.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, exception.getErrorCode());
    }

    @Test
    public void processUserInfoResponse_withJWSInputAndJWEConfigButInvalidEncKey_thenFail() {
        clientDetail.setEncPublicKey("invalid-key-format");

        String jws = createTestJws();

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(jws);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("JWE");

        Mockito.when(clientManagementService.getClientDetails("test-client-id")).thenReturn(clientDetail);

        EsignetException exception = Assertions.assertThrows(EsignetException.class, () ->
            userInfoResponseHelper.processUserInfoResponse(transaction)
        );

        Assertions.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, exception.getErrorCode());
    }

    @Test
    public void processUserInfoResponse_withNoUserInfoResponseTypeConfig_thenPass() throws Exception {
        String jws = createTestJws();

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(jws);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType(null);

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertEquals(jws, result);
    }

    @Test
    public void processUserInfoResponse_withEmptyAdditionalConfig_thenPass() throws Exception {
        String jws = createTestJws();

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(jws);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("");

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertEquals(jws, result);
    }

    @Test
    public void processUserInfoResponse_withLowercaseJWEConfig_thenPass() throws Exception {
        String jws = createTestJws();
        String mockJwe = "header.encrypted_key.iv.ciphertext.auth_tag";

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(jws);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("jwe");

        Mockito.when(clientManagementService.getClientDetails("test-client-id")).thenReturn(clientDetail);

        JWTCipherResponseDto jweResponse = new JWTCipherResponseDto();
        jweResponse.setData(mockJwe);
        Mockito.when(cryptomanagerService.jwtEncrypt(Mockito.any())).thenReturn(jweResponse);

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertNotNull(result);
        String[] segments = result.split("\\.");
        Assertions.assertEquals(5, segments.length, "Result should be JWE with 5 segments");
    }

    @Test
    public void processUserInfoResponse_withMixedCaseJWEConfig_thenPass() throws Exception {
        String jws = createTestJws();
        String mockJwe = "header.encrypted_key.iv.ciphertext.auth_tag";

        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setEncryptedKyc(jws);
        transaction.setClientId("test-client-id");
        transaction.setUserInfoResponseType("Jwe");

        Mockito.when(clientManagementService.getClientDetails("test-client-id")).thenReturn(clientDetail);

        JWTCipherResponseDto jweResponse = new JWTCipherResponseDto();
        jweResponse.setData(mockJwe);
        Mockito.when(cryptomanagerService.jwtEncrypt(Mockito.any())).thenReturn(jweResponse);

        String result = userInfoResponseHelper.processUserInfoResponse(transaction);

        Assertions.assertNotNull(result);
        String[] segments = result.split("\\.");
        Assertions.assertEquals(5, segments.length, "Result should be JWE with 5 segments");
    }

    private String createTestJws() {
        try {
            RSAKey signingKey = new RSAKeyGenerator(2048)
                    .keyID("signing-key")
                    .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                    .generate();

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject("user123")
                    .issuer("test-issuer")
                    .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claimsSet
            );

            signedJWT.sign(new RSASSASigner(signingKey));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test JWS", e);
        }
    }

    private String createTestJwe() {
        try {
            JsonWebEncryption jsonWebEncryption = new JsonWebEncryption();
            jsonWebEncryption.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.RSA_OAEP_256);
            jsonWebEncryption.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_256_GCM);
            jsonWebEncryption.setPayload("test-payload");
            jsonWebEncryption.setContentTypeHeaderValue("JWT");
            jsonWebEncryption.setKey(rsaJsonWebKey.getKey());
            jsonWebEncryption.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());
            return jsonWebEncryption.getCompactSerialization();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test JWE", e);
        }
    }
}
