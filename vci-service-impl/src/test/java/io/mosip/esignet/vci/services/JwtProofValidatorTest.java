/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.vci.services;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.esignet.core.dto.vci.CredentialProof;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.vci.pop.JwtProofValidator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class JwtProofValidatorTest {

    @InjectMocks
    JwtProofValidator jwtProofValidator;

    private RSAKey rsaKey;

    @Before
    public void setup() {
        ReflectionTestUtils.setField(jwtProofValidator, "supportedAlgorithms", List.of("RS256"));
        ReflectionTestUtils.setField(jwtProofValidator, "credentialIdentifier","test-credential-issuer");
        rsaKey = generateRsaKey();
    }

    @Test
    public void testGetProofType_thenPass() {
        Assert.assertEquals("jwt", jwtProofValidator.getProofType());
    }

    @Test
    public void testValidate_withInvalidProof_thenFail() {
        CredentialProof credentialProof = new CredentialProof();
        Assert.assertFalse(jwtProofValidator.validate("clientId", "cNonce", credentialProof));

        credentialProof.setJwt("");
        Assert.assertFalse(jwtProofValidator.validate("clientId", "cNonce", credentialProof));

        credentialProof.setJwt("    ");
        Assert.assertFalse(jwtProofValidator.validate("clientId", "cNonce", credentialProof));
    }

    @Test
    public void testValidate_withValidJwtProof_thenPass() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .jwk(rsaKey.toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertTrue(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testValidate_withDetachedPayload_thenFail() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .jwk(rsaKey.toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize(true));
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testValidate_withInvalidNonce_thenFail() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .jwk(rsaKey.toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .claim("nonce", "1231221124124")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testValidate_withInvalidPublicKey_thenFail() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .jwk(generateRsaKey().toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testValidate_withInvalidAudience_thenFail() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .jwk(rsaKey.toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("mock-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testValidate_withInvalidIssuer_thenFail() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .jwk(rsaKey.toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("unknown-client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testValidate_withInvalidTypeInHeader_thenFail() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("jwt"))
                .jwk(rsaKey.toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testValidate_withBothKidAndJWKInHeader_thenFail() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .keyID("test-key-id")
                .jwk(rsaKey.toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testValidate_withNoKeyInHeader_thenFail() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testGetKeyMaterial_withValidProof_thenPass() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .jwk(rsaKey.toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        String keymaterial = jwtProofValidator.getKeyMaterial(credentialProof);
        Assert.assertNotNull(keymaterial);
    }

    @Test(expected = InvalidRequestException.class)
    public void testGetKeyMaterial_withInValidProof_thenFail() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setJwt("jwt");
        jwtProofValidator.getKeyMaterial(credentialProof);
    }

    private RSAKey generateRsaKey() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();
            // Convert public key to JWK format
            return new RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
                    .privateKey((RSAPrivateKey)keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {}
        return null;
    }

}
