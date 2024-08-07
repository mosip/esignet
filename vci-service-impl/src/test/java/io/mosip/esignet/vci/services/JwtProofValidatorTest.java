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
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

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
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
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
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
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
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
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
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testValidate_withValidPrivateKey_thenFail() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .jwk(rsaKey)
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
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
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
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
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
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
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
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
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testValidate_withValidDIDAsKidInHeader_thenPass() throws Exception {
        Map<String, String> keyMap = new HashMap<>();
        keyMap.put("kid", "did:jwk:eyJrdHkiOiJSU0EiLCJlIjoiQVFBQiIsInVzZSI6InNpZyIsIm4iOiJweU52dE40V0ZXNE1qaVIwb2VpZkQ2VVY5UmlSNDdVTVl4UGhST0RrNUMyUWpqQ0xMRVJuN0xaQnNHX2hlMktVTWR0VDFCRXEwMzRwVkJ3QjBGVjFGX1NfWmRQZ0tybkVSSHNZUnF5dEx0LWVmNzlaLVUwM1JoWjRteFFBX2NBZDlWbFYxc2c3eGZSQmdqWV9uSEgtOUF6ZFQtUE83TFFXOTg5QTZuYWRyRFdweVVCZVhtY2ZvNThlVm12MVBzWHl3M0xiNzJldVI1QlBldGNPY1ktZUI5ZU1EcWZCYXdDRlR0alZLeFpfYVFqcllzTTNmeDlUc01SU1ZmWmctclltSGRoRlBTRHoxSUdsNTF1Vnd6emxKMGtXQ2tMTjJsX0FPM1cyV1cyX0VYY2pKZWN5VXdRV01mX1lBMDdURGJkUHFjT2V6VHdVY2RSdmhQVU5UYWZzVncifQ==");
        keyMap.put("p", "3XVDY_-ZjI7NH7p_ct4-S9hGULsAfwGMwqV9hInQZ_K3Zyd6fuKmx21AUvQo1UdG6AcZLKaMhTzSt2yQo07iemnG0cFMLoIh2j8W8NYI0ZrP9Gbm499IVG50BvL6_EahRM5aABqwqX0MCw91TUm5gm7oM-yKcisT45YGZrpxqGc");
        keyMap.put("kty","RSA");
        keyMap.put("q", "wTU15Su9Ql25I8lP6iPc1r6b-ULb4d2IlYn5A8oruoUXE467mTRWGdmL8vt6YfBoymIBu_GwRc8x-6rfUtC7HDKJ33IioubV5d_Bj0ePFC5T5NrNeLjFzgflsOX6QlnhdgIrYmZ3DUlcx-mWkuqOk9eCVbzw5u5BBafbyzlN5pE");
        keyMap.put("d","pDP6URVHWPJvP06tj0u8yWAE_HCRE8cRTl1_mW3hMhNZy3gBoxHpj_NXAgJI4jFtKrYx20yqaHGwJMQHPChZC5oWV_Iab59mJWlR5k1LL5veWd8ig_zKav80qhazCpkuVZbY8FRz9P0NRuIJCKguNJJW81_6MS6Uyg1B15eFPGM88ZXhS_OheODgSr4yCpg-QSofV9Dl0dnNDq7b1D7KzC08b0nrc3DIPGgqZ9vQdzsS8KPcMJUqbcEcsA7a-Dle84DBfe0WR9SIfb_bCovZnyKyXs8ikmcguz_Pf2PjvBVCU2_u_lylGkyaVQSFFf1hwisIy8FTzZQr1bO-8MuKwQ");
        keyMap.put("e","AQAB");
        keyMap.put("use","sig");
        keyMap.put("qi", "qmKIJDmDxk8E_xJWqpK7kE6MGvGGVnb4jE0P7Pxfb4Q3bny3olbNZt_VZGqkfBBsKrWdja-AzGgM4PdLPybiA7SnZKNYklBGnMQySZCeqnJDZ94RIzkYPw0TqgyfVjFpMO2rm-_gYHfFsa_Dj6F5upJn3KheuA06Z5pKqdTA0Es");
        keyMap.put("dp","KkeY9h52Uj9xKf2RF30Wp6RCyGbrUVQaa47sx8EH6NCN80O2P0NGVAynmy7CHPXes62nQL8LVOSn1h0EACmvU2-eZa1hvf9aNzCUUKaMSHgl-6MpsZePV48-15TMFh3l7Bz5UcvGrpURF4t7-aV5pU1HR6KBTAqtYWXjEvnFeUE");
        keyMap.put("dq", "HyV508DyWLGNOBSq-l7fqgq-UDeUBNxWuWytpQvBcucjqjZ3TaJfQvmMExaxSvqbmgykpOy4cviM4TpRmCMoFsqa6VeX99TopI8mv_dUPHefdKRFPXHkCWvCfnsElg-xRfnhjpJgHNc3ys6ARJzlcTXrv_CekUvVVZkS2LcbNNE");
        keyMap.put("n","pyNvtN4WFW4MjiR0oeifD6UV9RiR47UMYxPhRODk5C2QjjCLLERn7LZBsG_he2KUMdtT1BEq034pVBwB0FV1F_S_ZdPgKrnERHsYRqytLt-ef79Z-U03RhZ4mxQA_cAd9VlV1sg7xfRBgjY_nHH-9AzdT-PO7LQW989A6nadrDWpyUBeXmcfo58eVmv1PsXyw3Lb72euR5BPetcOcY-eB9eMDqfBawCFTtjVKxZ_aQjrYsM3fx9TsMRSVfZg-rYmHdhFPSDz1IGl51uVwzzlJ0kWCkLN2l_AO3W2WW2_EXcjJecyUwQWMf_YA07TDbdPqcOezTwUcdRvhPUNTafsVw");

        JSONObject jsonKey = new JSONObject(keyMap);
        RSAKey rsaKeyWithDidAsKId = RSAKey.parse(jsonKey);

        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .keyID(keyMap.get("kid"))
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKeyWithDidAsKId));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertTrue(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
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
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
    }

    @Test
    public void testValidate_withInvalidAlgInHeader_thenFail() throws JOSEException {
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS512)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .jwk(rsaKey.toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(Date.from(Instant.now(Clock.systemUTC())))
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
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
                .expirationTime(Date.from(Instant.now(Clock.systemUTC()).plusSeconds(1)))
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        String keymaterial = jwtProofValidator.getKeyMaterial(credentialProof);
        Assert.assertNotNull(keymaterial);
    }

    @Test
    public void testValidate_withExpiredValidProof_thenFail() throws JOSEException {
        Date iat = Date.from(Instant.now(Clock.systemUTC()));
        Date exp = Date.from(Instant.now(Clock.systemUTC()).minusSeconds(1));
        CredentialProof credentialProof = new CredentialProof();
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .jwk(rsaKey.toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("client-id")
                .audience("test-credential-issuer")
                .issueTime(iat)
                .expirationTime(exp)
                .claim("nonce", "test-nonce")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        credentialProof.setJwt(signedJWT.serialize());
        Assert.assertFalse(jwtProofValidator.validate("client-id", "test-nonce", credentialProof));
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
