/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding;

import com.nimbusds.jose.jwk.*;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.V1TBSCertificateGenerator;
import org.bouncycastle.x509.X509V1CertificateGenerator;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import javax.security.auth.x500.X500Principal;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

@Slf4j
public class TestUtil {

    public static JWK generateJWK_RSA() {
        // Generate the RSA key pair
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
        } catch (Exception e) {
            log.error("generateJWK_RSA failed", e);
        }
        return null;
    }

    public static JWK generateJWK_EC() {
        // Generate EC key pair with P-256 curve
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(Curve.P_256.toECParameterSpec());
            KeyPair keyPair = gen.generateKeyPair();

            // Convert to JWK format
            return new ECKey.Builder(Curve.P_256, (ECPublicKey) keyPair.getPublic())
                    .privateKey((ECPrivateKey) keyPair.getPrivate())
                    .build();
        } catch (Exception e) {
            log.error("generateJWK_EC failed", e);
        }
        return null;
    }
}
